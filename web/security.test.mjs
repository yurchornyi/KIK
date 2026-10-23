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
