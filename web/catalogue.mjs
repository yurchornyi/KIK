import {mediaUrl} from './public/playback-recovery.js';
import {socketHttp} from "./source-transport.mjs";
import {sha256} from "@noble/hashes/sha2.js";
import {titleKey} from "./sync.mjs";
export const HOSTS=["hdrezka.sh","hdrezka.website","rezka.ag","hdrezka.ag"],UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36";
const cookies=new Map(),pages=new Map();let host=HOSTS[0],pausedUntil=0,refusals=0,nextRequestAt=0;
const one=(h,re)=>h.match(re)?.[1]??null;
export function sourceRefusal(status,html){return /подозрительная активность с вашего IP/i.test(html)?'ip':status===429||status===403&&!html.includes('anubis_challenge')?'refused':null;}
function retryDelay(value){if(!value)return 0;const seconds=Number(value);if(Number.isFinite(seconds))return Math.min(86400000,Math.max(0,seconds)*1000);const date=Date.parse(value);return Number.isFinite(date)?Math.min(86400000,Math.max(0,date-Date.now())):0;}
/** How long KIK leaves the source alone after a refusal: 2 minutes (10 for an IP ban), doubling with each refusal in a row,
 *  at most 30, or longer when the source's Retry-After asks for it. One refusal no longer stops every title for half an hour. */
export function refusalPause(kind,streak,retryAfter=0){return Math.max(Math.min(30*60000,(kind==='ip'?10:2)*60000*2**Math.max(0,streak-1)),retryAfter);}
/** No slot for this request within its time budget, or no mirror answered: the web and TV retry by themselves. */
function busy(){return Object.assign(Error('Джерело фільмів зараз не відповідає. Спробуйте за хвилину.'),{sourceBusy:true,status:503});}
/** HDRezka refused KIK (ban or 429): the web and TV show this message as is. */
function paused(){return Object.assign(Error('Джерело фільмів тимчасово недоступне. Спробуйте пізніше.'),{sourcePaused:true,status:503});}
/** Requests to the source start at least a second apart. Each caller books its own start and waits inside its own request,
 *  so a viewer who gave up (a closed tab, a TV timeout) never holds the line for everyone else. Background work (similar
 *  titles) only takes a slot that is about to be free, so a title someone opens never waits behind it. */
export async function sourceSlot(task,until=Infinity,background=false){
 if(Date.now()<pausedUntil)throw paused();
 const start=Math.max(Date.now(),nextRequestAt),wait=start-Date.now();
 if(wait>(background?1500:18000)||start>until-1500)throw busy();
 nextRequestAt=start+1000;
 if(wait>0)await new Promise(resolve=>setTimeout(resolve,wait));
 if(Date.now()<pausedUntil)throw paused();
 return task();
}
export function text(h){return h==null?null:h.replace(/<br\s*\/?>/gi,"\n").replace(/<[^>]*>/g,"").replace(/&nbsp;/g," ").replace(/&quot;/g,'"').replace(/&#039;|&apos;/g,"'").replace(/&laquo;/g,"«").replace(/&raquo;/g,"»").replace(/&mdash;/g,"—").replace(/&ndash;/g,"–").replace(/&amp;/g,"&").replace(/&#(\d+);/g,(_,n)=>String.fromCodePoint(+n)).replace(/[ \t\r]+/g," ").trim();}
async function send(url,form=null,timeout=15000,until=Infinity,background=false){
 for(let hop=0;hop<6;hop++){
  const u=new URL(url);if(!HOSTS.includes(u.hostname))throw Error("Untrusted catalogue redirect: "+u.hostname);let jar=cookies.get(u.hostname);if(!jar){jar=new Map();cookies.set(u.hostname,jar);}
  const headers={"Accept":"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8","Accept-Encoding":"gzip","User-Agent":UA,"Accept-Language":"uk-UA,uk;q=0.9,ru;q=0.8","Cookie":[...jar].map(([k,v])=>`${k}=${v}`).join("; ")};
  if(!headers.Cookie)delete headers.Cookie;
  if(form){headers["Content-Type"]="application/x-www-form-urlencoded; charset=UTF-8";headers["X-Requested-With"]="XMLHttpRequest";headers.Referer=u.origin+"/";headers.Origin=u.origin;}
  const {r,content}=await sourceSlot(async()=>{const r=await socketHttp(url,{method:form?"POST":"GET",headers,body:form,redirect:"manual",timeout:Math.min(timeout,until-Date.now())}),content=await r.text();const refusal=sourceRefusal(r.status,content);if(refusal){if(Date.now()>=pausedUntil)pausedUntil=Date.now()+refusalPause(refusal,++refusals,retryDelay(r.headers.get('Retry-After')));throw paused();}refusals=0;return {r,content};},until,background);
  for(const cookie of r.headers.getSetCookie()){const pair=cookie.split(";",1)[0],at=pair.indexOf("=");if(at>0)jar.set(pair.slice(0,at),pair.slice(at+1));}
  if(r.status>=300&&r.status<400){url=new URL(r.headers.get("Location"),url).href;form=null;continue;}
  if(!r.ok&&!content.includes("anubis_challenge"))throw Error("Catalogue "+u.hostname+u.pathname+" HTTP "+r.status+" server="+r.headers.get("server")+" "+(one(content,/<title>(.*?)<\/title>/is)||content.slice(0,120)));if(content.length>3*1024*1024)throw Error("Catalogue limit");return {html:content,origin:u.hostname};
 }throw Error("Catalogue redirect limit");
}
/** HDRezka's routine bot check (Anubis): a small proof of work, as a browser would do. */
async function solve(result,url,until,background){
 const raw=one(result.html,/<script[^>]*id="anubis_challenge"[^>]*>(.*?)<\/script>/s);if(!raw)return result;const challenge=JSON.parse(raw),difficulty=challenge.rules?.difficulty??2,c=challenge.challenge;
 if(difficulty>4||typeof c?.randomData!=="string")throw Error("Каталог вимагає складнішої перевірки. Спробуйте пізніше.");
 const start=Date.now(),prefix=sha256.create().update(new TextEncoder().encode(c.randomData));let nonce=0,digest;
 const zero=bytes=>{for(let i=0;i<difficulty;i++){const value=i%2?bytes[i>>1]&15:bytes[i>>1]>>4;if(value!==0)return false;}return true;};
 for(;nonce<500000;nonce++){const bytes=prefix.clone().update(new TextEncoder().encode(String(nonce))).digest();if(zero(bytes)){digest=Array.from(bytes,b=>b.toString(16).padStart(2,"0")).join("");break;}}if(nonce===500000)throw Error("Catalogue check timeout");
 const q=new URLSearchParams({id:c.id,response:digest,nonce:String(nonce),redir:url,elapsedTime:String(Date.now()-start+500)});
 return send(`https://${result.origin}/.within.website/x/cmd/anubis/api/pass-challenge?${q}`,null,15000,until,background);
}
async function page(path,ttl=120000,options={}){if(options.bounded)options={...options,only:true,host:options.host||host,timeout:5000};const cached=pages.get(path);if(!options.fresh&&cached&&Date.now()-cached.at<ttl)return cached.value;if(Date.now()<pausedUntil&&cached&&Date.now()-cached.at<86400000)return cached.value;
 // One time budget for the whole page: the TV and the browser give up after ~25 s, a slower answer is wasted.
 const until=Date.now()+(options.budget||20000);
 let last;for(const h of (options.only?[options.host]:[options.host||host,...HOSTS.filter(x=>x!==(options.host||host))]))try{if(Date.now()>until-2000)break;const url="https://"+h+path;let r=await send(url,null,options.timeout||15000,until,options.background);if(r.html.includes("anubis_challenge"))r=await solve(r,url,until,options.background);if(r.html.includes("anubis_challenge"))throw Error("Catalogue check not passed on "+h);host=r.origin;if(pages.size>80)pages.delete(pages.keys().next().value);pages.set(path,{at:Date.now(),value:r});return r;}catch(e){if(e.sourcePaused||e.sourceBusy){if(cached&&Date.now()-cached.at<86400000)return cached.value;throw e;}console.warn("KIK mirror",h,e.message);last=e;}
 if(cached&&Date.now()-cached.at<86400000)return cached.value;
 // A missing page (HTTP 4xx) stays an error; mirrors that timed out or broke mean «try again in a minute».
 if(last&&/ HTTP 4\d\d /.test(last.message))throw last;
 throw Object.assign(busy(),{cause:last});
}
export function parseCards(html){const out=[];for(const chunk of html.split('<div class="b-content__inline_item"').slice(1)){const link=one(chunk,/^[^>]*data-url="([^"]+)"/);if(!link)continue;try{titleKey(link);}catch{continue;}const clean=chunk.replace(/<!--.*?-->/gs,"");let title=text(one(clean,/b-content__inline_item-link">\s*<a[^>]*>(.*?)<\/a>/s));if(title)out.push({link,title,poster:one(clean,/<img src="([^"]+)"/),info:text(one(clean,/b-content__inline_item-link">\s*<a[^>]*>.*?<\/a>\s*<div[^>]*>(.*?)<\/div>/s)),type:text(one(clean,/<i class="entity">(.*?)<\/i>/s))});}return out;}
const UK=/укра|ukrain|украї|1\+1|постмодерн|postmodern|le doyen|dniprofilm|так треба|ua ?team|hurtom|цікава ідея|eneida/i,FOREIGN=/оригин|оригін|original|субтитр|англи|англі|english|польск|польськ|корей|япон|франц|испан|іспан|немец|німец|грузин|белорус|білорус|казах|узбек|армян|вірмен|азербайдж|литов|латыш|эстон|турец|італій|итальян|китай|чешск|португал|иврит|арабск/i;
/** Ukrainian, then Russian (unmarked source tracks), then original or other languages. Mirrors PlaybackPolicy.java. */
const ORIGINAL=/оригин|оригін|original|англи|англі|english|субтитр/i;
export function voiceLang(name=""){return UK.test(name)?"uk":ORIGINAL.test(name)?"en":FOREIGN.test(name)?"other":"ru";}
export function voiceOrder(v){const s=(v.name||"").toLowerCase();let n=/дубляж|дубльован/.test(s)?0:/hdrezka|rezka studio/.test(s)?15:/lostfilm/.test(s)?25:/newstudio|кубик в кубе|baibako|coldfilm|tvshows|jaskier|red head sound|amedia|кураж|постмодерн|postmodern|le doyen|1\+1/.test(s)?30:/многоголос|багатоголос/.test(s)?35:/студия|студія|studio/.test(s)?45:/двухголос|двоголос/.test(s)?80:/одноголос|авторск|автор/.test(s)?150:100;
 if(/любител|аматор/.test(s))n+=200;if(/camrip|экран|екран/.test(s)||v.camrip)n+=400;if(v.ads)n+=60;if(/coldfilm|колдфильм|1win/.test(s))n+=600;return {uk:0,ru:1000,en:2000,other:2000}[voiceLang(s)]+n;}
export function qualityOrder(label=""){const s=label.toLowerCase();let n=Number(s.match(/\d+/)?.[0]||0)*2;if(s.includes("4k"))n=4320;else if(s.includes("2k"))n=2880;return n+(s.includes("ultra")?1:0);}
const fold=s=>(s||"").toLowerCase().replace(/ї|і/g,"и").replace(/є|э/g,"е").replace(/ґ/g,"г").replace(/ё/g,"е").replace(/ы/g,"и").replace(/['’ʼ]/g,"").replace(/[^\p{L}\p{N}]+/gu," ").trim();
export const searchForm=q=>q.toLowerCase().trim().replace(/ї|і/g,"и").replace(/є/g,"е").replace(/ґ/g,"г").replace(/['’ʼ]/g,"");
/** Stable relevance order: exact title, prefix, whole phrase, all words, then source order. */
export function relevant(query,items){const q=fold(query),words=q.split(" ").filter(Boolean);if(!q||items.length<2)return items;const score=item=>{const t=fold(item.title);let s=t===q?100:t.startsWith(q)?60:(" "+t+" ").includes(" "+q+" ")?45:t.includes(q)?35:(()=>{const hit=words.filter(w=>w.length>1&&(" "+t).includes(" "+w)).length;return hit===words.length?25:Math.floor(hit*15/words.length);})();const year=(item.info||"").match(/\d{4}/)?.[0];if(year&&words.includes(year))s+=20;return s;};return items.map((item,i)=>({item,i,s:score(item)})).sort((a,b)=>b.s-a.s||a.i-b.i).map(x=>x.item);}
export const CATEGORIES=["films","series","cartoons","animation"],FILTERS=["popular","last","watching"];
/** Source path of one catalogue page; a genre needs a category, and nothing outside the known names reaches the path. */
export function cataloguePath({category="",genre="",filter="popular",page:pageNumber=1,query=""}={}){
 if(!(category===""||CATEGORIES.includes(category))||!FILTERS.includes(filter)||genre&&(!category||!/^[a-z0-9-]{1,60}$/.test(genre)))throw Error("Некоректний фільтр");
 pageNumber=Math.max(1,Math.min(100,Math.floor(Number(pageNumber)||1)));
 return query?`/search/?do=search&subaction=search&q=${encodeURIComponent(query.slice(0,150))}&page=${pageNumber}`:`/${category?category+"/":""}${genre?genre+"/":""}${pageNumber>1?"page/"+pageNumber+"/":""}?filter=${filter}`;
}
/** Genres the source links from a category page, in source order, one per slug. */
export function parseGenres(html,category){
 if(!CATEGORIES.includes(category))throw Error("Некоректний фільтр");
 const found=new Map(),pattern=new RegExp('href="(?:https?://[^/"]+)?/'+category+'/([a-z0-9-]+)/"[^>]*>([^<]+)</a>','g');
 for(const m of html.matchAll(pattern))if(m[1]!=="page"&&!found.has(m[1])){const name=text(m[2]);if(name)found.set(m[1],{slug:m[1],name});}
 return [...found.values()];
}
export async function catalogueGenres(category,options={}){return parseGenres((await page(cataloguePath({category}),3600000,options)).html,category);}
export async function catalogue({category="",genre="",filter="popular",page:pageNumber=1,query=""},options={}){
 const path=cataloguePath({category,genre,filter,page:pageNumber,query});pageNumber=Math.max(1,Math.min(100,Math.floor(Number(pageNumber)||1)));
 let items=parseCards((await page(path,300000,options)).html);
 // The source spells titles in Russian: retry Ukrainian letters with their closest Russian forms.
 if(query&&!items.length&&searchForm(query)!==query.toLowerCase().trim())items=parseCards((await page(`/search/?do=search&subaction=search&q=${encodeURIComponent(searchForm(query).slice(0,150))}&page=${pageNumber}`,300000,options)).html);
 return query?relevant(query,items):items;
}
export function parseEpisodes(html){const out=[],seen=new Set();for(const m of html.matchAll(/<(?:li|a)[^>]*data-season_id="(\d+)"[^>]*data-episode_id="(\d+)"[^>]*>(.*?)<\/(?:li|a)>/gs)){const key=m[1]+":"+m[2];if(!seen.has(key)){seen.add(key);out.push({season:m[1],episode:m[2],title:text(m[3])});}}return out;}
export function parseFilm(link,h){const info=one(h,/<table class="b-post__info">(.*?)<\/table>/s)||"",f={link,title:text(one(h,/<h1[^>]*>(.*?)<\/h1>/s)),original:text(one(h,/<div class="b-post__origtitle"[^>]*>(.*?)<\/div>/s)),poster:one(h,/<div class="b-sidecover">\s*<a href="([^"]+)"/s)||one(h,/<div class="b-sidecover">.*?<img src="([^"]+)"/s),description:text(one(h,/<div class="b-post__description_text"[^>]*>(.*?)<\/div>/s)),genres:[],voices:[],episodes:[],favs:one(h,/id="ctrl_favs"[^>]*value="([^"]*)"/)};
 for(const m of info.matchAll(/<tr>\s*<td class="l"><h2>(.*?)<\/h2>:?<\/td>\s*<td[^>]*>(.*?)<\/td>\s*<\/tr>/gs)){const key=text(m[1]),v=m[2];if(key.startsWith("Рейтинг")){f.rating=one(v,/imdb">.*?<span class="bold">([\d.]+)<\/span>/s)||one(v,/kp">.*?<span class="bold">([\d.]+)<\/span>/s);}else if(key.startsWith("Дата выхода")||key.startsWith("Год"))f.year=one(v,/(\d{4})/);else if(key.startsWith("Жанр"))f.genres=[...v.matchAll(/itemprop="genre">(.*?)<\/span>/gs)].map(x=>text(x[1]));else if(key.startsWith("В переводе"))f.defaultVoiceName=text(v);else if(key.startsWith("Время"))f.duration=text(v);}
 const init=h.match(/initCDN(Series|Movies)Events\((\d+),\s*(\d+)(?:,\s*(\d+),\s*(\d+),\s*(\d+))?/);f.series=init?init[1]==="Series":link.includes("/series/");f.id=init?.[2]||one(h,/data-id="(\d+)"/);f.defaultVoice=init?.[3];f.defaultFlags=init?[init[4]==="1",init[5]==="1",init[6]==="1"]:[false,false,false];
 const tl=one(h,/id="translators-list"[^>]*>(.*?)<\/ul>/s)||"",seen=new Set();for(const m of tl.matchAll(/<li([^>]*)>(.*?)<\/li>/gs)){const attrs=m[1]+" "+(one(m[2],/^\s*<a([^>]*)>/)||""),id=one(attrs,/data-translator_id="(\d+)"/);if(!id||seen.has(id))continue;seen.add(id);let name=text(one(attrs,/title="([^"]*)"/)||m[2]);const lang=one(m[2],/alt="([^"]+)"/);if(lang&&!name.includes("("))name+=" ("+lang+")";f.voices.push({id,name,premium:attrs.includes("b-prem_translator"),camrip:/data-camrip="1"/.test(attrs),ads:/data-ads="1"/.test(attrs),director:/data-director="1"/.test(attrs)});}
 if(!f.voices.length&&f.defaultVoice)f.voices.push({id:f.defaultVoice,name:f.defaultVoiceName||"Основна доріжка",camrip:f.defaultFlags[0],ads:f.defaultFlags[1],director:f.defaultFlags[2]});f.voices.sort((a,b)=>voiceOrder(a)-voiceOrder(b));for(const v of f.voices)v.lang=voiceLang(v.name);
 const side=h.indexOf('class="b-sidelist"');f.related=side<0?[]:parseCards(h.slice(side).replace(/https?:\/\/[^/"]+\/(films|series|cartoons|animation|documentary)\//g,"https://hdrezka.sh/$1/")).filter(c=>titleKey(c.link)!==titleKey(link)).slice(0,12);
 f.episodes=parseEpisodes(h);return f;
}
/** What only a title's own page has: its player, its voices, its facts table or its title block. */
const TITLE_PAGE=/initCDN(?:Series|Movies)Events|id="translators-list"|class="b-post__(?:info|title)"/;
/**
 * A fetched page read as a title. HDRezka answers a title it does not have with its home page (HTTP 200, an <h1> and a
 * stray data-id), which would read as a film named «Смотреть фильмы в HD онлайн» with no voices: that is «no such
 * title» (404, which the web and TV show as «Упс, такого тут немає»), never a film.
 */
export function readFilm(link,html){
 if(!TITLE_PAGE.test(html))throw Object.assign(Error("Такої назви немає в джерелі"),{status:404});
 const f=parseFilm(link,html);f.imdb=one(html,/imdb\.com\/title\/(tt\d+)/);if(!f.id||!f.title)throw Error("Не вдалося прочитати сторінку фільму");return f;
}
export async function film(link,options={}){
 // HDRezka's own 404 for the address means the same as its home page: no such title.
 const key=titleKey(link),r=await page("/"+key,120000,options).catch(e=>{throw / HTTP 404 /.test(e.message)?Object.assign(Error("Такої назви немає в джерелі"),{status:404,cause:e}):e;});
 const f=readFilm("https://hdrezka.sh/"+key,r.html);f.origin=r.origin;return f;
}
export async function episodes(f,voice){if(!f.voices.some(v=>v.id===voice&&!v.premium))throw Error("Озвучення недоступне");const r=await send(`https://${f.origin}/ajax/get_cdn_series/?t=${Date.now()}`,new URLSearchParams({id:f.id,translator_id:voice,favs:f.favs||"",action:"get_episodes"}).toString()),o=JSON.parse(r.html);if(!o.success)throw Error("Список серій недоступний");return parseEpisodes((o.seasons||"")+(o.episodes||""));}
export function decode(value){if(!value.startsWith("#h"))return value;let s=value.slice(2).replaceAll("//_//","");const combos=n=>n?[...combos(n-1)].flatMap(p=>["@","#","!","^","$"].map(x=>p+x)):[""];for(const n of [2,3])for(const p of combos(n))s=s.split(Buffer.from(p).toString("base64")).join("");try{return Buffer.from(s,"base64").toString("utf8");}catch{return s;}}
export async function stream(f,voice,season,episode){const v=f.voices.find(v=>v.id===voice&&!v.premium);if(!v)throw Error("Озвучення недоступне");if(f.series&&(!/^\d{1,4}$/.test(season)||!/^\d{1,5}$/.test(episode)))throw Error("Виберіть серію");
 const body=new URLSearchParams({id:f.id,translator_id:v.id,favs:f.favs||"",...(f.series?{season,episode,action:"get_stream"}:{is_camrip:v.camrip?"1":"0",is_ads:v.ads?"1":"0",is_director:v.director?"1":"0",action:"get_movie"})});const r=await send(`https://${f.origin}/ajax/get_cdn_series/?t=${Date.now()}`,body.toString(),6000),o=JSON.parse(r.html);if(!o.success)throw Error(text(o.message)||"Відео недоступне");const decoded=decode(o.url||"");const qualities=[];for(const m of decoded.matchAll(/\[([^\]]+)\]([^,\[]+)/g)){const alternatives=[...new Set(m[2].split(" or ").map(x=>mediaUrl(x.trim())).filter(Boolean))].sort((a,b)=>Number(b.includes(".mp4"))-Number(a.includes(".mp4")));const url=alternatives[0];if(url)qualities.push({label:text(m[1]),url,alternatives});}if(!qualities.length)throw Error("Потік не знайдено");qualities.sort((a,b)=>qualityOrder(b.label)-qualityOrder(a.label));return {qualities};
}
