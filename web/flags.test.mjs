import {test} from 'node:test';import assert from 'node:assert/strict';
import {cleanFlags,flagsFrom,flagEvent,loadFlags,currentFlags,setFlags,FLAG_DEFAULTS,FLAG_KEYS} from './public/flags.js';
import worker from './server.mjs';

const ALL_ON={holidays:true,intro:true,loudness:true};
test('the owner switches are all on unless an answer says otherwise; odd values stay on',()=>{
 assert.deepEqual(FLAG_KEYS,['holidays','intro','loudness']);assert.deepEqual({...FLAG_DEFAULTS},ALL_ON);
 for(const raw of [null,undefined,'off',[],{holidays:'false',intro:0,loudness:null}])assert.deepEqual(cleanFlags(raw),ALL_ON);
 assert.deepEqual(cleanFlags({holidays:false,intro:true,extra:false}),{holidays:false,intro:true,loudness:true});
});
test('the newest admin event per switch is its state; other events and unknown switches are ignored',()=>{
 assert.deepEqual(flagsFrom([]),ALL_ON);
 assert.deepEqual(flagsFrom(['holidays:off','intro:off','holidays:on','volume:off','holidays','loudness:maybe',null]),{holidays:false,intro:false,loudness:true});
 assert.deepEqual(flagsFrom(['intro:on','intro:off']),ALL_ON);
});
test('the admin log reads a switch change in words',()=>{
 assert.equal(flagEvent('holidays:off'),'Свята: вимкнено для всіх');assert.equal(flagEvent('intro:on'),'Заставка перед відео: увімкнено для всіх');
 assert.equal(flagEvent('loudness:off'),'Однакова гучність фільмів: вимкнено для всіх');assert.equal(flagEvent('volume:off'),'Перемикач для всіх');
});
test('the page asks for the switches once and keeps the answer; an admin change replaces it',async()=>{
 assert.deepEqual(currentFlags(),ALL_ON,'all on before the answer');
 let calls=0;const fetcher=async url=>{calls++;assert.equal(url,'/api/flags');return Response.json({holidays:false,intro:true,loudness:true});};
 assert.deepEqual(await loadFlags(fetcher),{holidays:false,intro:true,loudness:true});await loadFlags(fetcher);
 assert.equal(calls,1);assert.equal(currentFlags().holidays,false);
 setFlags({holidays:true,intro:false});assert.deepEqual(currentFlags(),{holidays:true,intro:false,loudness:true});
});
test('GET /api/flags needs no sign-in, is kept five minutes and stays all on when D1 cannot answer',async()=>{
 const ask=db=>worker.fetch(new Request('https://kik.example/api/flags'),{DB:db},{waitUntil(){}});
 const stored={prepare(sql){assert.match(sql,/admin_events/);return {async first(){return {holidays:'holidays:off',intro:null,loudness:'loudness:on'};}};}};
 let r=await ask(stored);assert.equal(r.status,200);assert.equal(r.headers.get('Cache-Control'),'public, max-age=300');assert.deepEqual(await r.json(),{holidays:false,intro:true,loudness:true});
 r=await ask({prepare(){throw Error('D1 unavailable');}});assert.equal(r.status,200);assert.deepEqual(await r.json(),ALL_ON);
});
test('a holiday film shelf is only for the holidays KIK knows',async()=>{
 for(const id of ['','easter2','__proto__','constructor']){const r=await worker.fetch(new Request('https://kik.example/api/holiday-films?id='+id),{},{waitUntil(){}});assert.equal(r.status,400,id);}
});
