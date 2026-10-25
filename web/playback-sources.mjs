import {HOSTS,film,episodes,stream} from './catalogue.mjs';
/** Refresh per-mirror session data; each resolved host is attempted once per request. */
export async function resolvePlayback(link,voice,season,episode,{attempt=0,method='stream',loadFilm=film,readStream=stream,readEpisodes=episodes}={}){
 if(!Number.isInteger(attempt)||attempt<0||attempt>2)throw Object.assign(Error('Некоректна спроба відновлення'),{status:400});
 const start=attempt%HOSTS.length,hosts=[...HOSTS.slice(start),...HOSTS.slice(0,start)],targets=attempt?hosts:[null,...hosts],deadline=Date.now()+25000,seen=new Set();let last;
 for(const host of targets){if(Date.now()>=deadline)break;if(host&&seen.has(host))continue;try{const f=await loadFilm(link,host?{fresh:true,host,only:true,timeout:6000}:{timeout:6000});if(seen.has(f.origin))continue;seen.add(f.origin);const track=f.voices.find(v=>v.id===voice&&!v.premium&&!v.ads);if(!track)throw Error('Немає доріжки без рекламної позначки');return method==='episodes'?{episodes:await readEpisodes(f,voice)}:await readStream(f,voice,season,episode);}catch(e){if(e.sourcePaused)throw e;last=e;}}
 throw last||Error('Резервні адреси наразі недоступні');
}
