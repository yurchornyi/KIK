import test from 'node:test';
import assert from 'node:assert/strict';
import {cachedStream,streamKey,STREAM_FRESH,STREAM_STALE} from './stream-cache.mjs';
import {mayProbe,PROBE_EVERY} from './catalogue.mjs';

function memory(){const store=new Map();return {store,async match(k){return store.get(k.url)?.clone();},async put(k,r){store.set(k.url,r);}};}
const links=n=>({qualities:[{label:'1080p',url:'https://cdn.example/'+n+'.mp4',alternatives:['https://cdn.example/'+n+'.mp4']}]});
const key=streamKey('https://kik.test',{link:'https://hdrezka.sh/films/drama/1-a.html',voice:'56',season:'',episode:''});

test('a found stream answers again for two hours without asking the source',async()=>{
 const cache=memory();let clock=1e12,asked=0;const now=()=>clock,load=async()=>links(++asked);
 assert.equal((await cachedStream(key,{load,cache,now})).qualities[0].url,'https://cdn.example/1.mp4');
 clock+=STREAM_FRESH-1000;
 assert.equal((await cachedStream(key,{load,cache,now})).qualities[0].url,'https://cdn.example/1.mp4');
 assert.equal(asked,1);
 clock+=2000;
 assert.equal((await cachedStream(key,{load,cache,now})).qualities[0].url,'https://cdn.example/2.mp4');
 assert.equal(asked,2);
});

test('a recovery attempt always asks the source and replaces the copy',async()=>{
 const cache=memory();let asked=0;const load=async()=>links(++asked);
 await cachedStream(key,{load,cache});
 assert.equal((await cachedStream(key,{attempt:1,load,cache})).qualities[0].url,'https://cdn.example/2.mp4');
 assert.equal((await cachedStream(key,{load,cache})).qualities[0].url,'https://cdn.example/2.mp4');
 assert.equal(asked,2);
});

test('while the source refuses or is busy, a copy up to six hours old still plays; an older one does not',async()=>{
 const cache=memory();let clock=1e12;const now=()=>clock;
 await cachedStream(key,{load:async()=>links(1),cache,now});
 clock+=STREAM_FRESH+1000;
 const refused=async()=>{throw Object.assign(Error('paused'),{sourcePaused:true,status:503});};
 assert.equal((await cachedStream(key,{load:refused,cache,now})).qualities[0].url,'https://cdn.example/1.mp4');
 const missing=async()=>{throw Object.assign(Error('gone'),{status:404});};
 await assert.rejects(cachedStream(key,{load:missing,cache,now}),/gone/);
 clock+=STREAM_STALE;
 await assert.rejects(cachedStream(key,{load:refused,cache,now}),/paused/);
});

test('an empty answer is not kept, and each voice and episode has its own copy',async()=>{
 const cache=memory();let asked=0;
 await cachedStream(key,{load:async()=>(asked++,{qualities:[]}),cache});
 await cachedStream(key,{load:async()=>(asked++,links(1)),cache});
 assert.equal(asked,2);
 const other=streamKey('https://kik.test',{link:'https://hdrezka.sh/films/drama/1-a.html',voice:'110',season:'',episode:''});
 assert.notEqual(other.url,key.url);
 assert.notEqual(streamKey('https://kik.test',{link:'https://hdrezka.sh/series/drama/2-b.html',voice:'56',season:'1',episode:'2'}).url,
  streamKey('https://kik.test',{link:'https://hdrezka.sh/series/drama/2-b.html',voice:'56',season:'1',episode:'3'}).url);
});

test('during a refusal pause one viewer request may try the source every 30 s, never background work or an IP ban',()=>{
 assert.equal(mayProbe({now:1000,probeAt:0}),true);
 assert.equal(mayProbe({now:1000,probeAt:1000+PROBE_EVERY}),false);
 assert.equal(mayProbe({now:1000,probeAt:0,background:true}),false);
 assert.equal(mayProbe({now:1000,probeAt:0,kind:'ip'}),false);
 assert.equal(mayProbe({now:1000,probeAt:0,kind:'refused'}),true);
});
