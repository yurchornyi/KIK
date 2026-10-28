import test from 'node:test';import assert from 'node:assert/strict';import {complete,SENTRA_DAILY} from './sentra-ai.mjs';
import {RECS_DAILY} from './recommendations.mjs';import {SYNOPSIS_DAILY} from './synopsis.mjs';import {FIX_DAILY} from './search-fix.mjs';import {AD_FRAMES_DAILY,AD_FRAMES_PER_CALL} from './ad-breaks.mjs';
test('KIK\'s own daily caps together stay under Sentra\'s daily limit for KIK',()=>{
 // A frame call of 16 is six Sentra requests (three frames each).
 const frameRequests=Math.ceil(AD_FRAMES_DAILY/AD_FRAMES_PER_CALL)*Math.ceil(AD_FRAMES_PER_CALL/3);
 assert.ok(RECS_DAILY+SYNOPSIS_DAILY+FIX_DAILY+frameRequests<=SENTRA_DAILY,'caps add up to '+(RECS_DAILY+SYNOPSIS_DAILY+FIX_DAILY+frameRequests));
});
test('complete says why there is no answer and logs the HTTP status; a 429 is not logged as a failure',async()=>{
 const original=globalThis.fetch,warn=console.warn,logged=[];let reply;console.warn=m=>logged.push(m);globalThis.fetch=async()=>reply();
 const ask=(env={SENTRA_URL:'https://sentra.test',SENTRA_TOKEN:'t'})=>complete(env,{skill:'kik-test',messages:[],maxTokens:1,timeout:1000},raw=>raw==='ok'?raw:null,'test');
 try{
  assert.deepEqual(await ask({}),{value:null,why:'off'});assert.deepEqual(await ask({}),{value:null,why:'off'});assert.equal(logged.filter(m=>/SENTRA_URL/.test(m)).length,1);
  reply=()=>new Response('x',{status:502});assert.deepEqual(await ask(),{value:null,why:'error'});assert.match(logged.at(-1),/HTTP 502/);
  const n=logged.length;reply=()=>new Response('x',{status:429});assert.deepEqual(await ask(),{value:null,why:'limit'});assert.equal(logged.length,n);
  reply=()=>Response.json({choices:[{message:{content:'nope'}}]});assert.deepEqual(await ask(),{value:null,why:'rejected'});
  reply=()=>Response.json({choices:[{message:{content:'ok'}}]});assert.deepEqual(await ask(),{value:'ok',by:'sentra'});
 }finally{globalThis.fetch=original;console.warn=warn;}
});
test('an unusable answer is reported to Sentra as «rejected», with its sample and why',async()=>{
 const original=globalThis.fetch,warn=console.warn,calls=[];console.warn=()=>{};
 globalThis.fetch=async(url,init)=>{calls.push({url:String(url),body:JSON.parse(init.body)});return String(url).endsWith('/v1/feedback')?Response.json({ok:true}):Response.json({choices:[{message:{content:'[1,2,3]'}}]},{headers:{'x-sentra-sample-id':'kik-recommendations/123-abc'}});};
 const env={SENTRA_URL:'https://sentra.test/',SENTRA_TOKEN:'t'};
 try{
  assert.deepEqual(await complete(env,{skill:'kik-test',messages:[],maxTokens:1,timeout:1000},()=>{throw Error('AI ranking is only the input order');},'KIK recommendations'),{value:null,why:'rejected'});
  assert.equal(calls.at(-1).url,'https://sentra.test/v1/feedback');
  assert.deepEqual(calls.at(-1).body,{sample:'kik-recommendations/123-abc',signal:'rejected',note:'KIK recommendations: AI ranking is only the input order'});
  // A usable answer reports nothing.
  calls.length=0;assert.deepEqual(await complete(env,{skill:'kik-test',messages:[],maxTokens:1,timeout:1000},raw=>raw,'test'),{value:'[1,2,3]',by:'sentra'});assert.equal(calls.length,1);
 }finally{globalThis.fetch=original;console.warn=warn;}
});
