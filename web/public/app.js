import {createHeaderSearch} from './header-search.js';
import {dropdown} from './dropdown.js';
import {studioBadge,studioById} from './studios.js';
import {RecoveryPlan,cleanVoices,studioIntro,sponsoredVoice,runtimeMs,isPreview,preferDirect} from './playback-recovery.js';
import {adSkip} from './ad-skip.js';
import {introSkip,nextOffer,openingLogo,NEXT_COUNTDOWN} from './episode-marks.js';
import {creditsAt,askRating,asked,markAsked,said,LIKED,DISLIKED} from './rate-prompt.js';
import {engagement,engagementWeight,engagementLabel,byTitle as titleGroups} from './watching.js';
import {facetsOf,tasteModel,tastePages,rankByTaste,similarity,titleId,fade,devotion} from './taste.js';
import {uniqueTitles,uniqueShelves} from './catalogue-identity.js';
import {searchMatches,itemScore} from './search-match.js';
import {AVATARS,avatarUrl} from './avatars.js';
import {preferredQuality} from './preferences.js';
import {createManagement} from './manage.js';
import {storage} from './storage.js';
import {beaconPayload,SYNC_BATCH} from './sync-queue.js';
import {createCatalogueView,catalogueSelection,selectionQuery} from './catalogue-view.js';
import {oops} from './oops.js';
import {createDownloads,formatBytes,defaultDownloadQuality,DOWNLOAD_QUALITIES,watchedCopies,nextToSave} from './downloads.js';
import {loadFlags,currentFlags} from './flags.js';
import {holidayPlan,dressUp,holidayIntro,HOLIDAY_INTRO_MS,fireworksDue,fireworksOver} from './holiday-mark.js';
const $=s=>document.querySelector(s),main=$('#main'),modal=$('#modal'),modalContent=$('#modal-content'),playerModal=$('#player-modal');
// Like an app: no pinch zoom (Safari ignores user-scalable=no, so its gesture events are cancelled too).
for(const t of ['gesturestart','gesturechange'])document.addEventListener(t,e=>e.preventDefault(),{passive:false});
const state={user:null,profiles:[],profile:null,records:new Map(),pending:new Map(),since:0,pool:[],ukrainian:[],films:[],series:[],tab:'home',query:'',syncing:false,clockOffset:0,ready:false};
let pageGeneration=0,modalGeneration=0,playGeneration=0,toastTimer,player=null;
const pageTabs=['home','catalogue','later','history','downloads','settings','admin'];
/** A page re-rendered in place (more data, a sync) stays still: only a page that newly opens plays its entrance. */
/** The footer mark turns only while it is on screen. */
if('IntersectionObserver' in window){const author=document.querySelector('.author');if(author)new IntersectionObserver(([e])=>author.classList.toggle('paused',!e.isIntersecting)).observe(author);}
if(typeof MutationObserver==='function')new MutationObserver(records=>{for(const r of records){const gone=[...r.removedNodes].find(n=>n.nodeType===1);if(!gone)continue;const kind=gone.classList[0],title=gone.querySelector('h1')?.textContent;for(const n of r.addedNodes)if(n.nodeType===1&&n.classList[0]===kind&&n.querySelector('h1')?.textContent===title)n.classList.add('settled');}}).observe(main,{childList:true});
// #films and #series are the old section links: they open the catalogue on that category.
function readRoute(){const [tab,query='']=location.hash.slice(1).split('?'),params=new URLSearchParams(query);if(tab==='films'||tab==='series')params.set('c',tab);return {tab:pageTabs.includes(tab)||tab==='search'?tab:tab==='films'||tab==='series'?'catalogue':'home',query:params.get('q')||'',browse:catalogueSelection(params)};}
Object.assign(state,readRoute());
/** A title's key is asked for on every card, shelf and record compare: parsed once per link. */
const keyCache=new Map();
const keyOf=link=>{let key=keyCache.get(link);if(key===undefined){try{const u=new URL(link);key=(u.hostname==='uaserials.com'?'uaserials/':'')+u.pathname.slice(1);}catch{key='';}if(keyCache.size>3000)keyCache.clear();keyCache.set(link,key);}return key;},recId=r=>r.kind+'\n'+r.key;
const baseTitle=document.title;
const downloads=createDownloads({api,storage,keyOf,owner:()=>state.user?.id||null,onChange:()=>downloadsChanged(),onProgress:x=>paintDownload(x),
 onDone:x=>toast('Завантажено: «'+x.title+'»'+(x.series?' · С'+x.season+' · Е'+x.episode:''))});
const hoverable=matchMedia('(hover:hover)');
function el(tag,attrs={},...children){const node=document.createElement(tag);for(const [k,v] of Object.entries(attrs)){if(k.startsWith('on'))node.addEventListener(k.slice(2).toLowerCase(),v);else if(k==='class')node.className=v;else if(v!==null&&v!==undefined)node.setAttribute(k,String(v));}for(const c of children.flat(Infinity)){if(c!=null)node.append(c instanceof Node?c:document.createTextNode(String(c)));}return node;}
const button=(label,action,classes='pill')=>el('button',{class:classes,type:'button',onClick:e=>{try{Promise.resolve(action(e)).catch(error=>toast(error.message));}catch(error){toast(error.message);}}},label);
const selectField=(label,select,settings)=>el('div',{class:'field'},el('span',{},label),dropdown(select,'',settings));
function profileOption(option){const profile=state.profiles.find(p=>p.id===option.value);return [el('img',{class:'dropdown-avatar',src:avatarUrl(profile?.avatar),alt:'',width:28,height:28}),el('span',{class:'dropdown-option-copy'},option.textContent)];}
/** The toast is a popover: it joins the top layer, so it shows over an open title window or the player too. */
function toast(message){const t=$('#toast');t.textContent=message;try{if(t.popover&&!t.matches(':popover-open'))t.showPopover();}catch{}(typeof requestAnimationFrame==='function'?requestAnimationFrame:setTimeout)(()=>t.classList.add('show'));clearTimeout(toastTimer);toastTimer=setTimeout(()=>{t.classList.remove('show');toastTimer=setTimeout(()=>{try{if(t.matches(':popover-open'))t.hidePopover();}catch{}},260);},3800);}
async function api(path,data,options={}){
 if(path==='/api/sync'&&data)data={...data,source_schema:2};
 const controller=new AbortController(),abort=()=>controller.abort(),timeout=setTimeout(abort,options.timeout||25000);
 options.signal?.addEventListener('abort',abort,{once:true});if(options.signal?.aborted)abort();
 try{const r=await fetch(path,{method:data===undefined?'GET':'POST',credentials:'same-origin',signal:controller.signal,headers:data===undefined?{}:{'Content-Type':'application/json'},body:data===undefined?undefined:JSON.stringify(data)});let body;try{body=await r.json();}catch{throw Object.assign(Error('Сервер тимчасово недоступний'),{status:r.status,server:true});}if(!r.ok)throw Object.assign(Error(body.error||'Не вдалося виконати запит'),{status:r.status,retry:body.retry===true});return body;}
 // Flags for the «Упс» screens (oops.js): a slow answer and a lost connection are retried by themselves.
 catch(e){if(controller.signal.aborted)throw Object.assign(Error('Запит триває надто довго. Спробуйте ще раз.'),{slow:!options.signal?.aborted});if(e instanceof TypeError)throw Object.assign(Error('Немає з’єднання з KIK'),{offline:true});throw e;}
 finally{clearTimeout(timeout);options.signal?.removeEventListener('abort',abort);}
}
function showModal(...content){modalGeneration++;delete modalContent.dataset.link;modal.classList.remove('wide-modal','detail-modal','detail-loading','search-modal','profile-modal');modal.removeAttribute('aria-labelledby');modalContent.replaceChildren(...content);if(!modal.open){modal.showModal();if(document.activeElement===modal.querySelector('.close')){modal.tabIndex=-1;modal.focus({preventScroll:true});}}}
modal.querySelector('.close').onclick=()=>modal.close();playerModal.querySelector('.close').onclick=()=>playerModal.close();
/** Records changed behind a window (a title saved, rated, forgotten; progress): the page follows once the window closes.
 *  A card the keyboard is on stays put; the shelves keep their scroll. */
let pageDirty=false;
function refreshPage(){if(['home','later','history','downloads'].includes(state.tab))render();}
modal.addEventListener('close',()=>{if(!modal.open)modalGeneration++;if(pageDirty&&!main.querySelector(':focus-visible'))refreshPage();});playerModal.addEventListener('close',()=>{if(!playerModal.open){playGeneration++;disposePlayer();if(reloadWhenOnline)location.reload();}});
for(const dialog of [modal,playerModal])dialog.addEventListener('toggle',()=>{document.documentElement.classList.toggle('modal-open',modal.open||playerModal.open);});
for(const dialog of [modal,playerModal])dialog.addEventListener('click',e=>{if(e.target===dialog){const r=dialog.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)dialog.close();}});
const cacheKey=()=>`kik:${state.user?.id}:${state.profile?.id}`;
function store(){if(!state.user||!state.profile)return;if(!storage.setItem(cacheKey(),JSON.stringify({records:[...state.records.values()],pending:[...state.pending.values()],since:state.since})))$('#sync-status').textContent='Сховище браузера недоступне. Не закривайте сторінку до синхронізації.';}
function restore(){state.records=new Map();state.pending=new Map();state.since=0;if(!state.user||!state.profile)return;try{const data=JSON.parse(storage.getItem(cacheKey())||'{}');for(const r of data.records||[])state.records.set(recId(r),r);for(const r of data.pending||[])state.pending.set(recId(r),r);state.since=Number.isSafeInteger(data.since)?data.since:0;}catch{}}
function change(kind,key,data){if(!state.user||!state.profile){auth();return;}const id=kind+'\n'+key,old=state.records.get(id),r={kind,key,data,updated_at:Math.max(Date.now()+state.clockOffset,(old?.updated_at||0)+1)};state.records.set(id,r);state.pending.set(id,r);pageDirty=true;store();sync();}
/** One sync at a time. Changes made meanwhile, or another profile picked meanwhile, go up as soon as it settles; a big offline queue in batches. */
let syncFlight=null;
async function sync(){if(!state.user||!state.profile)return;const key=cacheKey();if(syncFlight){const active=syncFlight;await active.promise;if(cacheKey()===key&&active.key!==key)return sync();return;}
 const flight={key,promise:performSync(state.profile.id,state.user.id)};syncFlight=flight;let more;try{more=await flight.promise;}finally{if(syncFlight===flight){syncFlight=null;state.syncing=false;}}if(more&&cacheKey()===key&&state.pending.size)return sync();}
async function performSync(profile,account){const sent=[...state.pending.values()].slice(0,SYNC_BATCH);state.syncing=true;$('#sync-status').textContent='Синхронізація…';try{const response=await api('/api/sync',{profile_id:profile,records:sent,...(state.since?{since:state.since}:{})});if(state.profile?.id!==profile||state.user?.id!==account)return false;state.clockOffset=response.server_time-Date.now();state.since=response.server_time;let changed=Object.entries(response.profile||{}).some(([key,value])=>state.profile[key]!==value);Object.assign(state.profile,response.profile);refreshAccount();for(const r of response.records){const id=recId(r),local=state.records.get(id);if(!local||r.updated_at>=local.updated_at){if(!local||r.updated_at!==local.updated_at||JSON.stringify(r.data)!==JSON.stringify(local.data))changed=true;state.records.set(id,r);}}for(const r of sent){const id=recId(r);if(state.pending.get(id)?.updated_at===r.updated_at)state.pending.delete(id);}store();smartTidy();$('#sync-status').textContent='Історію та прогрес синхронізовано';if(changed)backgroundRender();return true;}catch(e){if(state.profile?.id===profile&&state.user?.id===account)$('#sync-status').textContent=e.status===401?'Увійдіть знову для синхронізації':'Без мережі • зміни збережено на цьому пристрої';return false;}}
/** Catalogue pages, related titles and syncs often land together: they share one repaint. */
let backgroundTimer=0;function backgroundRender(){clearTimeout(backgroundTimer);backgroundTimer=setTimeout(()=>{if(!['home','later','history'].includes(state.tab)||playerModal.open||modal.open||main.contains(document.activeElement)||scrollY>120)return;render();},150);}

function data(kind){return [...state.records.values()].filter(r=>r.kind===kind&&r.data!==null);}
function historyItems(){const byTitle=new Map();for(const r of data('progress')){const k=keyOf(r.data.link),old=byTitle.get(k);if(!old||r.updated_at>old.updated_at)byTitle.set(k,r);}return [...byTitle.values()].sort((a,b)=>b.updated_at-a.updated_at).map(r=>({...r.data,title:r.data.displayTitle||r.data.title,ts:r.updated_at}));}
/** «Продовжити перегляд»: unfinished titles, and a series whose last episode reached the credits comes back as its next episode. */
function continueItems(){return historyItems().flatMap(x=>!x.dur||x.pos/x.dur<.93?[x]:x.season?[{...x,pos:0,next:true}]:[]);}
function latestFor(link,season,episode){const list=data('progress').filter(r=>keyOf(r.data.link)===keyOf(link)&&(season===undefined||(r.data.season||'0')===(season||'0')&&(r.data.episode||'0')===(episode||'0'))).sort((a,b)=>b.updated_at-a.updated_at);return list[0]?.data;}
function liked(link){return state.records.get('rating\n'+keyOf(link))?.data?.value||0;}
function inList(link){return !!state.records.get('later\n'+keyOf(link))?.data;}
function toggleLater(item){if(!state.user){auth();return;}const was=inList(item.link);change('later',keyOf(item.link),was?null:{link:item.link,title:item.title,poster:item.poster,year:item.year||'',genre:item.genres?.join(', ')||item.info||''});toast(was?'Прибрано з мого списку':'Додано до мого списку');}
function heroIcon(kind){
 const paths={play:'<path d="M7 4 20 12 7 20Z" fill="currentColor" stroke="none"/>',add:'<path d="M5 12h14M12 5v14"/>',check:'<path d="m5 12 5 5L20 7"/>',info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v6"/><circle cx="12" cy="7.5" r="1.2" fill="currentColor" stroke="none"/>'};
 const icon=el('span',{class:'hero-action-icon','aria-hidden':'true'});icon.innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">'+paths[kind]+'</svg>';return icon;
}
/** Match the TV hero: save the featured title without replacing it or losing button focus. */
function heroActions(item){
 const later=button([],()=>{if(!state.user||!state.profile){auth();return;}later.focus({preventScroll:true});toggleLater(item);paintLater();},'pill hero-list');
 function paintLater(){const saved=inList(item.link);later.setAttribute('aria-pressed',String(saved));later.title=saved?'Прибрати з мого списку':'Додати до мого списку';later.replaceChildren(heroIcon(saved?'check':'add'),el('span',{},saved?'У списку':'Мій список'));}
 paintLater();
 const details=button([heroIcon('info')],()=>openDetails(item),'pill hero-details');details.setAttribute('aria-label','Докладніше');details.title='Докладніше';
 return el('div',{class:'actions hero-actions'},button([heroIcon('play'),el('span',{},item.resume?'Продовжити':'Дивитися')],()=>play(item),'pill primary'),later,details);
}
function setRating(item,value){change('rating',keyOf(item.link),value?{link:item.link,title:item.title,poster:item.poster,genre:item.genres?.join(', ')||item.info||'',value}:null);}
/** One tap sets, a second tap on the same thumb clears it: the same as the TV title page. */
function rate(item,value){if(!state.user){auth();return;}const next=liked(item.link)===value?0:value;setRating(item,next);toast(said(next));}
const artwork=new Map(),artWaiting=new Map();let artTimer=0,artBusy=false,artEnabled=true,artSaveTimer=0;
/** Found artwork is kept for a week, so cards and the hero open with their final images instead of swapping them in. */
const ART_KEY='kik-artwork:v1',ART_TTL=7*864e5;
try{const now=Date.now();for(const [key,a] of JSON.parse(storage.getItem(ART_KEY)||'[]'))if(a?.poster&&now-(a.at||0)<ART_TTL)artwork.set(key,a);}catch{}
function saveArtwork(){clearTimeout(artSaveTimer);artSaveTimer=setTimeout(()=>storage.setItem(ART_KEY,JSON.stringify([...artwork].filter(([,a])=>a?.poster).slice(-300))),1500);}
/** TMDB artwork for a catalogue item, batched and cached; resolves to null when unavailable. */
function artworkFor(item,refresh=false){let key;try{key=keyOf(item.link);}catch{return Promise.resolve(null);}if(!artEnabled||!item.title)return Promise.resolve(null);if(refresh)artwork.delete(key);if(artwork.has(key))return Promise.resolve(artwork.get(key));
 let wait=artWaiting.get(key);if(!wait){let done;const promise=new Promise(r=>done=r);wait={item,promise,done};artWaiting.set(key,wait);clearTimeout(artTimer);artTimer=setTimeout(flushArtwork,120);}return wait.promise;}
async function flushArtwork(){if(artBusy||!artWaiting.size)return;artBusy=true;const batch=[...artWaiting.entries()].slice(0,12);
 try{const r=await api('/api/artwork/batch',{items:batch.map(([,w])=>({link:w.item.link,title:w.item.sourceTitle||w.item.title,original:w.item.original||'',year:String(w.item.year||w.item.info||'').match(/\d{4}/)?.[0]||''}))});if(!r.configured)artEnabled=false;for(const [key,w] of batch){const a=r.results?.[key]||null;if(a)a.at=Date.now();artwork.delete(key);artwork.set(key,a);if(artwork.size>400)artwork.delete(artwork.keys().next().value);w.done(a);artWaiting.delete(key);}saveArtwork();}
 catch{for(const [key,w] of batch){w.done(null);artWaiting.delete(key);}}
 finally{artBusy=false;if(artWaiting.size)artTimer=setTimeout(flushArtwork,artEnabled?60:0);if(!artEnabled)for(const [key,w] of artWaiting){w.done(null);artWaiting.delete(key);}}}
const artObserver='IntersectionObserver' in window?new IntersectionObserver(entries=>{for(const e of entries)if(e.isIntersecting){artObserver.unobserve(e.target);e.target._art?.();}},{rootMargin:'400px'}):null;
const sourcePosterQueue=[],sourcePosterJobs=new Map();let sourcePosterBusy=0;
function fullPoster(item){const key=keyOf(item.link);if(sourcePosterJobs.has(key))return sourcePosterJobs.get(key);let done;const promise=new Promise(r=>done=r);if(sourcePosterJobs.size>=120)sourcePosterJobs.delete(sourcePosterJobs.keys().next().value);sourcePosterJobs.set(key,promise);sourcePosterQueue.push({item,done});pumpPosters();return promise;}
function pumpPosters(){while(sourcePosterBusy<2&&sourcePosterQueue.length){const {item,done}=sourcePosterQueue.shift();sourcePosterBusy++;getFilm(item).then(f=>{if(f.poster){sourcePosters.set(keyOf(item.link),f.poster);if(sourcePosters.size>200)sourcePosters.delete(sourcePosters.keys().next().value);}done(f.poster||null);}).catch(()=>done(null)).finally(()=>{sourcePosterBusy--;pumpPosters();});}}
/** Resolved full-size source posters, so a repeated render paints them at once. */
const sourcePosters=new Map();
function posterImage(a,url,lazy=false){const img=new Image();img.referrerPolicy='no-referrer';img.alt='';img.dataset.poster=url;if(lazy)img.loading='lazy';if(a?.poster){img.srcset=`${a.poster} 500w, ${a.posterLarge} 780w`+(a.posterOriginal&&a.posterWidth>780?`, ${a.posterOriginal} ${a.posterWidth}w`:'');img.sizes='(max-width:700px) 40vw, 220px';}img.src=url;return img;}
function paintStudioBadge(image,ids){image.querySelector('.studio-badge')?.remove();const badge=studioBadge(ids);if(badge)image.append(badge);}
/** The better poster fades in over the current one once decoded, so the card never flashes empty. */
function upgradePoster(image,item){if(!artObserver||!item.link)return;image._art=async()=>{image._art=null;const a=await artworkFor(item);if(!image.isConnected)return;paintStudioBadge(image,item.studios?.length?item.studios:a?.studios);const url=a?.poster||await fullPoster(item);if(!url||!image.isConnected||!a&&url===item.poster)return;const img=posterImage(a,url);img.className='art-in';img.onload=async()=>{try{await img.decode();}catch{}if(!image.isConnected)return;const old=[...image.querySelectorAll('img')],drop=()=>{for(const o of old)o.remove();};image.prepend(img);img.addEventListener('animationend',drop,{once:true});setTimeout(drop,700);};};artObserver.observe(image);}
const heroRefined=new Map();
/** The wide picture of a slide. w1280 is plenty behind the hero gradients and arrives fast; the original joins only when it is small. */
function backdropImage(a){const img=new Image();img.alt='';img.referrerPolicy='no-referrer';img.className='hero-art wide';img.dataset.src=a.backdrop;img.srcset=`${a.backdrop} 1280w`+(a.backdropOriginal&&a.backdropWidth>1280&&a.backdropWidth<=2200?`, ${a.backdropOriginal} ${a.backdropWidth}w`:'');img.sizes='100vw';return img;}
/** Paints the slide's wide picture into the hero: the first one at once, every later one fading in over the previous one
 *  (a crossfade). A title without a wide picture gets its poster, blurred across the hero, so the hero is never empty. */
function upgradeHero(hero,item){
 const key=keyOf(item.link);hero.dataset.art=key;
 const live=()=>hero.dataset.art===key&&hero.isConnected,painted=()=>[...hero.querySelectorAll('.hero-art')].some(i=>i.dataset.key===key);
 function place(img,fade){img.dataset.key=key;const olds=[...hero.querySelectorAll('.hero-art')].filter(i=>i!==img);if(fade)img.classList.add('fade-in');hero.insertBefore(img,hero.querySelector('.hero-copy'));const drop=()=>{for(const o of olds)o.remove();};if(fade&&olds.length)setTimeout(drop,700);else drop();}
 function paint(a){if(!a?.backdrop||hero.dataset.art!==key)return;
  // The same picture already on screen (the page is being re-rendered) moves over as is.
  const shown=[...document.querySelectorAll('.hero-art')].find(i=>i.dataset.src===a.backdrop);if(shown){if(shown.parentNode!==hero)place(shown,false);return;}
  const img=backdropImage(a);
  if(!hero.isConnected){img.fetchPriority='high';img.src=a.backdrop;place(img,false);return;}
  img.onload=async()=>{try{await img.decode();}catch{}if(live())place(img,true);};img.src=a.backdrop;
 }
 function fill(a){if(!live()||painted())return;const url=a?.posterLarge||a?.poster||item.poster;if(!url)return;
  const shown=[...document.querySelectorAll('.hero-art')].find(i=>i.dataset.src===url);if(shown){if(shown.parentNode!==hero)place(shown,false);return;}
  const img=new Image();img.alt='';img.referrerPolicy='no-referrer';img.className='hero-art wide poster-fill';img.dataset.src=url;img.onload=()=>{if(live()&&!painted())place(img,true);};img.src=url;}
 const known=artwork.get(key);if(known?.backdrop){paint(known);return;}
 artworkFor(item).then(async a=>{if(a?.backdrop){paint(a);return;}if(hero.dataset.art!==key)return;
  if(artEnabled){if(!heroRefined.has(key)){if(heroRefined.size>=80)heroRefined.delete(heroRefined.keys().next().value);heroRefined.set(key,getFilm(item).catch(()=>null));}const film=await heroRefined.get(key);if(hero.dataset.art!==key)return;
   if(film?.original){const b=await artworkFor({...item,original:film.original,year:film.year||item.year},true);if(b?.backdrop){paint(b);return;}a=b||a;}}
  fill(a);});
 // Prioritize an actual landscape image; vertical posters belong in the cards.
 clearTimeout(artTimer);flushArtwork();
}
/** Poster pictures already on screen, by title, while a page repaints: the new cards take them over, so nothing blinks. */
const livePosters=new Map();
function keepPosters(){livePosters.clear();for(const n of main.querySelectorAll('.poster[data-key] .poster-image img:not(.art-in)'))if(n.complete&&n.naturalWidth)livePosters.set(n.closest('.poster').dataset.key,n);}
function card(item,progress=false,onClick){const image=el('div',{class:'poster-image'}),key=keyOf(item.link),a=artwork.get(key),known=a?.poster||sourcePosters.get(key),live=livePosters.get(key);paintStudioBadge(image,item.studios?.length?item.studios:a?.studios);if(downloads.supported&&downloads.forTitle(item.link).some(x=>x.status==='done'))image.append(el('span',{class:'dl-badge',title:'Завантажено на цей пристрій'},'⤓'));if(live&&live.dataset.poster===(known||item.poster)){livePosters.delete(key);image.append(live);if(!known)upgradePoster(image,item);}else if(known)image.append(posterImage(a,known,true));else{upgradePoster(image,item);if(item.poster)image.append(el('img',{src:item.poster,alt:'',loading:'lazy',decoding:'async',referrerpolicy:'no-referrer','data-poster':item.poster}));}if(progress&&item.dur>0){const i=el('i');i.style.width=Math.max(0,Math.min(100,item.pos/item.dur*100))+'%';image.append(el('div',{class:'progress'},i),el('span',{class:'continue-label'},'▶ ',item.next?'Наступна серія':item.season?`С${item.season} · Е${item.episode}`:`${Math.floor(item.pos/60000)} хв`));}let dwell=0;const warm=()=>{if(item.link&&!item.external)getFilm(item).catch(()=>{});};
 // Details start loading on hover, focus or touch, so the window opens complete instead of growing from «Завантаження…».
 return el('button',{class:'poster',type:'button','aria-label':item.title,'data-key':key,onClick:onClick||(()=>openDetails(item)),onPointerenter:e=>{if(e.pointerType==='mouse'){clearTimeout(dwell);dwell=setTimeout(warm,150);}},onPointerleave:()=>clearTimeout(dwell),onPointerdown:warm,onFocus:()=>{clearTimeout(dwell);dwell=setTimeout(warm,250);},onBlur:()=>clearTimeout(dwell)},image,el('span',{class:'poster-title'},item.title),el('span',{class:'poster-info'},item.info||item.year||(progress?'Продовжити перегляд':'Фільми та серіали')));}
/** A row of cards; `rank` puts a big number before each card (the week's Top 10, as on the TV and on Netflix). */
function shelf(title,items,subtitle='',progress=false,rank=false){if(!items.length)return null;return el('section',{class:'shelf'+(rank?' top-ten':'')},el('div',{class:'shelf-head'},el('h2',{},title),el('span',{class:'subtle'},subtitle)),el('div',{class:'shelf-line'},items.slice(0,24).map((i,n)=>rank?el('div',{class:'ranked'},el('span',{class:'rank','aria-hidden':'true'},String(n+1)),card(i,progress)):card(i,progress))));}
function tokens(s){return (s||'').toLowerCase().match(/[\p{L}\p{N}]{3,}/gu)||[];}
const COUNTRIES=[['Россия','Росія'],['СССР','СРСР'],['Беларусь','Білорусь'],['Казахстан','Казахстан'],['Узбекистан','Узбекистан'],['Индия','Індія'],['Турция','Туреччина'],['Китай','Китай'],['Южная Корея','Південна Корея'],['Япония','Японія']],COUNTRY_DEFAULT=['Россия','СССР','Беларусь','Казахстан'];
/** Countries hidden from catalogue, search and recommendations on this device. Mirrors CountryFilter.java. */
function hiddenCountries(){try{const v=JSON.parse(storage.getItem('kik-hidden-countries'));if(Array.isArray(v))return v;}catch{}return COUNTRY_DEFAULT;}
function allowed(items){const hidden=new Set(hiddenCountries());if(hidden.has('Беларусь'))hidden.add('Белоруссия');if(hidden.has('Россия'))hidden.add('Российская Федерация');return uniqueTitles(items||[]).filter(i=>!i.external&&!(i.sourceInfo||i.info||'').split(',').some(p=>hidden.has(p.trim())));}
function applyCatalogue(){const [all=[],films=[],series=[]]=state.catalogue||[];state.films=allowed(films);state.series=allowed(series);state.ukrainian=allowed(state.ukrainianRaw||[]);const unique=new Map();for(const item of allowed([...all,...films,...series,...(state.ukrainianRaw||[])]))unique.set(keyOf(item.link),item);state.pool=[...unique.values()];headerSearch.refresh();}
function countriesDialog(){const hidden=new Set(hiddenCountries()),list=el('div',{class:'country-list'},COUNTRIES.map(([id,label])=>{const box=el('input',{type:'checkbox'});box.checked=hidden.has(id);box.onchange=()=>{box.checked?hidden.add(id):hidden.delete(id);storage.setItem('kik-hidden-countries',JSON.stringify([...hidden]));applyCatalogue();render();};return el('label',{class:'country'},box,' '+label);}));showModal(el('h2',{},'Приховати фільми з країн'),list,el('p',{class:'muted'},'Позначені країни не показуються в каталозі, пошуку й рекомендаціях. Історія та «Мій список» не змінюються.'));}
/**
 * What the home rows know beyond the popular pages, about the titles a taste rests on most (its seeds): what viewers of a
 * seed also watch (`picks`: TMDB's, from /api/similar), the parts of its franchise and its genres. The source's own «see
 * also» is not read here: it names titles by a shared word («Перрі Мейсон» for a film about Меттью Перрі), not by taste.
 * Public catalogue facts, the same for every profile: kept in this browser for three days, 24 titles at most.
 */
const SEEDS_KEY='kik-taste-seeds:v1',SEED_TTL=3*864e5,seedFacts=new Map(),seedLoading=new Set(),genrePages=new Map(),pageLoading=new Set();
try{const now=Date.now();for(const [key,facts] of JSON.parse(storage.getItem(SEEDS_KEY)||'[]'))if(facts?.keep&&now-facts.at<SEED_TTL&&Array.isArray(facts.picks)&&Array.isArray(facts.parts))seedFacts.set(key,facts);}catch{}
function saveSeeds(){storage.setItem(SEEDS_KEY,JSON.stringify([...seedFacts].filter(([,f])=>f.keep).sort((a,b)=>b[1].at-a[1].at).slice(0,24)));}
const slim=c=>({link:c.link,title:c.title,poster:c.poster,info:c.info,sourceInfo:c.sourceInfo,sourceTitle:c.sourceTitle});
/** Of a franchise only the title's own part and the six after it are kept: the home offers what comes next. */
function laterParts(parts){const own=parts.find(p=>p.current);return own?parts.filter(p=>p.current||p.num>own.num).sort((a,b)=>a.num-b.num).slice(0,7).map(p=>({...slim(p),num:p.num,current:!!p.current})):[];}
function infoOf(link){const k=keyOf(link);return state.pool.find(i=>keyOf(i.link)===k)?.info||'';}
/**
 * The profile's taste for the ranker (taste.js): ratings, real watching and «Мій список», one signal per title. Watching
 * keeps no genres, so a title's line comes from its own page (a seed), from its rating or its save, or from the catalogue.
 */
function tasteSignals(){const out=[],seen=new Set(),noted=new Map();
 for(const r of [...data('rating'),...data('later')])if(r.data.genre&&!noted.has(r.key))noted.set(r.key,r.data.genre);
 const signal=(d,key,w,at)=>({link:d.link,title:String(d.displayTitle||d.title||'').split(/\s+\/\s+/)[0],info:seedFacts.get(key)?.info||noted.get(key)||infoOf(d.link),w,at});
 for(const r of data('rating')){seen.add(r.key);if(r.data.value>=-1)out.push({...signal(r.data,r.key,r.data.value>0?LIKED:DISLIKED),rated:true});}
 // Real watching per title (watching.js): to the end is a mild like, given up early a mild dislike; hours with a title add to a like.
 const watched=[...titleGroups(data('progress'),keyOf)].map(([key,list])=>({key,d:list[0].data,e:engagement(list)})).filter(x=>x.e&&engagementWeight(x.e)&&!seen.has(x.key)).sort((a,b)=>b.e.updated_at-a.e.updated_at).slice(0,30);
 for(const {key,d,e} of watched){const w=engagementWeight(e);seen.add(key);out.push(signal(d,key,w>0?w*devotion(e.watched):w,e.updated_at));}
 for(const r of data('later').sort((a,b)=>b.updated_at-a.updated_at).slice(0,20))if(!seen.has(r.key)){seen.add(r.key);out.push(signal(r.data,r.key,2,r.updated_at));}
 return out;}
const strength=s=>s.w*(s.at?fade(s.at):1);
/** The titles whose facts are worth a request: the six the taste rests on most, and the two latest dislikes (what is like them goes down). */
function tasteSeeds(signals){const own=signals.filter(s=>!keyOf(s.link).startsWith('uaserials/'));return [...own.filter(s=>s.w>0).sort((a,b)=>strength(b)-strength(a)).slice(0,6),...own.filter(s=>s.w<=DISLIKED).slice(0,2)];}
/** The catalogue pages a taste asks for: what is popular in the genres it watches most (taste.js tastePages), and what is new in the two it watches most. */
function wantedPages(model){const pages=tastePages(model);return [...pages.map(page=>page+'/popular'),...pages.slice(0,2).map(page=>page+'/last')];}
/** True once everything the taste asks for has arrived (or failed): the AI order is asked for only then, once, with all the candidates. */
function tasteSettled(signals){return tasteSeeds(signals).every(s=>seedFacts.has(keyOf(s.link)))&&wantedPages(tasteModel(signals)).every(page=>genrePages.has(page));}
/** The source was busy with what viewers opened (a 503 for a request fetched ahead): that piece is asked again in 20 s,
 *  twice at most, instead of counting as empty. */
const warmRetries=new Map();let warmTimer=0;
function retryWarm(e,what){if(e?.status!==503)return false;const n=(warmRetries.get(what)||0)+1;if(n>2)return false;warmRetries.set(what,n);clearTimeout(warmTimer);warmTimer=setTimeout(()=>warmTaste(),20000);return true;}
/** Loads what the taste asks for: the facts of its seeds and its catalogue pages. The home rows repaint when they arrive. */
async function warmTaste(){if(!state.user||!state.profile)return;const signals=tasteSignals(),jobs=[];
 for(const s of tasteSeeds(signals)){const key=keyOf(s.link);if(seedFacts.has(key)||seedLoading.has(key))continue;seedLoading.add(key);
  // warm=1: fetched ahead for the home rows, behind anything the viewer opens (server.mjs); a busy source is asked again later.
  jobs.push(api('/api/similar?link='+encodeURIComponent(s.link)+'&warm=1')
   // An answer without TMDB's part (it failed) or of the old shape (the browser's own cache) serves this visit and is asked again on the next.
   .then(r=>seedFacts.set(key,{at:Date.now(),keep:Array.isArray(r.parts)&&!r.partial,info:(r.genres||[]).join(', '),picks:(r.items||[]).slice(0,r.picks||0).map(slim),parts:laterParts(r.parts||[])}),e=>{if(!retryWarm(e,'seed:'+key))seedFacts.set(key,{at:Date.now(),keep:false,info:'',picks:[],parts:[]});}).finally(()=>seedLoading.delete(key)));}
 for(const page of wantedPages(tasteModel(signals))){if(genrePages.has(page)||pageLoading.has(page))continue;pageLoading.add(page);const [category,genre,filter]=page.split('/');
  jobs.push(api('/api/catalogue?category='+category+'&genre='+genre+'&filter='+filter+'&warm=1').then(r=>genrePages.set(page,r.items||[]),e=>{if(!retryWarm(e,'page:'+page))genrePages.set(page,[]);}).finally(()=>pageLoading.delete(page)));}
 if(!jobs.length)return;await Promise.all(jobs);saveSeeds();recsMemo=null;if(state.tab==='home')backgroundRender();recommendations();}
/**
 * The regular ranking of everything KIK can offer this profile now (taste.js): the popular pages, the taste's genre pages
 * (popular and new; a title found on one has that genre too, which its catalogue card does not say) and what the seeds
 * name. One title is one candidate under any address the source gives it. Kept for the current turn of the page: one
 * render asks several times.
 */
let recsMemo=null;
function tasteRanking(){if(recsMemo)return recsMemo;
 const signals=tasteSignals(),model=tasteModel(signals),excluded=new Set(),pool=new Map(),ids=new Map(),boost=new Map(),sequels=new Map();
 const keyIn=link=>{const k=keyOf(link),id=titleId(k);return id&&ids.get(id)||k;},add=(item,also)=>{const k=keyIn(item.link),id=titleId(k),had=pool.get(k);if(id&&!ids.has(id))ids.set(id,k);if(!had)pool.set(k,also?{...item,also:[also]}:item);else if(also&&!had.also?.includes(also))pool.set(k,{...had,also:[...(had.also||[]),also]});return k;};
 for(const [k,list] of titleGroups(data('progress'),keyOf))if(engagementWeight(engagement(list)))excluded.add(k);
 for(const r of [...data('rating'),...data('later')])excluded.add(r.key);
 const gone=new Set([...excluded].map(k=>titleId(k)||k));
 // A title new in a favourite genre gets a small lift over the long-popular ones around it.
 state.pool.forEach(item=>add(item));for(const page of wantedPages(model))for(const item of allowed(genrePages.get(page)||[])){const k=add(item,page.split('/')[1]);if(page.endsWith('/last'))boost.set(k,(boost.get(k)||0)+1);}
 for(const s of signals){const facts=seedFacts.get(keyOf(s.link));if(!facts)continue;
  // What viewers of this title also watch goes up for a liked title and down for a disliked one.
  for(const item of allowed(facts.picks)){const k=s.w>0?add(item):keyIn(item.link);boost.set(k,(boost.get(k)||0)+strength(s));}
  // The next part of a franchise the profile watched or liked goes first, the one after it follows.
  const own=s.w>=3&&facts.parts.find(p=>p.current);if(own)allowed(facts.parts.filter(p=>p.num>own.num&&!gone.has(titleId(keyOf(p.link))||keyOf(p.link)))).slice(0,2).forEach((p,i)=>{const k=add(p);sequels.set(k,Math.max(sequels.get(k)||0,i?.35:1));});}
 recsMemo={signals,ranked:rankByTaste([...pool.values()],model,{keyOf:i=>keyIn(i.link),excluded,boost,sequels,limit:72})};setTimeout(()=>{recsMemo=null;},0);return recsMemo;}
let enhanced={profile:'',signature:'',keys:[],mode:'standard',busy:false};
function resetEnhanced(){enhanced={profile:'',signature:'',keys:[],mode:'standard',busy:false};recsMemo=null;}
/**
 * «Для вас»: the regular ranking; for a profile with enhanced recommendations, the AI's order of it once that has arrived,
 * the next parts of the profile's franchises still first. The AI is asked once the profile's records are in (the first
 * sync on a new device) and the taste has all its candidates: an earlier ask would rank a pool the next minute replaces.
 */
function recommendations(){const {signals,ranked}=tasteRanking(),base=uniqueTitles(ranked.map(x=>x.item));if(!state.user||!state.profile?.enhanced_recs)return base.slice(0,20);const owner=state.user.id+':'+state.profile.id,signature=owner+'|'+JSON.stringify([...data('rating'),...data('later'),...data('progress')].map(r=>[r.kind,r.key,r.updated_at]))+'|'+base.map(i=>keyOf(i.link)).join(',');if(!enhanced.busy&&enhanced.signature!==signature&&base.length>=3&&state.since&&tasteSettled(signals)){enhanced.busy=true;enhanced.signature=signature;enhanced.profile=owner;api('/api/recommendations',{profile_id:state.profile.id,items:base.slice(0,72).map(i=>({link:i.link,title:i.title,original:i.original,info:i.info,sourceInfo:i.sourceInfo,year:i.year,description:i.description}))}).then(r=>{if(state.user?.id+':'+state.profile?.id!==owner)return;enhanced.keys=r.keys||[];enhanced.mode=r.mode==='ai'&&(r.keys||[]).filter(k=>base.some(i=>keyOf(i.link)===k)).length>=3?'ai':'standard';if(state.tab==='home')backgroundRender();}).catch(()=>{}).finally(()=>{if(enhanced.profile===owner)enhanced.busy=false;});}if(enhanced.profile!==owner||enhanced.mode!=='ai')return base.slice(0,20);const byKey=new Map(base.map(i=>[keyOf(i.link),i])),ordered=[],take=key=>{const item=byKey.get(key);if(item){ordered.push(item);byKey.delete(key);}};for(const x of ranked)if(x.next>=1)take(x.key);for(const key of enhanced.keys)take(key);return [...ordered,...byKey.values()].slice(0,20);}
/**
 * «Тому що ви дивилися …»: up to two rows, each around one title the taste rests on most: what its viewers also watch
 * and what the catalogue has like it, of one kind per row and without the titles of «Для вас».
 */
function becauseRows(taken){const {signals,ranked}=tasteRanking(),rows=[],used=new Set(taken.map(i=>keyOf(i.link))),kinds=new Set();
 for(const s of tasteSeeds(signals)){if(s.w<3||!s.title)continue;const facts=seedFacts.get(keyOf(s.link));if(!facts)continue;const f=facetsOf(s),kind=f.section+'/'+f.slug;if(kinds.has(kind))continue;
  const named=new Set(facts.picks.map(x=>keyOf(x.link))),items=ranked.filter(x=>!used.has(x.key)).map(x=>({x,v:(named.has(x.key)?1:0)+similarity(x.f,f)})).filter(y=>y.v>=.4).sort((a,b)=>b.v-a.v).slice(0,12).map(y=>y.x.item);
  if(items.length<4)continue;kinds.add(kind);for(const i of items)used.add(keyOf(i.link));rows.push({title:(s.rated?'Тому що вам сподобалося «':'Тому що ви дивилися «')+s.title+'»',items});if(rows.length===2)break;}
 return rows;}

/** The home hero is a slideshow, as on the TV: the titles still to finish first, then recommendations, up to 10 slides
 *  turning every 9 s. It pauses under the pointer, while a hero button has the keyboard focus, and while the page is hidden. */
const SLIDE_MS=9000,slideshow={keys:'',slides:[],index:0,shownAt:0,timer:0,hero:null};
/** Once three or more picks have a wide picture only those stay, so the hero is always filled; the pictures of every
 *  pick are looked up, and the slides are arranged again on screen when they arrive. */
function heroSlides(){
 const recent=continueItems(),picks=[],seen=new Set();
 const add=(item,resume)=>{const key=keyOf(item.link);if(!key||seen.has(key)||picks.length>=16||liked(item.link)<0||!allowed([item]).length)return;seen.add(key);picks.push({...item,resume});};
 for(const x of recent.slice(0,3))add(x,true);for(const x of recommendations())add(x,false);
 if(!picks.length){const x=state.pool.find(i=>liked(i.link)>=0);if(x)add(x,false);}
 // The slides are arranged again only when a picture arrived: with artwork off or failing nothing changed, and asking again would never end.
 const waiting=picks.filter(p=>!artwork.has(keyOf(p.link))).map(p=>artworkFor(p));if(waiting.length)Promise.all(waiting).then(found=>{if(found.some(Boolean))refreshSlides();});
 const wide=picks.filter(p=>artwork.get(keyOf(p.link))?.backdrop);return (wide.length>=3?wide:picks).slice(0,10);
}
/** The same picks keep the slide on screen and its timer while the page re-renders; a changed set keeps the current title when it is still among them. */
function arrangeSlides(){
 const picks=heroSlides(),profile=state.profile?.id||'',was=slideshow.keys.split('|')[0],now=slideshow.slides[slideshow.index];
 // Another profile starts its own show from the first slide; the account arriving after a guest first paint is not another profile.
 const sameShow=!!now&&(!was||was===profile);let at=sameShow?picks.findIndex(p=>keyOf(p.link)===keyOf(now.link)):-1;
 // The title on screen keeps its turn even when it has just left the picks (saved to the list, for one): it never slips away under a tap.
 if(sameShow&&at<0&&liked(now.link)>=0&&allowed([now]).length){at=Math.min(slideshow.index,picks.length,9);picks.splice(at,0,now);if(picks.length>10)picks.length=10;}
 const keys=profile+'|'+picks.map(p=>keyOf(p.link)).join(',');
 if(keys===slideshow.keys&&slideshow.slides.length)return;
 slideshow.keys=keys;slideshow.slides=picks;slideshow.index=Math.max(0,at);if(at<0)slideshow.shownAt=Date.now();
}
/** The picks changed (wide pictures arrived, a title was saved or finished): the hero on screen follows without a page render. */
function refreshSlides(){const hero=slideshow.hero;if(!hero?.isConnected||state.tab!=='home')return;const before=slideshow.slides[slideshow.index];arrangeSlides();const now=slideshow.slides[slideshow.index];if(!now)return;if(keyOf(now.link)!==keyOf(before?.link||''))showSlide(slideshow.index);else{paintDots(hero);scheduleSlide();}}
/** Today's holiday (holiday-mark.js holidayPlan) once the owner's switches allow it, and the film shelf before it (holidayDress). */
let festive=null,holidayFilms=null,fireworksYear=0;
function heroCopy(item){
 const resume=item.resume&&item.dur>0,where=item.season?`С${item.season} · Е${item.episode}`:`${Math.floor((item.pos||0)/60000)} хв`;
 return el('div',{class:'hero-copy'},festive?el('div',{class:'eyebrow holiday-greeting'},festive.greeting):el('div',{class:'eyebrow'},'KIK · ЗНАЙДІТЬ СВОЮ НАСТУПНУ ІСТОРІЮ'),el('h1',{},item.title),
  el('div',{class:'meta'},el('span',{class:'match'},item.resume?'Продовжити перегляд':state.user?'Підібрано для вас':'Варто подивитися'),el('span',{},item.info?.split(',').slice(0,2).join(' · ')||item.genre||item.year||'Фільми та серіали'),el('span',{class:'tag'},'HD')),
  resume?el('div',{class:'hero-resume'},el('span',{class:'hero-bar'},el('i',{style:`width:${Math.max(3,Math.min(100,item.pos/item.dur*100))}%`})),el('span',{},item.next?'Далі: наступна серія':`Ви зупинилися на ${where}`)):null,
  item.resume?null:el('p',{},state.user?`Нові історії, які захочеться прожити. Рекомендації для профілю «${state.profile?.name||''}» враховують ваш вибір і перегляди.`:'Кіно для кожного настрою. Увійдіть, щоб зберігати своє та продовжувати перегляд на телефоні, комп’ютері або TV.'),
  heroActions(item),el('div',{class:'hero-dots',role:'group','aria-label':'Слайди'}));
}
/** Small pills under the buttons: the slide on screen is a wider track that fills in white over the 9 s it stays, like Netflix. */
function paintDots(hero){const dots=hero.querySelector('.hero-dots');if(!dots)return;const n=slideshow.slides.length;dots.replaceChildren();dots.hidden=n<2;if(n<2)return;
 slideshow.slides.forEach((s,k)=>{const on=k===slideshow.index,fill=el('i');if(on){fill.style.animationDuration=SLIDE_MS+'ms';fill.style.animationDelay=-(Date.now()-slideshow.shownAt)+'ms';}
  const d=button(el('span',{class:'hero-dot-track'},fill),()=>{if(k===slideshow.index)return;showSlide(k);hero.querySelector('.hero-dot.on')?.focus({preventScroll:true});},'hero-dot'+(on?' on':''));d.setAttribute('aria-label',`${k+1} з ${n}: ${s.title}`);if(on)d.setAttribute('aria-current','true');dots.append(d);});}
function showSlide(n){const hero=slideshow.hero,item=slideshow.slides[n];if(!hero?.isConnected||!item)return;slideshow.index=n;slideshow.shownAt=Date.now();
 const copy=heroCopy(item),old=hero.querySelector('.hero-copy');if(old)old.replaceWith(copy);else hero.append(copy);hero.classList.remove('settled');
 upgradeHero(hero,item);paintDots(hero);scheduleSlide();prefetchSlide((n+1)%slideshow.slides.length);}
/** The next slide's picture is fetched while the current one is on screen, so the crossfade never waits. */
function prefetchSlide(n){const s=slideshow.slides[n];if(!s||n===slideshow.index)return;artworkFor(s).then(a=>{if(a?.backdrop&&![...document.querySelectorAll('.hero-art')].some(i=>i.dataset.src===a.backdrop)){const img=backdropImage(a);img.src=a.backdrop;}});}
function scheduleSlide(){clearTimeout(slideshow.timer);if(slideshow.slides.length<2||!slideshow.hero?.isConnected)return;slideshow.timer=setTimeout(nextSlide,Math.max(300,SLIDE_MS-(Date.now()-slideshow.shownAt)));}
function nextSlide(){const hero=slideshow.hero;if(!hero?.isConnected||slideshow.slides.length<2)return;
 // A finger leaves a sticky :hover behind on phones, so only a real pointer holds the slide.
 if(document.hidden||modal.open||playerModal.open||hoverable.matches&&hero.matches?.(':hover')||hero.querySelector(':focus-visible')){slideshow.shownAt=Date.now();paintDots(hero);scheduleSlide();return;}
 showSlide((slideshow.index+1)%slideshow.slides.length);}
function pauseSlides(){clearTimeout(slideshow.timer);}
/** By hand, without extra buttons: a swipe across the hero on a phone or tablet, ← → on a computer (as Disney+ and Prime Video). */
function turnSlide(by){const n=slideshow.slides.length;if(n<2||!slideshow.hero?.isConnected)return false;showSlide(((slideshow.index+by)%n+n)%n);return true;}
function swipeSlides(hero){let x=0,y=0,on=false;
 hero.addEventListener('touchstart',e=>{const t=e.touches[0];on=e.touches.length===1&&!e.target.closest('.hero-dots');x=t.clientX;y=t.clientY;},{passive:true});
 hero.addEventListener('touchend',e=>{if(!on)return;on=false;const t=e.changedTouches[0],dx=t.clientX-x,dy=t.clientY-y;if(Math.abs(dx)>50&&Math.abs(dx)>Math.abs(dy)*1.5)turnSlide(dx<0?1:-1);},{passive:true});}
document.addEventListener('keydown',e=>{
 if(e.key!=='ArrowLeft'&&e.key!=='ArrowRight'||e.metaKey||e.ctrlKey||e.altKey||e.shiftKey||state.tab!=='home'||modal.open||playerModal.open)return;
 const a=document.activeElement;if(/^(SELECT|INPUT|TEXTAREA)$/.test(a?.tagName)||a?.isContentEditable||a?.closest?.('.row,.rail,.shelf,[role=listbox]'))return;
 const r=slideshow.hero?.getBoundingClientRect();if(!r||r.bottom<innerHeight*.35)return;
 if(turnSlide(e.key==='ArrowRight'?1:-1))e.preventDefault();});
function resumeSlides(){const hero=slideshow.hero;if(!hero?.isConnected)return;slideshow.shownAt=Date.now();paintDots(hero);scheduleSlide();}
document.addEventListener('visibilitychange',()=>{if(document.hidden)pauseSlides();else resumeSlides();});
/** The section strip scrolls on phones: a fade on the side that has more. */
const navStrip=document.querySelector('.header nav');
function paintNavEdges(){navStrip.classList.toggle('more-left',navStrip.scrollLeft>4);navStrip.classList.toggle('more-right',navStrip.scrollLeft+navStrip.clientWidth<navStrip.scrollWidth-4);}
navStrip.addEventListener('scroll',paintNavEdges,{passive:true});window.addEventListener('resize',paintNavEdges);
/** Signed out, the site is only a sign-in page: no catalogue, search or player until the person logs in. */
/** An account left without a profile (the TV asks for its name after creating the account) needs one before anything else. */
function profileGate(){if(main.querySelector('.profile-gate'))return;main.replaceChildren(el('section',{class:'gate profile-gate'},el('div',{class:'gate-copy'},el('h1',{},'Створіть профіль, щоб дивитися'),el('div',{class:'actions'},button('Створити профіль',()=>newProfile(),'pill red'),button('Вийти',()=>logout(),'pill')))));}
function gate(){if(main.querySelector('.gate:not(.profile-gate)'))return;main.replaceChildren(el('section',{class:'gate'},el('div',{class:'gate-copy'},el('div',{class:'eyebrow'},'ОДИН ПРОФІЛЬ. УСІ ВАШІ ПРИСТРОЇ.'),el('h1',{},'Увійдіть, щоб дивитися'),el('div',{class:'actions'},button('Увійти',()=>auth(),'pill red'),button('Створити обліковий запис',()=>auth(true),'pill')))));}
function render(){pageDirty=false;if(['home','catalogue','later','history','search'].includes(state.tab))downloads.quiet(8000);document.body.classList.toggle('signed-out',state.ready&&!state.user);if(!state.user){if(state.ready)gate();return;}if(!state.profile){profileGate();return;}artObserver?.disconnect();keepPosters();for(const n of document.querySelectorAll('[data-tab]')){const active=n.dataset.tab===state.tab;n.classList.toggle('active',active);if(active){n.setAttribute('aria-current','page');const nav=n.parentElement;if(nav.scrollWidth>nav.clientWidth)nav.scrollLeft=Math.max(0,n.offsetLeft-(nav.clientWidth-n.offsetWidth)/2);}else n.removeAttribute('aria-current');}paintNavEdges();if(state.tab==='search'){runSearch(state.query,false);return;}if(state.tab==='downloads'){renderDownloads();return;}if(state.tab==='settings'){management.renderSettings();return;}if(state.tab==='admin'){management.renderAdmin();return;}if(state.tab!=='home'){renderGrid();return;}warmTaste();arrangeSlides();const featured=slideshow.slides[slideshow.index];if(!featured){if(state.catalogueError)renderError();else main.replaceChildren(el('section',{class:'grid-page'},el('h1',{},'Що подивимось?'),el('p',{role:'status'},'Завантажуємо фільми й серіали для вас.')));return;}const hero=el('section',{class:'hero'},heroCopy(featured));slideshow.hero=hero;hero.addEventListener('mouseenter',()=>{if(hoverable.matches)pauseSlides();});hero.addEventListener('focusin',pauseSlides);hero.addEventListener('mouseleave',()=>{if(hoverable.matches)resumeSlides();});hero.addEventListener('focusout',e=>{if(!hero.contains(e.relatedTarget))resumeSlides();});
 // A tap or click on the hero holds its slide for another turn, so a title just saved or opened never slips away under the finger.
 hero.addEventListener('click',()=>{if(slideshow.slides.length>1&&slideshow.hero===hero){slideshow.shownAt=Date.now();paintDots(hero);scheduleSlide();}});swipeSlides(hero);upgradeHero(hero,featured);paintDots(hero);const rows=el('div',{class:'rows'}),recent=continueItems(),picks=recommendations(),ai=state.profile?.enhanced_recs&&enhanced.mode==='ai';const shelves=uniqueShelves([{title:'Продовжити перегляд',items:recent,subtitle:'Ваш прогрес на всіх пристроях',progress:true},...holidayRow(),{title:state.user?'Для вас, '+state.profile?.name:'Популярне зараз',items:picks,subtitle:ai?'Покращена добірка з урахуванням вашого смаку':state.user?'На основі вашого смаку':'Час для хорошої історії'},...becauseRows(picks),{title:'Фільми на вечір',items:state.films.filter(i=>liked(i.link)>=0)},{title:'Ще одна серія?',items:state.series.filter(i=>liked(i.link)>=0)},{title:'Мій список',items:data('later').sort((a,b)=>b.updated_at-a.updated_at).map(r=>({...r.data,title:r.data.displayTitle||r.data.title}))}]);shelves.splice(1+holidayRow().length,0,...topTenRow());for(const r of shelves){const row=shelf(r.title,r.items,r.subtitle||'',r.progress,r.rank);if(row)rows.append(row);}
 // The viewer's own rows stay when the source is down; the catalogue under them retries by itself.
 if(state.catalogueError)rows.append(oops({error:state.catalogueFailure,retry:loadCatalogue,key:'home',size:'compact'}));
 // A shelf the viewer has scrolled along keeps its place through a repaint (a sync, a closed window).
 const kept=new Map([...main.querySelectorAll('.shelf')].map(s=>[s.querySelector('h2')?.textContent,s.querySelector('.shelf-line')?.scrollLeft||0]));main.replaceChildren(hero,rows);for(const s of rows.querySelectorAll('.shelf')){const left=kept.get(s.querySelector('h2').textContent);if(left)s.querySelector('.shelf-line').scrollLeft=left;}paintNavEdges();scheduleSlide();prefetchSlide((slideshow.index+1)%slideshow.slides.length);maybeFireworks();}
function renderError(){main.replaceChildren(el('section',{class:'grid-page oops-holder'},oops({error:state.catalogueFailure,retry:loadCatalogue,key:'home',size:'page'})));}
function gridPage(title,items,empty='Тут поки порожньо. Знайдіть фільм до настрою.'){artObserver?.disconnect();main.replaceChildren(el('section',{class:'grid-page'},el('div',{class:'page-head'},el('h1',{},title),null),items.length?el('div',{class:'grid'},uniqueTitles(items).map(i=>card(i,state.tab==='history'))):el('div',{class:'grid-empty'},empty)));}
const catalogueView=createCatalogueView({main,el,button,card,keyOf,api,oops,visible:allowed,isActive:()=>state.tab==='catalogue',onSelection:selection=>{state.browse=selection;const q=selectionQuery(selection),hash='#catalogue'+(q?'?'+q:'');if(location.hash!==hash)history.replaceState(null,'',hash);}});
/** The catalogue kept on screen by a repeated render still upgrades its posters (render() disconnects the observer). */
function renderCatalogue(){if(!catalogueView.open(state.browse)&&artObserver)for(const n of main.querySelectorAll('.poster-image'))if(n._art)artObserver.observe(n);}
function renderGrid(){if(state.tab==='catalogue')renderCatalogue();else if(state.tab==='later')gridPage('Мій список',data('later').sort((a,b)=>b.updated_at-a.updated_at).map(r=>({...r.data,title:r.data.displayTitle||r.data.title})),state.user?'Зберігайте фільми та серіали, щоб повернутися до них пізніше.':'Увійдіть, щоб ваш список був доступний на всіх пристроях.');else if(state.tab==='history'){const groups=titleGroups(data('progress'),keyOf);gridPage('Історія перегляду',historyItems().map(x=>({...x,info:engagementLabel(engagement(groups.get(keyOf(x.link))||[]))||x.info})),state.user?'Тут будуть ваші перегляди з TV та інших пристроїв.':'Увійдіть, щоб побачити історію.');}}
let catalogueGeneration=0;
async function loadCatalogue(){
 const generation=++catalogueGeneration;state.catalogueError=false;state.catalogue||=[[],[],[]];state.catalogueLoading=[true,true,true];state.catalogueErrors=[false,false,false];let completed=0,first=state.pool.length?Promise.resolve():null;
 await Promise.allSettled(['filter=popular','category=films&filter=popular','category=series&filter=popular'].map(async(params,index)=>{
  try{const response=await api('/api/catalogue?'+params);if(generation!==catalogueGeneration)return;state.catalogue[index]=response.items||[];applyCatalogue();
   // The first paint of the home waits a moment for the account (identityReady), so a signed-in viewer opens on their own picks.
   if(state.pool.length){if(!first)first=(async()=>{await identityReady;if(generation===catalogueGeneration&&state.tab==='home')render();})();else{await first;if(generation===catalogueGeneration)backgroundRender();}}}
  catch(e){if(generation===catalogueGeneration){state.catalogueErrors[index]=true;state.catalogueFailure=e;}}
  finally{completed++;if(generation===catalogueGeneration)state.catalogueLoading[index]=false;}
 }));
 if(generation!==catalogueGeneration)return;
 if(!state.pool.length&&completed===3){state.catalogueError=true;if(state.tab==='home')render();}
}
for(const b of document.querySelectorAll('[data-tab]'))b.onclick=()=>navigate(b.dataset.tab);
const headerSearch=createHeaderSearch({trigger:$('#search-open'),header:document.querySelector('.header'),el,button,storage,context:()=>({userId:state.user?.id,profileId:state.profile?.id}),suggestions:()=>recommendations(),card,onSearch:query=>runSearch(query,true),onSelect:openDetails,live:q=>({now:allowed(localMatches(q)),later:()=>searchSource(q,'').then(allowed)})});
function searchDialog(){headerSearch.open(state.query);}
/** Search answers per query and source, fresh for 3 minutes: typing, «Знайти», back and forward share them. */
const searchCache=new Map();
function searchSource(q,source){const key=source+'\n'+q.toLowerCase(),hit=searchCache.get(key);if(hit&&Date.now()-hit.at<180000)return hit.task;const task=api('/api/catalogue?query='+encodeURIComponent(q)+(source?'&source='+source:'')).then(r=>uniqueTitles(r.items||[]).filter(i=>!i.external));task.catch(()=>searchCache.delete(key));searchCache.set(key,{task,at:Date.now()});if(searchCache.size>60)searchCache.delete(searchCache.keys().next().value);return task;}
const fold=s=>String(s||'').toLowerCase().replace(/ё/g,'е').replace(/[’'`ʼ]/g,'');
function localMatches(q){const f=fold(q);return state.pool.filter(i=>fold(i.title).includes(f)||fold(i.original).includes(f)).slice(0,6);}
async function runSearch(query,push=false){
 const q=String(query||'').trim().slice(0,150),gen=++pageGeneration;state.tab='search';state.query=q;
 if(push){history.pushState(null,'','#search?q='+encodeURIComponent(q));window.scrollTo(0,0);}
 for(const n of document.querySelectorAll('[data-tab]')){n.classList.remove('active');n.removeAttribute('aria-current');}
 if(!q){gridPage('Пошук',[],'Введіть назву фільму або серіалу.');main.querySelector('.grid-page').append(button('Знайти фільм',searchDialog,'pill primary'));return;}
 gridPage('Шукаємо «'+q+'»…',[],'Завантаження результатів…');main.setAttribute('aria-busy','true');
 let found=new Map(),successes=0,pending=2,fixed='',failure=null;
 const add=(items,into=found)=>{for(const i of items||[]){const key=keyOf(i.link),old=into.get(key);if(!old||i.uaLink&&!old.uaLink)into.set(key,i);}};
 // Each source shows its titles as soon as it answers; the slower one joins in after (new titles go to the end).
 const paint=()=>{if(gen!==pageGeneration)return;const items=allowed([...found.values()]);if(pending&&!items.length)return;
  // Neither source answered: KIK's «Упс» screen, which searches again by itself while the cause is temporary.
  if(!pending&&!successes){main.replaceChildren(el('section',{class:'grid-page oops-holder'},oops({error:failure,retry:()=>runSearch(q),title:'Пошук «'+q+'»',key:'search',size:'page'})));return;}
  gridPage('Результати для «'+(fixed||q)+'»',items,pending?'Завантаження результатів…':successes?'Нічого не знайшли. Спробуйте іншу назву.':'Не вдалося завантажити результати. Перевірте інтернет.');if(fixed)main.querySelector('.page-head')?.after(el('p',{class:'search-fixed'},'Ви шукали «'+q+'»'));const page=main.querySelector('.grid-page');if(pending)page.append(el('p',{class:'subtle search-more',role:'status'},'Шукаємо ще в інших джерелах…'));else page.append(el('div',{class:'actions search-feedback'},button('Змінити запит',searchDialog),!successes?button('Спробувати ще',()=>runSearch(q),'pill primary'):null));};
 // Other names of the title (typos, wrong keyboard layout, another language), searched apart from the first results.
 const aliasSearch=async()=>{try{const extra=await api('/api/search-aliases?query='+encodeURIComponent(q)),names=(extra.aliases||[]).slice(0,2),better=new Map();await Promise.allSettled(names.map(async name=>{add(await searchSource(name,''),better);successes++;}));return {names,better};}catch{return null;}};
 try{
  let alias=null;
  await Promise.allSettled(['','uaserials'].map(async source=>{try{add(await searchSource(q,source));successes++;
   // Nothing on the main source: other names of the title are looked up while the second source is still searching.
   if(!source&&!found.size)alias=aliasSearch();}catch(e){failure=e;throw e;}finally{pending--;if(pending)paint();}}));
  if(gen!==pageGeneration)return;
  // A typo, the wrong keyboard layout or a name in another language: the titles it most likely meant go first.
  if(!searchMatches(q,[...found.values()])){const r=await (alias||aliasSearch());if(gen!==pageGeneration)return;
   if(r?.better.size){fixed=r.names.find(n=>[...r.better.values()].some(i=>itemScore(n,i)>=.6))||r.names[0];for(const [k,v] of found)if(!r.better.has(k))r.better.set(k,v);found=r.better;}}
  paint();
 }finally{if(gen===pageGeneration)main.removeAttribute('aria-busy');}
}

/** Title details: one request per title at a time, fresh for 3 minutes, at most 80 titles kept. */
const filmCache=new Map(),filmPending=new Map();async function getFilm(item){const key=keyOf(item.link),cached=filmCache.get(key);if(cached&&Date.now()-cached.at<180000)return cached.film;if(cached)filmCache.delete(key);if(filmPending.has(key))return filmPending.get(key);const task=api('/api/film?link='+encodeURIComponent(item.uaLink||item.link)).then(f=>{filmCache.set(key,{film:f,at:Date.now()});if(filmCache.size>80)filmCache.delete(filmCache.keys().next().value);return f;}).finally(()=>filmPending.delete(key));filmPending.set(key,task);return task;}
/** «Вам може сподобатися»: titles like this one that KIK can play (TMDB recommendations, else the source's «see also»), without hidden countries, disliked titles or the title itself. */
function similarShelf(f,generation){const box=el('section',{class:'detail-similar','aria-label':'Вам може сподобатися',hidden:true});
 const fill=list=>{const disliked=new Set(data('rating').filter(r=>r.data?.value<0).map(r=>r.key));const items=allowed(list).filter(i=>keyOf(i.link)!==keyOf(f.link)&&!disliked.has(keyOf(i.link))).slice(0,12);if(!items.length||!box.isConnected||generation!==modalGeneration)return;const line=el('div',{class:'similar-line'},items.map(i=>card(i)));line.addEventListener('click',()=>modal.scrollTo?.({top:0}));box.replaceChildren(el('h3',{},'Вам може сподобатися'),line);box.hidden=false;if(artObserver)for(const n of line.querySelectorAll('.poster-image'))if(n._art)artObserver.observe(n);};
 if(f.external||keyOf(f.link).startsWith('uaserials/'))return null;
 api('/api/similar?link='+encodeURIComponent(f.link)).then(r=>fill(r.items?.length?r.items:f.related),()=>fill(f.related));return box;}
/** A short nod on 👍 / 👎 once the rating is set (style.css .nod); a second press clears the rating quietly. */
function nod(b){b.classList.remove('nod');if(b.classList.contains('rated')){void b.offsetWidth;b.classList.add('nod');}}
async function openDetails(item){showModal(el('h2',{},item.title),el('p',{},'Завантаження деталей…'));if(!filmCache.has(keyOf(item.link)))modal.classList.add('detail-modal','detail-loading');const target=item.link,generation=modalGeneration;modalContent.dataset.link=target;try{const f=await getFilm(item);if(!modal.open||generation!==modalGeneration||modalContent.dataset.link!==target)return;const image=f.poster?el('img',{class:'detail-poster',src:f.poster,alt:'',referrerpolicy:'no-referrer'}):null,laterButton=button('＋',()=>{toggleLater(f);paintActions();},'round-action'),like=button('👍',()=>{rate(f,1);paintActions();nod(like);},'round-action'),dislike=button('👎',()=>{rate(f,-1);paintActions();nod(dislike);},'round-action'),playButton=f.external?el('span',{class:'pill unavailable'},'Перегляд поки недоступний'):button('▶ '+(latestFor(f.link)?.pos?'Продовжити':'Дивитися'),()=>{modal.close();play(f);},'pill primary');
 // The three round actions repaint in place: the window, its scroll and the keyboard focus stay where they are.
 function paintActions(){const saved=inList(f.link),v=liked(f.link);laterButton.textContent=saved?'✓':'＋';laterButton.classList.toggle('rated',saved);laterButton.setAttribute('aria-pressed',String(saved));laterButton.setAttribute('aria-label',saved?'У списку':'Мій список');laterButton.title=saved?'Прибрати з «Мого списку»':'Додати в «Мій список»';for(const [b,value,name] of [[like,1,'Подобається'],[dislike,-1,'Не подобається']]){const on=v===value;b.classList.toggle('rated',on);b.setAttribute('aria-pressed',String(on));b.setAttribute('aria-label',name);b.title=name;}}
 paintActions();// The round actions stay together: on a narrow window they move to the next line as one row, ⤓ never on its own.
 const actions=el('div',{class:'detail-actions'},playButton,el('div',{class:'detail-round'},laterButton,like,dislike,downloadButton(f,item)));const forget=latestFor(f.link)?button('Прибрати з історії',()=>{for(const r of data('progress').filter(r=>keyOf(r.data.link)===keyOf(f.link)))change('progress',r.key,null);toast('Прибрано з історії');forget.remove();if(playButton.tagName==='BUTTON')playButton.textContent='▶ Дивитися';},'text-button'):null;const overview=el('p',{class:'detail-overview'},f.description||'Опис наразі недоступний.'),more=button('Детальніше',()=>{const open=overview.classList.toggle('open');more.textContent=open?'Згорнути':'Детальніше';},'text-button detail-more');more.hidden=true;modal.classList.remove('detail-loading');modal.classList.add('detail-modal');const backdrop=el('div',{class:'detail-backdrop'});if(f.poster)backdrop.style.backgroundImage='url('+JSON.stringify(f.poster)+')';modalContent.replaceChildren(backdrop,el('div',{class:'detail-body'},image,el('div',{class:'detail-info'},el('h2',{class:'detail-title'},f.title),el('div',{class:'detail-meta'},[f.year,f.rating?'★ '+f.rating:null,f.duration].filter(Boolean).map(t=>el('span',{},t)),f.genres.slice(0,3).map(g=>el('span',{class:'detail-genre'},g))),actions,overview,more,f.external?el('p',{class:'subtle'},'Зараз для цього фільму немає доступного відтворення у KIK.'):null,forget)),el('div',{id:'extra-metadata',class:'source-metadata'}),similarShelf(f,generation));requestAnimationFrame(()=>{more.hidden=overview.scrollHeight<=overview.clientHeight+2;});if(document.activeElement===modal||document.activeElement===document.body)actions.querySelector('button')?.focus({preventScroll:true});paintDetailStudios(item.studios);artworkFor(f).then(a=>{if(modal.open&&generation===modalGeneration&&modalContent.dataset.link===target)paintDetailStudios(a?.studios?.length?a.studios:item.studios);});enrichDetails(f,target,generation);}catch(e){if(modal.open&&generation===modalGeneration&&modalContent.dataset.link===target){modal.classList.remove('detail-loading');modalContent.replaceChildren(oops({error:e,retry:()=>openDetails(item),title:item.title,key:'film:'+keyOf(item.link)}));}}}
function refreshAccount(){headerSearch.refresh();$('[data-tab=admin]').hidden=state.user?.role!=='admin';const name=state.profile?.name||'Увійти';$('#account-name').textContent=name;$('#account').setAttribute('aria-label',state.user?'Профіль: '+name:'Увійти до KIK');$('#account .avatar').replaceChildren(el('img',{src:avatarUrl(state.profile?.avatar),alt:state.profile?'Аватарка профілю':'Увійти'}));paintDownloadNav();paintOffline();downloads.kick();}
async function selectProfile(profile){resetEnhanced();management.reset();if(playerModal.open)playerModal.close();await sync();state.profile=profile;storage.setItem('kik-profile:'+state.user.id,profile.id);restore();await management.load();refreshAccount();render();await sync();}
function account(){
 if(!state.user){auth();return;}
 const tiles=state.profiles.map(profile=>{
  const active=profile.id===state.profile?.id;
  const avatar=el('span',{class:'profile-avatar'},el('img',{src:avatarUrl(profile.avatar),alt:'',width:136,height:136}),active?el('span',{class:'profile-current','aria-hidden':'true'},'✓'):null);
  const tile=button([avatar,el('span',{class:'profile-tile-name'},profile.name)],async()=>{modal.close();await selectProfile(profile);},'profile-tile');
  tile.setAttribute('aria-pressed',String(active));tile.setAttribute('aria-label',profile.name+(active?' — поточний профіль':''));return tile;
 });
 if(state.profiles.length<8){const add=button([el('span',{class:'profile-avatar profile-avatar-add','aria-hidden':'true'},'＋'),el('span',{class:'profile-tile-name'},'Створити профіль')],newProfile,'profile-tile profile-tile-add');add.setAttribute('aria-label','Створити профіль');tiles.push(add);}
 showModal(
  el('header',{class:'profile-dialog-header'},el('div',{class:'profile-dialog-brand','aria-hidden':'true'},'KIK'),el('h2',{id:'profile-picker-title'},'Хто дивиться?')),
  el('div',{class:'profile-grid',role:'group','aria-label':'Профілі'},tiles),
  el('footer',{class:'profile-dialog-footer'},el('div',{class:'profile-dialog-tools'},button('Налаштування',()=>{modal.close();navigate('settings');},'pill profile-tool'),button('Вийти',logout,'text-button profile-logout')),el('p',{class:'profile-account'},state.user.email||state.user.username))
 );
 modal.classList.add('profile-modal');modal.setAttribute('aria-labelledby','profile-picker-title');
 (tiles.find(tile=>tile.getAttribute('aria-pressed')==='true')||tiles[0])?.focus({preventScroll:true});
}
$('#account').onclick=account;$('#connect-tv').onclick=()=>{if(!state.user)auth();else pairDialog();};
function auth(register=false){const error=el('div',{class:'error',role:'alert'}),username=el('input',{type:'email',autocomplete:'email',required:true,maxlength:254,placeholder:'you@example.com',inputmode:'email',autocapitalize:'none',spellcheck:'false'}),password=el('input',{type:'password',autocomplete:register?'new-password':'current-password',required:true,minlength:register?6:1,maxlength:256,placeholder:register?'Щонайменше 6 символів':'Ваш пароль'}),name=el('input',{autocomplete:'nickname',maxlength:40,placeholder:'Як вас називати?'}),submit=el('button',{class:'pill red auth-submit',type:'submit'},register?'Створити обліковий запис':'Увійти'),form=el('form',{},el('label',{class:'field'},'Електронна пошта',username),register?el('label',{class:'field'},'Ім’я профілю',name):null,el('label',{class:'field'},'Пароль',password),error,submit);showModal(el('div',{class:'auth-kicker'},new URL(location.href).searchParams.get('pair')?'УВІЙДІТЬ, ЩОБ ПІДКЛЮЧИТИ TV':'ОДИН ПРОФІЛЬ. УСІ ВАШІ ПРИСТРОЇ.'),el('h2',{},register?'Ваша історія починається тут':'Раді знову вас бачити'),el('p',{},'Ваша історія на TV, телефоні й комп’ютері. Цей браузер запам’ятає вхід на 90 днів; під час користування термін поновлюється.'),form,button(register?'Уже є обліковий запис? Увійти':'Вперше тут? Створити обліковий запис',()=>auth(!register),'text-button'));username.focus();form.onsubmit=async e=>{e.preventDefault();submit.disabled=true;error.textContent='';try{const result=await api('/api/auth/'+(register?'register':'login'),{email:username.value,password:password.value,name:name.value});state.user=result.user;state.profiles=result.profiles;state.profile=state.profiles.find(p=>p.id===storage.getItem('kik-profile:'+state.user.id))||state.profiles[0];restore();management.reset();await management.load();refreshAccount();modal.close();render();await sync();if(new URL(location.href).searchParams.get('pair'))pairDialog();else if(register)taste();}catch(e){error.textContent=e.message;}finally{submit.disabled=false;}};}
function avatarChoices(selected,onChange){const grid=el('div',{class:'avatar-grid'});for(const a of AVATARS){const pick=button(el('img',{src:avatarUrl(a.id),alt:a.name,loading:'lazy'}),()=>{for(const n of grid.querySelectorAll('button'))n.setAttribute('aria-pressed',String(n===pick));onChange(a.id);},'avatar-choice');pick.setAttribute('aria-label',a.name);pick.setAttribute('aria-pressed',String(selected===a.id));grid.append(pick);}return grid;}
function chooseAvatar(){if(!state.user)return;let selected=state.profile.avatar;const error=el('p',{class:'error',role:'alert'}),save=button('Зберегти аватарку',async()=>{save.disabled=true;
 // The profile is fixed before the request: a switch during a slow save must not give another profile this one's id.
 const profile=state.profile;try{const p=await api('/api/profiles/update',{profile_id:profile.id,avatar:selected});Object.assign(profile,p);refreshAccount();modal.close();render();toast('Аватарку змінено');}catch(e){error.textContent=e.message;}finally{save.disabled=false;}},'pill primary');showModal(el('h2',{},'Виберіть свою аватарку'),avatarChoices(selected,id=>selected=id),error,save);}
function newProfile(){let avatar='bear';const input=el('input',{required:true,maxlength:40,placeholder:'Ім’я профілю'}),error=el('div',{class:'error'}),submit=el('button',{class:'pill primary',type:'submit'},'Створити'),form=el('form',{},el('label',{class:'field'},'Ім’я',input),el('p',{class:'muted'},'Виберіть аватарку'),avatarChoices(avatar,id=>avatar=id),error,submit);showModal(el('h2',{},'Новий профіль'),el('p',{},'Окремі рекомендації, історія й список збереженого.'),form);input.focus();form.onsubmit=async e=>{e.preventDefault();submit.disabled=true;try{const p=await api('/api/profiles',{name:input.value,avatar});state.profiles.push(p);await selectProfile(p);taste();}catch(e){error.textContent=e.message;}finally{submit.disabled=false;}};}

async function logout(){if(player)playerModal.close();await sync();await api('/api/auth/logout',{});const prefixes=['kik:'+state.user.id+':','kik:search:'+state.user.id+':'];for(const k of storage.keys())if(prefixes.some(p=>k.startsWith(p))){
 // Changes that never reached the server (a revoked session, no connection) wait for this account's next sign-in; the rest goes.
 let pending=[];try{if(k.startsWith(prefixes[0]))pending=JSON.parse(storage.getItem(k)||'{}').pending||[];}catch{}if(pending.length)storage.setItem(k,JSON.stringify({records:[],pending,since:0}));else storage.removeItem(k);}storage.removeItem('kik-me');resetEnhanced();management.reset();state.user=null;state.profiles=[];state.profile=null;state.records.clear();state.pending.clear();state.since=0;refreshAccount();modal.close();$('#sync-status').textContent='';render();}
function pairDialog(){if(!state.user){auth();return;}if(!state.profile){newProfile();return;}const code=el('input',{class:'code-input',required:true,maxlength:12,placeholder:'ABCD EFGH',autocomplete:'off',spellcheck:'false','aria-label':'Код із TV'});const scanned=new URL(location.href).searchParams.get('pair')||'';code.value=scanned.length===8?scanned.slice(0,4)+' '+scanned.slice(4):scanned;const profiles=el('select',{'aria-label':'Профіль для TV'},state.profiles.map(p=>el('option',{value:p.id},p.name)));profiles.value=state.profile.id;const error=el('div',{class:'error'}),submit=el('button',{class:'pill red auth-submit',type:'submit'},scanned?'Підтвердити й прив’язати':'Прив’язати TV'),form=el('form',{},el('label',{class:'field'},'Код із екрана телевізора',code),selectField('Спільний профіль',profiles,{option:profileOption,preview:profileOption}),error,submit);showModal(el('h2',{},scanned?'Підключити цей TV?':'Ваш KIK на великому екрані'),el('p',{},scanned?'Ви відсканували QR-код із телевізора. Перевірте, що код збігається з кодом на екрані, виберіть профіль і підтвердіть. Історія й прогрес профілю об’єднаються.':'У застосунку на TV відкрийте Налаштування → Синхронізація профілю. Відскануйте QR-код камерою телефона або введіть код, який з’явиться на екрані. Історія й прогрес вибраного профілю об’єднаються.'),form);(scanned?submit:code).focus();form.onsubmit=async e=>{e.preventDefault();submit.disabled=true;try{await api('/api/pair/claim',{code:code.value,profile_id:profiles.value});history.replaceState(null,'',location.pathname+location.hash);showModal(el('h2',{},'TV прив’язано'),el('p',{},'Застосунок на телевізорі зараз підключиться. Ваша історія з’явиться тут після синхронізації.'),button('До перегляду',()=>modal.close(),'pill red'));setTimeout(sync,4000);}catch(e){error.textContent=e.message;}finally{submit.disabled=false;}};}
function taste(){if(!state.user){auth();return;}const picks=new Map(),items=state.pool.slice(0,24);if(!items.length){toast('Спочатку завантажимо каталог');return;}const grid=el('div',{class:'taste-grid'}),save=button('Продовжити',()=>{for(const item of picks.values())change('rating',keyOf(item.link),{link:item.link,title:item.title,poster:item.poster,genre:item.info||'',value:1});modal.close();render();toast('Смак збережено');},'pill red');save.disabled=true;const count=el('span',{class:'subtle'},'Вибрано: 0');for(const item of items){const b=card(item,false,()=>{const key=keyOf(item.link);if(picks.has(key))picks.delete(key);else picks.set(key,item);b.classList.toggle('selected',picks.has(key));b.setAttribute('aria-pressed',String(picks.has(key)));count.textContent='Вибрано: '+picks.size;save.disabled=!picks.size;});b.setAttribute('aria-pressed','false');grid.append(b);}showModal(el('div',{class:'auth-kicker'},'ПРОФІЛЬ · '+state.profile.name),el('h2',{class:'onboarding-title'},'Що вам подобається?'),el('p',{},'Виберіть фільми й серіали, які вам близькі. Почнемо рекомендації з вашого смаку. Цей крок можна пропустити.'),grid,count,el('div',{class:'actions'},button('Пропустити',()=>modal.close()),save));modal.classList.add('wide-modal');}
/** Explicit choice for this title in this profile, then the track last watched, then the best track: the server lists Ukrainian, then Russian, then the rest. */
function bestVoice(voices,preferred){const free=cleanVoices(voices);return free.find(v=>v.id===preferred)||free.slice().sort((a,b)=>voiceScore(b)-voiceScore(a))[0];}
/** What the viewer picked for this title (voice, quality, speed): the player opens with it next time, on any device. */
function choicesFor(link){return data('voice').find(r=>r.key===keyOf(link))?.data||{};}
function chosenVoice(link){return choicesFor(link).voice;}
function rememberChoices(p,extra={}){if(!state.user||!state.profile)return;const f=p.film,v=f.voices.find(x=>x.id===p.voice),old=choicesFor(f.link);change('voice',keyOf(f.link),{...old,link:f.link,title:f.title,poster:f.poster,voice:v?.id||old.voice,voiceName:v?.name||old.voiceName,...extra});}
function voiceScore(v){const order=[management.settings.audio,...['uk','ru','en'].filter(x=>x!==management.settings.audio),'other'];return 4-order.indexOf(langOf(v));}
/** Languages, not studios: each one plays its best free studio (the server lists voices best first). */
const LANG_LABEL={uk:'Українська',ru:'Російська',en:'Англійська',other:'Інша мова'};
function langOf(v){return v?.lang||(/укра|ukrain/i.test(v?.name||'')?'uk':/оригин|оригін|original|англ|english|субтитр/i.test(v?.name||'')?'en':'ru');}
function langChoices(f){const free=cleanVoices(f.voices),out=['uk','ru','en'].filter(l=>free.some(v=>langOf(v)===l));return out.length?out:free.length?['other']:[];}
/** Always Ukrainian, Russian, English; a language missing in the title (or, once known, the season) is disabled. */
function fillVoices(p){const f=p.film,free=cleanVoices(f.voices),offered=l=>free.some(v=>langOf(v)===l&&(!f.series||!p.season||!p.coverage?.has(v.id)||p.coverage.get(v.id).some(e=>e.season===p.season)));
 p.voiceSelect.replaceChildren(...['uk','ru','en'].map(l=>{const o=option(l,LANG_LABEL[l]);o.disabled=!offered(l);return o;}));p.voiceSelect.value=langOf(f.voices.find(v=>v.id===p.voice));
 // Studios of the playing language, best first; one missing the selected season stays disabled.
 const lang=p.voiceSelect.value,studios=free.filter(v=>langOf(v)===lang);p.studioSelect.replaceChildren(...studios.map(v=>{const o=option(v.id,studioName(v));o.disabled=!!(f.series&&p.season&&p.coverage?.has(v.id)&&!p.coverage.get(v.id).some(e=>e.season===p.season));return o;}));p.studioSelect.value=p.voice;p.studioField.hidden=studios.length<2;}
/** Short studio name for the menu: «Укр · 1+1», «Укр · дубляж» instead of «Українська (1+1)». */
const LANG_SHORT={uk:'Укр',ru:'Рос',en:'Англ'};
function studioName(v){const short=LANG_SHORT[langOf(v)],word=/^(україн|украин|ukrain|російськ|русск|russian|англ|english)\S*\s*/i;let n=String(v.name||'').trim().replace(/\s*\((україн|украин|ukrain|російськ|русск|англ|english)[^)]*\)/gi,'').replace(word,'').trim().replace(/^\((.*)\)$/,'$1').trim();
 return short?(n?short+' · '+n:short):n||v.name;}
function bestOfLang(f,lang){return f.voices.find(v=>!v.premium&&langOf(v)===lang);}
let intro=null;
function chime(){try{const ctx=new (window.AudioContext||window.webkitAudioContext)(),t0=ctx.currentTime+.05,master=ctx.createGain(),lp=ctx.createBiquadFilter(),delay=ctx.createDelay(),fb=ctx.createGain(),wet=ctx.createGain(),tone=ctx.createBiquadFilter();master.gain.value=.9;lp.type='lowpass';lp.frequency.value=1100;delay.delayTime.value=.11;fb.gain.value=.38;wet.gain.value=.32;tone.type='lowpass';tone.frequency.value=1400;lp.connect(master);lp.connect(delay);delay.connect(tone).connect(fb).connect(delay);fb.connect(wet).connect(master);master.connect(ctx.destination);const swell=ctx.createBufferSource(),buf=ctx.createBuffer(1,ctx.sampleRate,ctx.sampleRate),d=buf.getChannelData(0);for(let i=0;i<d.length;i++)d[i]=Math.random()*2-1;swell.buffer=buf;const bp=ctx.createBiquadFilter(),sg=ctx.createGain();bp.type='bandpass';bp.Q.value=1.2;bp.frequency.setValueAtTime(180,t0+.1);bp.frequency.exponentialRampToValueAtTime(2600,t0+1);sg.gain.setValueAtTime(0,t0+.1);sg.gain.linearRampToValueAtTime(.14,t0+.95);sg.gain.setValueAtTime(0,t0+1.02);swell.connect(bp).connect(sg).connect(lp);swell.start(t0+.1);swell.stop(t0+1.05);const hit=(t,f,dur,level)=>{for(const [type,mul,g0] of [['triangle',1,.5],['sine',.5,.8],['sawtooth',2,.1]]){const o=ctx.createOscillator(),g=ctx.createGain();o.type=type;o.frequency.setValueAtTime(f*mul*1.6,t);o.frequency.exponentialRampToValueAtTime(f*mul,t+.07);o.frequency.exponentialRampToValueAtTime(f*mul*.85,t+dur);g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(level*g0,t+.015);g.gain.exponentialRampToValueAtTime(.0006,t+dur);o.connect(g).connect(lp);o.start(t);o.stop(t+dur);}};hit(t0+1,110,.55,.5);hit(t0+1.35,73.4,2,.55);setTimeout(()=>ctx.close().catch(()=>{}),3800);}catch{}}
/** The intro before a video, unless the owner switched it off for everyone (flags.js); on a holiday it wears the holiday (holiday-mark.js). */
function playIntro(){if(matchMedia('(prefers-reduced-motion: reduce)').matches||!currentFlags().intro)return Promise.resolve();const node=el('div',{class:'intro','aria-hidden':'true'});const mark=kind=>`<svg class="mark ${kind}" viewBox="0 0 102 48" aria-hidden="true"><g fill="none" stroke-width="7.6" stroke-linecap="round" stroke-linejoin="round"><path pathLength="1" d="M8,8L8,40"/><path pathLength="1" d="M29,8L8,24L29,40"/><path pathLength="1" d="M51,8L51,40"/><path pathLength="1" d="M73,8L73,40"/><path pathLength="1" d="M94,8L73,24L94,40"/></g></svg>`;
 // The glow and the flash are still copies whose opacity alone moves, so they stay smooth while the player loads.
 node.innerHTML=(festive?'':'<div class="intro-logo">'+mark('glow')+mark('draw')+mark('flash')+'</div>')+'<span class="intro-credit"><svg viewBox="0 0 18 18" aria-hidden="true"><circle cx="9" cy="9" r="8" fill="none" stroke="currentColor" stroke-width="1"/><path d="M9 1a8 8 0 0 1 0 16z" fill="currentColor"/></svg>Створено Чорним</span>';(player?.stage||playerModal).append(node);
 const stop=festive?holidayIntro(node,festive):null;chime();const done=new Promise(r=>setTimeout(r,festive?HOLIDAY_INTRO_MS:3250));done.then(()=>{stop?.();node.remove();if(intro===done)intro=null;});return intro=done;}
async function play(item){const generation=++playGeneration;await management.load();if(generation!==playGeneration)return;if(item.source==='uaserials'||new URL(item.link).hostname==='uaserials.com'){await openDetails(item);return;}if(!state.user){auth();return;}if(playerModal.open)disposePlayer();playerShell(item.title,item.poster,'Готуємо відео…','busy');if(!playerModal.open){playerModal.showModal();if(document.activeElement===playerModal.querySelector('.close')){playerModal.tabIndex=-1;playerModal.focus({preventScroll:true});}}try{const f=await filmForPlay(item);if(!playerModal.open||generation!==playGeneration)return;const previous=latestFor(f.link),explicit=chosenVoice(f.link),voice=bestVoice(f.voices,explicit||previous?.voice);if(!voice)throw Error('Немає доступного озвучення');const p={film:f,offline:!!f.offline,episodes:[],coverage:new Map(),userVoice:explicit||null,voice:voice.id,season:null,episode:null,quality:choicesFor(f.link).quality||management.settings.quality,watched:previous?.watched||0,lastSave:0,loadId:0};
 if(f.series){const own=f.offline?f.episodes:(await api('/api/episodes',{link:f.link,voice:voice.id})).episodes;p.coverage.set(voice.id,own);p.episodes=mergeEpisodes(f.episodes,own);const at=p.episodes.findIndex(e=>e.season===previous?.season&&e.episode===previous?.episode),done=previous?.dur>0&&previous.pos/previous.dur>.93;const asked=item.playEpisode&&p.episodes.find(e=>e.season===item.playEpisode.season&&e.episode===item.playEpisode.episode),target=asked||(at>=0&&done&&p.episodes[at+1])||p.episodes[at]||p.episodes[0];if(!target)throw Error('Серії наразі недоступні');p.voice=await voiceFor(p,target.season,target.episode)||voice.id;p.season=target.season;p.episode=target.episode;}
 if(!playerModal.open||generation!==playGeneration)return;player=p;buildPlayer();await loadStream();}catch(e){if(!playerModal.open||generation!==playGeneration)return;playerShell(item.title,item.poster,e,'error',()=>{playerModal.close();setTimeout(()=>play(item),0);});}}
/** The player window at its final size before the stream is known: the title, the poster on a dark stage and what is going on,
 *  so the window never grows from a line of text into the picture. */
function playerShell(title,poster,text,kind,retry){const status=kind==='error'?el('div',{class:'player-status error oops-holder'},oops({error:text,retry,key:'play:'+title,size:'compact'})):el('div',{class:'player-status '+kind},el('span',{},text),retry?button('Спробувати ще',retry,'pill small'):null);$('#player-content').replaceChildren(el('div',{class:'player-head'},el('h2',{},title)),el('div',{class:'player-stage'},poster?el('div',{class:'player-poster',style:'background-image:url('+JSON.stringify(poster)+')'}):null,status));}
/** What the player is doing, over the picture: a spinner while it works, red when it failed (with «Спробувати ще»), a plain
 *  line while it waits for a tap; quiet news stays with screen readers only. */
function note(p,text,kind='quiet'){p.note.textContent=text;p.noteKind=kind;if(!p.status)return;p.status.hidden=kind==='quiet';p.status.className='player-status '+kind;p.statusText.textContent=text;}
/** «Як вам?» at the credits (rate-prompt.js, the same rules as the TV): plays on, hides itself after 15 s, asked once per title. */
function ratePrompt(p){const title=el('strong',{},''),choices=el('div',{class:'rate-choices'},el('button',{class:'rate-thumb',type:'button','aria-label':'Подобається',title:'Подобається',onClick:()=>answerRating(p,1)},'👍'),el('button',{class:'rate-thumb',type:'button','aria-label':'Не подобається',title:'Не подобається',onClick:()=>answerRating(p,-1)},'👎'),button('Пропустити',()=>answerRating(p,0),'text-button'));const timer=el('i',{class:'rate-timer'}),box=el('div',{class:'rate-prompt',role:'group','aria-label':'Оцінка'},el('div',{class:'rate-line'},title,choices),timer);box.hidden=true;Object.assign(p,{rateBox:box,rateTitle:title,rateChoices:choices,rateTimer:timer});return box;}
function maybeAskRating(p){const v=p.video,f=p.film,item=p.season+':'+p.episode;if(p.askItem!==item){p.askItem=item;p.watchedItem=0;p.askChecked=false;hideRating(p);}
 if(p.askChecked||!state.user||!state.profile||!(v.duration>0)||v.currentTime<creditsAt(v.duration))return;p.askChecked=true;
 const key=keyOf(f.link),owner=state.user.id+':'+state.profile.id,index=p.episodes.findIndex(e=>e.season===p.season&&e.episode===p.episode),finished=f.series?data('progress').filter(r=>keyOf(r.data.link)===key&&r.data.dur>0&&r.data.pos/r.data.dur>.93).length:0;
 if(!askRating({time:v.currentTime,duration:v.duration,watched:f.series?p.watchedItem:Math.max(p.watchedItem,p.watched),series:!!f.series,last:!!f.series&&index===p.episodes.length-1,finished})||liked(f.link)||asked(owner,key,storage))return;
 markAsked(owner,key,storage);p.rateTitle.textContent='Як вам «'+f.title+'»?';p.rateChoices.hidden=false;for(const b of p.rateChoices.children)b.classList.remove('chosen');p.rateBox.classList.remove('leaving','thanks');p.rateBox.hidden=false;p.rateOpen=true;restartTimer(p.rateTimer);clearTimeout(p.rateHide);p.rateHide=setTimeout(()=>hideRating(p),8000);}
/** The thin line under the card runs out in 8 s, when the card hides by itself. */
function restartTimer(t){t.style.animation='none';void t.offsetWidth;t.style.animation='';}
function answerRating(p,value){if(!p.rateOpen)return;p.rateOpen=false;clearTimeout(p.rateHide);if(!value){hideRating(p);return;}setRating(p.film,value);{const rest=said(value).replace(/^[^·]*· /,'');p.rateTitle.textContent='Дякуємо! '+rest[0].toUpperCase()+rest.slice(1);}[...p.rateChoices.children].forEach((b,i)=>b.classList.toggle('chosen',i===(value>0?0:1)));p.rateBox.classList.add('thanks');p.rateHide=setTimeout(()=>hideRating(p),2200);}
function hideRating(p){p.rateOpen=false;clearTimeout(p.rateHide);const box=p.rateBox;if(!box||box.hidden)return;box.classList.add('leaving');p.rateHide=setTimeout(()=>{box.hidden=true;box.classList.remove('leaving');},300);}
function option(value,label){return el('option',{value},label);}
/** All seasons and episodes known for the title, whichever voice has them; source titles in Ukrainian. */
function mergeEpisodes(...lists){const all=new Map();for(const list of lists)for(const e of list||[])all.set(e.season+':'+e.episode,{...e,title:/^Серия\s*\d+$/.test(e.title||'')?'Серія '+e.episode:e.title});return [...all.values()].sort((a,b)=>a.season-b.season||a.episode-b.episode);}
/** Voices differ in which seasons they cover: the viewer's own choice first, then Ukrainian, then Russian, then the rest. */
async function voiceFor(p,season,episode){if(p.offline)return downloads.find(p.film.link,season,episode)?.voice||null;const free=cleanVoices(p.film.voices),mine=free.find(v=>v.id===p.userVoice),order=[...(free.some(v=>v.id===p.userVoice)?[p.userVoice]:[]),...free.filter(v=>mine&&langOf(v)===langOf(mine)).map(v=>v.id),...free.slice().sort((a,b)=>voiceScore(b)-voiceScore(a)).map(v=>v.id)].filter((v,i,a)=>v&&a.indexOf(v)===i);
 for(const id of order){let eps=p.coverage.get(id);if(!eps){try{eps=(await api('/api/episodes',{link:p.film.link,voice:id})).episodes||[];}catch{eps=[];}p.coverage.set(id,eps);p.episodes=mergeEpisodes(p.episodes,eps);}if(eps.some(e=>e.season===season&&e.episode===episode))return id;}return null;}
async function goToEpisode(p,season,episode,shouldPlay=true){const changed=p.season!==season||p.episode!==episode,request=p.episodeRequest=(p.episodeRequest||0)+1;const id=await voiceFor(p,season,episode);if(player!==p||request!==p.episodeRequest)return;if(!id){note(p,'Для цієї серії немає доступного озвучення','error');p.retry.hidden=true;fillEpisodes(p);return;}if(changed)p.introPlayed=false;p.season=season;p.episode=episode;
 if(id&&id!==p.voice){p.voice=id;const v=p.film.voices.find(x=>x.id===id),wanted=langOf(p.film.voices.find(x=>x.id===p.userVoice))||langChoices(p.film)[0];if(langOf(v)!==wanted)toast(LANG_LABEL[wanted]+' озвучка для цієї серії недоступна. Увімкнено: '+LANG_LABEL[langOf(v)].toLowerCase()+'.');}
 fillEpisodes(p);fillVoices(p);await loadStream(shouldPlay);smartTidy();}
function fillEpisodes(p){p.seasonSelect.replaceChildren(...[...new Set(p.episodes.map(e=>e.season))].map(s=>option(s,'Сезон '+s)));p.seasonSelect.value=p.season;p.episodeSelect.replaceChildren(...p.episodes.filter(e=>e.season===p.season).map(e=>option(e.episode,e.title||'Серія '+e.episode)));p.episodeSelect.value=p.episode;}
const voiceName=p=>p.film.voices.find(v=>v.id===p.voice)?.name;
function buildPlayer(){const p=player,f=p.film,video=el('video',{controls:true,playsinline:true,preload:'metadata',poster:f.poster||'','aria-label':f.title}),voice=el('select',{'aria-label':'Озвучення'}),studio=el('select',{'aria-label':'Студія'}),season=el('select',{'aria-label':'Сезон'}),episode=el('select',{'aria-label':'Серія'}),quality=el('select',{'aria-label':'Якість'}),speed=el('select',{'aria-label':'Швидкість'},[.75,1,1.25,1.5,2].map(n=>option(n,n+'×'))),sleep=el('select',{'aria-label':'Таймер сну'},option(0,'Вимкнено'),SLEEP_MINUTES.map(n=>option(n,sleepLabel(n))),f.series?option('episode','Після цієї серії'):null),note=el('p',{class:'player-note',role:'status'},'Підготовка відео…'),retry=button('Спробувати ще',()=>loadStream());retry.hidden=true;const rate=choicesFor(f.link).speed||management.settings.speed;speed.value=String(rate);video.playbackRate=rate;const studioField=dropdown(studio,'Студія');Object.assign(p,{video,voiceSelect:voice,studioSelect:studio,studioField,seasonSelect:season,episodeSelect:episode,qualitySelect:quality,sleepSelect:sleep,note,retry});fillEpisodes(p);fillVoices(p);const tools=el('div',{class:'player-tools'},dropdown(voice,'Озвучення'),studioField,f.series?dropdown(season,'Сезон'):null,f.series?dropdown(episode,'Серія'):null,dropdown(quality,'Якість'),dropdown(speed,'Швидкість'),sleepDropdown(sleep));const skip=button('Пропустити рекламу',()=>{if(p.skipTo!=null){video.currentTime=p.skipTo;skip.hidden=true;}});p.onSkipKey=e=>{if(e.key==='Escape'&&p.rateOpen&&player===p){e.preventDefault();answerRating(p,0);return;}if(e.key==='Enter'&&!skip.hidden&&p.skipTo!=null&&player===p&&!/^(SELECT|INPUT|BUTTON)$/.test(document.activeElement?.tagName)){e.preventDefault();skip.click();}
  if(e.key==='Enter'&&p.nextBox&&!p.nextBox.hidden&&skip.hidden&&player===p&&!/^(SELECT|INPUT|BUTTON)$/.test(document.activeElement?.tagName)){e.preventDefault();playNext(p);}
  // F (or А on the Ukrainian layout) toggles the full screen, as in every player; not while a field or a menu has the keyboard.
  if(/^[fFаА]$/.test(e.key)&&player===p&&playerModal.open&&!e.metaKey&&!e.ctrlKey&&!e.altKey&&!/^(SELECT|INPUT|TEXTAREA)$/.test(document.activeElement?.tagName)&&!document.querySelector('.dropdown-panel:popover-open')){e.preventDefault();toggleFullscreen(p);}};document.addEventListener('keydown',p.onSkipKey);skip.className='skip-ad';skip.title='Enter';skip.hidden=true;p.skipAd=skip;retry.className='pill small';
 // The full screen opens with F or a double click on the picture; there a round «exit» sits on the picture.
 const exit=button([],()=>toggleFullscreen(p),'player-exit');exit.setAttribute('aria-label','Вийти з повного екрана');exit.title='Вийти з повного екрана (F)';exit.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5"/></svg>';
 video.ondblclick=()=>toggleFullscreen(p);
 p.statusText=el('span',{},'');p.status=el('div',{class:'player-status',hidden:true},p.statusText,retry);
 p.stage=el('div',{class:'player-stage'},video,skip,nextCard(p),ratePrompt(p),p.status,exit);$('#player-content').replaceChildren(el('div',{class:'player-head'},el('h2',{},f.title)),p.stage,tools,note);let lastVideo=0,lastClock=performance.now();video.ontimeupdate=()=>{const now=performance.now(),delta=video.currentTime-lastVideo;if(!video.paused&&!video.seeking&&delta>0&&delta<5){const add=Math.min(delta*1000,now-lastClock);p.watched+=add;p.watchedItem=(p.watchedItem||0)+add;p.recovery?.progress(Date.now());clearTimeout(p.recoveryTimer);p.recoveryTimer=null;armRecovery(p);}if(p.mediaReady&&!p.switching&&!video.seeking){const to=adSkip(p.ads?.breaks,video.currentTime,video.duration,p.ads?.duration);if(to!==null){video.currentTime=to;toast('Рекламу пропущено');}else{const hint=adSkip(p.ads?.suggested,video.currentTime,video.duration,p.ads?.duration),intro=hint===null?introSkip(p.marks,video.currentTime,video.duration):null;p.skipTo=hint??intro;p.skipAd.textContent=hint!==null?'Пропустити рекламу':'Пропустити заставку';p.skipAd.hidden=p.skipTo===null;
  // The channel logo an episode opens with (Netflix) is passed by itself, once, as a voice studio's ident is.
  const logo=p.logoDone?0:openingLogo(p.marks,video.duration);if(logo&&video.currentTime<Math.min(3,logo-.5)){p.logoDone=true;video.currentTime=logo;}}maybeAskRating(p);paintNext(p);}if(p.mediaReady&&!p.switching)p.lastPosition=video.currentTime;lastVideo=video.currentTime;lastClock=now;if(now-p.lastSave>12000)saveProgress();};video.onpause=()=>{if(!p.switching&&p.mediaReady&&!video.ended)p.playWanted=false;saveProgress();};video.onended=async()=>{clearTimeout(p.recoveryTimer);p.recoveryTimer=null;if(dropPreview(p))return;if(Number.isFinite(video.duration)&&video.duration-video.currentTime>90&&(p.earlyEnds=(p.earlyEnds||0)+1)<=3){recoverPlayer(p);return;}p.playWanted=false;saveProgress();const index=p.episodes.findIndex(e=>e.season===p.season&&e.episode===p.episode),next=p.episodes[index+1];if(p.sleepAfterEpisode){p.sleepAfterEpisode=false;p.sleepOn=false;sleep.value='0';paintSleep(p);toast('Таймер сну: серію завершено');return;}if(f.series&&next&&management.settings.autoplay)await goToEpisode(p,next.season,next.episode);};video.onerror=()=>recoverPlayer(p);video.onwaiting=video.onstalled=()=>armRecovery(p);video.onplay=()=>{p.playWanted=true;if(p.noteKind==='info')note(p,'');armRecovery(p);};const useVoice=async best=>{if(!best)return;const wanted=p.playWanted!==false;saveProgress();p.userVoice=best.id;if(state.user&&state.profile)change('voice',keyOf(f.link),{...choicesFor(f.link),link:f.link,title:f.title,poster:f.poster,voice:best.id,voiceName:best.name});voice.disabled=studio.disabled=true;
 try{if(f.series)await goToEpisode(p,p.season,p.episode,wanted);else{p.voice=best.id;fillVoices(p);await loadStream(wanted);}}catch(e){note(p,e.message,'error');}finally{voice.disabled=studio.disabled=false;}};
 voice.onchange=()=>useVoice(bestOfLang(f,voice.value));studio.onchange=()=>useVoice(f.voices.find(v=>v.id===studio.value));season.onchange=()=>{saveProgress();goToEpisode(p,season.value,p.episodes.find(e=>e.season===season.value)?.episode,p.playWanted!==false);};episode.onchange=()=>{saveProgress();goToEpisode(p,p.season,episode.value,p.playWanted!==false);};quality.onchange=()=>{saveProgress();p.quality=quality.value;rememberChoices(p,{quality:p.quality});setSource(p,p.qualities.find(q=>q.label===p.quality).url,p.mediaReady?p.video.currentTime:p.resumePosition||0,p.playWanted!==false);};speed.onchange=()=>{video.playbackRate=Number(speed.value);rememberChoices(p,{speed:Number(speed.value)});};sleep.onchange=()=>setSleep(p,sleep.value);}
const SLEEP_MINUTES=[15,30,45,60,90,120],CLOCK='<svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3.2 2"/></svg>';
/** The timer is one round clock at the end of the tools row; a red dot marks it while it runs. */
function sleepDropdown(select){const d=dropdown(select,'Таймер сну',{icon:CLOCK});d.classList.add('sleep-dropdown');return d;}
function paintSleep(p){p.sleepSelect.closest('.dropdown')?.querySelector('.dropdown-trigger')?.classList.toggle('active',!!p.sleepOn);}
function sleepLabel(n){return n%60===0?n/60+' год':n>60?Math.floor(n/60)+','+Math.floor(n%60*10/60)+' год':n+' хв';}
/** Sleep timer, set per viewing: playback pauses at the deadline (a minute's warning first), or the episode ends without the next one starting. */
function setSleep(p,value){clearTimeout(p.sleepTimer);clearTimeout(p.sleepWarn);const minutes=Number(value)||0,was=p.sleepOn;p.sleepAfterEpisode=value==='episode';p.sleepOn=p.sleepAfterEpisode||minutes>0;paintSleep(p);if(p.sleepAfterEpisode){toast('Плеєр зупиниться після цієї серії');return;}if(!minutes){if(was)toast('Таймер сну вимкнено');return;}toast('Відтворення зупиниться через '+sleepLabel(minutes));if(minutes>1)p.sleepWarn=setTimeout(()=>{if(player===p)toast('Таймер сну: ще хвилина');},(minutes-1)*60000);p.sleepTimer=setTimeout(()=>{if(player!==p)return;p.sleepOn=false;paintSleep(p);p.playWanted=false;p.video.pause();p.sleepSelect.value='0';saveProgress();toast('Таймер сну: відтворення зупинено');},minutes*60000);}
/** The picture and its overlays go full screen (iPhone Safari only knows the video's own full screen). */
function toggleFullscreen(p){const s=p.stage,v=p.video;if(document.fullscreenElement){document.exitFullscreen?.();return;}if(s.requestFullscreen)s.requestFullscreen().catch(()=>v.webkitEnterFullscreen?.());else if(v.webkitEnterFullscreen)v.webkitEnterFullscreen();}
async function fetchPlayback(p,attempt=0){const requestId=p.loadId;const result=await api('/api/stream',{link:p.film.link,voice:p.voice,season:p.season,episode:p.episode,recovery_attempt:attempt,direct:true},{signal:AbortSignal.timeout(25000)});if(player!==p||requestId!==p.loadId)return false;p.local=null;p.qualities=preferDirect(result.qualities);p.qualitySelect.replaceChildren(...result.qualities.map(q=>option(q.label,q.label)));return true;}
async function loadStream(shouldPlay=true){const p=player;if(!p)return;const id=++p.loadId;clearTimeout(p.autoTimer);clearTimeout(p.recoveryTimer);p.recoveryTimer=null;p.recovery=new RecoveryPlan();p.recoverBusy=false;p.retry.hidden=true;note(p,'Підготовка відео…','busy');p.switching=true;p.mediaReady=false;p.video.pause();const old=latestFor(p.film.link,p.season,p.episode);p.resumePosition=old?.pos?old.pos/1000:0;p.watched=old?.watched||0;p.playWanted=shouldPlay;try{loadMarks(p);if(await playLocal(p,id))return;if(!await fetchPlayback(p)||player!==p||id!==p.loadId)return;loadAds(p);const chosen=p.recovery.next(p.qualities,p.quality);if(!chosen)throw Error('Немає доступного потоку');p.quality=chosen.quality;p.qualitySelect.value=p.quality;setSource(p,chosen.url,p.resumePosition,p.playWanted);}catch{if(player===p&&id===p.loadId)recoverPlayer(p);}}
function armRecovery(p){if(player!==p||p.recoveryTimer||p.video.ended)return;const source=p.sourceId;p.recoveryTimer=setTimeout(()=>{p.recoveryTimer=null;if(player!==p||source!==p.sourceId||p.video.ended)return;if(document.hidden||!p.playWanted){armRecovery(p);return;}recoverPlayer(p);},20000);}
async function recoverPlayer(p){if(player!==p||p.recoverBusy)return;p.recoverBusy=true;p.recovery||=new RecoveryPlan();p.recovery.interrupted();clearTimeout(p.recoveryTimer);p.recoveryTimer=null;const id=++p.loadId;p.resumePosition=p.mediaReady&&Number.isFinite(p.video.currentTime)?(p.video.currentTime>0?p.video.currentTime:p.lastPosition??p.resumePosition??0):p.resumePosition||0;saveProgress();p.switching=true;p.mediaReady=false;p.video.pause();note(p,'Шукаємо резервний потік…','busy');p.retry.hidden=true;try{let next=p.recovery.next(p.qualities,p.quality);while(!next&&player===p&&id===p.loadId){if(p.recovery.refresh()){try{if(!await fetchPlayback(p,p.recovery.refreshes))return;}catch{}next=p.recovery.next(p.qualities,p.quality);continue;}const voice=p.recovery.voice(p.film.voices,p.voice);if(!voice)break;p.voice=voice.id;try{if(!await fetchPlayback(p,1))return;}catch{}next=p.recovery.next(p.qualities,p.quality);}if(player!==p||id!==p.loadId)return;if(!next){p.switching=false;const wait=Math.min(60,5*2**Math.min(4,p.autoRetries||0));p.autoRetries=(p.autoRetries||0)+1;note(p,'Потік тимчасово недоступний. Пробуємо знову за '+wait+' с…','error');p.retry.hidden=false;clearTimeout(p.autoTimer);p.autoTimer=setTimeout(()=>{if(player===p&&playerModal.open)loadStream(p.playWanted!==false);},wait*1000);return;}p.quality=next.quality;p.qualitySelect.value=p.quality;p.recovered=true;setSource(p,next.url,p.resumePosition,p.playWanted);}finally{if(player===p&&id===p.loadId)p.recoverBusy=false;}}
/** Ad clips the scanner found in a sponsored voice track; asked only for those voices, once per episode. */
/** Intro and credits of the episode (episode-marks.js), for profiles with «Пропуск реклами й заставок» only: asking also
 *  puts the episode and the next one in the scanner's queue. A downloaded episode brings its own. */
function loadMarks(p){const item=p.season+':'+p.episode;if(p.marksFor===item)return;p.marks=null;p.marksFor=item;if(!p.film.series||!state.profile?.ad_skip)return;
 const local=downloads.supported&&downloads.find(p.film.link,p.season,p.episode);if(local?.marks?.duration){p.marks=local.marks;return;}if(p.offline)return;
 const ask=(season,episode)=>api('/api/marks?'+new URLSearchParams({link:p.film.link,voice:p.voice,season,episode,profile_id:state.profile.id}));
 ask(p.season,p.episode).then(r=>{if(player===p&&p.marksFor===item&&r.duration)p.marks=r;}).catch(()=>{});
 const i=p.episodes.findIndex(e=>e.season===p.season&&e.episode===p.episode),next=p.episodes[i+1];if(next)ask(next.season,next.episode).catch(()=>{});}
/** «Наступна серія» from the credits, bottom right like «Пропустити рекламу». After scanned credits it counts down in
 *  playback time (a pause holds it) and goes on by itself when autoplay is on; over a guessed end it is a button only. */
function nextCard(p){const go=button('Наступна серія ▸',()=>playNext(p),'next-go'),stay=button('Дивитися титри',()=>{p.nextDismissed=true;p.nextBox.hidden=true;},'next-stay');
 p.nextBox=el('div',{class:'next-card',hidden:true},go,stay);p.nextGo=go;return p.nextBox;}
function nextEpisodeOf(p){const i=p.episodes.findIndex(e=>e.season===p.season&&e.episode===p.episode);return i>=0?p.episodes[i+1]||null:null;}
function paintNext(p){
 const v=p.video,box=p.nextBox;if(!box)return;const next=p.film.series&&!p.nextDismissed&&!p.sleepAfterEpisode&&nextEpisodeOf(p),offer=next&&nextOffer(p.marks,v.duration);
 if(!offer||v.currentTime<offer.at||!p.skipAd.hidden){box.hidden=true;return;}
 box.hidden=false;
 if(offer.sure&&management.settings.autoplay){p.nextUntil??=v.currentTime+NEXT_COUNTDOWN;const left=Math.ceil(p.nextUntil-v.currentTime);if(left<=0){playNext(p);return;}p.nextGo.textContent='Наступна серія через '+left;}
 else p.nextGo.textContent='Наступна серія ▸';
}
async function playNext(p){const next=nextEpisodeOf(p);if(!next||player!==p)return;p.nextBox.hidden=true;p.nextDismissed=true;saveProgress();await goToEpisode(p,next.season,next.episode,true);}
function loadAds(p){p.ads=null;if(!state.profile?.ad_skip||!sponsoredVoice(voiceName(p)))return;const at={link:p.film.link,voice:p.voice,season:p.season||'',episode:p.episode||''},id=p.loadId;api('/api/ad-breaks?'+new URLSearchParams(at)).then(r=>{if(player===p&&id===p.loadId)p.ads=r;}).catch(()=>{});}
/** Without premium the source serves a one-minute preview for its top quality: drop it and play the next one down. */
function dropPreview(p){const d=p.video.duration;if(!Number.isFinite(d)||!(p.qualities?.length>1))return false;const saved=latestFor(p.film.link,p.season,p.episode)?.dur||0;if(!isPreview(d*1000,Math.max(saved>=180000?saved:0,runtimeMs(p.film.duration))))return false;p.qualities=p.qualities.filter(q=>q.label!==p.quality);p.qualitySelect.replaceChildren(...p.qualities.map(q=>option(q.label,q.label)));p.resumePosition=Math.max(p.resumePosition||0,p.video.currentTime||0);p.mediaReady=false;recoverPlayer(p);return true;}
/** hls.js only for a stream that needs it (Safari plays HLS itself): one download, 15 s at most, retried after a failure. */
let hlsReady;function loadHls(){if(window.Hls)return Promise.resolve(window.Hls);if(hlsReady)return hlsReady;hlsReady=new Promise((resolve,reject)=>{const script=el('script',{src:'/hls.min.js'}),timer=setTimeout(()=>done(Error('HLS timeout')),15000),done=error=>{clearTimeout(timer);script.onload=script.onerror=null;if(error){script.remove();reject(error);}else resolve(window.Hls);};script.onload=()=>done(window.Hls?null:Error('HLS unavailable'));script.onerror=()=>done(Error('HLS unavailable'));document.head.append(script);}).catch(e=>{hlsReady=null;throw e;});return hlsReady;}

/** The phone's lock screen and Dynamic Island show what plays (Media Session): the poster, the title and the episode, with
 *  ±10 s; without it iOS shows only KIK's icon there (14.11, owner's pick). Cleared when the player closes. */
function nowPlaying(p){const ms=navigator.mediaSession,f=p.film;if(!ms||typeof MediaMetadata!=='function')return;
 try{ms.metadata=new MediaMetadata({title:f.title,artist:f.series?'Сезон '+p.season+' · Серія '+p.episode:'KIK',album:'KIK',artwork:f.poster?[{src:new URL(f.poster,location.href).href}]:[]});}catch{}
 const v=p.video,on=(action,run)=>{try{ms.setActionHandler(action,run&&(d=>{if(player===p)run(d||{});}));}catch{}};
 on('seekbackward',d=>{v.currentTime=Math.max(0,v.currentTime-(d.seekOffset||10));});
 on('seekforward',d=>{v.currentTime=Math.min(Number.isFinite(v.duration)?v.duration:Infinity,v.currentTime+(d.seekOffset||10));});
 on('seekto',d=>{if(Number.isFinite(d.seekTime))v.currentTime=d.seekTime;});}
function quietNowPlaying(){const ms=navigator.mediaSession;if(!ms)return;try{ms.metadata=null;}catch{}for(const a of ['seekbackward','seekforward','seekto'])try{ms.setActionHandler(a,null);}catch{}}
function setSource(p,url,position,shouldPlay){
 position=Math.max(position,studioIntro(voiceName(p)));
 clearTimeout(p.recoveryTimer);clearTimeout(p.restoreTimer);p.recoveryTimer=null;const source=p.sourceId=(p.sourceId||0)+1;const item=p.season+':'+p.episode;if(p.marksFor!==item){p.marks=null;p.marksFor=null;}if(p.nextItem!==item){p.nextItem=item;p.nextUntil=null;p.nextDismissed=false;p.logoDone=false;}p.resumePosition=position;p.playWanted=shouldPlay;p.switching=true;p.mediaReady=false;p.recovery||=new RecoveryPlan();if(!p.recovery.tried.has(url)){p.recovery.tried.add(url);p.recovery.switches++;}if(p.hls){p.hls.destroy();p.hls=null;}const video=p.video;nowPlaying(p);
 const ready=()=>{if(player!==p||source!==p.sourceId)return;clearTimeout(p.restoreTimer);p.mediaReady=true;p.switching=false;p.retry.hidden=true;p.autoRetries=0;note(p,'Прогрес зберігається для профілю «'+state.profile.name+'»');if(p.recovered){p.recovered=false;toast('Потік відновлено · позицію й мову збережено');}if(p.playWanted&&!p.introPlayed){p.introPlayed=true;playIntro();}if(p.playWanted)(intro||Promise.resolve()).then(()=>{if(player!==p||source!==p.sourceId||!p.playWanted)return;video.play().catch(()=>{p.playWanted=false;note(p,'Натисніть ▶, щоб почати перегляд','info');});});else video.pause();};
 video.onloadedmetadata=()=>{if(player!==p||source!==p.sourceId||dropPreview(p))return;video.playbackRate=Number(p.qualitySelect.closest('.player-tools').querySelector('select[aria-label="Швидкість"]').value);const restore=position>0&&position<video.duration-5;if(!restore){ready();return;}note(p,'Відновлюємо позицію…','busy');video.addEventListener('seeked',()=>{if(Math.abs(video.currentTime-position)<2)ready();},{once:true});video.currentTime=position;p.restoreTimer=setTimeout(()=>{if(player!==p||source!==p.sourceId)return;if(Math.abs(video.currentTime-position)<2)ready();else recoverPlayer(p);},6000);};
 if(/\.m3u8(?:[?#]|$)/i.test(url)&&!video.canPlayType('application/vnd.apple.mpegurl')){
  loadHls().then(Hls=>{if(player!==p||source!==p.sourceId)return;if(!Hls.isSupported())throw Error('Цей браузер не підтримує потік.');const hls=p.hls=new Hls({maxBufferLength:30});hls.loadSource(url);hls.attachMedia(video);hls.on(Hls.Events.ERROR,(_,data)=>{if(data.fatal&&source===p.sourceId)recoverPlayer(p);});}).catch(()=>{if(player===p&&source===p.sourceId){clearTimeout(p.recoveryTimer);note(p,'Не вдалося завантажити плеєр. Спробуйте ще раз.','error');p.retry.hidden=false;}});
 }else{video.src=url;video.load();}armRecovery(p);
}
function progressRecord(){const p=player;if(!p?.video||!p.mediaReady||!Number.isFinite(p.video.duration)||p.video.currentTime<.1)return null;return {kind:'progress',key:keyOf(p.film.link)+'|'+(p.season||'0')+':'+(p.episode||'0'),data:{link:p.film.link,title:p.film.title,poster:p.film.poster,season:p.season,episode:p.episode,voice:p.voice,voiceName:p.film.voices.find(v=>v.id===p.voice)?.name,pos:Math.floor(p.video.currentTime*1000),dur:Math.floor(p.video.duration*1000),watched:Math.floor(p.watched),observed:p.watched>30000},updated_at:Date.now()+state.clockOffset};}
function saveProgress(){const r=progressRecord();if(!r)return;change(r.kind,r.key,r.data);player.lastSave=performance.now();}
function disposePlayer(){const p=player;if(!p)return;if(p.onSkipKey)document.removeEventListener('keydown',p.onSkipKey);if(p.onFull)document.removeEventListener('fullscreenchange',p.onFull);if(document.fullscreenElement&&p.stage?.contains(document.fullscreenElement))document.exitFullscreen?.().catch(()=>{});saveProgress();clearTimeout(p.recoveryTimer);clearTimeout(p.restoreTimer);clearTimeout(p.autoTimer);clearTimeout(p.rateHide);clearTimeout(p.sleepTimer);clearTimeout(p.sleepWarn);p.loadId++;p.sourceId=(p.sourceId||0)+1;p.episodeRequest=(p.episodeRequest||0)+1;player=null;quietNowPlaying();const v=p.video;v.onpause=v.onplay=v.ontimeupdate=v.onended=v.onerror=v.onwaiting=v.onstalled=v.onloadedmetadata=null;v.pause();p.hls?.destroy();v.removeAttribute('src');v.load();if(p.blobUrl)URL.revokeObjectURL(p.blobUrl);smartTidy();refreshPage();}
window.addEventListener('pagehide',()=>{const r=progressRecord();if(r){state.records.set(recId(r),r);state.pending.set(recId(r),r);store();}if(state.user&&state.profile&&state.pending.size)navigator.sendBeacon('/api/sync',beaconPayload(state.profile.id,[...state.pending.values()],r,state.since));});document.addEventListener('visibilitychange',()=>{if(document.hidden)saveProgress();else sync();});setInterval(()=>{if(!document.hidden)sync();},30000);window.addEventListener('online',sync);
// Initial account loading must not replace a settings form the user has already started editing.
/** Settles once the account is known (or 1.5 s have passed): the home's first paint waits for it. */
let identityReady=Promise.resolve();
/* Downloads: films and episodes saved on this PC, tablet or phone for watching without the internet (downloads.js keeps
   the list and the files, download-worker.js writes them, sw.js opens the site offline). The TV app has none. */
const DL_ICON={
 down:'<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4v11"/><path d="m7 10 5 5 5-5"/><path d="M5 20h14"/></svg>',
 done:'<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 20h14"/><path d="m7 10.5 3.5 3.5L17 7"/></svg>',
 trash:'<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M4.5 7h15"/><path d="M9.5 7V4.5h5V7"/><path d="m6.5 7 1 12.5h9l1-12.5"/></svg>',
 pause:'<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M9 6v12M15 6v12"/></svg>',
 play:'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 5.5v13l10.5-6.5z" fill="currentColor"/></svg>',
 retry:'<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3"/><path d="M19.5 4.5v4.5H15"/></svg>',
 close:'<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="m6.5 6.5 11 11M17.5 6.5l-11 11"/></svg>'};
function iconButton(svg,label,action,classes='dl-icon'){const b=button([],action,classes);b.innerHTML=svg;b.setAttribute('aria-label',label);b.title=label;return b;}
/** «1 серія», «3 серії», «5 серій». */
function plural(n,one,few,many){const a=n%10,b=n%100;return n+' '+(a===1&&b!==11?one:a>=2&&a<=4&&(b<12||b>14)?few:many);}
const offline=()=>!!state.offline||globalThis.navigator?.onLine===false;
const touchDevice=()=>matchMedia('(pointer:coarse)').matches;
function downloadQuality(){return downloads.quality()||defaultDownloadQuality(touchDevice());}
const dlPercent=x=>x.total?Math.min(100,Math.floor(x.bytes/x.total*100)):0;
function downloadState(x){
 if(x.status==='done')return [x.quality,formatBytes(x.total||x.bytes)].filter(Boolean).join(' · ');
 if(x.status==='active')return x.total?dlPercent(x)+'% · '+formatBytes(x.bytes)+' з '+formatBytes(x.total):'Починаємо…';
 if(x.status==='queued')return x.retryAt>Date.now()?'Зв’язок перервався, зараз продовжимо…':x.bytes?'У черзі · '+dlPercent(x)+'%':'У черзі';
 if(x.status==='waiting')return 'Чекає на інтернет'+(x.total?' · '+dlPercent(x)+'%':'');
 if(x.status==='paused')return 'Пауза'+(x.total?' · '+dlPercent(x)+'%':'');
 return x.error||'Не вдалося завантажити';
}
const episodeLabel=x=>'С'+x.season+' · Е'+x.episode+(x.episodeTitle&&!/^(Серія|Серия)\s*\d+$/.test(x.episodeTitle)?' · '+x.episodeTitle:'');
function watchedFor(x){const r=latestFor(x.link,x.season||'',x.episode||'');return r?.dur>0?r.pos/r.dur:0;}
/** Minutes left of a saved file that was started and not finished (it plays on from there), else 0. */
function minutesLeft(x){const r=x.status==='done'?latestFor(x.link,x.season||'',x.episode||''):null,w=r?.dur>0?r.pos/r.dur:0;return w>.02&&w<.93?Math.max(1,Math.ceil((r.dur-r.pos)/60000)):0;}
/** The poster stays with the title while any of its files is on the device (the service worker shows it offline). */
function tellWorker(type,url){const sw=globalThis.navigator?.serviceWorker;if(!url||!sw)return;sw.ready.then(r=>r.active?.postMessage({type,url})).catch(()=>{});}

let downloadsTimer=0,detailDownload=null,wakeLock=null,wakePending=false,reloadWhenOnline=false;
function downloadsChanged(){paintDownloadNav();paintOffline();keepOffline();detailDownload?.();clearTimeout(downloadsTimer);downloadsTimer=setTimeout(()=>{if(state.tab==='downloads')renderDownloads();},120);}
function paintDownload(x){if(!x)return;paintDownloadNav();for(const n of document.querySelectorAll('[data-download="'+x.id+'"]'))fillDownloadRow(n,x);detailDownload?.();}
/** The menu item shows how far the running download is; the tab title too, for a computer where the tab sits in the background. */
function paintDownloadNav(){
 const b=$('[data-tab=downloads]');if(b)b.hidden=!downloads.supported||!state.user;if(!downloads.supported||!b)return;
 const s=downloads.summary();let badge=b.querySelector('.nav-badge');
 if(s.count&&state.user){if(!badge)b.append(badge=el('span',{class:'nav-badge'}));badge.textContent=s.active?.total?dlPercent(s.active)+'%':String(s.count);}else badge?.remove();
 document.title=s.active?.total?'↓ '+dlPercent(s.active)+'% · '+baseTitle:baseTitle;
 holdScreen(!!s.active);
}
/** A phone stops a page whose screen went dark: while a download runs the screen stays on (only on touch devices, only while KIK is in front). */
async function holdScreen(on){
 const lock=globalThis.navigator?.wakeLock;if(!lock||!touchDevice())return;
 if(on&&!wakeLock&&!wakePending&&!document.hidden){wakePending=true;try{wakeLock=await lock.request('screen');wakeLock.addEventListener('release',()=>{wakeLock=null;});}catch{}finally{wakePending=false;}}
 else if(!on&&wakeLock){const l=wakeLock;wakeLock=null;l.release().catch(()=>{});}
}
function paintOffline(){offlineBar.hidden=!offline()||!state.user;offlineBar.querySelector('button').hidden=!downloads.supported||state.tab==='downloads';}
const offlineBar=el('div',{class:'offline-bar',role:'status',hidden:true},el('span',{},'Немає інтернету'),button('Завантаження',()=>navigate('downloads'),'text-button'));
document.body.append(offlineBar);

/** A film from what is on this device, for the player when KIK's service cannot be reached. */
function offlineFilm(link){
 const xs=downloads.forTitle(link).filter(x=>x.status==='done');if(!xs.length)return null;const x=xs[0];
 const voices=[...new Map(xs.map(d=>[d.voice,{id:d.voice,name:d.voiceName||'Озвучення',...(d.voiceLang?{lang:d.voiceLang}:{})}])).values()];
 return {link:x.link,title:x.title,poster:x.poster,series:x.series,year:x.year,duration:x.duration,genres:[],related:[],voices,episodes:x.series?mergeEpisodes(xs.map(d=>({season:d.season,episode:d.episode,title:d.episodeTitle}))):[],offline:true};
}
async function filmForPlay(item){try{return await getFilm(item);}catch(e){const f=offlineFilm(item.link);if(f&&(e.offline||e.slow||offline()))return f;throw e;}}
/** The saved copy of what the player is about to play: no link, no waiting, no traffic. Online only in the voice the player
 *  chose (another voice streams); offline whatever is there. False when there is none and the stream should load. */
async function playLocal(p,id){
 if(!downloads.supported)return false;
 const local=downloads.find(p.film.link,p.film.series?p.season:'',p.film.series?p.episode:'',p.offline?null:p.voice);if(!local)return false;
 let file;try{file=await downloads.file(local.id);}catch{return false;}
 if(player!==p||id!==p.loadId)return true;
 if(p.blobUrl)URL.revokeObjectURL(p.blobUrl);p.blobUrl=URL.createObjectURL(file);
 const label=local.quality||'Файл';p.local=local;if(local.marks?.duration)p.marks=local.marks;p.voice=local.voice;p.qualities=[{label,url:p.blobUrl}];p.quality=label;
 p.qualitySelect.replaceChildren(option(label,label+' · на пристрої'));p.qualitySelect.value=label;
 p.ads=state.profile?.ad_skip&&sponsoredVoice(local.voiceName)?local.ads||null:null;
 setSource(p,p.blobUrl,p.resumePosition,p.playWanted);return true;
}
function playDownload(x){play({link:x.link,title:x.title,poster:x.poster,playEpisode:x.series?{season:x.season,episode:x.episode}:null});}

function downloadEntry(f,voiceId,e){
 const v=f.voices.find(x=>x.id===voiceId);
 return {link:f.link,title:f.title,poster:artwork.get(keyOf(f.link))?.poster||f.poster||'',series:!!f.series,season:e?.season||'',episode:e?.episode||'',episodeTitle:e?.title||'',
  voice:voiceId,voiceName:v?.name||'',voiceLang:v?langOf(v):'',wanted:downloadQuality(),runtime:runtimeMs(f.duration),duration:f.duration||'',year:f.year||'',sponsored:sponsoredVoice(v?.name)};
}
/** ⤓ in the title window: a film starts at once in the voice it would play in; a series opens the episode list. */
function downloadButton(f,item){
 if(f.external||!downloads.supported)return null;
 const b=iconButton(DL_ICON.down,'Завантажити',()=>downloadTitle(f,item),'round-action download-action');
 const paint=()=>{
  if(!modal.open||!b.isConnected){if(detailDownload===paint)detailDownload=null;return;}
  const xs=downloads.forTitle(f.link),done=xs.filter(x=>x.status==='done'),busy=xs.find(x=>x.status==='active')||xs.find(x=>x.status==='queued'||x.status==='waiting'),failed=xs.find(x=>x.status==='error');
  const label=busy?'Завантажується · '+dlPercent(busy)+'%':failed&&!f.series?'Не вдалося завантажити: спробувати ще':done.length?(f.series?'Завантажено: '+plural(done.length,'серія','серії','серій'):'Завантажено на цей пристрій'):f.series?'Завантажити серії':'Завантажити для перегляду без інтернету';
  b.classList.toggle('busy',!!busy);b.classList.toggle('rated',!busy&&!!done.length);b.style.setProperty('--p',busy?dlPercent(busy):0);
  b.innerHTML=busy?'<span class="download-ring"></span><span class="download-pct">'+dlPercent(busy)+'</span>':done.length?DL_ICON.done:DL_ICON.down;
  if(f.series&&done.length&&!busy)b.append(el('span',{class:'download-count'},String(done.length)));
  b.setAttribute('aria-label',label);b.title=label;
 };
 detailDownload=paint;queueMicrotask(paint);return b;
}
function downloadTitle(f,item){
 if(!state.user){auth();return;}
 if(f.series){episodePicker(f,item);return;}
 const old=downloads.forTitle(f.link)[0];
 if(old&&old.status!=='error'&&old.status!=='paused'){modal.close();navigate('downloads');return;}
 const voice=bestVoice(f.voices,chosenVoice(f.link)||latestFor(f.link)?.voice);if(!voice){toast('Немає доступного озвучення');return;}
 const entry=downloadEntry(f,voice.id);if(downloads.add([entry])){tellWorker('keep-poster',entry.poster);toast('Завантажуємо «'+f.title+'» · '+downloadQuality()+'. Прогрес — у «Завантаженнях».');}
}
/** Which episodes to save: a season at once, the next three from where the viewer stopped, or one by one. */
async function episodePicker(f,item){
 showModal(el('h2',{},'Завантажити серії'),el('p',{},'Шукаємо серії…'));const generation=modalGeneration;
 const remembered=chosenVoice(f.link)||latestFor(f.link)?.voice,voice=bestVoice(f.voices,remembered);
 if(!voice){modalContent.replaceChildren(el('h2',{},'Завантажити серії'),el('p',{},'Немає доступного озвучення.'));return;}
 const p={film:f,userVoice:remembered||voice.id,coverage:new Map(),episodes:[]};
 try{const own=(await api('/api/episodes',{link:f.link,voice:voice.id})).episodes||[];p.coverage.set(voice.id,own);p.episodes=mergeEpisodes(f.episodes,own);}
 catch(e){if(modal.open&&generation===modalGeneration)modalContent.replaceChildren(oops({error:e,retry:()=>episodePicker(f,item),title:f.title,key:'download:'+keyOf(f.link)}));return;}
 if(!modal.open||generation!==modalGeneration)return;
 const back=button('← '+f.title,()=>openDetails(item),'text-button dl-back');
 if(!p.episodes.length){modalContent.replaceChildren(back,el('h2',{},'Завантажити серії'),el('p',{},'Серії наразі недоступні.'));return;}
 const k=e=>e.season+':'+e.episode,saved=e=>downloads.forTitle(f.link).find(x=>x.season===e.season&&x.episode===e.episode&&x.status!=='error'),chosen=new Set();
 const seasons=[...new Set(p.episodes.map(e=>e.season))],last=latestFor(f.link);
 const season=el('select',{'aria-label':'Сезон'},seasons.map(s=>option(s,'Сезон '+s)));season.value=seasons.includes(last?.season)?last.season:seasons[0];
 const quality=el('select',{'aria-label':'Якість'},DOWNLOAD_QUALITIES.map(q=>option(q,q)));quality.value=downloadQuality();quality.onchange=()=>downloads.setQuality(quality.value);
 const list=el('div',{class:'dl-episodes',role:'group','aria-label':'Серії'}),free=el('span',{class:'dl-meta'}),submit=button('Виберіть серії',()=>start(),'pill red');
 downloads.usage().then(u=>{if(u.free!=null)free.textContent='Вільно для KIK ≈ '+formatBytes(u.free);});
 function paintList(){list.replaceChildren(...p.episodes.filter(e=>e.season===season.value).map(e=>{const have=saved(e),box=el('input',{type:'checkbox'});box.checked=!!have||chosen.has(k(e));box.disabled=!!have;box.onchange=()=>{if(box.checked)chosen.add(k(e));else chosen.delete(k(e));paintSubmit();};
  return el('label',{class:'dl-episode'+(have?' have':'')},box,el('span',{class:'dl-episode-name'},e.title||'Серія '+e.episode),el('span',{class:'dl-meta'},have?downloadState(have):''));}));paintSubmit();}
 function paintSubmit(){const n=chosen.size;submit.disabled=!n;submit.textContent=n?'Завантажити '+plural(n,'серію','серії','серій'):'Виберіть серії';}
 const open=list=>list.filter(e=>!saved(e));
 const wholeSeason=button('Увесь сезон',()=>{for(const e of open(p.episodes.filter(e=>e.season===season.value)))chosen.add(k(e));paintList();},'pill small');
 // From the episode after the last one finished (or the one stopped in), as «Продовжити перегляд» would play them.
 const at=last?p.episodes.findIndex(e=>e.season===last.season&&e.episode===last.episode):-1,from=at<0?0:last.dur>0&&last.pos/last.dur>.93?at+1:at;
 const nextThree=button(at<0?'Перші 3':'Наступні 3',()=>{const picked=open(p.episodes.slice(from)).slice(0,3);for(const e of picked)chosen.add(k(e));if(picked[0])season.value=picked[0].season;paintList();},'pill small');
 const clear=button('Очистити',()=>{chosen.clear();paintList();},'text-button');
 async function start(){
  submit.disabled=true;submit.textContent='Готуємо…';const entries=[];let missing=0;
  for(const key of chosen){const e=p.episodes.find(x=>k(x)===key);let id=null;try{id=await voiceFor(p,e.season,e.episode);}catch{}if(!modal.open||generation!==modalGeneration)return;if(id)entries.push(downloadEntry(f,id,e));else missing++;}
  const added=downloads.add(entries);if(added)tellWorker('keep-poster',entries[0].poster);
  toast(added?'Додано до завантажень: '+plural(added,'серія','серії','серій')+(missing?' · без озвучення: '+missing:''):'Ці серії вже завантажуються');
  openDetails(item);
 }
 season.onchange=paintList;
 modalContent.replaceChildren(el('div',{class:'dl-picker'},back,el('h2',{},'Завантажити серії'),
  el('div',{class:'dl-picker-tools'},seasons.length>1?selectField('Сезон',season):null,selectField('Якість',quality)),
  el('div',{class:'dl-quick'},wholeSeason,nextThree,clear),list,el('div',{class:'dl-picker-foot'},free,submit)));
 paintList();
}

function fillDownloadRow(node,x){
 const bar=node.querySelector('.dl-bar i');if(bar)bar.style.width=(x.status==='done'?Math.round(watchedFor(x)*100):dlPercent(x))+'%';
 const s=node.querySelector('.dl-state'),left=minutesLeft(x);if(s)s.textContent=downloadState(x)+(left?' · ще '+left+' хв':'');
}
function downloadRow(x,film=false){
 const watched=x.status==='done'?watchedFor(x):0;
 const actions=x.status==='done'?[film?null:iconButton(DL_ICON.play,'Дивитися '+episodeLabel(x),()=>playDownload(x)),film?null:iconButton(DL_ICON.trash,'Видалити '+episodeLabel(x),()=>removeOne(x))]
  :[x.status==='paused'||x.status==='error'?iconButton(x.status==='error'?DL_ICON.retry:DL_ICON.play,x.status==='error'?'Спробувати ще':'Продовжити',()=>downloads.resume(x.id)):iconButton(DL_ICON.pause,'Пауза',()=>downloads.pause(x.id)),
   iconButton(DL_ICON.close,'Скасувати завантаження',()=>removeOne(x))];
 const node=el(film?'div':'li',{class:'dl-row '+x.status+(watched>.93?' watched':''),'data-download':x.id},
  film?null:el('span',{class:'dl-row-name'},episodeLabel(x)),
  el('div',{class:'dl-progress'},el('div',{class:'dl-bar'+(x.status==='done'&&!watched?' empty':'')},el('i')),el('span',{class:'dl-state'})),
  el('div',{class:'dl-row-actions'},actions));
 fillDownloadRow(node,x);return node;
}
/** One title on the «Завантаження» page. One file (a film, or a series with one episode saved) has one set of buttons:
 *  play and delete, with how far it was watched. Several episodes: the header plays the next one, every episode has its
 *  own play and delete, and «Видалити все» sits quietly under the list (owner, 13.11: one episode showed two plays and
 *  two bins). On a phone the episodes take the card's whole width, not the strip beside the poster. */
function downloadGroup(xs){
 const x=xs[0],eps=x.series?xs.slice().sort((a,b)=>Number(a.season)-Number(b.season)||Number(a.episode)-Number(b.episode)):xs,done=eps.filter(d=>d.status==='done');
 const size=xs.reduce((s,d)=>s+(d.status==='done'?d.total:d.bytes||0),0),removeAll=()=>removeDownloads(xs,'«'+x.title+'»');
 const stub=()=>el('div',{class:'dl-poster stub','aria-hidden':'true'},(x.title||'K').trim()[0]);
 const poster=x.poster?el('img',{class:'dl-poster',src:x.poster,alt:'',loading:'lazy',decoding:'async',referrerpolicy:'no-referrer',onError:e=>e.target.replaceWith(stub())}):stub();
 let meta,actions=[],watchedBar=null,rows=null,foot=null;
 if(eps.length===1){
  const one=eps[0],w=one.status==='done'?watchedFor(one):0,left=minutesLeft(one),started=left>0;
  meta=[x.series?episodeLabel(one):x.year,one.status==='done'?downloadState(one):null,w>.93?'переглянуто':started?'ще '+left+' хв':null];
  if(one.status==='done'){
   actions=[button(started?'▶ Продовжити':'▶ Дивитися',()=>playDownload(one),'pill primary small'),iconButton(DL_ICON.trash,'Видалити «'+x.title+'» з пристрою',removeAll,'dl-icon dl-remove')];
   if(started){const fill=el('i');fill.style.width=Math.round(w*100)+'%';watchedBar=el('div',{class:'dl-bar dl-watched','aria-hidden':'true'},fill);}
  }else rows=el('div',{class:'dl-rows'},downloadRow(one,true));
 }else{
  const next=done.find(d=>watchedFor(d)<=.93)||done[0],coming=eps.length-done.length;
  meta=[done.length?plural(done.length,'серія','серії','серій')+' на пристрої':null,coming?'завантаження: '+plural(coming,'серія','серії','серій'):null,formatBytes(size)];
  if(next)actions=[button((minutesLeft(next)?'▶ Продовжити С':'▶ С')+next.season+' · Е'+next.episode,()=>playDownload(next),'pill primary small')];
  rows=el('ul',{class:'dl-rows'},eps.map(d=>downloadRow(d)));
  foot=button([],removeAll,'text-button dl-remove-all');foot.innerHTML=DL_ICON.trash;foot.append('Видалити все · '+formatBytes(size));
 }
 const head=el('div',{class:'dl-title-head'},el('div',{class:'dl-title-copy'},el('h2',{},x.title),el('p',{class:'dl-meta'},meta.filter(Boolean).join(' · ')),watchedBar),
  actions.length?el('div',{class:'dl-actions'},actions):null);
 return el('article',{class:'dl-title'},poster,head,rows,foot?el('div',{class:'dl-title-foot'},foot):null);
}
async function removeOne(x){await downloads.remove(x.id);if(!downloads.forTitle(x.link).length)tellWorker('drop-poster',x.poster);}
/** Smart downloads (on unless switched off on the «Завантаження» page, per device): a film or episode watched to the end,
 *  here or on the TV, leaves the device by itself; a series gets its next episode instead, as on Netflix. The file the
 *  player is on stays until it moves on. */
const SMART_KEY='kik-smart-downloads',smartDownloads=()=>storage.getItem(SMART_KEY)!=='0';
function progressOf(x){const r=data('progress').filter(r=>keyOf(r.data.link)===x.key&&(r.data.season||'0')===(x.season||'0')&&(r.data.episode||'0')===(x.episode||'0')).sort((a,b)=>b.updated_at-a.updated_at)[0];return r?{pos:r.data.pos,dur:r.data.dur,at:r.updated_at}:null;}
let tidying=false;
async function smartTidy(){
 if(tidying||!downloads.supported||!state.user||!state.profile||!smartDownloads())return;
 const gone=watchedCopies(downloads.items(),progressOf,player?.local?.id);if(!gone.length)return;
 tidying=true;let next=0;
 try{
  for(const x of gone){
   if(x.series&&!offline())try{const eps=(await api('/api/episodes',{link:x.link,voice:x.voice})).episodes,e=nextToSave(eps,downloads.forTitle(x.link),x);
    if(e&&downloads.add([{link:x.link,title:x.title,poster:x.poster,series:true,season:e.season,episode:e.episode,episodeTitle:e.title||'',voice:x.voice,voiceName:x.voiceName,voiceLang:x.voiceLang,wanted:downloadQuality(),runtime:x.runtime,duration:x.duration,year:x.year,sponsored:x.sponsored}])){next++;tellWorker('keep-poster',x.poster);}}catch{}
   await removeOne(x);
  }
  toast((gone.length===1?'Переглянуте видалено з пристрою: «'+gone[0].title+'»'+(gone[0].series?' · '+episodeLabel(gone[0]).split(' · ').slice(0,2).join(' · '):''):'Переглянуте видалено з пристрою: '+plural(gone.length,'файл','файли','файлів'))+(next?' · качаємо '+(next===1?'наступну серію':plural(next,'наступну серію','наступні серії','наступних серій')):''));
 }finally{tidying=false;}
}
/** Removing a whole title or every watched file asks first: it takes as long again to download. */
function removeDownloads(xs,what){
 const size=xs.reduce((s,d)=>s+(d.status==='done'?d.total:d.bytes||0),0);
 showModal(el('h2',{},'Видалити з цього пристрою?'),el('p',{},what+(size?' · звільниться '+formatBytes(size):'')+'. На інших пристроях і в історії нічого не зміниться.'),
  el('div',{class:'actions'},button('Видалити',async()=>{modal.close();const links=new Map(xs.map(d=>[d.link,d.poster]));await downloads.removeMany(xs.map(d=>d.id));for(const [link,poster] of links)if(!downloads.forTitle(link).length)tellWorker('drop-poster',poster);toast('Видалено з пристрою');},'pill red'),button('Скасувати',()=>modal.close(),'pill')));
}
function renderDownloads(){
 artObserver?.disconnect();
 const head=el('h1',{},'Завантаження');
 if(!downloads.supported){main.replaceChildren(el('section',{class:'grid-page dl-page'},head,downloads.settled?el('div',{class:'grid-empty'},'Цей браузер не вміє зберігати відео для перегляду без інтернету. Відкрийте KIK у Chrome, Edge, Firefox або Safari.'):null));return;}
 const items=downloads.items(),groups=new Map();
 for(const x of items.slice().sort((a,b)=>(b.added||0)-(a.added||0))){if(!groups.has(x.key))groups.set(x.key,[]);groups.get(x.key).push(x);}
 const quality=el('select',{'aria-label':'Якість нових завантажень'},DOWNLOAD_QUALITIES.map(q=>option(q,q)));quality.value=downloadQuality();quality.onchange=()=>{downloads.setQuality(quality.value);toast('Нові завантаження: '+quality.value);};
 const usage=el('p',{class:'dl-meta dl-usage'});downloads.usage().then(u=>{usage.textContent=(u.used?'Зайнято '+formatBytes(u.used):'Поки нічого не збережено')+(u.free!=null?' · вільно ще ≈ '+formatBytes(u.free):'');});
 const watched=items.filter(x=>x.status==='done'&&watchedFor(x)>.93);
 const ios=/iP(hone|ad|od)/.test(globalThis.navigator?.userAgent||'')||globalThis.navigator?.platform==='MacIntel'&&globalThis.navigator?.maxTouchPoints>1,installed=matchMedia('(display-mode: standalone)').matches||globalThis.navigator?.standalone===true;
 const running=items.some(x=>['active','queued','waiting'].includes(x.status));
 const smartBox=el('input',{type:'checkbox','aria-label':'Розумні завантаження'});smartBox.checked=smartDownloads();
 smartBox.onchange=()=>{storage.setItem(SMART_KEY,smartBox.checked?'1':'0');toast(smartBox.checked?'Переглянуте видалятиметься само':'Переглянуте лишатиметься на пристрої');if(smartBox.checked)smartTidy();};
 const smart=el('label',{class:'dl-smart'},smartBox,el('span',{},el('b',{},'Розумні завантаження'),el('small',{},'Переглянуте видаляється саме, а в серіалі натомість качається наступна серія.')));
 const notes=[offline()?'Немає інтернету. Завантажене дивіться як завжди: прогрес збережеться тут і синхронізується, щойно з’явиться мережа.':null,
  ios&&!installed?'На iPhone та iPad додайте KIK на початковий екран (Поділитися → «На початковий екран») і відкривайте звідти: так Safari не зітре завантаження.':null,
  running&&touchDevice()?'Поки йде завантаження, тримайте KIK відкритим: у фоні телефон його призупиняє. Відкриєте знову — продовжиться з того ж місця.':null].filter(Boolean).map(t=>el('p',{class:'dl-note'},t));
 const body=groups.size?el('div',{class:'dl-list'},[...groups.values()].map(downloadGroup))
  :el('div',{class:'grid-empty'},'Тут будуть фільми й серії для перегляду без інтернету: у дорозі, в літаку, на дачі. Відкрийте фільм чи серіал і натисніть ',el('b',{},'⤓'),'.');
 main.replaceChildren(el('section',{class:'grid-page dl-page'},el('div',{class:'page-head dl-head'},el('div',{},head,usage),
  el('div',{class:'dl-head-tools'},selectField('Якість нових',quality),watched.length?button('Видалити переглянуті',()=>removeDownloads(watched,'Переглянуті: '+plural(watched.length,'файл','файли','файлів')),'pill small'):null)),smart,notes,body));
 paintOffline();
}
window.addEventListener('offline',()=>{paintOffline();if(state.tab==='downloads')renderDownloads();});
// Opened without the internet: once it is back, KIK loads properly (after the film, if one is playing).
window.addEventListener('online',()=>{paintOffline();if(!state.offline)return;if(playerModal.open)reloadWhenOnline=true;else location.reload();});
document.addEventListener('visibilitychange',()=>{if(!document.hidden)paintDownloadNav();});
/** The account known last time, so KIK opens offline as the same person. No e-mail is kept. */
function rememberIdentity(me){if(me.user)storage.setItem('kik-me',JSON.stringify({user:{id:me.user.id,role:me.user.role},profiles:me.profiles}));else storage.removeItem('kik-me');}
function offlineIdentity(e){
 if(!e?.offline)return false;let kept=null;try{kept=JSON.parse(storage.getItem('kik-me')||'null');}catch{}
 if(!kept?.user?.id||!Array.isArray(kept.profiles))return false;
 state.offline=true;state.user=kept.user;state.profiles=kept.profiles;state.profile=state.profiles.find(p=>p.id===storage.getItem('kik-profile:'+state.user.id))||state.profiles[0];restore();state.ready=true;return true;
}
/** The offline copy of the site (sw.js) is only for a device that keeps downloads. Everywhere else no service worker
 *  stands between the page and its posters (on an iPhone every picture would pass through it), and one left from before
 *  goes with its caches. */
let offlineCopy=null;
function keepOffline(){
 const sw=globalThis.navigator?.serviceWorker;if(!sw||!(location.protocol==='https:'||['localhost','127.0.0.1'].includes(location.hostname)))return;
 const want=downloads.supported?downloads.any():(()=>{try{return JSON.parse(storage.getItem('kik-downloads')||'[]').length>0;}catch{return false;}})();
 if(offlineCopy===want)return;offlineCopy=want;
 if(want){sw.register('/sw.js').catch(()=>{});return;}
 sw.getRegistrations().then(list=>{for(const r of list)r.unregister();}).catch(()=>{});
 globalThis.caches?.keys().then(keys=>{for(const k of keys)if(/^kik-(shell|posters)/.test(k))caches.delete(k);}).catch(()=>{});
}
function registerOffline(){const go=()=>keepOffline();if(document.readyState==='complete')go();else window.addEventListener('load',go,{once:true});}
registerOffline();
/**
 * Holidays on the site (holidays.js), once the owner's switches are known: on the day the header mark, the greeting over
 * the hero, the poster ring and one thin decoration (holiday-mark.js); in the days before it a shelf of films for it,
 * after «Продовжити перегляд». «Свята» switched off in the admin panel leaves KIK as it is.
 */
function holidayDress(flags){const plan=holidayPlan(flags);if(!plan)return;
 if(plan.shelf)api('/api/holiday-films?id='+encodeURIComponent(plan.shelf)).then(r=>{holidayFilms={title:r.title||plan.shelfTitle,items:Array.isArray(r.items)?r.items:[]};if(holidayFilms.items.length&&state.tab==='home')backgroundRender();}).catch(()=>{});
 if(!plan.id)return;festive=plan;dressUp(plan);
 const eyebrow=main.querySelector('.hero:not(.skeleton) .hero-copy .eyebrow');if(eyebrow){eyebrow.className='eyebrow holiday-greeting';eyebrow.textContent=plan.greeting;}
 // New Year: the fireworks at midnight, also for a page left open on 31 December.
 if(plan.id==='ny'){const now=new Date();if(now.getMonth()===11)setTimeout(maybeFireworks,new Date(now.getFullYear()+1,0,1,0,0,2)-now);maybeFireworks();}}
/** «Топ-10 цього тижня» (/api/shelf, shelves.mjs; the TV has the same row): what the world watches this week, found on the
 *  catalogue, in its order. Asked once a page load; it comes after «Продовжити перегляд» and keeps its ten, even a title
 *  another row shows too. */
let topTen=null,topTenAsked=false;
function topTenRow(){if(!topTenAsked){topTenAsked=true;api('/api/shelf?id=trending').then(r=>{topTen=Array.isArray(r.items)?r.items:[];if(topTen.length&&state.tab==='home')backgroundRender();}).catch(()=>{});}
 const items=allowed(topTen||[]).slice(0,10);return items.length?[{title:'Топ-10 цього тижня',items,subtitle:'Що дивляться у світі цього тижня',rank:true}]:[];}
function holidayRow(){return holidayFilms?.items.length?[{title:holidayFilms.title,items:allowed(holidayFilms.items).filter(i=>liked(i.link)>=0),subtitle:'Добірка KIK до свята'}]:[];}
/** New Year's fireworks over the home banner: once, in the first ten minutes of 1 January, while the home is on screen. */
function maybeFireworks(){if(festive?.id!=='ny'||state.tab!=='home'||document.hidden||modal.open||playerModal.open)return;const now=new Date();if(!fireworksDue(now,fireworksYear||storage.getItem('kik-fireworks')))return;const hero=main.querySelector('.hero:not(.skeleton)');if(!hero||!fireworksOver(hero))return;fireworksYear=now.getFullYear();storage.setItem('kik-fireworks',String(fireworksYear));}
async function init(){
 let failure=null;
 // The owner's switches come alongside the account and never hold the page: until they do, KIK looks as always.
 loadFlags().then(holidayDress).catch(()=>{});
 const identity=(async()=>{try{const me=await api('/api/me');state.clockOffset=me.server_time-Date.now();state.user=me.user;state.profiles=me.profiles;rememberIdentity(me);if(state.user){state.profile=state.profiles.find(p=>p.id===storage.getItem('kik-profile:'+state.user.id))||state.profiles[0];restore();}state.ready=true;return true;}catch(e){failure=e;return offlineIdentity(e);}})();
 identityReady=Promise.race([identity,new Promise(r=>setTimeout(r,1500))]);loadCatalogue();
 const known=await identity;
 // Offline with films on this device: they open at once; the rest of KIK waits for the internet.
 if(state.offline){await downloads.ready;if(!location.hash&&downloads.items().some(x=>x.status==='done'))state.tab='downloads';render();refreshAccount();management.load().catch(()=>{});return;}
 // No answer about the account (no connection, a rate limit, KIK restarting) is not «signed out»: the «Упс» screen asks again by itself.
 if(!known){main.replaceChildren(el('section',{class:'grid-page oops-holder'},oops({error:failure,retry:()=>location.reload(),key:'me',size:'page'})));return;}
 // Nothing is drawn before the account is known (render() waits for it), so the first paint is the gate or the viewer's own page.
 if(!state.user||main.querySelector('.hero.skeleton'))render();
 if(state.user){await management.load();refreshAccount();if(state.tab==='admin'||state.tab==='settings'&&!main.querySelector('.settings-grid'))render();await sync();}
 if(known&&new URL(location.href).searchParams.has('pair')){if(state.user)pairDialog();else auth();}}
function navigate(tab){if(!pageTabs.includes(tab))return;pageGeneration++;main.removeAttribute('aria-busy');state.tab=tab;if(location.hash!=='#'+tab)history.pushState(null,'','#'+tab);render();window.scrollTo({top:0,behavior:'instant'});}
window.addEventListener('popstate',()=>{Object.assign(state,readRoute());pageGeneration++;main.removeAttribute('aria-busy');render();});
const management=createManagement({state,api,el,button,toast,showModal,modal,main,navigate,account,newProfile,pairDialog,taste,countriesDialog,logout,refreshAccount,chooseAvatar,onProfileUpdate:profile=>{if(state.profile?.id===profile.id){Object.assign(state.profile,profile);resetEnhanced();}}});
init();

/** Extra facts for a title page. No outside provider is named on it (owner, 12.11: the screens say only KIK); they are credited on «Джерела даних». */
async function enrichDetails(f,target,generation){if(!f.imdb)return;try{const m=await api('/api/metadata?imdb='+encodeURIComponent(f.imdb)+'&kind='+(f.series?'tv':'movie'));if(!modal.open||generation!==modalGeneration||modalContent.dataset.link!==target)return;const box=$('#extra-metadata');if(!box)return;if(m.title&&!f.external)modalContent.querySelector('.detail-title').textContent=m.title;if(m.overview){const overview=modalContent.querySelector('.detail-overview');if(overview){overview.textContent=m.overview;const more=modalContent.querySelector('.detail-more');if(more)more.hidden=!overview.classList.contains('open')&&overview.scrollHeight<=overview.clientHeight+2;}}if(m.nextEpisode){const n=m.nextEpisode;box.append(el('p',{},'Наступна серія: '+n.airdate+' · S'+n.season+'E'+n.episode),el('p',{class:'subtle'},'Дата виходу не гарантує наявності українського озвучення.'));}}catch{}}

function paintDetailStudios(ids){modalContent.querySelector('.detail-studios')?.remove();const studios=[...new Set(ids||[])].map(studioById).filter(Boolean);if(!studios.length)return;const row=el('div',{class:'detail-studios'},studios.map(s=>el('span',{class:'detail-studio'},studioBadge([s.id]),s.name)));modalContent.querySelector('.detail-meta')?.after(row);}
