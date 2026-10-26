import {studioById} from './public/studios.js';
import {matchTitle} from './tmdb.mjs';

const API='https://api.themoviedb.org/3',SIZE=5,MAX_PAGE=4000;
const fold=s=>String(s||'').toLowerCase().replace(/[^\p{L}\p{N}]/gu,'');
const normalizeCompany=s=>String(s||'').toLowerCase().replace(/[^a-z0-9]/g,'');
/** The source's genre slugs as discovery genres (movie, tv; «|» means any of them). Without this the brand's most popular
 * titles are checked one by one and a narrow genre such as documentaries almost never turns up. Unmapped genres still work,
 * only more slowly: every matched title is checked on the source either way. */
const GENRES={action:['28','10759'],adventures:['12','10759'],comedy:['35','35'],crime:['80','80'],detective:['9648|80','9648|80'],documentary:['99','99'],cognitive:['99','99'],
 drama:['18','18'],family:['10751','10751'],kids:['10751','10762'],fantasy:['14','10765'],fiction:['878','10765'],historical:['36',''],horror:['27',''],military:['10752','10768'],
 melodrama:['10749',''],musical:['10402',''],concert:['10402',''],thriller:['53',''],western:['37','37'],realtv:['','10764'],telecasts:['','10767|10763']};
const MAX_STUDIOS=9;
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
 /** Several studios at once: any of their networks for series, any of their companies otherwise.
  * A series discovery cannot mix networks with companies, so studios without a network (Warner, Pixar) only count when no selected studio has one. */
 async function brandFilter(studios,kind,token){
  const networks=[...new Set(studios.flatMap(s=>s.networks))];
  if(kind==='tv'&&networks.length)return ['with_networks',networks.join('|')];
  const ids=[...new Set((await Promise.all(studios.map(s=>companies(s,token)))).flat())];return ids.length?['with_companies',ids.join('|')]:null;
 }
 async function lookup(params,{token}={}){
  if(!String(token||'').trim())throw Object.assign(Error('Фільтр студій тимчасово недоступний. Спробуйте пізніше.'),{status:503});
  const ids=[...new Set(String(params.studio||'').split(',').map(x=>x.trim()).filter(Boolean))],studios=ids.map(studioById),category=params.category||'films',order=params.filter||'popular',genre=params.genre||'';
  if(!ids.length||ids.length>MAX_STUDIOS||studios.some(x=>!x)||!['films','series','cartoons','animation'].includes(category)||!['popular','last'].includes(order)||genre&&!/^[a-z0-9-]{1,60}$/.test(genre))throw Error('Некоректний фільтр студії');
  if(params.query||params.source&&params.source!=='rezka')throw Error('Несумісні фільтри каталогу');
  let page=Math.max(1,Math.min(MAX_PAGE,Math.floor(Number(params.page)||1)));
  const kinds=['cartoons','animation'].includes(category)?['movie','tv']:[category==='series'?'tv':'movie'];
  page=Math.min(page,2000*kinds.length);
  const kind=kinds[(page-1)%kinds.length],window=Math.floor((page-1)/kinds.length),sourcePage=Math.floor(window/4)+1,offset=window%4*SIZE;
  const q=new URLSearchParams({language:'ru-RU',include_adult:'false',page:String(sourcePage),sort_by:order==='last'?(kind==='tv'?'first_air_date.desc':'primary_release_date.desc'):'popularity.desc'});
  const brand=await brandFilter(studios,kind,token);if(!brand)return {items:[],hasMore:kinds.length>1&&kind===kinds[0],page};q.set(...brand);
  q.set(kind==='tv'?'first_air_date.lte':'primary_release_date.lte',new Date(now()).toISOString().slice(0,10));
  // The source keeps animated titles under «Мультфільми» and «Аніме»: in films and series they would never match.
  const mapped=category==='cartoons'||category==='animation'?'':GENRES[genre]?.[kind==='tv'?1:0]||'';
  if(category==='cartoons'||category==='animation')q.set('with_genres','16');else{q.set('without_genres','16');if(mapped)q.set('with_genres',mapped);}
  const key=studios.map(x=>x.id).sort().join('+');
  const data=await cached('discover:'+key+':'+category+':'+q,()=>request('/discover/'+kind+'?'+q,token));
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
   // With several studios the discovery page does not say whose title it is: the badge then comes from the title's own metadata.
   items.push({...card,studios:studios.length===1?[studios[0].id]:[],original,year,_order:candidates.indexOf(candidate)});
  }));
  items.sort((a,b)=>a._order-b._order);for(const item of items)delete item._order;
  const kindHasMore=offset+SIZE<data.results.length||sourcePage<Math.min(500,Number(data.total_pages)||1);
  // Alternate animation films and series; only the end of both streams ends the catalogue.
  let hasMore=kindHasMore;
  if(kinds.length>1&&!hasMore){const other=new URLSearchParams(q);other.delete('with_companies');other.delete('with_networks');
   const otherKind=kind==='movie'?'tv':'movie';other.delete('first_air_date.lte');other.delete('primary_release_date.lte');other.set(otherKind==='tv'?'first_air_date.lte':'primary_release_date.lte',new Date(now()).toISOString().slice(0,10));
   const otherBrand=await brandFilter(studios,otherKind,token);if(otherBrand)other.set(...otherBrand);
   other.set('sort_by',order==='last'?(otherKind==='tv'?'first_air_date.desc':'primary_release_date.desc'):'popularity.desc');
   if(other.has('with_networks')||other.has('with_companies')){const next=await cached('discover:'+key+':'+category+':'+other,()=>request('/discover/'+otherKind+'?'+other,token));hasMore=offset+(kind===kinds[0]?0:SIZE)<(next.results||[]).length||sourcePage<(Number(next.total_pages)||1);}
  }
  return {items,hasMore:hasMore&&page<MAX_PAGE,page};
 }
 return {lookup};
}
