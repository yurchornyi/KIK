import {hash, requestToken} from './auth.mjs';
import {mediaUrl} from './public/playback-recovery.js';

const enc = new TextEncoder();
const MAX_MANIFEST = 1024 * 1024;
// This provider's gateway redirects media to its own named CDN nodes.
const CDN_GATEWAYS = ['stream.voidboost.one'];
export const MEDIA_TTL = 6 * 60 * 60 * 1000;
const fail = (message, status = 403) => { throw Object.assign(Error(message), {status}); };
const b64 = bytes => Buffer.from(bytes).toString('base64url');

// Only the catalogue adapter can mint tickets. Clients cannot supply a proxy target.
function target(value, origins) {
  let url;
  try { url = new URL(value); } catch { fail('Некоректний потік', 502); }
  const host = url.hostname;
  if (url.protocol !== 'https:' || url.username || url.password || url.port || url.hash ||
      !host.includes('.') || host.includes(':') || /^[\d.]+$/.test(host) ||
      /(?:^|\.)(?:localhost|local|internal|invalid|test)$/.test(host) ||
      origins && !origins.includes(url.origin) && !CDN_GATEWAYS.some(gateway =>
        origins.includes('https://' + gateway) && host.endsWith('.' + gateway))) fail('Адреса потоку недоступна', 502);
  return url;
}

// A manifest seals every segment: importing the key once per isolate keeps that inside the Worker's CPU limit.
let keyHex = null, keyPromise = null;
async function key(env) {
  if (!/^[a-f0-9]{64}$/.test(env.MEDIA_TOKEN_KEY || '')) fail('Захист відео не налаштовано', 503);
  if (keyHex !== env.MEDIA_TOKEN_KEY) {
    keyHex = env.MEDIA_TOKEN_KEY;
    keyPromise = crypto.subtle.importKey('raw', Buffer.from(keyHex, 'hex'), 'AES-GCM', false, ['encrypt', 'decrypt']);
    keyPromise.catch(() => { keyHex = null; });
  }
  return keyPromise;
}

async function seal(payload, sessionHash, origin, env) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const data = await crypto.subtle.encrypt({name: 'AES-GCM', iv, additionalData: enc.encode(origin + '\n' + sessionHash)},
    await key(env), enc.encode(JSON.stringify(payload)));
  const suffix = payload.playlist ? 'm3u8' : payload.video ? 'mp4' : payload.extension || 'bin';
  return origin + '/api/media/' + b64(iv) + '.' + b64(new Uint8Array(data)) + '.' + suffix;
}

async function open(request, env, sessionHash, now) {
  const url = new URL(request.url), match = url.pathname.match(/^\/api\/media\/([\w-]{16})\.([\w-]{20,16000})\.(mp4|m3u8|bin|ts|m4s|key|aac|mp3|vtt)$/);
  if (!match || url.search) fail('Посилання на відео недійсне');
  let payload;
  try {
    const raw = await crypto.subtle.decrypt({name: 'AES-GCM', iv: Buffer.from(match[1], 'base64url'),
      additionalData: enc.encode(url.origin + '\n' + sessionHash)}, await key(env), Buffer.from(match[2], 'base64url'));
    payload = JSON.parse(new TextDecoder().decode(raw));
  } catch (error) { if (error.status === 503) throw error; fail('Посилання на відео недійсне'); }
  if (!Number.isSafeInteger(payload.expires) || payload.expires <= now || payload.expires > now + MEDIA_TTL + 60000 ||
      !Array.isArray(payload.origins) || !payload.origins.length || payload.origins.length > 40) fail('Посилання на відео прострочене');
  target(payload.url, payload.origins);
  return payload;
}

// {direct:true}: a signed-in app may also try the CDN addresses itself (they are what it would get from the catalogue
// directly); the sealed relay stays as its fallback.
export async function protectPlayback(result, request, env, now = Date.now(), {direct = false} = {}) {
  const sessionHash = await hash(requestToken(request)), origin = new URL(request.url).origin;
  const urls = result.qualities.flatMap(q => [q.url, ...(q.alternatives || [])]);
  const origins = [...new Set(urls.map(value => {
    if (!mediaUrl(value)) fail('Некоректний потік', 502);
    return target(value).origin;
  }))];
  if (!origins.length || origins.length > 40) fail('Некоректний потік', 502);
  const memo = new Map();
  async function protect(url) {
    if (!memo.has(url)) memo.set(url, seal({url, origins, expires: now + MEDIA_TTL,
      playlist: /\.m3u8(?:$|[/:])/i.test(new URL(url).pathname), video: true}, sessionHash, origin, env));
    return memo.get(url);
  }
  return {qualities: await Promise.all(result.qualities.map(async q => ({label: q.label, url: await protect(q.url),
    alternatives: await Promise.all((q.alternatives || [q.url]).map(protect)),
    ...(direct ? {direct: [...new Set([q.url, ...(q.alternatives || [])])]} : {})})))};
}

async function readManifest(response) {
  const reader = response.body.getReader(), chunks = []; let size = 0;
  try {
    for (;;) {
      const {done, value} = await reader.read(); if (done) break;
      size += value.byteLength; if (size > MAX_MANIFEST) fail('Завеликий список відео', 502);
      chunks.push(value);
    }
  } finally { await reader.cancel(); }
  const bytes = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  const text = new TextDecoder().decode(bytes);
  if (!text.startsWith('#EXTM3U') || /\{\$|#EXT-X-DEFINE:/.test(text)) fail('Непідтримуваний список відео', 502);
  return text;
}

export async function rewriteManifest(text, payload, sessionHash, origin, env) {
  if (text.split(/\r?\n/).length > 12000 || (text.match(/\bURI="|^[^#\s].*$/gm) || []).length > 4096) fail('Завеликий список відео', 502);
  async function child(value, fallback = 'bin') {
    const url = target(new URL(value, payload.url).href, payload.origins);
    const extension = url.pathname.match(/\.(mp4|ts|m4s|key|aac|mp3|vtt)(?:$|[/:])/i)?.[1]?.toLowerCase();
    return seal({...payload, url: url.href, playlist: /\.m3u8(?:$|[/:])/i.test(url.pathname), video: false,
      // This gateway names MPEG-TS chunks .mp4; clients that check extensions need the HLS format.
      extension: fallback === 'ts' && extension === 'mp4' ? 'ts' : extension || fallback}, sessionHash, origin, env);
  }
  // Decided once per playlist: scanning the whole text again for every segment line costs more CPU than the sealing.
  const segment = text.includes('#EXT-X-MAP:') ? 'm4s' : 'ts';
  return (await Promise.all(text.split(/\r?\n/).map(async line => {
    if (!line.trim()) return line;
    if (!line.startsWith('#')) return child(line.trim(), segment);
    const matches = [...line.matchAll(/\bURI="([^"]*)"/g)];
    if (/\bURI=/.test(line) && !matches.length) fail('Некоректний список відео', 502);
    for (const match of matches) line = line.replace(match[0], 'URI="' + await child(match[1],
      /^#EXT-X-(?:SESSION-)?KEY:/.test(line) ? 'key' : line.startsWith('#EXT-X-MAP:') ? 'mp4' : 'bin') + '"');
    return line;
  }))).join('\n');
}

const sessionActive = (db, sessionHash) =>
  db.prepare('SELECT 1 AS active FROM sessions WHERE token_hash=? AND expires_at>?').bind(sessionHash, Date.now()).first();

// On Workers a whole MP4 is piped natively (IdentityTransformStream): no JavaScript runs per chunk, so a film-long
// transfer stays inside the CPU limit. A side task rechecks the session every 10 s; when it is gone the source is
// cancelled and the player's stream simply ends there. Erroring the response stream instead would be an uncaught
// exception in the Worker, so every pipe and check failure is caught here.
function nativeRevocableBody(body, db, sessionHash, waitUntil) {
  const {readable, writable} = new IdentityTransformStream(), stop = new AbortController();
  let finished = false;
  const pipe = body.pipeTo(writable, {signal: stop.signal, preventAbort: true}).catch(async () => {
    if (!stop.signal.aborted) return;
    await body.cancel().catch(() => {}); await writable.close().catch(() => {});
  }).finally(() => { finished = true; });
  const watch = (async () => {
    while (!finished) {
      let timer;
      await Promise.race([pipe, new Promise(resolve => { timer = setTimeout(resolve, 10000); })]).finally(() => clearTimeout(timer));
      if (finished) return;
      let active = null;
      try { active = await sessionActive(db, sessionHash); } catch {}
      if (!active) { stop.abort(Error('Session revoked')); return; }
    }
  })();
  waitUntil(Promise.all([pipe, watch]));
  return readable;
}

// Recheck a long MP4 transfer as well as each subsequent Range/HLS request (JavaScript fallback outside Workers).
function revocableBody(body, db, sessionHash) {
  if (!body) return null;
  const reader = body.getReader(); let checkAt = Date.now() + 10000;
  return new ReadableStream({
    async pull(controller) {
      try {
        if (Date.now() >= checkAt) {
          const active = await sessionActive(db, sessionHash);
          if (!active) { await reader.cancel(); controller.error(Error('Session revoked')); return; }
          checkAt = Date.now() + 10000;
        }
        const {done, value} = await reader.read(); if (done) controller.close(); else controller.enqueue(value);
      } catch (error) { await reader.cancel().catch(() => {}); controller.error(error); }
    },
    cancel(reason) { return reader.cancel(reason); }
  });
}

export async function serveMedia(request, env, {fetcher = fetch, now = Date.now(), waitUntil = null} = {}) {
  const sessionHash = await hash(requestToken(request)), payload = await open(request, env, sessionHash, now);
  const range = request.headers.get('Range');
  if (range && !/^bytes=(?:\d{1,16}-\d{0,16}|-\d{1,16})$/.test(range)) fail('Некоректний діапазон', 416);
  const headers = new Headers({Accept: '*/*', 'Accept-Encoding': 'identity'});
  // Some native clients send Range on manifests; always rewrite a complete playlist.
  if (range && !payload.playlist) headers.set('Range', range);
  let response, upstream = payload.url;
  for (let hop = 0; hop < 4; hop++) {
    target(upstream, payload.origins);
    const controller = new AbortController(), timer = setTimeout(() => controller.abort(), 15000);
    try { response = await fetcher(upstream, {method: request.method, headers, redirect: 'manual', signal: controller.signal}); }
    catch (error) { if (controller.signal.aborted) fail('Потік тимчасово недоступний', 504); throw error; }
    finally { clearTimeout(timer); }
    if (![301, 302, 303, 307, 308].includes(response.status)) break;
    const location = response.headers.get('Location'); await response.body?.cancel();
    if (!location || hop === 3) fail('Потік тимчасово недоступний', 502);
    upstream = target(new URL(location, upstream).href, payload.origins).href;
  }
  if (![200, 206, 416].includes(response.status)) { await response.body?.cancel(); fail('Потік тимчасово недоступний', 502); }
  const out = new Headers({'Cache-Control': 'private, no-store', Vary: 'Cookie, Authorization',
    'X-Content-Type-Options': 'nosniff', 'Cross-Origin-Resource-Policy': 'same-origin'});
  for (const name of ['Content-Range', 'Accept-Ranges']) if (response.headers.has(name)) out.set(name, response.headers.get(name));
  const playlist = payload.playlist || /application\/(?:vnd\.apple\.mpegurl|x-mpegurl)/i.test(response.headers.get('Content-Type') || '');
  if (playlist && request.method !== 'HEAD' && response.status !== 416) {
    const text = await readManifest(response);
    out.set('Content-Type', 'application/vnd.apple.mpegurl');
    return new Response(await rewriteManifest(text, {...payload, url: upstream}, sessionHash, new URL(request.url).origin, env), {headers: out});
  }
  const type = response.headers.get('Content-Type') || '';
  if (response.status !== 416 && !/^(?:video\/|audio\/|application\/(?:octet-stream|vnd\.apple\.mpegurl|x-mpegurl|mp2t))/i.test(type)) {
    await response.body?.cancel(); fail('Некоректний формат потоку', 502);
  }
  out.set('Content-Type', payload.extension === 'ts' ? 'video/mp2t' : type || 'application/octet-stream');
  if (response.headers.has('Content-Length')) out.set('Content-Length', response.headers.get('Content-Length'));
  // A whole MP4 is one long transfer, rechecked while it streams. HLS segments are short and each one passes the
  // sign-in check on its own request, so they stream straight through: piping them through JS exceeds the CPU limit.
  const native = waitUntil && typeof globalThis.IdentityTransformStream === 'function' && response.body;
  const body = request.method === 'HEAD' ? null : !payload.video ? response.body :
    native ? nativeRevocableBody(response.body, env.DB, sessionHash, waitUntil) : revocableBody(response.body, env.DB, sessionHash);
  return new Response(body, {status: response.status, headers: out});
}
