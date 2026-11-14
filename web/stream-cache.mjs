/**
 * A found stream (the source's video links for one title, voice and episode) is the same for every viewer, and the
 * source's links last about a day. KIK keeps it at the edge for two hours, so starting the same film again, from another
 * device, or the retry after «Упс» gets it at once instead of after ~3 s of asking HDRezka. While the source refuses or is
 * busy, a copy up to six hours old still answers. A recovery attempt (the links just failed in a player) always asks the
 * source and replaces the copy.
 */
export const STREAM_FRESH=2*3600e3,STREAM_STALE=6*3600e3;

export function streamKey(origin,{link,voice,season='',episode=''}){
 return new Request(origin+'/api/stream?'+new URLSearchParams({link,voice:String(voice),season:String(season||''),episode:String(episode||''),__v:'1'}));
}

const usable=value=>Array.isArray(value?.qualities)&&value.qualities.length>0;

export async function cachedStream(key,{attempt=0,load,cache=globalThis.caches?.default,keep=promise=>promise,now=Date.now}){
 if(!key||!cache)return load();
 const save=value=>{if(usable(value))keep(cache.put(key,new Response(JSON.stringify(value),{headers:{'Content-Type':'application/json',
  'Cache-Control':'public, max-age='+STREAM_STALE/1000,'X-KIK-Stored':String(now())}})).catch(()=>{}));return value;};
 if(attempt)return save(await load());
 const stored=await cache.match(key).catch(()=>null),age=stored?now()-Number(stored.headers.get('X-KIK-Stored')||0):Infinity;
 const copy=stored&&age<STREAM_STALE?await stored.json().catch(()=>null):null;
 if(usable(copy)&&age<STREAM_FRESH)return copy;
 try{return save(await load());}
 catch(error){if(usable(copy)&&(error.sourceBusy||error.sourcePaused))return copy;throw error;}
}
