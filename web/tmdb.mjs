import {titleKey} from './sync.mjs';

const API='https://api.themoviedb.org/3',CDN='https://image.tmdb.org/t/p';
const DAY=86400000,MAX_ENTRIES=400;
const normalize=value=>String(value||'').normalize('NFKD').toLowerCase().replace(/\p{M}/gu,'').replace(/[^\p{L}\p{N}]/gu,'');
const imagePath=value=>typeof value==='string'&&/^\/[a-zA-Z0-9_-]+\.(jpg|png|webp)$/.test(value);

export function artworkIdentity(input){
 const path=titleKey(input.link);
 const title=typeof input.title==='string'?input.title.trim():'';
 if(!title||title.length>200)throw Error('Некоректна назва для обкладинки');
 const original=typeof input.original==='string'?input.original.trim().slice(0,200):'';
 const year=Number(String(input.year||'').match(/(?:18|19|20)\d{2}/)?.[0]||path.match(/-((?:18|19|20)\d{2})\.html$/)?.[1])||null;
 const kind=input.kind==='tv'||path.startsWith('series/')?'tv':'movie';
 return {title,original,year,kind};
}

/** Require a close title and date match; never pick a popular unrelated result. */
export function matchTitle(identity,results){
 const names=[identity.original,identity.title].filter(Boolean).map(normalize);
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
 const posters=(images.posters||[]).filter(i=>valid(i)&&i.width>=300&&i.height>=450&&i.width/i.height>=.55&&i.width/i.height<=.8);
 posters.sort((a,b)=>language(b)-language(a)+(b.vote_average||0)-(a.vote_average||0));
 const backdrop=backdrops[0],poster=posters[0];
 if(!backdrop&&!poster)return null;
 return {source:'tmdb',id,kind,page:`https://www.themoviedb.org/${kind}/${id}`,
  poster:poster?`${CDN}/w500${poster.file_path}`:null,
  posterLarge:poster?`${CDN}/w780${poster.file_path}`:null,
  backdrop:backdrop?`${CDN}/w1280${backdrop.file_path}`:null,
  backdropOriginal:backdrop?`${CDN}/original${backdrop.file_path}`:null,
  backdropWidth:backdrop?.width||null};
}

/** Public artwork only. Credentials stay in Authorization headers on this server. */
export function createArtworkService({fetcher=globalThis.fetch,now=Date.now}={}){
 const memory=new Map(),pending=new Map();let blockedUntil=0;
 async function request(path,token,signal){
  const r=await fetcher(API+path,{headers:{Authorization:`Bearer ${token}`,Accept:'application/json'},signal,redirect:'error'});
  if(!r.ok){if([401,403,429].includes(r.status))blockedUntil=now()+60000;throw Error('Artwork provider unavailable');}
  return r.json();
 }
 async function resolve(identity,token){
  const signal=AbortSignal.timeout(6500),language=/[ієїґ]/i.test(identity.title)?'uk-UA':/[а-яё]/i.test(identity.title)?'ru-RU':'en-US';
  const queries=[...new Set([identity.original,identity.title].filter(Boolean))];
  let match=null;
  for(const query of queries){
   const params=new URLSearchParams({query,language,include_adult:'false',page:'1'});
   if(identity.year)params.set(identity.kind==='tv'?'first_air_date_year':'year',String(identity.year));
   const data=await request(`/search/${identity.kind}?${params}`,token,signal);
   match=matchTitle(identity,data.results);if(match)break;
  }
  if(!match)return null;
  const images=await request(`/${identity.kind}/${match.id}/images?include_image_language=uk,en,null`,token,signal);
  return selectImages(images,match.id,identity.kind);
 }
 async function lookup(input,{token,cache,origin='https://kik-artwork.invalid'}={}){
  const identity=artworkIdentity(input);
  if(typeof token!=='string'||!token.trim())return {configured:false,artwork:null,retryAfter:30};
  token=token.trim();
  const bytes=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(JSON.stringify(identity)+'\n'+token));
  const digest=Array.from(new Uint8Array(bytes),b=>b.toString(16).padStart(2,'0')).join('');
  const key=new Request(new URL('/__artwork-cache/v1/'+digest,origin));
  const hit=memory.get(digest);if(hit&&hit.until>now())return hit.value;
  if(pending.has(digest))return pending.get(digest);
  if(pending.size>=12)return {configured:true,artwork:null,retryAfter:10};
  const work=(async()=>{
   if(cache)try{const cached=await cache.match(key);if(cached){const value=await cached.json();remember(value,Math.min(value.artwork?7*DAY:DAY/2,60000));return value;}}catch{}
   if(now()<blockedUntil)return {configured:true,artwork:null,retryAfter:60};
   let artwork;
   try{artwork=await resolve(identity,token);}catch{const value={configured:true,artwork:null,retryAfter:60};remember(value,60000);return value;}
   const value={configured:true,artwork},ttl=artwork?7*DAY:DAY/2;remember(value,ttl);
   if(cache)try{await cache.put(key,new Response(JSON.stringify(value),{headers:{'Content-Type':'application/json','Cache-Control':`public, max-age=${ttl/1000}`}}));}catch{}
   return value;
  })();
  function remember(value,ttl){if(memory.size>=MAX_ENTRIES)memory.delete(memory.keys().next().value);memory.set(digest,{value,until:now()+ttl});}
  pending.set(digest,work);try{return await work;}finally{pending.delete(digest);}
 }
 return {lookup};
}
