import test from 'node:test';import assert from 'node:assert/strict';
import {fingerprint,RATE,FPS} from './ad-fingerprint.mjs';
import {detectMarks,neighbours,union,intersect} from './marks-detect.mjs';
import {cleanIntro,cleanCredits,introSkip,openingLogo,nextOffer,NEXT_COUNTDOWN} from './public/episode-marks.js';
import {episodeMarks,marksScanner} from './episode-marks.mjs';

// Synthetic sound: every 4 s «shot» is its own noise and tones, so only copied stretches match.
function rng(a){return ()=>{a=a+0x6d2b79f5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return ((t^t>>>14)>>>0)/4294967296;};}
function shot(seed,seconds){const r=rng(seed),pcm=new Int16Array(seconds*RATE),tone=[...Array(6)].map(()=>[200+r()*1600,r()]);let a=0;
 for(let i=0;i<pcm.length;i++){if(i%800===0)a=0.3+r();let v=(r()-0.5)*800;for(const [f,w] of tone)v+=Math.sin(2*Math.PI*f*i/RATE+w*6)*1500*a*w;pcm[i]=v;}return pcm;}
const scene=(seed,seconds)=>Array.from({length:Math.ceil(seconds/4)},(_,i)=>shot(seed*1000+i,4));
const join=parts=>{const out=new Int16Array(parts.reduce((n,p)=>n+p.length,0));let at=0;for(const p of parts){out.set(p,at);at+=p.length;}return out;};
const LOGO=scene(7,4),THEME=scene(8,20),STING=scene(9,8);
/** An episode of `seconds` whose first 120 s and last 120 s are fingerprinted, as the scanner does with 10 and 7 minutes. */
function episode(n,{seconds=600,themeAt=40,recapOf=null,sting=true}={}){
 const head=[LOGO,...(recapOf?[scene(recapOf*100+1,12)]:[]),scene(n*100+2,themeAt-4-(recapOf?12:0)),THEME,scene(n*100+3,120-themeAt-20)].flat();
 const tail=[scene(n*100+4,60),...(sting?STING:scene(n*100+6,8)),scene(n*100+5,52)].flat();
 return {duration:seconds,endFrom:seconds-120,start:fingerprint(join(head)),end:fingerprint(join(tail))};
}

test('an intro is what two other episodes share near the start; a recap shared with one is not',()=>{
 const a=episode(1,{themeAt:40}),b=episode(2,{themeAt:52}),c=episode(3,{themeAt:36,recapOf:2}),d=episode(4,{themeAt:60});
 const m=detectMarks(c,[a,b,d]);
 assert.equal(m.intro.length,2,JSON.stringify(m.intro));
 assert.ok(m.intro[0][0]<1&&Math.abs(m.intro[0][1]-4)<1.5,'the logo the episode opens with');
 assert.ok(Math.abs(m.intro[1][0]-36)<1.5&&Math.abs(m.intro[1][1]-56)<1.5,'the theme, wherever it sits');
 assert.ok(Math.abs(m.credits-(600-60))<1.5,'credits start with the sting all episodes share');
 // The recap (12 s of episode 2's own scene 1) is shared with nobody else: never an «intro».
 assert.ok(!m.intro.some(([s,e])=>s<16&&e>6),'no recap');
});

test('near the end one other episode is enough; nothing shared means no credits',()=>{
 const a=episode(1),b=episode(2),c=episode(3,{sting:false});
 assert.ok(detectMarks(a,[b]).credits>500);
 assert.equal(detectMarks(c,[a,b]).credits,null);
 assert.deepEqual(detectMarks(a,[]),{intro:[],credits:null});
 // With a single neighbour only the opening minutes count as intro.
 assert.ok(detectMarks(a,[b]).intro.every(([s])=>s<180));
});

test('neighbours: the nearest four of the same season, either side',()=>{
 const list=[1,2,3,4,5,6].map(e=>({season:'1',episode:String(e)})).concat([{season:'2',episode:'1'}]);
 assert.deepEqual(neighbours(list,'1','1').map(e=>e.episode),['2','3','4']);
 assert.deepEqual(neighbours(list,'1','4').map(e=>e.episode),['3','5','2','6']);
 assert.deepEqual(neighbours(list,'2','1'),[]);assert.deepEqual(neighbours(list,'9','9'),[]);
 assert.deepEqual(union([[0,2],[2.5,4],[10,11]]),[[0,4],[10,11]]);assert.deepEqual(intersect([[0,10]],[[5,20],[30,40]]),[[5,10]]);
});

test('players: a logo at the start is passed, a longer intro gets a button, the next episode waits for the credits',()=>{
 const marks={duration:2668,intro:[[0.9,4.9],[176,183]],credits:2565};
 assert.equal(openingLogo(marks,2668.4),4.9);assert.equal(openingLogo(marks,2700),0,'another file: nothing');
 assert.equal(openingLogo({duration:100,intro:[[0,40]]},100),0,'a long opening is the viewer’s choice');
 assert.equal(introSkip(marks,178,2668),183);assert.equal(introSkip(marks,182,2668),null,'not in the last 1.5 s');assert.equal(introSkip(marks,10,2668),null);
 assert.deepEqual(nextOffer(marks,2668),{at:2565,sure:true});
 assert.deepEqual(nextOffer(null,1300),{at:1260,sure:false},'a guess sits in the last 40 s');assert.equal(nextOffer(null,60),null,'a 60-second preview gets no guess');
 assert.equal(nextOffer(marks,3000).sure,false,'credits of another file are not trusted');
 assert.equal(NEXT_COUNTDOWN,10);
 assert.deepEqual(cleanIntro([[0,1],[0.9,4.9],[2,6],[900,920],[100,400],'x'],2668),[[0.9,4.9]],'too short, overlapping, too late, too long, junk');
 assert.equal(cleanCredits(100,2668),null);assert.equal(cleanCredits(2665,2668),null);assert.equal(cleanCredits(2565.04,2668),2565);
});

/** D1 stand-in for the statements episode-marks.mjs runs. */
function db(){const marks=new Map(),queue=new Map(),k=a=>a.slice(0,4).join('|');
 const run=(sql,a)=>{
  if(sql.startsWith('SELECT duration'))return {first:marks.get(k(a))||null};
  if(sql.startsWith('SELECT COUNT'))return {first:{n:[...queue.values()].filter(q=>q.attempts<3).length}};
  if(sql.startsWith('INSERT OR IGNORE INTO mark_scan_queue')){if(!queue.has(k(a)))queue.set(k(a),{key:a[0],voice:a[1],season:a[2],episode:a[3],requested_at:a[4],attempts:0});return {};}
  if(sql.startsWith('DELETE FROM mark_scan_queue WHERE requested_at')){for(const [x,q] of queue)if(q.requested_at<a[0])queue.delete(x);return {};}
  if(sql.startsWith('SELECT title_key'))return {all:{results:[...queue.values()].filter(q=>q.attempts<3).slice(0,a[0]).map(q=>({title_key:q.key,voice:q.voice,season:q.season,episode:q.episode}))}};
  if(sql.startsWith('UPDATE mark_scan_queue')){queue.get(k(a)).attempts++;return {};}
  if(sql.startsWith('INSERT INTO episode_marks')){marks.set(k(a),{duration:a[4],intro:a[5],credits:a[6],scanned_at:a[7]});return {};}
  if(sql.startsWith('DELETE FROM mark_scan_queue WHERE title_key')){queue.delete(k(a));return {};}
  throw Error('unexpected '+sql);};
 const prepare=sql=>({bind:(...a)=>({first:async()=>run(sql,a).first,all:async()=>run(sql,a).all,run:async()=>run(sql,a)}),first:async()=>run(sql,[]).first});
 return {marks,queue,prepare,batch:async list=>Promise.all(list.map(s=>s.run()))};}
const link='https://hdrezka.sh/series/documentary/30365-formula-1.html',ep={link,voice:'359',season:'8',episode:'2'};

test('only a profile that wants skips queues an episode; known marks are read by everybody',async()=>{
 const d=db();
 assert.deepEqual(await episodeMarks(d,ep,1000,{queue:false}),{intro:[]});assert.equal(d.queue.size,0,'no toggle, no scan');
 assert.deepEqual(await episodeMarks(d,ep,1000),{intro:[],pending:true});assert.equal(d.queue.size,1);
 assert.deepEqual(await episodeMarks(d,{...ep,season:'',episode:''},1000),{intro:[]},'films are not scanned');
 await assert.rejects(episodeMarks(d,{...ep,voice:'x'}),/Некоректна серія/);
});

test('the scanner: its secret, links through KIK, a report checked like the players check it',async()=>{
 const d=db(),env={AD_SCANNER_TOKEN:'t'.repeat(40)},req=(token,b={})=>new Request('https://kik.example/x',{method:'POST',headers:token?{Authorization:'Bearer '+token}:{},body:JSON.stringify(b)});
 const read=async r=>r.json(),asked=[],resolve=async(...a)=>{asked.push(a);return a[4].method==='episodes'?{episodes:[{season:'8',episode:'1'}]}:{qualities:[{label:'360p',url:'https://cdn.example/a.mp4',alternatives:['https://cdn.example/b.mp4'],direct:['x']}]};};
 await assert.rejects(marksScanner(req(null),d,env,'/api/marks/queue',read),{status:404});
 await assert.rejects(marksScanner(req('w'.repeat(40)),d,env,'/api/marks/queue',read),{status:404});
 await episodeMarks(d,ep,1000);
 const q=await marksScanner(req(env.AD_SCANNER_TOKEN),d,env,'/api/marks/queue',read,2000);assert.deepEqual(q.items,[{link:'https://hdrezka.sh/series/documentary/30365-formula-1.html',voice:'359',season:'8',episode:'2'}]);
 const s=await marksScanner(req(env.AD_SCANNER_TOKEN,ep),d,env,'/api/marks/source',read,2000,{resolve});
 assert.deepEqual(s,{qualities:[{label:'360p',url:'https://cdn.example/a.mp4',alternatives:['https://cdn.example/b.mp4']}]});
 assert.deepEqual((await marksScanner(req(env.AD_SCANNER_TOKEN,{...ep,list:true}),d,env,'/api/marks/source',read,2000,{resolve})).episodes,[{season:'8',episode:'1'}]);
 assert.equal(asked[0][4].method,'stream');assert.equal(asked[1][4].method,'episodes');
 const r=await marksScanner(req(env.AD_SCANNER_TOKEN,{items:[{...ep,duration:2668,intro:[[0.9,4.9],[-1,9],[100,500]],credits:2565}]}),d,env,'/api/marks/report',read,3000);
 assert.deepEqual(r,{saved:1});assert.equal(d.queue.size,0);
 assert.deepEqual(await episodeMarks(d,ep,4000),{duration:2668,intro:[[0.9,4.9]],credits:2565});
 await assert.rejects(marksScanner(req(env.AD_SCANNER_TOKEN,{items:[{...ep,duration:5}]}),d,env,'/api/marks/report',read),/тривалість/);
 // Nothing found: asked again after three days, not on every start.
 await marksScanner(req(env.AD_SCANNER_TOKEN,{items:[{...ep,episode:'3',duration:2600,intro:[],credits:null}]}),d,env,'/api/marks/report',read,5000);
 assert.equal((await episodeMarks(d,{...ep,episode:'3'},6000)).pending,undefined);
 assert.equal((await episodeMarks(d,{...ep,episode:'3'},5000+3*86400_000+1)).pending,true);
});
