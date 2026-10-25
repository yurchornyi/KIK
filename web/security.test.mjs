import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readJson} from './request-body.mjs';
import {previewArtwork} from './artwork-preview.mjs';
import worker from './server.mjs';
import {hash} from './auth.mjs';
import {resolvePlayback} from './playback-sources.mjs';

function chunked(text) {
  const bytes = new TextEncoder().encode(text);
  return new Request('https://kik.example/api/test',{method:'POST',duplex:'half',body:new ReadableStream({start(c){c.enqueue(bytes);c.close();}})});
}
test('chunked requests are limited while reading, before the complete body is buffered', async () => {
  await assert.rejects(readJson(chunked('{"padding":"'+'x'.repeat(1024)+'"}'),100), e=>e.status===413);
  assert.deepEqual(await readJson(chunked('{"ok":true}'),100),{ok:true});
  for (const text of ['null','[]','"string"','42','true','{broken']) await assert.rejects(readJson(chunked(text)),e=>e.status===400);
});
test('the local artwork preview also bounds bodies before forwarding or buffering', async () => {
  let calls=0;
  await assert.rejects(previewArtwork(chunked('{"padding":"'+'x'.repeat(33000)+'"}'),async()=>{calls++;}),e=>e.status===413);
  assert.equal(calls,0);
});
test('account login throttling normalizes and hashes identities across source IPs', async () => {
  const keys=[];const env={LOGIN_LIMIT:{async limit({key}){keys.push(key);return {success:false};}}};
  for (const [email,ip] of [['USER@Example.COM','203.0.113.1'],['user@example.com','203.0.113.2']]) {
    const r=await worker.fetch(new Request('https://kik.example/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json','CF-Connecting-IP':ip},body:JSON.stringify({email,password:'test-password'})}),env,{});
    assert.equal(r.status,429);assert.equal(r.headers.get('Retry-After'),'60');
  }
  assert.deepEqual(keys,[await hash('user@example.com'),await hash('user@example.com')]);
});
test('a saturated login limit cannot block signing out', async () => {
  let authCalls=0;
  const r=await worker.fetch(new Request('https://kik.example/api/auth/logout',{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'}),{
    AUTH_LIMIT:{async limit(){authCalls++;return {success:false};}},API_LIMIT:{async limit(){return {success:true};}}
  },{});
  assert.equal(r.status,200);assert.equal(authCalls,0);
});
test('server playback resolution never calls the stream adapter for premium or unknown voice IDs', async () => {
  let reads=0;
  for(const voice of ['paid','unknown']) await assert.rejects(resolvePlayback('fixture',voice,'1','1',{
    loadFilm:async(_link,options={})=>({origin:options.host||'hdrezka.sh',voices:[{id:'paid',premium:true},{id:'free',premium:false}]}),
    readStream:async()=>{reads++;return {qualities:[]};}
  }));
  assert.equal(reads,0);
});
test('supplying another email cannot bypass the legacy account login limit', async () => {
  const keys=[],lookups=[];
  const env={LOGIN_LIMIT:{async limit({key}){keys.push(key);return {success:true};}},DB:{prepare(){return {bind(...args){lookups.push(args);return {async first(){return null;}};}};}}};
  for(const data of [{email:'different@example.com',username:'legacy.user'},{username:'legacy.user'}]) {
    const r=await worker.fetch(new Request('https://kik.example/api/auth/login',{method:'POST',body:JSON.stringify({...data,password:'wrong-password'})}),env,{});
    assert.equal(r.status,401);
  }
  assert.deepEqual(lookups,[['different@example.com',''],['','legacy.user']]);
  assert.deepEqual(keys,[await hash('different@example.com'),await hash('legacy.user')]);
});
test('a signed-out search for other spellings never reaches Sentra or Workers AI; a signed-in one does', async () => {
  const original={fetch:globalThis.fetch,caches:globalThis.caches},sent=[],store=new Map();
  globalThis.caches={default:{async match(k){return store.get(k.url)?.clone();},async put(k,r){store.set(k.url,r);}}};
  globalThis.fetch=async(url,opts)=>{if(String(url).startsWith('https://sentra.test'))sent.push(opts.headers['x-sentra-skill']);return String(url).startsWith('https://sentra.test')?Response.json({choices:[{message:{content:'{"titles":["Интерстеллар"]}'}}]}):Response.json({search:[],results:{bindings:[]}});};
  const token='a'.repeat(64),runs=[],calls=[];
  const db={prepare(sql){return {bind(...args){return {async first(){calls.push(sql);if(sql.includes('FROM sessions'))return args[0]===await hash(token)?{id:'u1',username:'u',email:'u@kik.test',role:'user',expires_at:Date.now()+90*86400000}:null;if(sql.includes('ai_daily_budget'))return {calls:1};return null;},async run(){return {meta:{changes:1}};}};}};}};
  const env={DB:db,SENTRA_URL:'https://sentra.test',SENTRA_TOKEN:'t',AI:{async run(){runs.push(1);return {response:'{"titles":["X"]}'};}}};
  try{
    const anonymous=await worker.fetch(new Request('https://kik.example/api/search-aliases?query=qwpoeiruty'),env,{waitUntil(){}});
    assert.deepEqual((await anonymous.json()).aliases,[]);assert.deepEqual(sent,[]);assert.equal(runs.length,0);
    assert.ok(!calls.some(sql=>sql.includes('ai_daily_budget')),'the daily AI budget is not spent');
    const member=await worker.fetch(new Request('https://kik.example/api/search-aliases?query=qwpoeiruty',{headers:{Authorization:'Bearer '+token}}),env,{waitUntil(){}});
    assert.deepEqual((await member.json()).aliases,['Интерстеллар']);assert.deepEqual(sent,['kik-search-fix']);
  }finally{globalThis.fetch=original.fetch;globalThis.caches=original.caches;}
});
