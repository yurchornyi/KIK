/** KIK without the internet: the site itself opens offline, so downloaded films and episodes play anywhere.
 *  - The page comes from the network first (a fresh deploy always wins) and from here only when there is none.
 *  - Fingerprinted files (?v=, see build.mjs) never change at their URL: kept once, served from here.
 *  - Posters of downloaded titles are kept on the page's request (downloads.js) and shown from here only offline.
 *  - KIK's API and every video byte go straight to the network: never touched, never stored here. */
const SHELL='kik-shell-v1',POSTERS='kik-posters',ICONS=['/manifest.webmanifest','/icon-192.png','/icon-512.png','/apple-touch-icon.png'];
self.addEventListener('install',e=>{e.waitUntil(fill().catch(()=>{}).then(()=>self.skipWaiting()));});
self.addEventListener('activate',e=>{e.waitUntil((async()=>{for(const k of await caches.keys())if(k.startsWith('kik-shell')&&k!==SHELL)await caches.delete(k);await self.clients.claim();})());});

const shellFiles=html=>[...new Set([...html.matchAll(/(?:src|href)="(\/[^"#?]*(?:\?v=[\w-]+)?)"/g)].map(m=>m[1]).filter(u=>!u.startsWith('/api/')))];
/** The page and everything it names, fetched once when KIK is first installed here. */
async function fill(){
 const page=await fetch('/',{cache:'no-store',credentials:'same-origin'});if(!page.ok)return;
 const cache=await caches.open(SHELL),html=await page.clone().text();await cache.put('/',page);
 await Promise.all([...shellFiles(html),...ICONS].map(async url=>{try{if(await cache.match(url))return;const r=await fetch(url,{credentials:'same-origin'});if(r.ok)await cache.put(url,r);}catch{}}));
}
/** A newer page: older fingerprints of the files it names go (files it does not name, like the HLS player, stay). */
async function prune(html){
 const cache=await caches.open(SHELL),current=new Map(shellFiles(html).map(u=>new URL(u,self.location.origin)).filter(u=>u.searchParams.has('v')).map(u=>[u.pathname,u.href]));
 for(const req of await cache.keys()){const u=new URL(req.url),now=current.get(u.pathname);if(u.searchParams.has('v')&&now&&now!==u.href)await cache.delete(req);}
}
async function page(request,wait){
 try{const r=await fetch(request);if(r.ok&&r.type==='basic'&&!r.redirected){const copy=r.clone();wait(caches.open(SHELL).then(async c=>{await c.put('/',copy.clone());await prune(await copy.text());}).catch(()=>{}));}return r;}
 catch(error){const kept=await caches.match('/',{cacheName:SHELL});if(kept)return kept;throw error;}
}
async function fixed(request){
 const cache=await caches.open(SHELL),kept=await cache.match(request);if(kept)return kept;
 const r=await fetch(request);if(r.ok&&r.type==='basic')cache.put(request,r.clone()).catch(()=>{});return r;
}
/** Icons, avatars, the logo: what is kept shows at once and is refreshed behind it. */
async function fresh(request,wait){
 const cache=await caches.open(SHELL),kept=await cache.match(request,{ignoreSearch:false});
 const live=fetch(request).then(r=>{if(r.ok&&r.type==='basic')wait(cache.put(request,r.clone()).catch(()=>{}));return r;});
 if(kept){wait(live.catch(()=>{}));return kept;}
 return live;
}
async function poster(request){const kept=await caches.match(request.url,{cacheName:POSTERS});return kept||Response.error();}

self.addEventListener('fetch',e=>{
 const r=e.request,wait=p=>{try{e.waitUntil(p);}catch{}};if(r.method!=='GET')return;
 const u=new URL(r.url);
 if(u.origin!==self.location.origin){if(r.destination==='image'&&self.navigator.onLine===false)e.respondWith(poster(r));return;}
 if(u.pathname.startsWith('/api/')||u.pathname==='/sw.js'||u.pathname==='/download-worker.js')return;
 if(r.mode==='navigate'){if(u.pathname==='/')e.respondWith(page(r,wait));return;}
 if(u.searchParams.has('v')){e.respondWith(fixed(r));return;}
 if(/^\/(?:avatars\/[\w-]+\.svg|logo\.svg|tmdb-logo\.svg|icon\.svg|icon-\d+\.png|apple-touch-icon\.png|manifest\.webmanifest)$/.test(u.pathname))e.respondWith(fresh(r,wait));
});

/** downloads.js keeps the poster of every downloaded title and lets it go with the title's last file. */
self.addEventListener('message',e=>{
 const m=e.data||{},url=typeof m.url==='string'?m.url:'';let ok=false;try{ok=new URL(url).protocol==='https:';}catch{}
 if(!ok)return;
 if(m.type==='keep-poster')e.waitUntil((async()=>{const cache=await caches.open(POSTERS);if(await cache.match(url))return;
  for(const mode of ['cors','no-cors']){try{const r=await fetch(url,{mode,credentials:'omit',referrerPolicy:'no-referrer'});if(r.ok||r.type==='opaque'){await cache.put(url,r);return;}}catch{}}})());
 else if(m.type==='drop-poster')e.waitUntil(caches.open(POSTERS).then(c=>c.delete(url)));
});
