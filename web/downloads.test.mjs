import test from 'node:test';import assert from 'node:assert/strict';
import {createDownloads,chooseStream,looksLikePreview,formatBytes,defaultDownloadQuality,watchedCopies,nextToSave} from './public/downloads.js';

const MB=1<<20;
const link='https://hdrezka.sh/films/drama/1-test.html',series='https://hdrezka.sh/series/drama/2-show.html';
const keyOf=l=>new URL(l).pathname.slice(1);
const tick=()=>new Promise(r=>setImmediate(r));

/** The manager on fakes: an in-memory list, a disk of file sizes, a worker whose messages the test sends itself. */
function harness({qualities,owner='u1',online=true}={}){
 const memory=new Map(),storage={getItem:k=>memory.get(k)??null,setItem:(k,v)=>{memory.set(k,v);return true;},removeItem:k=>memory.delete(k)};
 const files=new Map(),removed=[],listeners={},posted=[],calls=[];let clock=1_000_000,isOnline=online,who=owner;
 const timers=[];
 const env={
  storage,files,removed,posted,calls,
  set online(v){isOnline=v;},set owner(v){who=v;},
  advance(ms){clock+=ms;for(const t of timers.splice(0))if(t.at<=clock)t.fn();else timers.push(t);},
  fire(type){for(const fn of listeners[type]||[])fn({key:'kik-downloads'});},
  async send(m){await env.worker.onmessage({data:m});await tick();},
  last(type){return posted.filter(m=>m.type===type).at(-1);}
 };
 const api=async(path,body)=>{calls.push({path,body});if(path!=='/api/stream')return {};return {qualities:typeof qualities==='function'?await qualities(body):qualities||[
  {label:'1080p Ultra',url:'/api/media/ultra.mp4',alternatives:['/api/media/ultra.mp4']},
  {label:'1080p',url:'/api/media/a1080.mp4',alternatives:['/api/media/a1080.mp4','/api/media/b1080.mp4']},
  {label:'720p',url:'/api/media/a720.mp4',alternatives:['/api/media/a720.mp4']}]};};
 const disk={supported:async()=>true,file:async id=>({id}),remove:async id=>{removed.push(id);files.delete(id+'.mp4');},names:async()=>[...files.keys()],estimate:async()=>({quota:100*1024*MB,usage:0}),persist:async()=>{}};
 env.downloads=createDownloads({api,storage,keyOf,owner:()=>who,disk,locks:null,online:()=>isOnline,now:()=>clock,
  events:{addEventListener:(t,fn)=>(listeners[t]||=[]).push(fn)},
  timers:{setTimeout:(fn,ms)=>{const t={fn,at:clock+ms};timers.push(t);return t;},clearTimeout:t=>{const i=timers.indexOf(t);if(i>=0)timers.splice(i,1);}},
  makeWorker:()=>env.worker={postMessage:m=>posted.push(m),onmessage:null}});
 return env;
}
const film={link,title:'Тест',voice:'1',voiceName:'Українська',wanted:'1080p',runtime:120*60000};

test('a download takes the wanted quality as an MP4, never the HLS playlist or the premium preview, and the next address on each retry',()=>{
 const qs=[{label:'1080p Ultra',url:'u.m3u8',alternatives:['u.m3u8','u.mp4']},{label:'1080p',url:'a.m3u8',alternatives:['a.m3u8','a.mp4','b.mp4']},{label:'720p',url:'c.mp4'},{label:'480p',url:'d.m3u8',alternatives:['d.mp4']}];
 assert.deepEqual(chooseStream(qs,{wanted:'1080p'}),{label:'1080p',url:'a.mp4',lower:true},'the source lists the playlist first; the MP4 is taken');
 assert.equal(chooseStream(qs,{wanted:'720p'}).label,'720p');
 assert.equal(chooseStream(qs,{wanted:'1080p',attempts:1}).url,'b.mp4','a retry tries the other MP4 address');
 assert.equal(chooseStream(qs,{wanted:'480p',quality:'720p'}).label,'720p','a started file keeps its quality');
 assert.equal(chooseStream(qs,{wanted:'1080p',avoid:['1080p']}).label,'720p');
 assert.equal(chooseStream(qs,{wanted:'240p'}).url,'d.mp4','below every quality: the smallest one');
 assert.equal(chooseStream([{label:'1080p Ultra',url:'u.mp4'}],{wanted:'1080p'}).label,'1080p Ultra','only Ultra: still worth a try');
 assert.equal(chooseStream([{label:'720p',url:'x.m3u8',alternatives:['x.m3u8']}],{wanted:'720p'}),null,'HLS only: nothing to save');
 assert.equal(chooseStream([{label:'720p',url:'https://kik.example/api/media/a.b.mp4?x=1'}],{}).url,'https://kik.example/api/media/a.b.mp4?x=1');
 assert.equal(chooseStream([],{}),null);
});

test('sizes read in Ukrainian, a preview is told by its size, phones save space by default',()=>{
 assert.equal(formatBytes(1.5*1024*MB),'1,5 ГБ');assert.equal(formatBytes(12*1024*MB),'12 ГБ');assert.equal(formatBytes(350*MB),'350 МБ');assert.equal(formatBytes(10),'1 КБ');
 assert.ok(looksLikePreview(20*MB,120*60000));assert.ok(looksLikePreview(20*MB,0),'unknown length: a tiny file is suspicious');
 assert.ok(!looksLikePreview(20*MB,5*60000),'a five-minute short may be that small');assert.ok(!looksLikePreview(900*MB,120*60000));
 assert.equal(defaultDownloadQuality(true),'720p');assert.equal(defaultDownloadQuality(false),'1080p');
});

test('downloads run one at a time through the relay, and the next starts when one is done',async()=>{
 const h=harness();await h.downloads.ready;
 assert.equal(h.downloads.add([film,{...film,link:series,title:'Серіал',series:true,season:'1',episode:'2'}]),2);await tick();
 const first=h.downloads.items()[0],second=h.downloads.items()[1];
 assert.equal(h.last('start').id,first.id);assert.equal(h.last('start').url,'/api/media/a1080.mp4');assert.equal(h.posted.filter(m=>m.type==='start').length,1,'only one at a time');
 assert.deepEqual(h.calls[0].body,{link,voice:'1',season:'',episode:'',recovery_attempt:0});
 assert.equal(h.downloads.get(first.id).status,'active');assert.equal(h.downloads.get(second.id).status,'queued');
 await h.send({type:'progress',id:first.id,bytes:400*MB,total:1600*MB});
 assert.equal(h.downloads.summary().active.bytes,400*MB);
 await h.send({type:'done',id:first.id,total:1600*MB});
 assert.equal(h.downloads.get(first.id).status,'done');assert.equal(h.downloads.find(link,'','').id,first.id);assert.equal(h.downloads.find(link,'','','2'),null,'another voice is not this copy');
 assert.equal(h.last('start').id,second.id);
 assert.equal(h.downloads.add([film]),0,'a saved film is not added twice');
});

test('an expired link or a dropped connection resumes from the same byte with a fresh link',async()=>{
 const h=harness();await h.downloads.ready;h.downloads.add([film]);await tick();
 const id=h.downloads.items()[0].id;
 await h.send({type:'progress',id,bytes:700*MB,total:1600*MB});
 await h.send({type:'error',id,code:'http',status:403,message:'Посилання на відео прострочене',bytes:700*MB});
 const x=h.downloads.get(id);assert.equal(x.status,'queued');assert.equal(x.attempts,1);assert.ok(x.retryAt>0);
 h.advance(2100);await tick();
 const start=h.last('start');assert.equal(h.posted.filter(m=>m.type==='start').length,2);
 assert.equal(start.total,1600*MB,'the worker is told the size, so it asks only for the rest');assert.equal(start.url,'/api/media/b1080.mp4','the retry takes the other address');
 assert.equal(h.calls.at(-1).body.recovery_attempt,1);
 await h.send({type:'progress',id,bytes:700*MB+3*MB,total:1600*MB});
 assert.equal(h.downloads.get(id).attempts,0,'progress after a retry clears the count');
});

test('without the internet a download waits and goes on by itself when the connection is back',async()=>{
 const h=harness();await h.downloads.ready;h.downloads.add([film]);await tick();const id=h.downloads.items()[0].id;
 h.online=false;await h.send({type:'error',id,code:'network',bytes:10*MB});
 assert.equal(h.downloads.get(id).status,'waiting');
 h.online=true;h.fire('online');await tick();
 assert.equal(h.downloads.get(id).status,'active');assert.equal(h.posted.filter(m=>m.type==='start').length,2);
});

test('the one-minute premium preview is dropped and the next quality down is saved instead',async()=>{
 const h=harness({qualities:[{label:'1080p',url:'/api/media/p.mp4'},{label:'720p',url:'/api/media/q.mp4'}]});await h.downloads.ready;
 h.downloads.add([film]);await tick();const id=h.downloads.items()[0].id;
 await h.send({type:'progress',id,bytes:2*MB,total:18*MB});
 assert.deepEqual(h.last('stop'),{type:'stop',id});
 await h.send({type:'stopped',id,bytes:3*MB});
 assert.ok(h.removed.includes(id),'the preview file is deleted once the worker lets go of it');
 assert.equal(h.last('start').url,'/api/media/q.mp4');assert.deepEqual(h.downloads.get(id).avoid,['1080p']);
});

test('removing or pausing a download stops it; a pause while the link loads leaves nothing stuck',async()=>{
 const h=harness();await h.downloads.ready;h.downloads.add([film,{...film,link:series,series:true,season:'1',episode:'1'}]);await tick();
 const [a,b]=h.downloads.items();
 await h.downloads.remove(a.id);assert.deepEqual(h.last('stop'),{type:'stop',id:a.id});
 await h.send({type:'stopped',id:a.id});assert.ok(h.removed.includes(a.id));assert.equal(h.last('start').id,b.id);
 assert.equal(h.downloads.items().length,1);
 h.downloads.pause(b.id);await h.send({type:'stopped',id:b.id});assert.equal(h.downloads.get(b.id).status,'paused');assert.ok(!h.removed.includes(b.id),'a paused file keeps its bytes');
 h.downloads.resume(b.id);await tick();assert.equal(h.last('start').id,b.id);
 // Paused before the link arrived: the queue must not wait for a worker that never started.
 let release;const gate=new Promise(r=>release=r);
 const h2=harness({qualities:()=>gate.then(()=>[{label:'720p',url:'/api/media/x.mp4'}])});await h2.downloads.ready;
 h2.downloads.add([film,{...film,link:series,series:true,season:'1',episode:'1'}]);await tick();
 const [c,d]=h2.downloads.items();h2.downloads.pause(c.id);release();await tick();await tick();
 assert.equal(h2.last('start').id,d.id,'the next one starts');
});

test('each account sees and downloads only its own files',async()=>{
 const h=harness();await h.downloads.ready;h.downloads.add([film]);await tick();const id=h.downloads.items()[0].id;
 h.owner='u2';assert.deepEqual(h.downloads.items(),[]);assert.equal(h.downloads.find(link,'',''),null);
 h.downloads.kick();assert.deepEqual(h.last('stop'),{type:'stop',id},'signed out: the running download stops');
 await h.send({type:'stopped',id});assert.equal(h.downloads.items().length,0);
 h.owner='u1';assert.equal(h.downloads.items()[0].status,'queued','back in the queue for its owner, not «active» for ever');
});

test('not enough space is said plainly; a 401 asks to sign in again',async()=>{
 const h=harness();await h.downloads.ready;h.downloads.add([film]);await tick();const id=h.downloads.items()[0].id;
 await h.send({type:'error',id,code:'space',need:2*1024*MB});
 assert.equal(h.downloads.get(id).status,'error');assert.match(h.downloads.get(id).error,/Недостатньо місця.*2,0 ГБ/);
 h.downloads.resume(id);await tick();await h.send({type:'error',id,code:'http',status:401});
 assert.equal(h.downloads.get(id).error,'Увійдіть знову, щоб завантажити');
});

test('only the offline service worker may fetch https images itself; pages keep connect-src self',async()=>{
 const worker=(await import('./server.mjs')).default,env={ASSETS:{fetch:async()=>new Response('x',{headers:{'Content-Type':'text/javascript'}})}};
 const sw=(await worker.fetch(new Request('https://kik.example/sw.js'),env,{})).headers.get('Content-Security-Policy');
 const page=(await worker.fetch(new Request('https://kik.example/'),env,{})).headers.get('Content-Security-Policy');
 assert.match(sw,/connect-src 'self' https:;/);assert.match(page,/connect-src 'self';/);assert.ok(sw.includes("script-src 'self'")&&sw.includes("worker-src 'self' blob:"));
});

test('smart downloads: a copy watched to the end after it was saved goes, the one playing stays, a rewatch is not cut short',()=>{
 const items=[{id:'a',status:'done',finished:1000,season:'1',episode:'1'},{id:'b',status:'done',finished:1000,season:'1',episode:'2'},{id:'c',status:'done',finished:5000,season:'1',episode:'3'},
  {id:'d',status:'active',finished:0,season:'1',episode:'4'},{id:'e',status:'done',finished:1000,season:'1',episode:'5'}];
 const progress={a:{pos:1290e3,dur:1300e3,at:2000},b:{pos:1290e3,dur:1300e3,at:2000},c:{pos:1290e3,dur:1300e3,at:4000},d:{pos:1300e3,dur:1300e3,at:9000},e:{pos:600e3,dur:1300e3,at:2000}};
 assert.deepEqual(watchedCopies(items,x=>progress[x.id]||null,'b').map(x=>x.id),['a'],'b plays now, c was watched before it was saved again, d is still downloading, e is half watched');
 assert.deepEqual(watchedCopies(items,()=>null,null),[]);assert.deepEqual(watchedCopies(null,()=>null),[]);
});

test('smart downloads: the next episode to save is the first one past everything on the device',()=>{
 const eps=[1,2,3,4,5].map(e=>({season:'1',episode:String(e),title:'Серія '+e})).concat([{season:'2',episode:'1'}]);
 const items=[{season:'1',episode:'2'},{season:'1',episode:'3'}];
 assert.equal(nextToSave(eps,items,{season:'1',episode:'2'}).episode,'4','episode 3 is already there');
 assert.deepEqual(nextToSave(eps,[{season:'1',episode:'5'}],{season:'1',episode:'5'}),{season:'2',episode:'1'},'on to the next season');
 assert.equal(nextToSave(eps,[{season:'2',episode:'1'}],{season:'2',episode:'1'}),null,'the last one: nothing');
 assert.equal(nextToSave(null,[],{season:'1',episode:'1'}),null);
});

test('a playlist saved by mistake (under 1 MB) is fetched again; an answer that is not video drops that quality',async()=>{
 // Its own list, disk and worker: one manager only, so nothing races it for the item.
 const memory=new Map(),storage={getItem:k=>memory.get(k)??null,setItem:(k,v)=>{memory.set(k,v);return true;}};
 storage.setItem('kik-downloads',JSON.stringify([{id:'broken1',owner:'u1',key:'films/drama/1-test.html',link,title:'Т',voice:'1',status:'done',bytes:4096,total:4096,finished:1,quality:'1080p',added:1}]));
 const files=new Map([['broken1.mp4',4096]]),removed=[],posted=[];let worker=null;
 const d=createDownloads({api:async()=>({qualities:[{label:'1080p',url:'/x.m3u8',alternatives:['/x.mp4']},{label:'720p',url:'/y.mp4'}]}),storage,keyOf,owner:()=>'u1',locks:null,online:()=>true,events:null,
  disk:{supported:async()=>true,file:async()=>({}),remove:async id=>{removed.push(id);files.delete(id+'.mp4');},names:async()=>[...files.keys()],estimate:async()=>null,persist:async()=>{}},
  makeWorker:()=>worker={postMessage:m=>posted.push(m),onmessage:null}});
 const lastStart=()=>posted.filter(m=>m.type==='start').at(-1);
 await d.ready;for(let i=0;i<5&&!lastStart();i++)await tick();
 assert.ok(removed.includes('broken1'),'the tiny file goes');assert.equal(lastStart().url,'/x.mp4','and comes back as the MP4');
 const before=posted.length;await worker.onmessage({data:{type:'error',id:'broken1',code:'format'}});for(let i=0;i<5&&posted.length===before;i++)await tick();
 assert.deepEqual(d.get('broken1').avoid,['1080p']);assert.equal(lastStart().url,'/y.mp4','a non-video answer moves to the next quality');
});
