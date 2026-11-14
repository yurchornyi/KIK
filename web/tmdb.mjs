import {titleKey} from './sync.mjs';
import {studiosFor} from './public/studios.js';

const API='https://api.themoviedb.org/3',CDN='https://image.tmdb.org/t/p';
const DAY=86400000,MAX_ENTRIES=400;
const normalize=value=>String(value||'').normalize('NFKD').toLowerCase().replace(/\p{M}/gu,'').replace(/[^\p{L}\p{N}]/gu,'');
const imagePath=value=>typeof value==='string'&&/^\/[a-zA-Z0-9_-]+\.(jpg|png|webp)$/.test(value);

/** A season's or an edition's mark the source adds to a name and TMDB never has: «Атака титанов [ТВ-2]», «[OVA-1]», «(2023)». */
const SEASON_MARK=/\s*\[(?:ТВ|TV|OVA|ONA|ТВ-спешл|ТВ-спэшл|спешл|спэшл|фильм)[^\]]*\]/gi,EDITION_YEAR=/\s*\((?:18|19|20)\d{2}\)\s*$/;
const bare=name=>name.replace(SEASON_MARK,' ').replace(EDITION_YEAR,'').replace(/\s+/g,' ').trim();
export function artworkIdentity(input){
 const path=titleKey(input.link);
 const raw=typeof input.title==='string'?input.title.trim():'';
 if(!raw||raw.length>200)throw Error('Некоректна назва для обкладинки');
 const original=typeof input.original==='string'?bare(input.original.trim().slice(0,200)):'';
 // A second or later season starts years after the show did, and TMDB knows a show by the year it began: its own year
 // proves nothing (14.11: «Атака титанов [ТВ-2]» 2017 is TMDB's 2013 show).
 const later=[...raw.matchAll(/\[(?:ТВ|TV)-(\d+)\]/gi)].some(m=>Number(m[1])>1);
 const year=later?null:Number(String(input.year||'').match(/(?:18|19|20)\d{2}/)?.[0]||path.match(/-((?:18|19|20)\d{2})\.html$/)?.[1])||null;
 const kind=input.kind==='tv'||path.startsWith('series/')?'tv':'movie';
 // The source joins alternative names with " / " ("Клан Сопрано / Сопрано"); each one is a separate candidate, without
 // the season marks.
 const title=bare(raw)||raw,aliases=[...new Set(raw.split(/\s+\/\s+/).map(x=>bare(x)).filter(Boolean))].slice(0,3);
 return {title,original,year,kind,aliases};
}

/** Require a close title and date match; never pick a popular unrelated result. */
export function matchTitle(identity,results){
 const names=[identity.original,identity.title,...(identity.aliases||[])].filter(Boolean).map(normalize);
 const ranked=[];
 for(const item of results||[]){
  if(!Number.isSafeInteger(item.id)||item.id<=0||item.adult)continue;
  const titles=[item.title,item.original_title,item.name,item.original_name].filter(Boolean).map(normalize);
  if(!names.some(name=>titles.includes(name)))continue;
  const year=Number((item.release_date||item.first_air_date||'').slice(0,4));
  if(identity.year&&(!year||Math.abs(year-identity.year)>1))continue;
  const score=identity.year?(year===identity.year?100:90):100;
  ranked.push({item,score});
 }
 ranked.sort((a,b)=>b.score-a.score);
 if(!ranked.length||ranked[1]?.score===ranked[0].score)return null;
 return ranked[0].item;
}

export function selectImages(images,id,kind){
 const valid=image=>imagePath(image.file_path)&&Number.isFinite(image.width)&&Number.isFinite(image.height);
 const backdrops=(images.backdrops||[]).filter(i=>valid(i)&&i.width>=1280&&i.height>=720&&i.width/i.height>=1.5&&i.width/i.height<=2.2);
 backdrops.sort((a,b)=>(b.iso_639_1==null?100:0)-(a.iso_639_1==null?100:0)+Math.min(b.width,3840)/384-Math.min(a.width,3840)/384+(b.vote_average||0)-(a.vote_average||0));
 const language=i=>i.iso_639_1==='uk'?30:i.iso_639_1==null?20:i.iso_639_1==='en'?10:0;
 const posters=(images.posters||[]).filter(i=>valid(i)&&i.width>=500&&i.height>=750&&i.width/i.height>=.55&&i.width/i.height<=.8);
 posters.sort((a,b)=>language(b)-language(a)+(b.vote_average||0)-(a.vote_average||0));
 const backdrop=backdrops[0],poster=posters[0];
 if(!backdrop&&!poster)return null;
 return {source:'tmdb',id,kind,page:`https://www.themoviedb.org/${kind}/${id}`,
  poster:poster?`${CDN}/w500${poster.file_path}`:null,
  posterLarge:poster?`${CDN}/w780${poster.file_path}`:null,
  posterOriginal:poster?`${CDN}/original${poster.file_path}`:null,
  posterWidth:poster?.width||null,
  backdrop:backdrop?`${CDN}/w1280${backdrop.file_path}`:null,
  backdropOriginal:backdrop?`${CDN}/original${backdrop.file_path}`:null,
  backdropWidth:backdrop?.width||null};
}

/** A lookup still marked in flight after this long is taken as dead (its request was cancelled), never as running. */
const ACTIVE_FOR=20000;
/** Public artwork only. Both TMDB credential formats stay on this server. */
export function createArtworkService({fetcher=globalThis.fetch,now=Date.now,originalOf=null}={}){
 // Only settled answers are shared across requests. A Worker request cannot await another request's I/O: it never settles
 // once that request is cancelled (06.11 audit). `active` only counts lookups in flight (at most 12) and forgets a mark
 // after ACTIVE_FOR, so a lookup that never finished cannot hold a place for the isolate's life.
 const memory=new Map(),active=new Map();let blockedUntil=0;
 async function request(path,token,signal){
  // Workers accept only "follow" or "manual"; TMDB never redirects, so a 3xx is treated as a failure below.
  const credential=token.trim().replace(/^Bearer\s+/i,''),key=/^[a-f0-9]{32}$/i.test(credential);
  const url=API+path+(key?(path.includes('?')?'&':'?')+'api_key='+encodeURIComponent(credential):'');
  const r=await fetcher(url,{headers:{Accept:'application/json',...(!key?{Authorization:`Bearer ${credential}`}:{})},signal,redirect:'manual'});
  if(!r.ok){if([401,403,429].includes(r.status))blockedUntil=now()+60000;console.warn('TMDB request failed',r.status);throw Error('Artwork provider unavailable');}
  return r.json();
 }
 async function resolve(identity,token,link){
  const signal=AbortSignal.timeout(6500);
  const queries=[...new Set([identity.original,...(identity.aliases?.length?identity.aliases:[identity.title])].filter(Boolean))].slice(0,3);
  async function search(query,who=identity,kind=identity.kind){
   const language=/[ієїґ]/i.test(query)?'uk-UA':/[а-яё]/i.test(query)?'ru-RU':'en-US',yearKey=kind==='tv'?'first_air_date_year':'year';
   const params=new URLSearchParams({query,language,include_adult:'false',page:'1'});
   if(identity.year)params.set(yearKey,String(identity.year));
   const data=await request(`/search/${kind}?${params}`,token,signal);
   const found=matchTitle(who,data.results);if(found)return {...found,kind};
   // Regional release years can differ; validate the bounded year difference ourselves.
   if(identity.year){params.delete(yearKey);const nearby=await request(`/search/${kind}?${params}`,token,signal);const near=matchTitle(who,nearby.results);return near?{...near,kind}:null;}
   return null;
  }
  // Found in three steps, each only when the one before found nothing (14.11, owner: «треба прокачати визначення сервісу»;
  // 120 of 331 Home and catalogue titles were found before):
  //  1. the card's names, as the kind its section says;
  //  2. the same names as the other kind: anime and cartoon series live under /animation/ and /cartoons/, not /series/,
  //     and were searched as films (78 of 86 anime missed);
  //  3. the original name from the title's page on the source, both kinds: a row's card has only its Russian name, and TMDB
  //     often has none for a new title yet («Пропавшая без вести: Натали Холлоуэй» is «Taken: Natalee Holloway», Netflix),
  //     or spells it with «ё» the source leaves out («Зелёная миля»).
  const other=identity.kind==='tv'?'movie':'tv';
  let match=null;
  for(const query of queries){match=await search(query);if(match)break;}
  if(!match)for(const query of queries){match=await search(query,identity,other);if(match)break;}
  if(!match&&!identity.original&&originalOf){
   let original='';try{original=String(await originalOf(link)||'').trim();}catch{}
   // The page's original name is a name of the title too: a result is matched against it as well.
   if(original&&!queries.includes(original)){const who={...identity,original};match=await search(original,who)||await search(original,who,other);}
  }
  if(!match)return null;
  const kind=match.kind;
  const details=await request(`/${kind}/${match.id}?append_to_response=images&include_image_language=uk,en,null`,token,signal);
  const artwork=selectImages(details.images||details,match.id,kind),studios=studiosFor(details);
  return artwork?{...artwork,studios}:studios.length?{source:'tmdb',id:match.id,kind,page:`https://www.themoviedb.org/${kind}/${match.id}`,studios}:null;
 }
 async function lookup(input,{token,cache,origin='https://kik-artwork.invalid'}={}){
  const identity=artworkIdentity(input);
  if(typeof token!=='string'||!token.trim())return {configured:false,artwork:null,retryAfter:30};
  token=token.trim();
  const bytes=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(JSON.stringify(identity)+'\n'+token));
  const digest=Array.from(new Uint8Array(bytes),b=>b.toString(16).padStart(2,'0')).join('');
  const key=new Request(new URL('/__artwork-cache/v5/'+digest,origin));
  const hit=memory.get(digest);if(hit&&hit.until>now())return hit.value;
  for(const [mark,at] of active)if(now()-at>ACTIVE_FOR)active.delete(mark);
  if(active.size>=12)return {configured:true,artwork:null,retryAfter:10};
  const mark=Symbol(digest);active.set(mark,now());
  const work=(async()=>{
   if(cache)try{const cached=await cache.match(key);if(cached){const value=await cached.json();remember(value,Math.min(value.artwork?7*DAY:DAY/2,60000));return value;}}catch{}
   if(now()<blockedUntil)return {configured:true,artwork:null,retryAfter:60};
   let artwork;
   try{artwork=await resolve(identity,token,input.link);}catch(e){console.warn('TMDB lookup failed',e?.message);const value={configured:true,artwork:null,retryAfter:60};remember(value,60000);return value;}
   const value={configured:true,artwork},ttl=artwork?7*DAY:DAY/2;remember(value,ttl);
   if(cache)try{await cache.put(key,new Response(JSON.stringify(value),{headers:{'Content-Type':'application/json','Cache-Control':`public, max-age=${ttl/1000}`}}));}catch{}
   return value;
  })();
  function remember(value,ttl){if(memory.size>=MAX_ENTRIES)memory.delete(memory.keys().next().value);memory.set(digest,{value,until:now()+ttl});}
  try{return await work;}finally{active.delete(mark);}
 }
 return {lookup};
}
