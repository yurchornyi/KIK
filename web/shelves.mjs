/**
 * Home shelves that TMDB picks and the catalogue serves (14.11, owner: «Популярне» was the source's all-time list —
 * «Друзі», «Втеча із Шоушенка», Hot Wheels — and «Мультфільми» old cartoons the source had just added):
 *  - films, series: what the world watches this week, TMDB's weekly trending films and series: Home's two Top 10s, as on
 *    Netflix (TMDB has no country's own chart, so «цього тижня», not «в Україні»);
 *  - cartoons: the most popular animated films and series of this year and last, anime aside;
 *  - anime: the most popular Japanese animation of this year and last;
 *  - documentary: «Топ-10 документальних цього тижня» (14.11, owner: two documentary rows «як Топ-10 серіалів цього
 *    тижня, Нові серіали»): the documentaries among TMDB's trending films and series of the week, then, to fill ten, the
 *    most popular documentaries of this year and last; reality, talk and news shows aside («реаліті-шоу ні»);
 *  - new-documentary: «Нові документальні», not TMDB's: the source's newest documentary series and films (freshShelf).
 * Each title is found on the catalogue by its Russian and original names and its year (holiday-films.mjs), in TMDB's
 * order. One search at a time as background work; server.mjs keeps a whole shelf at the edge (/api/shelf).
 */
import {resolve} from './holiday-films.mjs';
import {titleKey} from './sync.mjs';

const API='https://api.themoviedb.org/3',DAY=86400000,RETRY=600000,BUDGET=20000;
export const SHELVES={films:{limit:10},series:{limit:10},cartoons:{limit:15},anime:{limit:15},documentary:{limit:10},'new-documentary':{limit:15}};
// TMDB's series can be a documentary and a reality, talk or news show at once: those stay out of «Документальне».
const NOT_DOCUMENTARY=[10764,10767,10763];
const memo=new Map();

/** The TMDB lists a shelf reads, films first: [kind, path, tier]. Discovery covers this year and last, up to today. A
 *  later tier only fills what the earlier ones leave (candidates). */
export function shelfQueries(id,now=Date.now()){
 const today=new Date(now).toISOString().slice(0,10),from=(new Date(now).getUTCFullYear()-1)+'-01-01';
 const discover=(kind,extra)=>{const q=new URLSearchParams({language:'ru-RU',include_adult:'false',sort_by:'popularity.desc',with_genres:'16','vote_count.gte':'10',...extra});
  q.set(kind==='tv'?'first_air_date.gte':'primary_release_date.gte',from);q.set(kind==='tv'?'first_air_date.lte':'primary_release_date.lte',today);return [kind,'/discover/'+kind+'?'+q];};
 if(id==='films')return [['movie','/trending/movie/week?language=ru-RU']];
 if(id==='series')return [['tv','/trending/tv/week?language=ru-RU']];
 if(id==='cartoons')return [discover('movie',{}),discover('tv',{})];
 if(id==='anime')return [discover('tv',{with_original_language:'ja'}),discover('movie',{with_original_language:'ja'})];
 if(id==='documentary'){const week=(kind,page)=>[kind,'/trending/'+kind+'/week?language=ru-RU&page='+page,0];
  return [week('movie',1),week('tv',1),week('movie',2),week('tv',2),week('movie',3),week('tv',3),[...discover('movie',{with_genres:'99'}),1],[...discover('tv',{with_genres:'99',without_genres:NOT_DOCUMENTARY.join(',')}),1]];}
 throw Object.assign(Error('Невідома полиця'),{status:400});
}

/** TMDB's lists taken in turns (a film, a series, …), each title once, as entries to find: names, year and kind. Pages of
 *  one kind follow each other; a later tier comes after the whole of an earlier one. */
export function candidates(id,lists){
 const out=[],seen=new Set();
 for(const tier of [...new Set(lists.map(l=>l.tier||0))].sort((a,b)=>a-b)){
  const kinds=[];for(const l of lists)if((l.tier||0)===tier){let k=kinds.find(x=>x.kind===l.kind);if(!k)kinds.push(k={kind:l.kind,results:[]});k.results.push(...l.results);}
  const longest=Math.max(0,...kinds.map(l=>l.results.length));
  for(let n=0;n<longest;n++)for(const {kind,results} of kinds){const t=results[n];if(!t||t.adult)continue;
  const year=Number(String(t.release_date||t.first_air_date||'').slice(0,4)),title=t.title||t.name,original=t.original_title||t.original_name;
  if(!year||!title||seen.has(kind+t.id))continue;
  const animated=(t.genre_ids||[]).includes(16);
  // Cartoons leave Japanese animation to «Аніме».
  if(id==='cartoons'&&t.original_language==='ja')continue;
  // The week's trends hold every genre: «Документальне» takes their documentaries only.
  if(id==='documentary'&&(!(t.genre_ids||[]).includes(99)||(t.genre_ids||[]).some(g=>NOT_DOCUMENTARY.includes(g))))continue;
  seen.add(kind+t.id);out.push({id:kind+':'+t.id,year,queries:[...new Set([title,original].filter(Boolean))],kind:animated?'cartoon':kind==='tv'?'series':'film'});
 }}
 return out;
}

/** A shelf the source fills itself: its genre's newest additions (the «last» list) in these sections, in turns. */
const FRESH={'new-documentary':{genre:'documentary',categories:['series','films']}},FRESH_PAGES=3;
/** This year's and last year's titles only, as «Нові серіали» (newRelease on the website, FreshPolicy on the TV): the
 *  source's «last» list also holds old titles it has just added. A series counts by the year it began. */
export function freshRelease(card,now=Date.now()){const year=Number(String(card?.info||'').match(/\d{4}/)?.[0]),thisYear=new Date(now).getUTCFullYear();return year>thisYear-2&&year<=thisYear+1;}
/** «Нові документальні»: up to three «last» pages of documentary series and of documentary films, new releases only, a
 *  series and a film in turns. A section that fails leaves the other's titles, `partial`, so the server does not keep it. */
async function freshShelf(id,{list,now,limit}){
 if(typeof list!=='function')throw Object.assign(Error('Полиця тимчасово недоступна'),{status:503});
 const {genre,categories}=FRESH[id];
 const settled=await Promise.allSettled(categories.map(async category=>{const out=[];
  for(let page=1;page<=FRESH_PAGES&&out.length<limit;page++){let cards;try{cards=await list({category,genre,filter:'last',page});}catch(e){if(page===1)throw e;break;}
   out.push(...(cards||[]).filter(c=>freshRelease(c,now())));if(!cards?.length)break;}
  return out;}));
 if(settled.every(r=>r.status==='rejected'))throw settled[0].reason;
 const lists=settled.map(r=>r.status==='fulfilled'?r.value:[]),items=[],seen=new Set(),longest=Math.max(0,...lists.map(l=>l.length));
 for(let n=0;n<longest&&items.length<limit;n++)for(const l of lists){const card=l[n];if(!card)continue;let k;try{k=titleKey(card.link);}catch{continue;}if(seen.has(k))continue;seen.add(k);items.push(card);if(items.length>=limit)break;}
 return {items,partial:settled.some(r=>r.status==='rejected')};
}

async function request(path,token,fetcher){
 const credential=String(token||'').trim().replace(/^Bearer\s+/i,''),key=/^[a-f0-9]{32}$/i.test(credential),url=new URL(API+path);
 if(key)url.searchParams.set('api_key',credential);
 const r=await fetcher(url.href,{headers:{Accept:'application/json',...(!key?{Authorization:'Bearer '+credential}:{})},signal:AbortSignal.timeout(6500),redirect:'manual'});
 if(!r.ok)throw Error('TMDB '+r.status);const data=await r.json();if(!Array.isArray(data.results))throw Error('TMDB list');return data.results;
}

/**
 * A shelf: TMDB's titles found on the catalogue, in TMDB's order, at most the shelf's limit. A title is searched once a day
 * (a failed search again after ten minutes); no new search starts after `budget` ms, and a shelf cut short by that or by
 * an error is `partial`, so the server does not keep it and the next request goes on from here.
 */
export async function shelf(id,{token,search,list,fetcher=globalThis.fetch,now=Date.now,budget=BUDGET,cache=memo}={}){
 if(!Object.hasOwn(SHELVES,id))throw Object.assign(Error('Невідома полиця'),{status:400});
 if(Object.hasOwn(FRESH,id))return freshShelf(id,{list,now,limit:SHELVES[id].limit});
 if(!String(token||'').trim())throw Object.assign(Error('Полиця тимчасово недоступна'),{status:503});
 const started=now(),lists=await Promise.all(shelfQueries(id,started).map(async([kind,path,tier=0])=>({kind,tier,results:await request(path,token,fetcher)})));
 const items=[],seen=new Set(),limit=SHELVES[id].limit;let partial=false;
 for(const entry of candidates(id,lists)){
  let hit=cache.get(entry.id);
  if(!hit||hit.until<=now()){
   if(now()-started>budget){partial=true;break;}
   try{hit={card:await resolve(entry,search),until:now()+DAY};}catch(e){hit={failed:true,until:now()+RETRY};console.warn('KIK shelf',id,entry.queries[0],e?.message);}
   if(cache.size>=600)cache.delete(cache.keys().next().value);cache.set(entry.id,hit);
  }
  if(hit.failed){partial=true;continue;}
  const card=hit.card;if(!card)continue;let k;try{k=titleKey(card.link);}catch{continue;}if(seen.has(k))continue;seen.add(k);items.push(card);
  if(items.length>=limit)break;
 }
 return {items,partial};
}
