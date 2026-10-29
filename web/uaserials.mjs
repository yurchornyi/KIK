/** Read-only catalogue adapter. Playback stays on the provider until its player is verified. */
import {text,catalogue,film} from './catalogue.mjs';
const HOST='https://uaserials.com',cache=new Map();
export function uaLink(value){const u=new URL(value);if(u.origin!==HOST||u.username||u.password||u.search||u.hash||!/^\/\d+-[a-z0-9-]+\.html$/.test(u.pathname))throw Error('Некоректне посилання UASerials');return u.href;}
async function page(path){const key=HOST+path,hit=cache.get(key);if(hit&&Date.now()-hit.at<300000)return hit.html;const r=await fetch(key,{headers:{'User-Agent':'KIK/1.4 (https://github.com/yurchornyi/KIK)'},redirect:'manual',signal:AbortSignal.timeout(6000)});if(!r.ok)throw Error('UASerials HTTP '+r.status);const reader=r.body.getReader(),parts=[];let size=0;try{for(;;){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>2*1024*1024)throw Error('UASerials response limit');parts.push(value);}}finally{await reader.cancel();}const bytes=new Uint8Array(size);let at=0;for(const p of parts){bytes.set(p,at);at+=p.length;}const html=new TextDecoder().decode(bytes);if(cache.size>=50)cache.delete(cache.keys().next().value);cache.set(key,{at:Date.now(),html});return html;}
export function parseUaCards(html){const out=[],seen=new Set();for(const c of html.split(/<div class="short-item\b/).slice(1)){const link=c.match(/class="short-img[^>]+href="([^"]+)"/)?.[1];try{uaLink(link);}catch{continue;}if(seen.has(link))continue;seen.add(link);const title=text(c.match(/class="th-title truncate"[^>]*>(.*?)<\/div>/s)?.[1]),original=text(c.match(/class="th-title-oname[^>]*>(.*?)<\/div>/s)?.[1]),poster=c.match(/<img[^>]+data-src="([^"]+)"/)?.[1]||c.match(/<img[^>]+src="([^"]+)"/)?.[1];if(title)out.push({link,title,original,poster,info:'Українською · UASerials',source:'uaserials',external:true});}for(const m of html.matchAll(/<a class="uas-card"([^>]*)>(.*?)<\/a>/gs)){const link=m[1].match(/href="([^"]+)"/)?.[1];try{uaLink(link);}catch{continue;}if(seen.has(link))continue;seen.add(link);const c=m[2],title=text(c.match(/class="uas-card__title"[^>]*>(.*?)<\/span>/s)?.[1]),original=text(c.match(/class="uas-card__orig"[^>]*>(.*?)<\/span>/s)?.[1]),poster=c.match(/<img[^>]+data-src="([^"]+)"/)?.[1],year=text(c.match(/class="uas-card__year"[^>]*>(.*?)<\/span>/s)?.[1]);if(title)out.push({link,title,original,poster:poster?new URL(poster,HOST).href:null,info:[year,'Українською','UASerials'].filter(Boolean).join(' · '),source:'uaserials',external:true});}return out.slice(0,60);}
export async function uaCatalogue({category='',query='',page:pageNumber=1}={}){if(!['','films','series','cartoons','animation'].includes(category))throw Error('Некоректна категорія');const n=Math.max(1,Math.min(50,Number(pageNumber)||1)),cat={films:'films',series:'series',cartoons:'fcartoon',animation:'anime'}[category];const path=query?'/index.php?'+new URLSearchParams({do:'search',subaction:'search',story:String(query).slice(0,150)}):'/'+(cat?cat+'/':'')+(n>1?'page/'+n+'/':'');return parseUaCards(await page(path));}
export function parseUaFilm(link,html){link=uaLink(link);let graph=[];for(const m of html.matchAll(/<script type="application\/ld\+json">(.*?)<\/script>/gs))try{const j=JSON.parse(m[1]);graph.push(...(j['@graph']||[j]));}catch{}const d=graph.find(x=>['Movie','TVSeries'].includes(x['@type']));if(!d?.name)throw Error('Не вдалося прочитати UASerials');return {link,id:link.match(/\/(\d+)-/)[1],title:d.name,original:d.alternateName,poster:d.image,description:d.description,genres:d.genre||[],year:d.datePublished,rating:null,imdb:html.match(/imdb\.com\/title\/(tt\d+)/)?.[1],series:d['@type']==='TVSeries',voices:[],episodes:[],source:'uaserials',external:true,externalUrl:link,playbackNotice:'Перегляд відкривається на UASerials. Прогрес зовнішнього плеєра не синхронізується з KIK.'};}
export async function uaFilm(link){link=uaLink(link);return parseUaFilm(link,await page(new URL(link).pathname));}

const plain=v=>String(v||'').replace(/&/g,' and ').normalize('NFKD').toLowerCase().replace(/\p{M}/gu,'').replace(/[^\p{L}\p{N}]+/gu,' ').trim(),sameName=(a,b)=>{const x=plain(b);return !!x&&String(a||'').split(/\s+\/\s+/).some(n=>plain(n)===x);};
const year4=v=>String(v||'').match(/(?:19|20)\d{2}/)?.[0]||'',twins=new Map();
/** The same title on HDRezka, where KIK can play it with seasons, episodes and Ukrainian voices: searched by the original name, same start year, same kind when known. */
export async function rezkaTwin({title,original,year,series}={}){
 const y=year4(year),names=[...new Set([original,title].filter(Boolean).map(s=>String(s).trim()).filter(s=>s.length>1))].slice(0,2);if(!y||!names.length)return null;
 const key=names.join('|')+'|'+y+'|'+series;if(twins.has(key))return twins.get(key);
 let found=null,failed=false;
 for(const query of names){let cards;try{cards=await catalogue({query});}catch{failed=true;continue;}
  // Anime and cartoon series live under /animation/ and /cartoons/: only /films/ or /series/ rules a card out.
  const candidates=cards.slice(0,8).filter(c=>year4(c.info)===y&&(series===undefined||!c.link.includes(series?'/films/':'/series/'))).slice(0,3);
  // A same-year result is not enough ("Friends: The Reunion" vs a Beatles film): the original names must agree.
  for(const c of candidates){if(!original){found=c;break;}try{const page=await film(c.link);if(sameName(page.original,original)||sameName(page.title,original)){found=c;break;}}catch{failed=true;}}
  if(found)break;}
 // A source that failed (paused, busy) has not said «no twin»: the next search asks again.
 if(found||!failed){if(twins.size>=300)twins.delete(twins.keys().next().value);twins.set(key,found);}return found;
}
/** Search results: a title also on HDRezka becomes that playable card under its Ukrainian name, so one show appears once. */
export async function uaSearch(params){
 const items=await uaCatalogue(params);if(!params.query)return items;
 return Promise.all(items.map(async(item,i)=>{if(i>=8)return item;const twin=await rezkaTwin({title:item.title,original:item.original,year:item.info}).catch(()=>null);return twin?{...twin,title:item.title,poster:twin.poster||item.poster,uaLink:item.link}:item;}));
}
/** Detail page: prefer the playable HDRezka twin, keeping the Ukrainian name and description. */
export async function uaFilmPlayable(link,load){
 const f=await uaFilm(link),twin=await rezkaTwin({title:f.title,original:f.original,year:f.year,series:f.series}).catch(()=>null);if(!twin)return f;
 const r=await load(twin.link);return {...r,title:f.title,description:f.description||r.description,uaLink:link};
}
