import {test} from 'node:test';import assert from 'node:assert/strict';
import {fingerprint,RATE} from './ad-fingerprint.mjs';import {FPS,SIZE} from './ad-align.mjs';
import {fromLibrary,discover,fromReference,merge,suggest} from './ad-detect.mjs';
import {cleanBreaks,adSkip} from './public/ad-skip.js';import {adBreaks,adScanner,episodeOf,AD_FRAMES_DAILY} from './ad-breaks.mjs';

// Synthetic episodes: "shots" of noise with their own spectrum and their own picture, so every second is recognisable.
function rng(a){return ()=>{a=a+0x6d2b79f5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return ((t^t>>>14)>>>0)/4294967296;};}
function shot(seed,seconds){const r=rng(seed),pcm=new Int16Array(seconds*RATE),gray=new Uint8Array(seconds*FPS*SIZE);
 const tone=[...Array(6)].map(()=>[200+r()*1600,r()]);let a=0;
 for(let i=0;i<pcm.length;i++){if(i%800===0)a=0.3+r();let v=(r()-0.5)*800;for(const [f,w] of tone)v+=Math.sin(2*Math.PI*f*i/RATE+w*6)*1500*a*w;pcm[i]=v;}
 const base=Array.from({length:SIZE},()=>40+r()*170);for(let f=0;f<seconds*FPS;f++)for(let i=0;i<SIZE;i++)gray[f*SIZE+i]=Math.max(0,Math.min(255,base[i]+(r()-0.5)*6+f*0.3));
 return {pcm,gray};}
function film(seed,seconds){const parts=[];for(let t=0;t<seconds;t+=4)parts.push(shot(seed*1000+t,4));return parts;}
function join(parts){const pcm=new Int16Array(parts.reduce((n,p)=>n+p.pcm.length,0)),gray=new Uint8Array(parts.reduce((n,p)=>n+p.gray.length,0));let a=0,b=0;for(const p of parts){pcm.set(p.pcm,a);a+=p.pcm.length;gray.set(p.gray,b);b+=p.gray.length;}return {pcm,gray};}
const ad=film(99,20);
function episode(key,title,parts){const {pcm,gray}=join(parts);return {key,title,duration:pcm.length/RATE,audio:fingerprint(pcm),gray};}
// Ad clip spliced in at `at` seconds of a film of `seconds`.
const spliced=(seed,seconds,at,clip=ad)=>[...film(seed,at),...clip,...film(seed+50,seconds-at)];
const inside=(b,s,e)=>b[0]>=s-0.3&&b[1]<=e+0.3&&b[1]-b[0]>=e-s-2.5;

test('an ad clip shared by two different titles is found in both, within the clip',()=>{
 const a=episode('a','title-a',spliced(1,260,60)),b=episode('b','title-b',spliced(2,260,100));
 const d=discover(a,[b]);assert.equal(d.creatives.length,1);
 const [x]=merge(d.breaks,a.duration);assert.ok(inside(x,60,80),JSON.stringify(x));
 assert.ok(inside([d.elsewhere[0].start,d.elsewhere[0].end],100,120),JSON.stringify(d.elsewhere));
 // A third episode is then matched from the library alone.
 const c=episode('c','title-c',spliced(3,260,88)),[y]=merge(fromLibrary(c,[{id:'x',...d.creatives[0]}]),c.duration);assert.ok(inside(y,88,108),JSON.stringify(y));
});
test('a sponsor read spoken over the film is never cut: same sound, the film\'s own picture',()=>{
 const read=(seed)=>ad.map((p,i)=>({pcm:p.pcm,gray:shot(seed+i,4).gray}));
 const a=episode('a','title-a',spliced(1,260,60,read(500))),b=episode('b','title-b',spliced(2,260,100,read(700)));
 assert.deepEqual(discover(a,[b]).breaks,[]);
});
test('episodes of one title may share openings: never taken for ads',()=>{
 const a=episode('a','same',spliced(1,260,60)),b=episode('b','same',spliced(2,260,100));assert.deepEqual(discover(a,[b]).breaks,[]);
});
test('picture another voice of the episode lacks is reported to the frame; a replaced scene is not',()=>{
 const own=film(5,300),ref=episode('r','t',own),track=episode('k','t',[...own.slice(0,20),...ad,...own.slice(20)]);
 const [x]=fromReference(track,ref);assert.ok(Math.abs(x.start-80)<=0.4&&Math.abs(x.end-100)<=0.4,JSON.stringify(x));
 const swapped=episode('s','t',[...own.slice(0,20),...ad,...own.slice(25)]);assert.deepEqual(fromReference(swapped,ref),[]);
});
test('players jump only inside a sane break and only on the file that was scanned',()=>{
 assert.deepEqual(cleanBreaks([[900,920.56],[30,40],[2,9],[50,300],[1000,1000.5],[915,930],'x'],2500),[[30,40],[900,920.6]]);
 assert.deepEqual(cleanBreaks([[100,250],[300,450],[500,520]],3000),[],'more than five minutes of "ads" is not trusted');
 assert.deepEqual(cleanBreaks([[2400,2560]],2500),[]);
 const b=[[900,920.6]];assert.equal(adSkip(b,899.9,2500,2500),null);assert.equal(adSkip(b,900,2500,2500),920.6);assert.equal(adSkip(b,910,2501.5,2500),920.6);
 assert.equal(adSkip(b,919.5,2500,2500),null,'landing just before the end does not loop');assert.equal(adSkip(b,910,2510,2500),null,'another cut of the episode');assert.equal(adSkip([],910,2500,2500),null);
});
function db(){const tables={ad_breaks:new Map(),ad_scan_queue:new Map()};const k=a=>a.slice(0,4).join('|');
 const run=(sql,args)=>{if(sql.startsWith('SELECT duration'))return {first:tables.ad_breaks.get(k(args))||null};if(sql.startsWith('SELECT COUNT'))return {first:{n:tables.ad_scan_queue.size}};
  if(sql.startsWith('INSERT OR IGNORE INTO ad_scan_queue')){if(!tables.ad_scan_queue.has(k(args)))tables.ad_scan_queue.set(k(args),{title_key:args[0],voice:args[1],season:args[2],episode:args[3],requested_at:args[4],attempts:0});return {};}
  if(sql.startsWith('DELETE FROM ad_scan_queue WHERE requested_at'))return {};
  if(sql.startsWith('SELECT title_key'))return {all:{results:[...tables.ad_scan_queue.values()].filter(r=>r.attempts<3)}};
  if(sql.startsWith('UPDATE ad_scan_queue')){tables.ad_scan_queue.get(k(args)).attempts++;return {};}
  if(sql.startsWith('INSERT INTO ad_breaks')){tables.ad_breaks.set(k(args),{duration:args[4],breaks:args[5],suggested:args[6],scanned_at:args[7]});return {};}
  if(sql.startsWith('DELETE FROM ad_scan_queue WHERE title_key')){tables.ad_scan_queue.delete(k(args));return {};}
  if(sql.startsWith('INSERT INTO ai_daily_budget')){if(api.budget+args[1]>args[2])return {first:null};api.budget+=args[1];return {first:{calls:api.budget}};}
  throw Error(sql);};
 const prepare=sql=>{let args=[];const s={bind(...a){args=a;return s;},async first(){return run(sql,args).first;},async all(){return run(sql,args).all;},async run(){return run(sql,args);}};return s;};
 const api={tables,prepare,budget:0,async batch(list){for(const s of list)await s.run();}};return api;}
test('players read breaks; only sponsored voices queue; only the scanner with its secret reports',async()=>{
 const d=db(),link='https://hdrezka.ag/series/thriller/92903-mrachnye-2026.html',env={AD_SCANNER_TOKEN:'s'.repeat(40)};
 assert.deepEqual(await adBreaks(d,{link,voice:'110',season:'1',episode:'1'}),{breaks:[]});assert.equal(d.tables.ad_scan_queue.size,0);
 assert.deepEqual(await adBreaks(d,{link,voice:'35',season:'1',episode:'1'}),{breaks:[],pending:true});assert.equal(d.tables.ad_scan_queue.size,1);
 assert.throws(()=>episodeOf({link:'https://evil.example/x.html',voice:'35'}));assert.throws(()=>episodeOf({link,voice:'35;drop'}));
 const req=(path,token,body)=>new Request('https://kik.example'+path,{method:'POST',headers:token?{Authorization:'Bearer '+token}:{},body:JSON.stringify(body||{})});
 const read=async r=>r.json();
 await assert.rejects(()=>adScanner(req('/api/ad-breaks/queue'),d,env,'/api/ad-breaks/queue',read),e=>e.status===404);
 await assert.rejects(()=>adScanner(req('/api/ad-breaks/queue','x'.repeat(40)),d,env,'/api/ad-breaks/queue',read),e=>e.status===404);
 await assert.rejects(()=>adScanner(req('/api/ad-breaks/queue',env.AD_SCANNER_TOKEN),d,{},'/api/ad-breaks/queue',read),e=>e.status===404,'no secret configured: closed');
 const q=await adScanner(req('/api/ad-breaks/queue',env.AD_SCANNER_TOKEN),d,env,'/api/ad-breaks/queue',read);
 assert.deepEqual(q.items,[{link:'https://hdrezka.sh/series/thriller/92903-mrachnye-2026.html',voice:'35',season:'1',episode:'1'}]);
 const report={items:[{...q.items[0],duration:2542.8,breaks:[[911.1,929],[1738.2,1757.8],[5,8]]}]};
 assert.deepEqual(await adScanner(req('/api/ad-breaks/report',env.AD_SCANNER_TOKEN,report),d,env,'/api/ad-breaks/report',read),{saved:1});
 assert.equal(d.tables.ad_scan_queue.size,0);
 assert.deepEqual(await adBreaks(d,{link,voice:'35',season:'1',episode:'1'}),{duration:2542.8,breaks:[[911.1,929],[1738.2,1757.8]]});
});
test('an AI hint needs a run of whole-screen ad frames and ends on the hard cuts; banners over the film get nothing',()=>{
 // Film 0–100 s, a 24 s clip at 100–124 s, film again; the AI looked every 4 s at t = 2, 6, 10…
 const e=episode('x','t',[...film(7,100),...film(99,24),...film(8,96)]);
 const rate=(t,inside,overlay)=>({t,p:inside(t)?0.9:0.1,o:overlay&&inside(t)?0.8:0.15}),ad=t=>t>100&&t<124,samples=[];
 for(let t=2;t<220;t+=4)samples.push(rate(t,ad,false));
 const [h]=suggest(e,samples);assert.ok(Math.abs(h.start-100)<=0.3&&Math.abs(h.end-124)<=0.3,JSON.stringify(h));
 assert.deepEqual(suggest(e,samples.map(x=>rate(x.t,ad,true))),[],'a banner over a scene is never offered');
 assert.deepEqual(suggest(e,samples.map(x=>({...x,p:x.t===110?0.95:0.1}))),[],'one sure frame is not a clip');
 assert.deepEqual(suggest(e,samples,[[99,125]]),[],'already skipped automatically');
 // One doubtful frame inside the clip does not split it.
 const [g]=suggest(e,samples.map(x=>x.t===110?{...x,p:0.2}:x));assert.ok(Math.abs(g.end-124)<=0.3);
});
test('AI hints are stored apart from breaks and the frame check is behind the secret and a daily cap',async()=>{
 const d=db(),link='https://hdrezka.sh/series/thriller/92903-mrachnye-2026.html',env={AD_SCANNER_TOKEN:'s'.repeat(40)};
 const req=(path,body)=>new Request('https://kik.example'+path,{method:'POST',headers:{Authorization:'Bearer '+env.AD_SCANNER_TOKEN},body:JSON.stringify(body)}),read=async r=>r.json();
 await adScanner(req('/api/ad-breaks/report',{items:[{link,voice:'35',season:'1',episode:'3',duration:2687.7,breaks:[[900.3,918.1]],suggested:[[1735.1,1765.3],[905,915],[10,12]]}]}),d,env,'/api/ad-breaks/report',read);
 assert.deepEqual(await adBreaks(d,{link,voice:'35',season:'1',episode:'3'}),{duration:2687.7,breaks:[[900.3,918.1]],suggested:[[1735.1,1765.3]]});
 let used=0;const ai={run:async(model,input)=>{used++;assert.equal(model,'@cf/cloudflare/clef-flash');assert.ok(input.images[0].startsWith('data:image/jpeg;base64,'));return {answers:{ad:{type:'noul',noul:0.9},overlay:{type:'noul',noul:0.1}}};}};
 const frame='data:image/jpeg;base64,/9j/AAAA';
 d.budget=0;const r=await adScanner(req('/api/ad-breaks/classify',{frames:[frame,frame]}),d,{...env,AI:ai},'/api/ad-breaks/classify',read);assert.deepEqual(r,{ad:[0.9,0.9],overlay:[0.1,0.1]});assert.equal(used,2);
 d.budget=AD_FRAMES_DAILY;await assert.rejects(()=>adScanner(req('/api/ad-breaks/classify',{frames:[frame]}),d,{...env,AI:ai},'/api/ad-breaks/classify',read),e=>e.status===429);assert.equal(used,2);
 await assert.rejects(()=>adScanner(req('/api/ad-breaks/classify',{frames:['https://evil.example/x.jpg']}),d,{...env,AI:ai},'/api/ad-breaks/classify',read),e=>e.status===400);
});
test('the frame check asks Sentra first, all frames in one request, and Workers AI only when Sentra answers unusably',async()=>{
 const d=db(),env={AD_SCANNER_TOKEN:'s'.repeat(40),SENTRA_URL:'https://sentra.test',SENTRA_TOKEN:'t'},original=globalThis.fetch,sent=[];
 const req=body=>new Request('https://kik.example/api/ad-breaks/classify',{method:'POST',headers:{Authorization:'Bearer '+env.AD_SCANNER_TOKEN},body:JSON.stringify(body)}),read=async r=>r.json();
 const frame='data:image/jpeg;base64,/9j/AAAA';let used=0,answer='{"frames":[{"ad":0.8,"overlay":0},{"ad":0.1,"overlay":0.6}]}';
 const ai={run:async()=>{used++;return {answers:{ad:{type:'noul',noul:0.5},overlay:{type:'noul',noul:0.5}}};}};
 globalThis.fetch=async(url,init)=>{sent.push({url:String(url),headers:init.headers,body:JSON.parse(init.body)});return Response.json({choices:[{message:{content:answer}}]});};
 try{
  d.budget=0;assert.deepEqual(await adScanner(req({frames:[frame,frame]}),d,{...env,AI:ai},'/api/ad-breaks/classify',read),{ad:[0.8,0.1],overlay:[0,0.6]});
  assert.equal(used,0);assert.equal(sent.length,1);assert.equal(sent[0].headers['x-sentra-skill'],'kik-ad-frames');assert.equal(sent[0].body.model,'role:vision');
  assert.equal(sent[0].body.messages[0].content.filter(p=>p.type==='image_url').length,2);
  answer='{"frames":[{"ad":0.8,"overlay":0}]}';
  assert.deepEqual(await adScanner(req({frames:[frame,frame]}),d,{...env,AI:ai},'/api/ad-breaks/classify',read),{ad:[0.5,0.5],overlay:[0.5,0.5]});assert.equal(used,2);
  answer='{"frames":[{"ad":1,"overlay":0}]}';
  assert.deepEqual(await adScanner(req({frames:[frame]}),d,env,'/api/ad-breaks/classify',read),{ad:[1],overlay:[0]});
 }finally{globalThis.fetch=original;}
});
