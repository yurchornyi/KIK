import {studioById} from './public/studios.js';
import {matchTitle} from './tmdb.mjs';

const API='https://api.themoviedb.org/3',SIZE=5,MAX_PAGE=4000;
const fold=s=>String(s||'').toLowerCase().replace(/[^\p{L}\p{N}]/gu,'');
const normalizeCompany=s=>String(s||'').toLowerCase().replace(/[^a-z0-9]/g,'');
/** Discover the entire brand catalogue, then return only titles matched in KIK by title, year and type.
 * Five candidates per page bound source traffic. Empty intermediate pages carry explicit continuation.
 */
export function createStudioCatalogue({fetcher=globalThis.fetch,search,genres,film,now=Date.now}={}){
 const memo=new Map(),pending=new Map();
 async function request(path,token){
  const credential=String(token||'').trim().replace(/^Bearer\s+/i,'');
  if(!credential)throw Error('Фільтр студій тимчасово недоступний. Спробуйте пізніше.');
  const key=/^[a-f0-9]{32}$/i.test(credential),url=new URL(API+path);
  if(key)url.searchParams.set('api_key',credential);
  const r=await fetcher(url.href,{headers:{Accept:'application/json',...(!key?{Authorization:'Bearer '+credential}:{})},signal:AbortSignal.timeout(6500),redirect:'manual'});
  if(!r.ok)throw Error('Не вдалося завантажити студії. Спробуйте ще раз.');return r.json();
 }
 async function cached(key,fn){
  const hit=memo.get(key);if(hit&&hit.until>now())return hit.value;if(pending.has(key))return pending.get(key);
  const work=fn().then(value=>{if(memo.size>=200)memo.delete(memo.keys().next().value);memo.set(key,{value,until:now()+3600000});return value;}).finally(()=>pending.delete(key));pending.set(key,work);return work;
 }
 async function companies(studio,token){
  return cached('companies:'+studio.id,async()=>{
   const found=new Set();
   // Search exact registered names instead of guessing a company's ID or its parent corporation.
   await Promise.all(studio.companies.map(async name=>{const data=await request('/search/company?'+new URLSearchParams({query:name}),token);
    for(const c of data.results||[])if(Number.isSafeInteger(c.id)&&c.id>0&&studio.companies.some(n=>normalizeCompany(n)===normalizeCompany(c.name)))found.add(c.id);}));
   return [...found];
  });
 }
 async function lookup(params,{token}={}){
  if(!String(token||'').trim())throw Object.assign(Error('Фільтр студій тимчасово недоступний. Спробуйте пізніше.'),{status:503});
  const studio=studioById(params.studio),category=params.category||'films',order=params.filter||'popular',genre=params.genre||'';
  if(!studio||!['films','series','cartoons','animation'].includes(category)||!['popular','last'].includes(order)||genre&&!/^[a-z0-9-]{1,60}$/.test(genre))throw Error('Некоректний фільтр студії');
  if(params.query||params.source&&params.source!=='rezka')throw Error('Несумісні фільтри каталогу');
  let page=Math.max(1,Math.min(MAX_PAGE,Math.floor(Number(params.page)||1)));
  const kinds=['cartoons','animation'].includes(category)?['movie','tv']:[category==='series'?'tv':'movie'];
  page=Math.min(page,2000*kinds.length);
  const kind=kinds[(page-1)%kinds.length],window=Math.floor((page-1)/kinds.length),sourcePage=Math.floor(window/4)+1,offset=window%4*SIZE;
  const q=new URLSearchParams({language:'ru-RU',include_adult:'false',page:String(sourcePage),sort_by:order==='last'?(kind==='tv'?'first_air_date.desc':'primary_release_date.desc'):'popularity.desc'});
  if(kind==='tv'&&studio.networks.length)q.set('with_networks',studio.networks.join('|'));
  else{const ids=await companies(studio,token);if(!ids.length)return {items:[],hasMore:kinds.length>1&&kind===kinds[0],page};q.set('with_companies',ids.join('|'));}
  q.set(kind==='tv'?'first_air_date.lte':'primary_release_date.lte',new Date(now()).toISOString().slice(0,10));
  // The source keeps animated titles under «Мультфільми» and «Аніме»: in films and series they would never match.
  if(category==='cartoons'||category==='animation')q.set('with_genres','16');else q.set('without_genres','16');
  const data=await cached('discover:'+studio.id+':'+category+':'+q,()=>request('/discover/'+kind+'?'+q,token));
  if(!Array.isArray(data.results))throw Error('Некоректна відповідь студії');
  const candidates=data.results.slice(offset,offset+SIZE),items=[];
  const genreName=genre?(await genres(category)).find(g=>g.slug===genre)?.name:null;
  if(genre&&!genreName)throw Error('Цей жанр недоступний. Оберіть інший.');
  // A source error fails this page rather than silently losing a film in the catalogue.
  await Promise.all(candidates.map(async candidate=>{
   const year=String(candidate.release_date||candidate.first_air_date||'').slice(0,4),title=candidate.title||candidate.name,original=candidate.original_title||candidate.original_name;
   if(!title||!year)return;
   const cards=await search({query:title,category,filter:order});
   const eligible=cards.filter(c=>{try{return new URL(c.link).pathname.startsWith('/'+category+'/');}catch{return false;}});
   const unique=[...new Map(eligible.map(c=>[new URL(c.link).pathname,c])).values()];
   const matches=unique.filter(c=>{const year=Number(String(c.info||'').match(/\d{4}/)?.[0]);return year&&matchTitle({title:c.sourceTitle||c.title,year,aliases:[]},[candidate]);});
   if(matches.length!==1)return;
   const card=matches[0];if(genreName){const detail=await film(card.link);if(!(detail.genres||[]).some(g=>fold(g)===fold(genreName)))return;}
   items.push({...card,studios:[studio.id],original,year,_order:candidates.indexOf(candidate)});
  }));
  items.sort((a,b)=>a._order-b._order);for(const item of items)delete item._order;
  const kindHasMore=offset+SIZE<data.results.length||sourcePage<Math.min(500,Number(data.total_pages)||1);
  // Alternate animation films and series; only the end of both streams ends the catalogue.
  let hasMore=kindHasMore;
  if(kinds.length>1&&!hasMore){const other=new URLSearchParams(q);other.delete('with_companies');other.delete('with_networks');
   const otherKind=kind==='movie'?'tv':'movie';other.delete('first_air_date.lte');other.delete('primary_release_date.lte');other.set(otherKind==='tv'?'first_air_date.lte':'primary_release_date.lte',new Date(now()).toISOString().slice(0,10));
   if(otherKind==='tv'&&studio.networks.length)other.set('with_networks',studio.networks.join('|'));
   else{const ids=await companies(studio,token);if(ids.length)other.set('with_companies',ids.join('|'));}
   other.set('sort_by',order==='last'?(otherKind==='tv'?'first_air_date.desc':'primary_release_date.desc'):'popularity.desc');
   if(other.has('with_networks')||other.has('with_companies')){const next=await cached('discover:'+studio.id+':'+category+':'+other,()=>request('/discover/'+otherKind+'?'+other,token));hasMore=offset+(kind===kinds[0]?0:SIZE)<(next.results||[]).length||sourcePage<(Number(next.total_pages)||1);}
  }
  return {items,hasMore:hasMore&&page<MAX_PAGE,page};
 }
 return {lookup};
}
