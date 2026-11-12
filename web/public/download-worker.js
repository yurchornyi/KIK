/** KIK downloads (downloads.js): one file at a time, written straight to the browser's private disk (OPFS) through a
 *  sync access handle, so whatever arrived stays there and a cut-off transfer goes on from that byte. The outcome is
 *  posted only after the file is closed: the page may delete or reopen it as soon as it hears. */
'use strict';
const DIR='kik-downloads',FLUSH=8<<20;
let job=null;
self.onmessage=e=>{const m=e.data||{};if(m.type==='stop'){if(job&&job.id===m.id)job.controller.abort();}else if(m.type==='start')run(m);};
async function run({id,url,total}){
 job?.controller.abort();
 const controller=new AbortController(),me=job={id,controller};let handle=null,written=0,out=null;
 const post=(type,extra={})=>self.postMessage({type,id,bytes:written,...extra});
 try{
  const dir=await (await navigator.storage.getDirectory()).getDirectoryHandle(DIR,{create:true});
  // Early Safari made these methods async: awaiting a plain number costs nothing elsewhere.
  handle=await (await dir.getFileHandle(id+'.mp4',{create:true})).createSyncAccessHandle();written=await handle.getSize();
  if(total&&written>=total){if(written>total)await handle.truncate(written=total);out=['done',{total}];return;}
  // Always a range, from 0 too: the answer then names the whole size (Content-Range) even when it streams without a length,
  // so a transfer cut short is never taken for the end of the film.
  const r=await fetch(url,{headers:{Range:'bytes='+written+'-'},credentials:'same-origin',cache:'no-store',signal:controller.signal});
  if(r.status===416&&total&&written===total){out=['done',{total}];return;}
  if(!r.ok){let message='';try{message=(await r.json()).error||'';}catch{}out=['error',{code:'http',status:r.status,message}];return;}
  // A playlist or an error page is never written into the film.
  if(!/^(?:video\/|application\/octet-stream)/i.test(r.headers.get('Content-Type')||'video/mp4')){await r.body?.cancel();out=['error',{code:'format'}];return;}
  let size=0;
  if(r.status===206){const m=/^bytes (\d+)-\d+\/(\d+)$/.exec(r.headers.get('Content-Range')||'');if(!m||Number(m[1])!==written){await r.body?.cancel();await handle.truncate(written=0);out=['error',{code:'range'}];return;}size=Number(m[2]);}
  else{if(written)await handle.truncate(written=0);size=Number(r.headers.get('Content-Length'))||0;}
  // The source now serves another file under this quality: what is on disk belongs to the old one.
  if(total&&size&&size!==total){await r.body?.cancel();await handle.truncate(written=0);out=['error',{code:'changed',total:size}];return;}
  if(size){const est=await navigator.storage.estimate().catch(()=>null);if(est&&est.quota-est.usage<size-written+(64<<20)){await r.body?.cancel();out=['error',{code:'space',total:size,need:size-written}];return;}}
  post('progress',{total:size});
  const reader=r.body.getReader();let unflushed=0,shown=0;
  for(;;){const {done,value}=await reader.read();if(done)break;await handle.write(value,{at:written});written+=value.byteLength;unflushed+=value.byteLength;
   if(unflushed>=FLUSH){await handle.flush();unflushed=0;}const now=Date.now();if(now-shown>400){shown=now;post('progress',{total:size});}}
  out=size&&written<size?['error',{code:'short',total:size}]:['done',{total:size||written}];
 }catch(error){
  out=controller.signal.aborted?['stopped']:['error',{code:error?.name==='QuotaExceededError'?'space':error?.name==='NoModificationAllowedError'?'busy':'network',message:String(error?.message||error)}];
 }finally{
  try{await handle?.flush();await handle?.close();}catch{}
  if(job===me)job=null;
  if(out)post(...out);
 }
}
