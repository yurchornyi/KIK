/** KIK ad scanner: runs on the always-on PC, finds ad clips in episodes somebody started and reports them to the Worker.
 *  node --import ./register.mjs scan.mjs            — loop forever (queue every 5 minutes)
 *  node --import ./register.mjs scan.mjs <link> <season> <episode> [voice]   — scan one episode now, print, do not report
 *  Env: KIK_SCANNER_TOKEN (same secret as the Worker's AD_SCANNER_TOKEN), KIK_ORIGIN, KIK_SCANNER_DATA, FFMPEG, KIK_SCANNER_AI=0 to turn the AI check off. */
import {spawn} from 'node:child_process';import fs from 'node:fs';import path from 'node:path';import os from 'node:os';
import {film,stream,voiceOrder} from '../catalogue.mjs';import {titleKey} from '../sync.mjs';
import {fingerprint,RATE,toBase64,fromBase64} from '../ad-fingerprint.mjs';import {FPS,SIZE} from '../ad-align.mjs';
import {fromLibrary,discover,fromReference,merge,suggest,IDENT,CREDITS} from '../ad-detect.mjs';import {SPONSORED} from '../ad-breaks.mjs';
const ORIGIN=process.env.KIK_ORIGIN||'https://kik.basic-175.workers.dev',TOKEN=process.env.KIK_SCANNER_TOKEN||'',FFMPEG=process.env.FFMPEG||'ffmpeg';
const AI=process.env.KIK_SCANNER_AI!=='0',DATA=process.env.KIK_SCANNER_DATA||path.join(os.homedir(),'.kik-ad-scanner'),POOL=40,IDLE=5*60_000;
const log=(...a)=>console.log(new Date().toISOString().slice(0,19),...a);
const retry=async(what,n=4)=>{let last;for(let i=0;i<n;i++)try{return await what();}catch(e){last=e;await new Promise(r=>setTimeout(r,2000*(i+1)));}throw last;};

// ---------- local state: known clips and the last POOL episodes (their fingerprints, for spotting a clip in two titles) ----------
fs.mkdirSync(path.join(DATA,'pool'),{recursive:true});
const file=n=>path.join(DATA,n),readJson=(n,d)=>{try{return JSON.parse(fs.readFileSync(file(n),'utf8'));}catch{return d;}};
const writeJson=(n,v)=>{fs.writeFileSync(file(n)+'.tmp',JSON.stringify(v));fs.renameSync(file(n)+'.tmp',file(n));};
// The first start begins with the clips learnt while building the scanner (October 2026).
if(!fs.existsSync(file('creatives.json')))fs.copyFileSync(new URL('./creatives-seed.json',import.meta.url),file('creatives.json'));
const library=readJson('creatives.json',[]).map(c=>({...c,audio:fromBase64(c.audio),gray:new Uint8Array(Buffer.from(c.gray,'base64'))}));
const saveLibrary=()=>writeJson('creatives.json',library.map(c=>({...c,frames:undefined,audio:toBase64(c.audio),gray:Buffer.from(c.gray).toString('base64')})));
let pool=readJson('pool.json',[]);const savePool=()=>writeJson('pool.json',pool);
const poolName=item=>Buffer.from([item.key,item.voice,item.season,item.episode].join('|')).toString('base64url');
const loadPooled=p=>{const n=path.join(DATA,'pool',poolName(p));return {...p,audio:fromBase64(fs.readFileSync(n+'.fp','utf8')),gray:new Uint8Array(fs.readFileSync(n+'.gray'))};};
function remember(e){const n=path.join(DATA,'pool',poolName(e));fs.writeFileSync(n+'.fp',toBase64(e.audio));fs.writeFileSync(n+'.gray',e.gray);
 pool=[{key:e.key,title:e.title,voice:e.voice,season:e.season,episode:e.episode,duration:e.duration,breaks:e.breaks,suggested:e.suggested},...pool.filter(p=>poolName(p)!==poolName(e))];
 for(const old of pool.slice(POOL))for(const x of ['.fp','.gray','.ai.json'])fs.rmSync(path.join(DATA,'pool',poolName(old))+x,{force:true});pool=pool.slice(0,POOL);savePool();}

// ---------- media: one pass of ffmpeg over the smallest quality gives 8 kHz mono audio and 12×9 thumbnails at 5 fps ----------
/** With `samples`, also one 352-px JPEG every SAMPLE seconds (ffmpeg's fps filter takes the frame from the middle of each interval) for the AI check (top and bottom strips with channel logos cut off);
 *  the caller removes them with done(). */
const SAMPLE=4;
function features(url,{audio=true,samples=false}={}){return new Promise((resolve,reject)=>{
 const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'kik-scan-')),pcm=path.join(tmp,'a.pcm'),gray=path.join(tmp,'v.gray'),done=()=>fs.rmSync(tmp,{recursive:true,force:true});
 const args=['-nostdin','-loglevel','error','-rw_timeout','30000000','-i',url,...(audio?['-map','0:a:0','-ac','1','-ar',String(RATE),'-f','s16le','-y',pcm]:[]),'-map','0:v:0','-vf',`fps=${FPS},crop=iw*0.7:ih*0.7,scale=12:9,format=gray`,'-f','rawvideo','-y',gray,
  ...(samples?['-map','0:v:0','-vf',`fps=1/${SAMPLE},crop=iw:ih*0.84:0:ih*0.08,scale=352:-2`,'-pix_fmt','yuvj420p','-q:v','6','-y',path.join(tmp,'s%05d.jpg')]:[])];
 const p=spawn(FFMPEG,args,{stdio:['ignore','ignore','pipe']});let err='';p.stderr.on('data',d=>err+=d);
 p.on('error',e=>{done();reject(e);});p.on('close',code=>{try{if(code)throw Error('ffmpeg '+code+': '+err.slice(-300));
  const g=new Uint8Array(fs.readFileSync(gray));let a=null;if(audio){const b=fs.readFileSync(pcm);a=fingerprint(new Int16Array(b.buffer,b.byteOffset,b.length>>1));}
  const jpegs=samples?fs.readdirSync(tmp).filter(n=>/^s\d+\.jpg$/.test(n)).sort().map((n,i)=>({t:i*SAMPLE+SAMPLE/2,file:path.join(tmp,n)})):[];
  if(!samples)done();resolve({audio:a,gray:g,duration:g.length/SIZE/FPS,jpegs,done});}catch(e){done();reject(e);}});});}
const smallest=s=>[...s.qualities].sort((a,b)=>(parseInt(a.label)||9999)-(parseInt(b.label)||9999))[0];
async function track(f,voice,season,episode){const s=await retry(()=>stream(f,voice,season,episode)),q=smallest(s);return [q.url,...q.alternatives||[]];}
const tryUrls=async(urls,options)=>{let last;for(const u of [...new Set(urls)].sort((a,b)=>Number(a.includes('.m3u8'))-Number(b.includes('.m3u8'))))try{return await retry(()=>features(u,options),2);}catch(e){last=e;}throw last;};

// ---------- one episode ----------
async function scan(item){
 const f=await retry(()=>film(item.link,{timeout:15000})),key=titleKey(item.link);
 const e={key,title:key,voice:item.voice,season:item.season,episode:item.episode,...await tryUrls(await track(f,item.voice,item.season,item.episode),{samples:AI})};
 try{return await analyse(item,f,key,e);}finally{e.done();}
}
async function analyse(item,f,key,e){
 const found=fromLibrary(e,library),others=pool.map(loadPooled),d=discover(e,others),fresh=[];found.push(...d.breaks);
 for(const [i,c] of d.creatives.entries())if(!found.some(x=>x.creative&&x.start<d.breaks[i].end&&d.breaks[i].start<x.end))fresh.push({id:'d'+Date.now().toString(36)+fresh.length,...c});
 // Another voice of the same episode is downloaded only when the library could not account for the usual two breaks.
 let referenced=false;
 if(merge(found,e.duration).length<2){
  const ref=f.voices.filter(v=>!v.premium&&!v.ads&&!SPONSORED.has(v.id)&&!/1win|coldfilm|колдфильм/i.test(v.name)).sort((a,b)=>voiceOrder(a)-voiceOrder(b))[0];
  if(ref)try{const r=await tryUrls(await track(f,ref.id,item.season,item.episode),{audio:false});referenced=true;for(const b of fromReference(e,r)){found.push(b);if(!found.some(x=>x.creative&&x.start<b.end&&b.start<x.end))fresh.push({id:'r'+Date.now().toString(36)+fresh.length,...b.clip,found:[key]});}}
  catch(err){log('reference voice unavailable',ref.name,err.message);}
 }
 e.breaks=merge(found,e.duration);e.suggested=[];
 // Last resort for a clip seen nowhere else: the AI looks at one frame every few seconds. Its finds only get a button.
 if(AI&&TOKEN&&(process.env.KIK_SCANNER_AI==='always'||!referenced&&e.breaks.length<2)){const samples=[],todo=e.jpegs.filter(x=>x.t>=IDENT&&x.t<=e.duration-CREDITS&&!e.breaks.some(([a,b])=>x.t>=a-SAMPLE&&x.t<=b+SAMPLE));
  try{for(let i=0;i<todo.length;i+=16){const batch=todo.slice(i,i+16),r=await call('/api/ad-breaks/classify',{frames:batch.map(x=>'data:image/jpeg;base64,'+fs.readFileSync(x.file).toString('base64'))});r.ad.forEach((p,k)=>samples.push({t:batch[k].t,p,o:r.overlay?.[k]}));}
   fs.writeFileSync(path.join(DATA,'pool',poolName(e))+'.ai.json',JSON.stringify(samples));e.suggested=suggest(e,samples,e.breaks).map(r=>[r.start,r.end]);log('AI looked at',samples.length,'frames:',e.suggested.length,'hint(s)');}
  catch(err){log('AI check skipped:',err.message);}}
 const reports=[{...item,duration:e.duration,breaks:e.breaks,suggested:e.suggested}];
 if(fresh.length){library.push(...fresh);saveLibrary();
  // A newly learnt clip may also sit in episodes scanned before: correct them too.
  for(const o of others){const more=fromLibrary(o,fresh);const breaks=merge([...o.breaks.map(([start,end])=>({start,end})),...more],o.duration);
   if(JSON.stringify(breaks)!==JSON.stringify(o.breaks)){reports.push({link:'https://hdrezka.sh/'+o.key,voice:o.voice,season:o.season,episode:o.episode,duration:o.duration,breaks,suggested:o.suggested||[]});const p=pool.find(p=>poolName(p)===poolName(o));if(p)p.breaks=breaks;}}
  savePool();}
 remember(e);return reports;
}
async function call(p,body){const r=await fetch(ORIGIN+p,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+TOKEN},body:JSON.stringify(body)});if(!r.ok)throw Error(p+' '+r.status);return r.json();}
const span=l=>l.map(([s,e])=>s.toFixed(1)+'–'+e.toFixed(1)).join(', ');
const show=r=>`${titleKey(r.link)} ${r.season||'-'}x${r.episode||'-'} v${r.voice}: ${span(r.breaks)||'no ads'}${r.suggested?.length?' · AI hint '+span(r.suggested):''}`;
if(process.argv[2]){const [link,season='',episode='',voice='35']=process.argv.slice(2);for(const r of await scan({link,voice,season,episode}))log(show(r));process.exit(0);}
if(!TOKEN){console.error('Set KIK_SCANNER_TOKEN');process.exit(1);}
for(;;){let items=[];try{items=(await call('/api/ad-breaks/queue',{})).items;}catch(e){log('queue',e.message);}
 for(const item of items)try{const t=Date.now(),reports=await scan(item);await call('/api/ad-breaks/report',{items:reports});for(const r of reports)log(show(r),Math.round((Date.now()-t)/1000)+'s');}catch(e){log('failed',item.link,item.season,item.episode,e.message);}
 if(!items.length)await new Promise(r=>setTimeout(r,IDLE));}
