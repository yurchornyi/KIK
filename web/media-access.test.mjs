import {test} from 'node:test';
import assert from 'node:assert/strict';
import worker from './server.mjs';
import {protectPlayback, serveMedia, MEDIA_TTL} from './media-access.mjs';
import {hash} from './auth.mjs';
import {orderedStreams} from './public/playback-recovery.js';

const token = 'a'.repeat(64), other = 'b'.repeat(64), origin = 'https://kik.example';
const source = 'https://video.example/movie.mp4?provider_secret=private';
const request = (url = origin + '/api/stream', session = token, headers = {}, method = 'GET') =>
  new Request(url, {method, headers: {...(session ? {Cookie: 'kik_session=' + session} : {}), ...headers}});
const env = {MEDIA_TOKEN_KEY: 'c'.repeat(64)};
const playback = url => ({qualities: [{label: '1080p', url, alternatives: [url]}]});

async function fixture() {
  const active = new Set([await hash(token), await hash(other)]);
  const DB = {prepare(sql) { return {bind(...args) { return {
    async first() { return active.has(args[0]) ? {id: args[0], role: 'user', expires_at: Date.now()+90*86400000} : null; },
    async run() { return {meta: {changes: 1}}; }
  }; }}; }};
  return {active, env: {...env, DB}};
}

test('media tickets hide upstream credentials, retain quality selection, and bind to the issuing session', async () => {
  const protectedResult = await protectPlayback(playback(source), request(), env);
  const url = protectedResult.qualities[0].url;
  assert.ok(url.startsWith(origin + '/api/media/'));
  assert.ok(!JSON.stringify(protectedResult).includes('video.example'));
  assert.ok(!JSON.stringify(protectedResult).includes('provider_secret'));
  assert.equal(orderedStreams(protectedResult.qualities, '1080p')[0].url, url);
  let calls = 0;
  const fetcher = async (target, options) => { calls++; assert.equal(target, source); assert.equal(options.headers.get('Cookie'), null); return new Response('video', {headers: {'Content-Type':'video/mp4'}}); };
  assert.equal(await (await serveMedia(request(url), env, {fetcher})).text(), 'video');
  await assert.rejects(serveMedia(request(url, other), env, {fetcher}), e => e.status === 403);
  await assert.rejects(serveMedia(request(url.replace('kik.example', 'other.example')), env, {fetcher}), e => e.status === 403);
  assert.equal(calls, 1);
});

test('anonymous requests, copied tickets, expired sessions, and revoked sessions cannot fetch video or ranges', async () => {
  const f = await fixture(), url = (await protectPlayback(playback(source), request(), f.env)).qualities[0].url;
  for (const method of ['GET', 'HEAD']) for (const headers of [{}, {Range:'bytes=0-1023'}]) {
    assert.equal((await worker.fetch(request(url, null, headers, method), f.env, {})).status, 401);
    assert.equal((await worker.fetch(request(url, other, headers, method), f.env, {})).status, 403);
  }
  f.active.delete(await hash(token));
  assert.equal((await worker.fetch(request(url), f.env, {})).status, 401);
});

test('tampered and expired tickets, URL targets, and missing server keys fail closed without upstream requests', async () => {
  const url = (await protectPlayback(playback(source), request(), env, Date.now()-MEDIA_TTL-1000)).qualities[0].url;
  let calls = 0; const fetcher = async () => {calls++; throw Error('Should not fetch');};
  await assert.rejects(serveMedia(request(url), env, {fetcher}), e => e.status === 403);
  await assert.rejects(serveMedia(request(url.replace(/\.(mp4)$/, 'x.mp4')), env, {fetcher}), e => e.status === 403);
  await assert.rejects(serveMedia(request(origin+'/api/media/?url='+encodeURIComponent(source)), env, {fetcher}), e => e.status === 403);
  await assert.rejects(protectPlayback(playback(source), request(), {}), e => e.status === 503);
  assert.equal(calls, 0);
});

test('MP4 ranges and HEAD work without leaking source headers, cookies, redirects, or cacheable bytes', async () => {
  const url = (await protectPlayback(playback(source), request(), env)).qualities[0].url;
  const fetcher = async (_, options) => {
    assert.equal(options.headers.get('Range'), 'bytes=5-9');
    assert.equal(options.headers.get('Authorization'), null);
    assert.equal(options.redirect, 'manual');
    return new Response(options.method === 'HEAD' ? null : '12345', {status: 206, headers: {'Content-Type':'video/mp4',
      'Content-Length':'5','Content-Range':'bytes 5-9/100','Accept-Ranges':'bytes','Set-Cookie':'source=secret','Location':source,'Access-Control-Allow-Origin':'*'}});
  };
  for (const method of ['GET', 'HEAD']) {
    const response = await serveMedia(request(url, token, {Range:'bytes=5-9'}, method), env, {fetcher});
    assert.equal(response.status, 206); assert.equal(response.headers.get('Content-Range'), 'bytes 5-9/100');
    assert.equal(response.headers.get('Cache-Control'), 'private, no-store');
    for (const header of ['Set-Cookie','Location','Access-Control-Allow-Origin']) assert.equal(response.headers.get(header), null);
    assert.equal(await response.text(), method === 'HEAD' ? '' : '12345');
  }
  await assert.rejects(serveMedia(request(url, token, {Range:'bytes=0-1,4-5'}), env, {fetcher}), e => e.status === 416);
});

test('HLS playlists, variants, segments, maps, and encryption keys all use session-bound URLs', async () => {
  const url = (await protectPlayback(playback('https://video.example/master.m3u8'), request(), env)).qualities[0].url;
  const master = '#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nvariant.m3u8\n';
  const variant = '#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI="key.bin"\n#EXT-X-MAP:URI="init.mp4"\n#EXTINF:10,\nsegment.ts\n#EXT-X-ENDLIST';
  const fetcher = async target => {
    const path = new URL(target).pathname;
    if (path.endsWith('m3u8')) return new Response(path === '/master.m3u8' ? master : variant, {headers:{'Content-Type':'application/vnd.apple.mpegurl'}});
    return new Response('bytes', {headers:{'Content-Type':'application/octet-stream'}});
  };
  const rewrittenMaster = await (await serveMedia(request(url), env, {fetcher})).text();
  const child = rewrittenMaster.split('\n').find(line => line.startsWith('https:'));
  const rewrittenVariant = await (await serveMedia(request(child), env, {fetcher})).text();
  assert.ok(!rewrittenVariant.includes('video.example')); assert.ok(!rewrittenVariant.includes('key.bin'));
  const children = [...rewrittenVariant.matchAll(/URI="([^"]+)"|^(https:[^\n]+)/gm)].map(match => match[1]||match[2]);
  assert.equal(children.length, 3);
  for (const childUrl of children) {
    assert.equal(await (await serveMedia(request(childUrl), env, {fetcher})).text(), 'bytes');
    await assert.rejects(serveMedia(request(childUrl, other), env, {fetcher}), e => e.status === 403);
  }
  const full=await serveMedia(request(url,token,{Range:'bytes=0-'}),env,{fetcher:async(_target,options)=>{
    assert.equal(options.headers.get('Range'),null);return new Response(master,{headers:{'Content-Type':'application/vnd.apple.mpegurl'}});
  }});
  assert.equal(full.status,200);assert.ok((await full.text()).startsWith('#EXTM3U'));
});

test('media redirects and HLS child URLs cannot escape the origins selected by the catalogue', async () => {
  const url = (await protectPlayback(playback('https://video.example/master.m3u8'), request(), env)).qualities[0].url;
  for (const location of ['https://127.0.0.1/a.mp4','https://metadata.google.internal/a.mp4','https://attacker.example/a.mp4','http://video.example/a.mp4']) {
    let calls = 0;
    const fetcher = async () => {calls++; return Response.redirect(location);};
    await assert.rejects(serveMedia(request(url), env, {fetcher}), e => e.status === 502); assert.equal(calls, 1);
    const manifest = async () => new Response('#EXTM3U\n'+location, {headers:{'Content-Type':'application/vnd.apple.mpegurl'}});
    await assert.rejects(serveMedia(request(url), env, {fetcher:manifest}), e => e.status === 502);
  }
  for (const raw of ['https://127.0.0.1/a.mp4','https://localhost/a.mp4','https://metadata.google.internal/a.mp4','https://user:pass@video.example/a.mp4'])
    await assert.rejects(protectPlayback(playback(raw), request(), env), e => e.status === 502);
});

test('cross-site requests and source HTML are rejected; extensionless HLS resources are rewritten', async () => {
  const f = await fixture(), url = (await protectPlayback(playback(source), request(), f.env)).qualities[0].url;
  assert.equal((await worker.fetch(request(url, token, {'Sec-Fetch-Site':'cross-site'}), f.env, {})).status, 403);
  await assert.rejects(serveMedia(request(url), env, {fetcher:async()=>new Response('<script>bad</script>',{headers:{'Content-Type':'text/html'}})}), e=>e.status===502);
  const playlist = await serveMedia(request(url), env, {fetcher:async()=>new Response('#EXTM3U\npart.ts',{headers:{'Content-Type':'application/vnd.apple.mpegurl'}})});
  assert.ok((await playlist.text()).includes(origin+'/api/media/'));
});

test('the known provider gateway may redirect to its CDN nodes without admitting lookalike domains', async () => {
  const url=(await protectPlayback(playback('https://stream.voidboost.one/movie.mp4'),request(),env)).qualities[0].url;
  let calls=0;
  const fetcher=async target=>{calls++;return new URL(target).hostname==='stream.voidboost.one'
    ? Response.redirect('https://meteor.stream.voidboost.one/movie.mp4')
    : new Response('video',{headers:{'Content-Type':'video/mp4'}});};
  assert.equal(await (await serveMedia(request(url),env,{fetcher})).text(),'video');assert.equal(calls,2);
  await assert.rejects(serveMedia(request(url),env,{fetcher:async()=>Response.redirect('https://meteor.stream.voidboost.one.attacker.example/movie.mp4')}),e=>e.status===502);
});

test('revoking a session also interrupts an ongoing transfer at the next session check', {timeout:2000}, async () => {
  const url=(await protectPlayback(playback(source),request(),env)).qualities[0].url;
  let cancelled=false;const originalNow=Date.now;
  const DB={prepare(){return {bind(){return {async first(){return null;}};}};}};
  const body=new ReadableStream({start(controller){controller.enqueue(new TextEncoder().encode('initial bytes'));},cancel(){cancelled=true;}});
  const response=await serveMedia(request(url),{...env,DB},{fetcher:async()=>new Response(body,{headers:{'Content-Type':'video/mp4'}})});
  try {
    const later=originalNow()+11000;Date.now=()=>later;
    await assert.rejects(async()=>{const reader=response.body.getReader();for(;;){if((await reader.read()).done)break;}},/Session revoked/);
    assert.equal(cancelled,true);
  } finally {Date.now=originalNow;}
});

test('on Workers an MP4 is piped natively, rechecked by a side task, and a revoked session ends it without an uncaught error', {timeout:3000}, async () => {
  const url=(await protectPlayback(playback(source),request(),env)).qualities[0].url;
  const originalTimeout=globalThis.setTimeout;let active=true,cancelled=false;const tasks=[];
  globalThis.IdentityTransformStream=class extends TransformStream{};
  globalThis.setTimeout=(fn,ms,...args)=>originalTimeout(fn,ms===10000?5:ms,...args);
  try {
    const DB={prepare(){return {bind(){return {async first(){return active?{active:1}:null;}};}};}};
    const ok=await serveMedia(request(url),{...env,DB},{waitUntil:p=>tasks.push(p),fetcher:async()=>new Response('video',{headers:{'Content-Type':'video/mp4'}})});
    assert.equal(await ok.text(),'video');await Promise.all(tasks);
    const body=new ReadableStream({start(controller){controller.enqueue(new TextEncoder().encode('initial bytes'));},cancel(){cancelled=true;}});
    const response=await serveMedia(request(url),{...env,DB},{waitUntil:p=>tasks.push(p),fetcher:async()=>new Response(body,{headers:{'Content-Type':'video/mp4'}})});
    const reader=response.body.getReader();assert.equal(new TextDecoder().decode((await reader.read()).value),'initial bytes');
    active=false;
    let more=0;for(;;){const r=await reader.read();if(r.done)break;more+=r.value.byteLength;}
    assert.equal(more,0);await Promise.all(tasks);assert.equal(cancelled,true);
  } finally {delete globalThis.IdentityTransformStream;globalThis.setTimeout=originalTimeout;}
});
