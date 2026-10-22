import {socketHttp} from "./source-transport.mjs";
import {sha256} from "@noble/hashes/sha2.js";
import {titleKey} from "./sync.mjs";
const HOSTS=["hdrezka.sh","hdrezka.website","rezka.ag","hdrezka.ag"],UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36";
const cookies=new Map(),pages=new Map();let host=HOSTS[0];
const one=(h,re)=>h.match(re)?.[1]??null;
export function text(h){return h==null?null:h.replace(/<br\s*\/?>/gi,"\n").replace(/<[^>]*>/g,"").replace(/&nbsp;/g," ").replace(/&quot;/g,'"').replace(/&#039;|&apos;/g,"'").replace(/&laquo;/g,"«").replace(/&raquo;/g,"»").replace(/&mdash;/g,"—").replace(/&ndash;/g,"–").replace(/&amp;/g,"&").replace(/&#(\d+);/g,(_,n)=>String.fromCodePoint(+n)).replace(/[ \t\r]+/g," ").trim();}
async function send(url,form=null){
 for(let hop=0;hop<6;hop++){
  const u=new URL(url);if(!HOSTS.includes(u.hostname))throw Error("Untrusted catalogue redirect: "+u.hostname);let jar=cookies.get(u.hostname);if(!jar){jar=new Map();cookies.set(u.hostname,jar);}
  const headers={"Accept":"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8","Accept-Encoding":"gzip","User-Agent":UA,"Accept-Language":"uk-UA,uk;q=0.9,ru;q=0.8","Cookie":[...jar].map(([k,v])=>`${k}=${v}`).join("; ")};
  if(!headers.Cookie)delete headers.Cookie;
  if(form){headers["Content-Type"]="application/x-www-form-urlencoded; charset=UTF-8";headers["X-Requested-With"]="XMLHttpRequest";headers.Referer=u.origin+"/";headers.Origin=u.origin;}
  const r=await socketHttp(url,{method:form?"POST":"GET",headers,body:form,redirect:"manual",signal:AbortSignal.timeout(15000)});
  for(const cookie of r.headers.getSetCookie()){const pair=cookie.split(";",1)[0],at=pair.indexOf("=");if(at>0)jar.set(pair.slice(0,at),pair.slice(at+1));}
  if(r.status>=300&&r.status<400){url=new URL(r.headers.get("Location"),url).href;form=null;await r.body?.cancel();continue;}
  const content=await r.text();if(!r.ok&&!content.includes("anubis_challenge"))throw Error("Catalogue "+u.hostname+u.pathname+" HTTP "+r.status+" server="+r.headers.get("server")+" "+(one(content,/<title>(.*?)<\/title>/is)||content.slice(0,120)));if(content.length>3*1024*1024)throw Error("Catalogue limit");return {html:content,origin:u.hostname};
 }throw Error("Catalogue redirect limit");
}
async function solve(result,url){
 const raw=one(result.html,/<script[^>]*id="anubis_challenge"[^>]*>(.*?)<\/script>/s);if(!raw)return result;const challenge=JSON.parse(raw),difficulty=challenge.rules?.difficulty??2,c=challenge.challenge;
 if(difficulty>4||typeof c?.randomData!=="string")throw Error("Каталог вимагає складнішої перевірки. Спробуйте пізніше.");
 const start=Date.now(),prefix=sha256.create().update(new TextEncoder().encode(c.randomData));let nonce=0,digest;
 const zero=bytes=>{for(let i=0;i<difficulty;i++){const value=i%2?bytes[i>>1]&15:bytes[i>>1]>>4;if(value!==0)return false;}return true;};
 for(;nonce<500000;nonce++){const bytes=prefix.clone().update(new TextEncoder().encode(String(nonce))).digest();if(zero(bytes)){digest=Array.from(bytes,b=>b.toString(16).padStart(2,"0")).join("");break;}}if(nonce===500000)throw Error("Catalogue check timeout");
 
 const q=new URLSearchParams({id:c.id,response:digest,nonce:String(nonce),redir:url,elapsedTime:String(Date.now()-start+500)});
 return send(`https://${result.origin}/.within.website/x/cmd/anubis/api/pass-challenge?${q}`);
}
async function page(path,ttl=120000){const cached=pages.get(path);if(cached&&Date.now()-cached.at<ttl)return cached.value;
 let last;for(const h of [host,...HOSTS.filter(x=>x!==host)])try{const url="https://"+h+path;let r=await send(url);if(r.html.includes("anubis_challenge"))r=await solve(r,url);if(r.html.includes("anubis_challenge"))throw Error("Каталог не пройшов перевірку");host=r.origin;if(pages.size>80)pages.delete(pages.keys().next().value);pages.set(path,{at:Date.now(),value:r});return r;}catch(e){console.warn("KIK mirror",h,e.message);last=e;}
 throw last;
}
export function parseCards(html){const out=[];for(const chunk of html.split('<div class="b-content__inline_item"').slice(1)){const link=one(chunk,/^[^>]*data-url="([^"]+)"/);if(!link)continue;try{titleKey(link);}catch{continue;}const clean=chunk.replace(/<!--.*?-->/gs,"");let title=text(one(clean,/b-content__inline_item-link">\s*<a[^>]*>(.*?)<\/a>/s));if(title)out.push({link,title,poster:one(clean,/<img src="([^"]+)"/),info:text(one(clean,/b-content__inline_item-link">\s*<a[^>]*>.*?<\/a>\s*<div>(.*?)<\/div>/s)),type:text(one(clean,/<i class="entity">(.*?)<\/i>/s))});}return out;}
export async function catalogue({category="",filter="popular",page:pageNumber=1,query=""}){
 if(!["","films","series","cartoons","animation"].includes(category)||!["popular","last","watching"].includes(filter))throw Error("Некоректний фільтр");pageNumber=Math.max(1,Math.min(100,Number(pageNumber)||1));
 const path=query?`/search/?do=search&subaction=search&q=${encodeURIComponent(query.slice(0,150))}&page=${pageNumber}`:`/${category?category+"/":""}${pageNumber>1?"page/"+pageNumber+"/":""}?filter=${filter}`;
 return parseCards((await page(path,300000)).html);
}
export function parseEpisodes(html){const out=[],seen=new Set();for(const m of html.matchAll(/<(?:li|a)[^>]*data-season_id="(\d+)"[^>]*data-episode_id="(\d+)"[^>]*>(.*?)<\/(?:li|a)>/gs)){const key=m[1]+":"+m[2];if(!seen.has(key)){seen.add(key);out.push({season:m[1],episode:m[2],title:text(m[3])});}}return out;}
export function parseFilm(link,h){const info=one(h,/<table class="b-post__info">(.*?)<\/table>/s)||"",f={link,title:text(one(h,/<h1[^>]*>(.*?)<\/h1>/s)),original:text(one(h,/<div class="b-post__origtitle"[^>]*>(.*?)<\/div>/s)),poster:one(h,/<div class="b-sidecover">\s*<a href="([^"]+)"/s)||one(h,/<div class="b-sidecover">.*?<img src="([^"]+)"/s),description:text(one(h,/<div class="b-post__description_text"[^>]*>(.*?)<\/div>/s)),genres:[],voices:[],episodes:[],favs:one(h,/id="ctrl_favs"[^>]*value="([^"]*)"/)};
 for(const m of info.matchAll(/<tr>\s*<td class="l"><h2>(.*?)<\/h2>:?<\/td>\s*<td[^>]*>(.*?)<\/td>\s*<\/tr>/gs)){const key=text(m[1]),v=m[2];if(key.startsWith("Рейтинг")){f.rating=one(v,/imdb">.*?<span class="bold">([\d.]+)<\/span>/s)||one(v,/kp">.*?<span class="bold">([\d.]+)<\/span>/s);}else if(key.startsWith("Дата выхода")||key.startsWith("Год"))f.year=one(v,/(\d{4})/);else if(key.startsWith("Жанр"))f.genres=[...v.matchAll(/itemprop="genre">(.*?)<\/span>/gs)].map(x=>text(x[1]));else if(key.startsWith("В переводе"))f.defaultVoiceName=text(v);else if(key.startsWith("Время"))f.duration=text(v);}
 const init=h.match(/initCDN(Series|Movies)Events\((\d+),\s*(\d+)(?:,\s*(\d+),\s*(\d+),\s*(\d+))?/);f.series=init?init[1]==="Series":link.includes("/series/");f.id=init?.[2]||one(h,/data-id="(\d+)"/);f.defaultVoice=init?.[3];f.defaultFlags=init?[init[4]==="1",init[5]==="1",init[6]==="1"]:[false,false,false];
 const tl=one(h,/id="translators-list"[^>]*>(.*?)<\/ul>/s)||"",seen=new Set();for(const m of tl.matchAll(/<li([^>]*)>(.*?)<\/li>/gs)){const attrs=m[1]+" "+(one(m[2],/^\s*<a([^>]*)>/)||""),id=one(attrs,/data-translator_id="(\d+)"/);if(!id||seen.has(id))continue;seen.add(id);let name=text(one(attrs,/title="([^"]*)"/)||m[2]);const lang=one(m[2],/alt="([^"]+)"/);if(lang&&!name.includes("("))name+=" ("+lang+")";f.voices.push({id,name,premium:attrs.includes("b-prem_translator"),camrip:/data-camrip="1"/.test(attrs),ads:/data-ads="1"/.test(attrs),director:/data-director="1"/.test(attrs)});}
 if(!f.voices.length&&f.defaultVoice)f.voices.push({id:f.defaultVoice,name:f.defaultVoiceName||"Основна доріжка",camrip:f.defaultFlags[0],ads:f.defaultFlags[1],director:f.defaultFlags[2]});f.episodes=parseEpisodes(h);return f;
}
export async function film(link){const key=titleKey(link),r=await page("/"+key),f=parseFilm("https://hdrezka.sh/"+key,r.html);f.imdb=one(r.html,/imdb\.com\/title\/(tt\d+)/);f.origin=r.origin;if(!f.id||!f.title)throw Error("Не вдалося прочитати сторінку фільму");return f;}
export async function episodes(f,voice){if(!f.voices.some(v=>v.id===voice&&!v.premium))throw Error("Озвучення недоступне");const r=await send(`https://${f.origin}/ajax/get_cdn_series/?t=${Date.now()}`,new URLSearchParams({id:f.id,translator_id:voice,favs:f.favs||"",action:"get_episodes"}).toString()),o=JSON.parse(r.html);if(!o.success)throw Error("Список серій недоступний");return parseEpisodes((o.seasons||"")+(o.episodes||""));}
export function decode(value){if(!value.startsWith("#h"))return value;let s=value.slice(2).replaceAll("//_//","");const combos=n=>n?[...combos(n-1)].flatMap(p=>["@","#","!","^","$"].map(x=>p+x)):[""];for(const n of [2,3])for(const p of combos(n))s=s.split(Buffer.from(p).toString("base64")).join("");try{return Buffer.from(s,"base64").toString("utf8");}catch{return s;}}
export async function stream(f,voice,season,episode){const v=f.voices.find(v=>v.id===voice&&!v.premium);if(!v)throw Error("Озвучення недоступне");if(f.series&&(!/^\d{1,4}$/.test(season)||!/^\d{1,5}$/.test(episode)))throw Error("Виберіть серію");
 const body=new URLSearchParams({id:f.id,translator_id:v.id,favs:f.favs||"",...(f.series?{season,episode,action:"get_stream"}:{is_camrip:v.camrip?"1":"0",is_ads:v.ads?"1":"0",is_director:v.director?"1":"0",action:"get_movie"})});const r=await send(`https://${f.origin}/ajax/get_cdn_series/?t=${Date.now()}`,body.toString()),o=JSON.parse(r.html);if(!o.success)throw Error(text(o.message)||"Відео недоступне");const decoded=decode(o.url||"");const qualities=[];for(const m of decoded.matchAll(/\[([^\]]+)\]([^,\[]+)/g)){const alternatives=m[2].split(" or ").map(x=>x.trim());const url=alternatives.find(x=>x.endsWith(".mp4"))||alternatives.at(-1);if(/^https:\/\//.test(url))qualities.push({label:text(m[1]),url});}if(!qualities.length)throw Error("Потік не знайдено");return {qualities};
}
