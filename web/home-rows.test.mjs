import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createRowStore,KEEP_FOR,RETRIES,KEY} from './public/home-rows.js';

const memory=(init={})=>{const m=new Map(Object.entries(init));return {getItem:k=>m.get(k)??null,setItem:(k,v)=>m.set(k,v),map:m};};
const tick=()=>new Promise(r=>setImmediate(r));
const film=n=>({link:'https://hdrezka.sh/films/drama/'+n+'-f.html',title:'F'+n});

test('a row on its way holds its place, then shows its films and is kept for the next visit',async()=>{
 const storage=memory();let changed=0;let answer;
 const store=createRowStore({storage,load:()=>new Promise(r=>answer=r),onChange:()=>changed++});
 store.ask(['films']);
 assert.deepEqual(store.view('films'),{items:[],pending:true});
 await tick();answer([film(1),film(2)]);await tick();await tick();
 assert.equal(store.view('films').pending,false);assert.equal(store.view('films').items.length,2);assert.equal(changed,1);
 // The next visit shows them at once, before its own request answers.
 const next=createRowStore({storage,load:()=>new Promise(()=>{})});next.ask(['films']);
 assert.deepEqual(next.view('films'),{items:[film(1),film(2)],pending:false});
});

test('a kept row older than KEEP_FOR is not shown',()=>{
 const at=Date.now()-KEEP_FOR-1,storage=memory({[KEY]:JSON.stringify({films:{items:[film(1)],at}})});
 const store=createRowStore({storage,load:()=>new Promise(()=>{})});store.ask(['films']);
 assert.deepEqual(store.view('films'),{items:[],pending:true});
});

test('a failed request is asked again; after the last try a row with nothing kept leaves the page',async()=>{
 const waits=[];let calls=0,changed=0;
 const store=createRowStore({storage:memory(),load:async()=>{calls++;throw Error('down');},onChange:()=>changed++,wait:(fn,ms)=>waits.push([fn,ms])});
 store.ask(['anime']);await tick();await tick();
 assert.equal(calls,1);assert.deepEqual(waits.map(w=>w[1]),[RETRIES[0]]);assert.equal(store.view('anime').pending,true);
 for(let n=1;n<=RETRIES.length;n++){waits[n-1][0]();await tick();await tick();}
 assert.equal(calls,RETRIES.length+1);assert.deepEqual(store.view('anime'),{items:[],pending:false});assert.equal(changed,1);
});

test('a kept row stays on screen when the fresh request fails, and the same answer repaints nothing',async()=>{
 const storage=memory({[KEY]:JSON.stringify({films:{items:[film(1)],at:Date.now()}})});
 let changed=0;const failing=createRowStore({storage,load:async()=>{throw Error('down');},onChange:()=>changed++,wait:()=>{}});
 failing.ask(['films']);await tick();await tick();
 assert.deepEqual(failing.view('films'),{items:[film(1)],pending:false});assert.equal(changed,0);
 const same=createRowStore({storage,load:async()=>[film(1)],onChange:()=>changed++});same.ask(['films']);await tick();await tick();
 assert.equal(changed,0);
 // One request per row per visit, however often Home repaints.
 let calls=0;const once=createRowStore({storage:memory(),load:async()=>{calls++;return [film(2)];}});once.ask(['films']);once.ask(['films']);await tick();
 assert.equal(calls,1);
});

test('an empty answer is asked again and never replaces a kept row',async()=>{
 const storage=memory({[KEY]:JSON.stringify({series:{items:[film(1)],at:Date.now()}})}),waits=[];let calls=0,changed=0;
 const store=createRowStore({storage,load:async()=>{calls++;return calls<3?[]:[film(2)];},onChange:()=>changed++,wait:(fn,ms)=>waits.push([fn,ms])});
 store.ask(['series']);await tick();await tick();
 assert.deepEqual(store.view('series'),{items:[film(1)],pending:false},'the kept Top 10 stays');assert.deepEqual(waits.map(w=>w[1]),[RETRIES[0]]);
 waits[0][0]();await tick();await tick();waits[1][0]();await tick();await tick();
 assert.equal(calls,3);assert.deepEqual(store.view('series').items,[film(2)]);assert.equal(changed,1);
 const fresh=createRowStore({storage:memory(),load:async()=>[],wait:()=>{}});fresh.ask(['series']);await tick();await tick();
 assert.equal(fresh.view('series').pending,true,'nothing kept: its place is held while it is asked again');
});
