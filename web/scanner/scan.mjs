/** KIK ad scanner: runs on the always-on PC, finds ad clips in episodes somebody started and reports them to the Worker.
 *  It also finds the intro and the closing credits of series episodes (marks-detect.mjs, episode-marks.mjs).
 *  node --import ./register.mjs scan.mjs            — loop forever (queues every 5 minutes)
 *  node --import ./register.mjs scan.mjs <link> <season> <episode> [voice]   — scan one episode now, print, do not report
 *  node --import ./register.mjs scan.mjs marks <link> <season> <episode> <voice>   — intro and credits of one episode, print only
 *  Env: KIK_SCANNER_TOKEN (same secret as the Worker's AD_SCANNER_TOKEN), KIK_ORIGIN, KIK_SCANNER_DATA, FFMPEG, KIK_SCANNER_AI=0 to turn the AI check off,
 *  KIK_SCANNER_CA — a CA bundle when this FFmpeg build has no system certificates. */
import {spawn} from 'node:child_process';import fs from 'node:fs';import path from 'node:path';import os from 'node:os';
import {film,stream,voiceOrder} from '../catalogue.mjs';import {titleKey} from '../sync.mjs';
import {fingerprint,RATE,toBase64,fromBase64} from '../ad-fingerprint.mjs';import {FPS,SIZE} from '../ad-align.mjs';
import {fromLibrary,discover,fromReference,merge,suggest,IDENT,CREDITS} from '../ad-detect.mjs';import {SPONSORED} from '../ad-breaks.mjs';
import {detectMarks,neighbours,START_WINDOW,END_WINDOW} from '../marks-detect.mjs';
const ORIGIN=process.env.KIK_ORIGIN||'https://kiktv.pages.dev',TOKEN=process.env.KIK_SCANNER_TOKEN||'',FFMPEG=process.env.FFMPEG||'ffmpeg',CA=process.env.KIK_SCANNER_CA||'';
if(new URL(ORIGIN).origin!==ORIGIN||!ORIGIN.startsWith('https://'))throw Error('KIK_ORIGIN must be an HTTPS origin');
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
// Eight seconds still put one or two frames into the shortest clip (8 s) and halve the Sentra requests of an episode (~110 instead of ~217).
const SAMPLE=8;
function features(url,{audio=true,samples=false}={}){return new Promise((resolve,reject)=>{
 const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'kik-scan-')),pcm=path.join(tmp,'a.pcm'),gray=path.join(tmp,'v.gray'),done=()=>fs.rmSync(tmp,{recursive:true,force:true});
 // FFmpeg accepts any TLS certificate unless told otherwise: a forged source on the network would plant fake ad breaks.
 const args=['-nostdin','-loglevel','error','-rw_timeout','30000000','-protocol_whitelist','https,tls,tcp,crypto','-tls_verify','1',...(CA?['-ca_file',CA]:[]),'-t','21600','-i',url,...(audio?['-map','0:a:0','-ac','1','-ar',String(RATE),'-f','s16le','-y',pcm]:[]),'-map','0:v:0','-vf',`fps=${FPS},crop=iw*0.7:ih*0.7,scale=12:9,format=gray`,'-f','rawvideo','-y',gray,
  ...(samples?['-map','0:v:0','-vf',`fps=1/${SAMPLE},crop=iw:ih*0.84:0:ih*0.08,scale=352:-2`,'-pix_fmt','yuvj420p','-q:v','6','-y',path.join(tmp,'s%05d.jpg')]:[])];
 const p=spawn(FFMPEG,args,{stdio:['ignore','ignore','pipe'],timeout:20*60_000});let err='';p.stderr.on('data',d=>err=(err+d).slice(-4096));
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
  // 15 frames a call are five Sentra requests. A 429, a call Sentra answered for no frame, or `limited` ends the AI for this episode:
  // the frames looked at so far still give their hints.
  try{for(let i=0;i<todo.length;i+=15){const batch=todo.slice(i,i+15);let r;
    try{r=await call('/api/ad-breaks/classify',{frames:batch.map(x=>'data:image/jpeg;base64,'+fs.readFileSync(x.file).toString('base64'))});}catch(err){if(/ 429$/.test(err.message)){log('AI limit reached, rest of the episode without AI');break;}throw err;}
    r.ad.forEach((p,k)=>samples.push({t:batch[k].t,p,o:r.overlay?.[k]}));
    if(r.limited||r.ad.every(p=>p==null)){log('AI stopped: '+(r.limited?'Sentra limit':'no answer')+', rest of the episode without AI');break;}}
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
async function call(p,body){const r=await fetch(ORIGIN+p,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+TOKEN},body:JSON.stringify(body),redirect:'manual',signal:AbortSignal.timeout(120000)});if(!r.ok)throw Error(p+' '+r.status);return r.json();}
const span=l=>l.map(([s,e])=>s.toFixed(1)+'–'+e.toFixed(1)).join(', ');
const show=r=>`${titleKey(r.link)} ${r.season||'-'}x${r.episode||'-'} v${r.voice}: ${span(r.breaks)||'no ads'}${r.suggested?.length?' · AI hint '+span(r.suggested):''}`;

// ---------- intro and credits: the first 10 and the last 7 minutes of an episode and of up to four neighbours ----------
// Media links come from KIK (/api/marks/source): Cloudflare asks the source, this PC only reads the CDN.
fs.mkdirSync(path.join(DATA,'marks'),{recursive:true});
const MARKS_KEPT=400;
/** 8 kHz mono PCM of one part of a file, and what FFmpeg said about it (its length). */
function audioPart(url,before){return new Promise((resolve,reject)=>{
 const args=['-nostdin','-loglevel','info','-rw_timeout','30000000','-protocol_whitelist','https,tls,tcp,crypto','-tls_verify','1',...(CA?['-ca_file',CA]:[]),...before,'-i',url,'-vn','-map','0:a:0','-ac','1','-ar',String(RATE),'-f','s16le','pipe:1'];
 const p=spawn(FFMPEG,args,{stdio:['ignore','pipe','pipe'],timeout:10*60_000}),chunks=[];let err='';
 p.stdout.on('data',d=>chunks.push(d));p.stderr.on('data',d=>{if(err.length<65536)err+=d;});
 p.on('error',reject);p.on('close',code=>code?reject(Error('ffmpeg '+code+': '+err.slice(-300))):resolve({pcm:Buffer.concat(chunks),log:err}));});}
const pcmFp=b=>fingerprint(new Int16Array(b.buffer,b.byteOffset,b.length>>1));
/** Fingerprints of both edges of a file. A CDN node sometimes ends a part early without an error: such a part is fetched again. */
async function edgesOfUrl(url){
 const end=await audioPart(url,['-sseof','-'+END_WINDOW]),m=/Duration: (\d+):(\d+):([\d.]+)/.exec(end.log);if(!m)throw Error('no duration');
 const duration=+m[1]*3600+ +m[2]*60+ +m[3],endSeconds=end.pcm.length/2/RATE;
 if(endSeconds<Math.min(END_WINDOW,duration)*0.95)throw Error('short end part '+endSeconds.toFixed(0)+' s');
 const start=await audioPart(url,['-t',String(START_WINDOW)]),startSeconds=start.pcm.length/2/RATE;
 if(startSeconds<Math.min(START_WINDOW,duration)*0.95)throw Error('short start part '+startSeconds.toFixed(0)+' s');
 return {duration,endFrom:Math.max(0,duration-endSeconds),start:pcmFp(start.pcm),end:pcmFp(end.pcm)};
}
const markFile=e=>path.join(DATA,'marks',poolName(e)+'.json');
async function edgesOf(e){
 const f=markFile(e);try{const c=JSON.parse(fs.readFileSync(f,'utf8'));return {...c,start:fromBase64(c.start),end:fromBase64(c.end)};}catch{}
 const s=await retry(()=>call('/api/marks/source',{link:e.link,voice:e.voice,season:e.season,episode:e.episode})),q=smallest(s);if(!q)throw Error('no stream');
 const urls=[...new Set([q.url,...q.alternatives||[]])].sort((a,b)=>Number(a.includes('.m3u8'))-Number(b.includes('.m3u8')));let got,last;
 for(const u of urls){try{got=await retry(()=>edgesOfUrl(u),3);break;}catch(err){last=err;}}if(!got)throw last;
 fs.writeFileSync(f,JSON.stringify({duration:got.duration,endFrom:got.endFrom,start:toBase64(got.start),end:toBase64(got.end)}));
 const kept=fs.readdirSync(path.join(DATA,'marks')).map(n=>path.join(DATA,'marks',n)).sort((a,b)=>fs.statSync(b).mtimeMs-fs.statSync(a).mtimeMs);
 for(const old of kept.slice(MARKS_KEPT))fs.rmSync(old,{force:true});
 return got;
}
async function markEpisode(item){
 const key=titleKey(item.link),ep={...item,key},cur=await edgesOf(ep);
 const list=(await retry(()=>call('/api/marks/source',{link:item.link,voice:item.voice,list:true}))).episodes,others=[];
 const near=neighbours(list,item.season,item.episode);
 for(const n of near)try{others.push(await edgesOf({...ep,season:n.season,episode:n.episode}));}catch(err){log('neighbour unavailable',key,n.season+'x'+n.episode,err.message.split('\n')[0]);}
 // The CDN sometimes answers nobody for a while: with no episode to compare, the item stays queued (three tries) instead of
 // being saved as «nothing found».
 if(near.length&&!others.length)throw Error('no neighbour episode could be read');
 const m=detectMarks(cur,others);return {...item,duration:cur.duration,intro:m.intro,credits:m.credits,compared:others.length};
}
const clock=t=>t==null?'—':Math.floor(t/60)+':'+(t%60).toFixed(1).padStart(4,'0');
const showMarks=r=>`${titleKey(r.link)} ${r.season}x${r.episode} v${r.voice}: intro ${r.intro.map(([a,b])=>clock(a)+'–'+clock(b)).join(', ')||'—'} · credits ${clock(r.credits)} (${r.compared} compared)`;

if(process.argv[2]==='marks'){if(!TOKEN){console.error('Set KIK_SCANNER_TOKEN');process.exit(1);}const [link,season,episode,voice]=process.argv.slice(3);log(showMarks(await markEpisode({link,voice,season,episode})));process.exit(0);}
if(process.argv[2]){const [link,season='',episode='',voice='35']=process.argv.slice(2);for(const r of await scan({link,voice,season,episode}))log(show(r));process.exit(0);}
if(!TOKEN){console.error('Set KIK_SCANNER_TOKEN');process.exit(1);}
for(;;){let items=[],marks=[];try{items=(await call('/api/ad-breaks/queue',{})).items;}catch(e){log('queue',e.message);}
 for(const item of items)try{const t=Date.now(),reports=await scan(item);await call('/api/ad-breaks/report',{items:reports});for(const r of reports)log(show(r),Math.round((Date.now()-t)/1000)+'s');}catch(e){log('failed',item.link,item.season,item.episode,e.message);}
 try{marks=(await call('/api/marks/queue',{})).items;}catch(e){log('marks queue',e.message);}
 for(const item of marks)try{const t=Date.now(),r=await markEpisode(item);await call('/api/marks/report',{items:[r]});log(showMarks(r),Math.round((Date.now()-t)/1000)+'s');}catch(e){log('marks failed',item.link,item.season,item.episode,e.message);}
 if(!items.length&&!marks.length)await new Promise(r=>setTimeout(r,IDLE));}
