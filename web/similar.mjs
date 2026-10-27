/** «Вам може сподобатися»: TMDB recommendations for this title that KIK can play, then the source's own «see also». */
import {titleKey} from './sync.mjs';
import {foldQuery} from './public/search-match.js';
const API='https://api.themoviedb.org/3',LIMIT=12,year4=v=>Number(String(v||'').match(/(?:18|19|20)\d{2}/)?.[0])||0;
const isSeries=link=>titleKey(link).startsWith('series/');
/** One TMDB title on the catalogue: a search result with the same name, kind and year (±1). */
export function sameWork(rec,card){
 const names=[rec.title,rec.name,rec.original_title,rec.original_name].filter(Boolean).map(foldQuery),year=year4(rec.release_date||rec.first_air_date),y=year4(card.info);
 if(!String(card.title||'').split(/\s+\/\s+/).some(n=>names.includes(foldQuery(n))))return false;
 if(year&&y&&Math.abs(year-y)>1)return false;
 return rec.media_type?(rec.media_type==='tv')===isSeries(card.link):true;
}
async function tmdb(path,token,fetcher){
 const credential=String(token).trim().replace(/^Bearer\s+/i,''),key=/^[a-f0-9]{32}$/i.test(credential);
 const r=await fetcher(API+path+(key?(path.includes('?')?'&':'?')+'api_key='+encodeURIComponent(credential):''),{headers:{Accept:'application/json',...(!key?{Authorization:'Bearer '+credential}:{})},signal:AbortSignal.timeout(5000),redirect:'manual'});
 if(!r.ok)throw Error('TMDB HTTP '+r.status);return r.json();
}
/** At most `limit` jobs at once; results keep the input order. */
async function pool(items,limit,job){const out=new Array(items.length);let next=0;await Promise.all(Array.from({length:Math.min(limit,items.length)},async()=>{while(next<items.length){const i=next++;out[i]=await job(items[i]).catch(()=>null);}}));return out;}
export async function similar(link,{token,load,search,fetcher=globalThis.fetch}){
 const f=await load(link),self=titleKey(f.link||link),kind=f.series?'tv':'movie';let picks=[];
 if(token&&f.imdb)try{
  const found=await tmdb('/find/'+encodeURIComponent(f.imdb)+'?external_source=imdb_id',token,fetcher),item=(kind==='tv'?found.tv_results:found.movie_results)?.[0];
  if(Number.isSafeInteger(item?.id)){
   let recs=(await tmdb(`/${kind}/${item.id}/recommendations?language=ru-RU&page=1`,token,fetcher)).results||[];
   if(recs.length<6)recs=recs.concat((await tmdb(`/${kind}/${item.id}/similar?language=ru-RU&page=1`,token,fetcher).catch(()=>({}))).results||[]);
   recs=recs.filter(r=>Number.isSafeInteger(r.id)&&!r.adult&&(r.title||r.name)).map(r=>({...r,media_type:r.media_type||kind})).slice(0,10);
   // Searches run as background work on the source, two at a time, so a title someone opens goes first.
   picks=(await pool(recs,2,async r=>(await search({query:(r.title||r.name).slice(0,150)})).slice(0,10).find(c=>sameWork(r,c)))).filter(Boolean);
  }
 }catch(e){console.warn('KIK similar: TMDB unavailable',e?.message);}
 const out=[],seen=new Set([self]);for(const c of [...picks,...(f.related||[])]){const key=titleKey(c.link);if(seen.has(key))continue;seen.add(key);out.push(c);if(out.length>=LIMIT)break;}
 return out;
}
