import test from 'node:test';import assert from 'node:assert/strict';
import {createStorage} from './public/storage.js';
import {beaconPayload,SYNC_BATCH} from './public/sync-queue.js';

test('page-close sync fits the beacon quota and sends the last playback position first',async()=>{
 const pending=Array.from({length:500},(_,i)=>({kind:'later',key:String(i),data:{title:'Українська назва '.repeat(100)}})),latest={kind:'progress',key:'episode',data:{pos:189810}};
 const payload=beaconPayload('profile',pending,latest),body=JSON.parse(await payload.text());
 assert.ok(payload.size<=60000);assert.deepEqual(body.records[0],latest);assert.equal(body.profile_id,'profile');assert.equal(body.source_schema,2);assert.equal(pending.length,500);
 assert.equal(JSON.parse(await beaconPayload('profile',[latest],latest).text()).records.length,1);
 assert.equal(JSON.parse(await beaconPayload('profile',pending.slice(0,300).map(r=>({...r,data:{}}))).text()).records.length,SYNC_BATCH);
});

test('blocked or full browser storage never throws into account, settings or playback',()=>{
 const blocked=createStorage(()=>{throw Error('Storage denied');});
 assert.equal(blocked.getItem('profile'),null);assert.equal(blocked.setItem('profile','a'),false);assert.equal(blocked.removeItem('profile'),false);assert.deepEqual(blocked.keys(),[]);
 const full=createStorage(()=>({getItem:()=>null,setItem(){throw Error('QuotaExceededError');},removeItem(){}}));assert.equal(full.setItem('kik:1:2','{}'),false);
 const memory={},available=createStorage(()=>Object.assign(memory,{getItem:key=>memory['$'+key]??null,setItem:(key,value)=>{memory['$'+key]=value;},removeItem:key=>delete memory['$'+key]}));
 assert.equal(available.setItem('profile','a'),true);assert.equal(available.getItem('profile'),'a');assert.equal(available.removeItem('profile'),true);assert.equal(available.getItem('profile'),null);
});
