/** Downloads for watching without the internet (PC, tablet, phone; the TV app has none).
 *  Films and episodes are saved whole as MP4 in the browser's private disk (OPFS) by download-worker.js, one at a time
 *  and only through KIK's own sealed relay (the CDN sends no CORS headers). The list lives in localStorage, so every tab
 *  shows the same thing; only the tab holding the Web Lock downloads. A link expires after 6 h: the item asks
 *  /api/stream again and goes on from the byte it had. */
const KEY='kik-downloads',QUALITY_KEY='kik-download-quality',LOCK='kik-downloads';
export const DOWNLOAD_QUALITIES=['1080p','720p','480p','360p'];
const height=label=>/4k/i.test(label)?2160:/2k/i.test(label)?1440:Number(String(label).match(/\d+/)?.[0]||0);
const MB=1<<20,GB=1<<30;
/** «1,4 ГБ», «350 МБ»: Ukrainian decimal comma, one digit after it only where it says something. */
export function formatBytes(n){n=Math.max(0,Number(n)||0);if(n>=GB)return (n/GB).toFixed(n>=10*GB?0:1).replace('.',',')+' ГБ';if(n>=MB)return Math.round(n/MB)+' МБ';return Math.max(1,Math.round(n/1024))+' КБ';}
/** Phones and tablets save space by default, a computer takes the full picture. */
export function defaultDownloadQuality(coarse){return coarse?'720p':'1080p';}
/** Without premium the source's top quality («1080p Ultra») is a one-minute preview: a file that small for a title
 *  known to run 10+ minutes (or of unknown length) is not the film. */
export function looksLikePreview(bytes,runtimeMs){return bytes>0&&bytes<35*MB&&!(runtimeMs>0&&runtimeMs<10*60000);}
/** The stream for one item: the quality already started (a resumed file must stay the same file), else the best one not
 *  above the wanted height and not known to be a preview. Each retry takes the next address of that quality. */
export function chooseStream(qualities,{quality,wanted,avoid=[],attempts=0}={}){
 const all=(qualities||[]).filter(q=>q&&q.label&&q.url&&!avoid.includes(q.label)),plain=all.filter(q=>!/ultra/i.test(q.label)),list=plain.length?plain:all;
 if(!list.length)return null;
 const target=height(wanted)||1080,sorted=list.slice().sort((a,b)=>height(b.label)-height(a.label));
 const q=list.find(x=>x.label===quality)||sorted.find(x=>height(x.label)<=target)||sorted.at(-1);
 const urls=[...new Set([q.url,...(q.alternatives||[])])];
 return {label:q.label,url:urls[attempts%urls.length],lower:sorted.some(x=>height(x.label)<height(q.label))};
}
const itemKey=(key,season,episode)=>key+'|'+(season||'0')+':'+(episode||'0');
const valid=x=>x&&typeof x.id==='string'&&/^[\w-]{6,64}$/.test(x.id)&&typeof x.link==='string'&&typeof x.owner==='string';
const randomId=()=>{try{return crypto.randomUUID().replace(/-/g,'');}catch{return Date.now().toString(36)+Math.random().toString(36).slice(2,12);}};

/** The browser's private disk: one folder, one file per item. */
export function browserDisk(g=globalThis){
 const root=async()=>(await g.navigator.storage.getDirectory()).getDirectoryHandle('kik-downloads',{create:true});
 return {
  async supported(){try{if(!g.navigator?.storage?.getDirectory||typeof g.Worker!=='function')return false;await root();return true;}catch{return false;}},
  async file(id){return (await (await root()).getFileHandle(id+'.mp4')).getFile();},
  async remove(id){try{await (await root()).removeEntry(id+'.mp4');}catch{}},
  async names(){const out=[];try{for await (const name of (await root()).keys())out.push(name);}catch{return null;}return out;},
  async estimate(){try{return await g.navigator.storage.estimate();}catch{return null;}},
  async persist(){try{if(!(await g.navigator.storage.persisted?.()))await g.navigator.storage.persist?.();}catch{}}
 };
}

export function createDownloads({api,storage,keyOf,owner,onChange=()=>{},onProgress=()=>{},onDone=()=>{},disk=browserDisk(),
 makeWorker=()=>new Worker('/download-worker.js'),locks=globalThis.navigator?.locks,online=()=>globalThis.navigator?.onLine!==false,
 events=globalThis.window,now=Date.now,timers={setTimeout:(f,ms)=>setTimeout(f,ms),clearTimeout:t=>clearTimeout(t)}}={}){
 let list=load(),leader=false,worker=null,active=null,retryTimer=null,savedAt=0,supported=false,settled=false;
 const resetOnStop=new Set(),startBytes=new Map();
 function load(){try{const v=JSON.parse(storage.getItem(KEY)||'[]');return Array.isArray(v)?v.filter(valid):[];}catch{return [];}}
 function save(){storage.setItem(KEY,JSON.stringify(list));savedAt=now();}
 /** Read, change, write: another tab may have changed the list a moment ago. The item this tab is downloading keeps its own bytes. */
 function mutate(fn,quiet=false){const mine=active&&list.find(x=>x.id===active);list=load();if(mine){const i=list.findIndex(x=>x.id===mine.id);if(i>=0)list[i]={...list[i],bytes:Math.max(list[i].bytes||0,mine.bytes||0),total:list[i].total||mine.total};}fn(list);save();if(!quiet)onChange();}
 /** What a page shows apart from the bytes: a change here repaints it, bytes alone only move its bars. */
 const shape=l=>l.map(x=>x.id+x.status+(x.quality||'')+(x.error||'')).join();
 const item=id=>list.find(x=>x.id===id);
 const mine=()=>{const o=owner();return o?list.filter(x=>x.owner===o):[];};

 function ensureWorker(){if(worker)return worker;worker=makeWorker();worker.onmessage=e=>message(e.data||{});worker.onerror=()=>{const id=active;active=null;worker?.terminate?.();worker=null;if(id)failed(id,{code:'network'});};return worker;}
 async function message(m){
  const x=item(m.id);
  // A preview dropped above: whatever the worker says last, the file goes and the item starts again one quality down.
  if(m.type!=='progress'&&resetOnStop.has(m.id)){resetOnStop.delete(m.id);if(active===m.id)active=null;await disk.remove(m.id);kick();return;}
  // The last bytes the worker reports are written before the item leaves «active»: a resume asks only for the rest.
  if(m.type!=='progress'&&x&&active===m.id){x.bytes=Math.max(x.bytes||0,m.bytes||0);mutate(()=>{},true);}
  if(m.type==='progress'){if(!x||active!==m.id)return;
   // A preview of the top quality is known from its size alone: drop it before it is saved and take the next one down
   // (the file goes once the worker has let go of it).
   if(!x.total&&m.total&&x.lower&&looksLikePreview(m.total,x.runtime)){const label=x.quality;resetOnStop.add(m.id);stop(m.id);mutate(l=>{const y=l.find(z=>z.id===m.id);if(y)Object.assign(y,{avoid:[...(y.avoid||[]),label],quality:null,bytes:0,total:0,status:'queued'});});return;}
   x.bytes=m.bytes;if(m.total)x.total=m.total;
   const reset=x.attempts>0&&m.bytes-(startBytes.get(m.id)||0)>2*MB;
   if(reset||now()-savedAt>3000)mutate(l=>{if(reset){const y=l.find(z=>z.id===m.id);if(y)y.attempts=0;}},true);
   onProgress(item(m.id));return;}
  if(m.type==='done'){if(active===m.id)active=null;if(!x){await disk.remove(m.id);kick();return;}
   mutate(l=>{const y=l.find(z=>z.id===m.id);if(y)Object.assign(y,{status:'done',bytes:m.total,total:m.total,finished:now(),attempts:0,error:null,retryAt:0});});
   const y=item(m.id);if(y?.sponsored)api('/api/ad-breaks?'+new URLSearchParams({link:y.link,voice:y.voice,season:y.season||'',episode:y.episode||''})).then(ads=>mutate(l=>{const z=l.find(q=>q.id===m.id);if(z)z.ads=ads;})).catch(()=>{});
   // Intro and credits travel with an episode too (episode-marks.js): offline the player still offers to skip them.
   if(y?.series)api('/api/marks?'+new URLSearchParams({link:y.link,voice:y.voice,season:y.season,episode:y.episode})).then(marks=>{if(marks?.duration)mutate(l=>{const z=l.find(q=>q.id===m.id);if(z)z.marks=marks;});}).catch(()=>{});
   if(y)onDone(y);kick();return;}
  if(m.type==='stopped'){if(active===m.id)active=null;
   if(!x)await disk.remove(m.id);
   // Stopped for another account (signed out) or by a closing tab: it waits in the queue, not «active» for ever.
   else if(x.status==='active')mutate(l=>{const y=l.find(z=>z.id===m.id);if(y&&y.status==='active')y.status='queued';});
   kick();return;}
  if(m.type==='error'){if(active===m.id)active=null;failed(m.id,m);}
 }
 async function failed(id,m){
  const x=item(id);if(!x){await disk.remove(id);kick();return;}
  if(m.code==='changed'||m.code==='range'){mutate(l=>{const y=l.find(z=>z.id===id);if(y)Object.assign(y,{bytes:0,total:0,status:'queued',attempts:(y.attempts||0)+1});});kick();return;}
  if(m.code==='space'){mutate(l=>{const y=l.find(z=>z.id===id);if(y)Object.assign(y,{status:'error',error:'Недостатньо місця на пристрої'+(m.need?': потрібно ще '+formatBytes(m.need):'')});});kick();return;}
  if(m.status===401){mutate(l=>{const y=l.find(z=>z.id===id);if(y)Object.assign(y,{status:'error',error:'Увійдіть знову, щоб завантажити'});});return;}
  if(/createSyncAccessHandle/.test(m.message||'')){supported=false;mutate(l=>{const y=l.find(z=>z.id===id);if(y)Object.assign(y,{status:'error',error:'Цей браузер не вміє зберігати відео. Спробуйте Chrome, Edge, Firefox або Safari.'});});return;}
  if(!online()){mutate(l=>{const y=l.find(z=>z.id===id);if(y&&y.status!=='paused')y.status='waiting';});return;}
  const attempts=(x.attempts||0)+1;
  if(attempts>8){mutate(l=>{const y=l.find(z=>z.id===id);if(y)Object.assign(y,{status:'error',attempts,error:m.message||'Не вдалося завантажити. Спробуйте пізніше.'});});kick();return;}
  // An expired link, a dropped connection, a busy CDN node: a fresh link from /api/stream, a little later each time.
  const wait=Math.min(60000,2000*2**Math.min(5,attempts-1));
  mutate(l=>{const y=l.find(z=>z.id===id);if(y&&y.status==='active')Object.assign(y,{status:'queued',attempts,retryAt:now()+wait});});kick();
 }
 function stop(id){if(worker&&active===id)worker.postMessage({type:'stop',id});}
 async function begin(x){
  active=x.id;mutate(l=>{const y=l.find(z=>z.id===x.id);if(y)Object.assign(y,{status:'active',error:null,retryAt:0});});
  let pick;
  try{const r=await api('/api/stream',{link:x.link,voice:x.voice,season:x.season||'',episode:x.episode||'',recovery_attempt:Math.min(2,x.attempts||0)},{timeout:40000});pick=chooseStream(r.qualities,x);if(!pick)throw Object.assign(Error('Для цієї якості немає відео'),{final:true});}
  catch(e){if(active!==x.id)return;active=null;if(e.final||e.status===404){mutate(l=>{const y=l.find(z=>z.id===x.id);if(y)Object.assign(y,{status:'error',error:e.message});});kick();return;}failed(x.id,{code:'network',status:e.status,message:e.offline?'':e.message});return;}
  const y=item(x.id);if(active!==x.id)return;
  // Paused or removed while the link was on its way: nothing started, the queue moves on.
  if(!y||y.status!=='active'){active=null;if(!y)await disk.remove(x.id);kick();return;}
  // A file started in another quality would not continue in this one.
  if(y.quality&&y.quality!==pick.label&&y.bytes){await disk.remove(y.id);y.bytes=0;y.total=0;}
  mutate(l=>{const z=l.find(q=>q.id===x.id);if(z)Object.assign(z,{quality:pick.label,lower:pick.lower,bytes:y.bytes||0,total:y.total||0});});
  if(active!==x.id)return;
  startBytes.set(x.id,item(x.id)?.bytes||0);
  ensureWorker().postMessage({type:'start',id:x.id,url:pick.url,total:item(x.id)?.total||0});
 }
 /** The leader reconciles the list with what its worker does: a removed or paused item stops, the next queued one starts. */
 function kick(){
  if(!leader||!supported)return;
  if(active){const x=item(active);if(!x||x.status!=='active'||x.owner!==owner())stop(active);return;}
  const o=owner();if(!o)return;
  timers.clearTimeout(retryTimer);retryTimer=null;
  const queued=list.filter(x=>x.owner===o&&x.status==='queued').sort((a,b)=>(a.added||0)-(b.added||0));
  const ready=queued.find(x=>!(x.retryAt>now()));
  if(ready&&online()){begin(ready);return;}
  const next=queued.reduce((m,x)=>Math.min(m,x.retryAt||0),Infinity);
  if(Number.isFinite(next)&&next>now())retryTimer=timers.setTimeout(kick,next-now()+50);
 }
 async function tidy(){
  // Files the browser no longer has (it may evict storage that was not persisted) and files nobody lists.
  const names=await disk.names();if(!names)return;const have=new Set(names);
  const gone=list.filter(x=>x.status==='done'&&!have.has(x.id+'.mp4')).map(x=>x.id);
  if(gone.length)mutate(l=>{for(const y of l)if(gone.includes(y.id))Object.assign(y,{status:'queued',bytes:0,total:0});});
  const known=new Set(load().map(x=>x.id+'.mp4'));for(const n of names)if(!known.has(n)&&n.endsWith('.mp4'))await disk.remove(n.slice(0,-4));
 }
 async function lead(){
  leader=true;
  mutate(l=>{for(const y of l)if(y.status==='active')y.status='queued';});
  await tidy();kick();
 }
 events?.addEventListener?.('storage',e=>{if(e.key!==KEY)return;const before=shape(list);list=load();if(shape(list)!==before)onChange();else for(const x of list)if(x.status==='active')onProgress(x);kick();});
 events?.addEventListener?.('online',()=>{if(list.some(x=>x.status==='waiting'))mutate(l=>{for(const y of l)if(y.status==='waiting')Object.assign(y,{status:'queued',retryAt:0});});kick();});
 // Every tab repaints once it knows whether this browser can save files, leader or not.
 const ready=(async()=>{supported=await disk.supported();settled=true;onChange();if(!supported)return;
  if(locks?.request)locks.request(LOCK,()=>{lead();return new Promise(()=>{});}).catch(()=>{});else lead();})();

 return {
  ready,
  get supported(){return supported;},
  /** Known whether this browser can save files at all (until then the page says nothing about it). */
  get settled(){return settled;},
  /** This account's downloads, oldest first. */
  items:mine,
  get(id){return item(id);},
  forTitle(link){const k=keyOf(link);return mine().filter(x=>x.key===k);},
  /** A finished copy of this film or episode; with a voice, only in that voice. */
  find(link,season,episode,voice){const k=itemKey(keyOf(link),season,episode);return mine().find(x=>x.status==='done'&&itemKey(x.key,x.season,x.episode)===k&&(!voice||x.voice===voice))||null;},
  /** Adds films or episodes; one already saved or on its way stays as it is, one that failed starts again. */
  add(entries){
   const o=owner();if(!o||!supported)return 0;let added=0;
   mutate(l=>{for(const e of entries){const key=keyOf(e.link),k=itemKey(key,e.season,e.episode),old=l.find(x=>x.owner===o&&itemKey(x.key,x.season,x.episode)===k);
    if(old){if(old.status==='error'||old.status==='paused'){Object.assign(old,{status:'queued',attempts:0,retryAt:0,error:null});added++;}continue;}
    l.push({id:randomId(),owner:o,key,link:e.link,title:e.title,poster:e.poster||'',series:!!e.series,season:e.season||'',episode:e.episode||'',episodeTitle:e.episodeTitle||'',
     voice:String(e.voice),voiceName:e.voiceName||'',voiceLang:e.voiceLang||'',wanted:e.wanted||'1080p',quality:null,runtime:e.runtime||0,duration:e.duration||'',year:e.year||'',sponsored:!!e.sponsored,
     status:'queued',bytes:0,total:0,attempts:0,added:now()+added/1000});added++;}});
   if(added)disk.persist();kick();return added;
  },
  async remove(id){const x=item(id);if(!x)return;mutate(l=>{const i=l.findIndex(y=>y.id===id);if(i>=0)l.splice(i,1);});if(active===id)stop(id);else await disk.remove(id);kick();},
  async removeMany(ids){for(const id of ids)await this.remove(id);},
  pause(id){mutate(l=>{const y=l.find(z=>z.id===id);if(y&&y.status!=='done')Object.assign(y,{status:'paused',retryAt:0});});kick();},
  resume(id){mutate(l=>{const y=l.find(z=>z.id===id);if(y&&y.status!=='done')Object.assign(y,{status:'queued',attempts:0,retryAt:0,error:null});});kick();},
  file(id){return disk.file(id);},
  /** Bytes saved by KIK on this device (all accounts) and what the browser still allows. */
  async usage(){const e=await disk.estimate(),used=list.reduce((s,x)=>s+(x.bytes||0),0);return {used,free:e?.quota?Math.max(0,e.quota-e.usage):null};},
  quality(){const q=storage.getItem(QUALITY_KEY);return DOWNLOAD_QUALITIES.includes(q)?q:null;},
  setQuality(q){if(DOWNLOAD_QUALITIES.includes(q))storage.setItem(QUALITY_KEY,q);},
  /** What runs now for this account: for the tab title and the badge in the menu. */
  summary(){const xs=mine().filter(x=>x.status==='active'||x.status==='queued'||x.status==='waiting'),total=xs.reduce((s,x)=>s+(x.total||0),0),bytes=xs.reduce((s,x)=>s+(x.bytes||0),0);return {count:xs.length,active:xs.find(x=>x.status==='active')||null,percent:total?Math.floor(bytes/total*100):0};},
  kick
 };
}
