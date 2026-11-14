/**
 * Fresh KIK on a phone or tablet (14.11, owner: the home-screen app kept showing the old version, and a swipe down did
 * nothing). KIK on a home screen is a standalone app: no browser bar, no reload button, no pull to refresh of the phone's
 * own, and coming back to it shows the page it left.
 *  - Pull to refresh: from the top of the page, a pull down past THRESHOLD reloads it. A browser tab gets the same KIK
 *    pull instead of its own, so it looks and works the same everywhere.
 *  - A new version: when KIK comes back on screen, and every ten minutes while it stays, the page's own address is asked
 *    which files it names; a deploy changes their ?v= (build.mjs). Back from the background with nothing open, KIK
 *    reloads by itself; otherwise a «Оновити» pill waits for a tap.
 */
export const THRESHOLD=72,MAX=120,AWAY=30000,EVERY=600000;
const FILES=/(?:src|href)="(\/[\w.-]+\.(?:js|css)\?v=[\w-]+)"/g;

/** The files a page names with their ?v=: equal for the same deploy, different after a new one; empty without fingerprints. */
export function versionOf(html){return [...new Set([...String(html||'').matchAll(FILES)].map(m=>m[1]))].sort().join(' ');}
/** The version of the page on screen, from its own script and style tags. */
export function shownVersion(document){
 return versionOf([...document.querySelectorAll('script[src],link[href]')].map(e=>e.hasAttribute('src')?`src="${e.getAttribute('src')}"`:`href="${e.getAttribute('href')}"`).join(' '));
}
/** How far the round button comes down for a finger `dy` px down: half the finger's way, slower past the threshold, never past MAX. */
export function pullDistance(dy){if(!(dy>0))return 0;const d=dy*.55;return d<=THRESHOLD?d:Math.min(MAX,THRESHOLD+(d-THRESHOLD)*.35);}
/** A window, menu or the player is open: no reload under it. */
export function busy(document){
 if(document.querySelector('dialog[open]'))return true;
 try{return !!document.querySelector(':popover-open');}catch{return false;}
}

/** Watches for a new deploy. `check(back)`: back from the background with nothing open, reload; else show the pill. */
export function createVersionWatch({document,fetcher,reload,blocked=()=>false,now=Date.now}){
 const mine=shownVersion(document);let asking=false,last=now(),pill=null;
 function showPill(){
  if(pill)return;pill=document.createElement('button');pill.type='button';pill.className='update-pill';
  pill.textContent='Вийшла нова версія KIK · Оновити';pill.onclick=()=>reload();document.body.append(pill);
 }
 async function check(back=false){
  if(!mine||asking||document.hidden)return 'skip';asking=true;
  try{
   const r=await fetcher('/',{cache:'no-store',credentials:'same-origin'});if(!r.ok)return 'skip';
   const v=versionOf(await r.text());if(!v||v===mine)return 'same';
   if(back&&!busy(document)&&!blocked()){reload();return 'reload';}
   showPill();return 'pill';
  }catch{return 'skip';}
  finally{asking=false;last=now();}
 }
 return {check,get last(){return last;},get pill(){return pill;}};
}

/** Pull to refresh on a touch screen. Only a pull that starts at the very top and goes mostly down: a shelf's sideways
 *  swipe, the banner's slides and an ordinary scroll stay as they are. */
export function installPull({window,document,reload,blocked=()=>false}){
 const ring=document.createElement('div');ring.className='pull-refresh';ring.setAttribute('aria-hidden','true');
 ring.innerHTML='<svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true"><path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/><path d="M19.5 3.8v4.8h-4.8" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
 document.body.append(ring);
 let x0=0,y0=0,state='idle',dist=0;
 const atTop=()=>(document.scrollingElement||document.documentElement).scrollTop<=0;
 const reset=()=>{state='idle';dist=0;ring.classList.remove('on','ready');ring.style.transform='';ring.style.opacity='';};
 const paint=()=>{ring.style.transform=`translate(-50%,${dist}px) rotate(${Math.round(dist*3)}deg)`;ring.style.opacity=String(Math.min(1,dist/36));ring.classList.toggle('ready',dist>=THRESHOLD);};
 window.addEventListener('touchstart',e=>{
  if(state==='refreshing')return;
  if(e.touches.length!==1||!atTop()||busy(document)||blocked()||e.target?.closest?.('input,textarea,select,[contenteditable]')){state='idle';return;}
  x0=e.touches[0].clientX;y0=e.touches[0].clientY;state='maybe';
 },{passive:true});
 window.addEventListener('touchmove',e=>{
  if(state!=='maybe'&&state!=='pulling')return;
  const t=e.touches[0],dx=t.clientX-x0,dy=t.clientY-y0;
  if(state==='maybe'){
   if(Math.abs(dx)<8&&Math.abs(dy)<8)return;
   if(dy<=0||Math.abs(dx)*1.2>dy||!atTop()){state='idle';return;}
   state='pulling';ring.classList.add('on');
  }
  // While pulling, the page itself stays put: no rubber band, no browser pull of its own on top of KIK's.
  if(e.cancelable)e.preventDefault();dist=pullDistance(dy);paint();
 },{passive:false});
 window.addEventListener('touchend',()=>{
  if(state!=='pulling'){if(state!=='refreshing')state='idle';return;}
  if(dist<THRESHOLD){reset();return;}
  state='refreshing';ring.classList.remove('ready');ring.classList.add('spin');ring.style.transform=`translate(-50%,${THRESHOLD}px)`;
  window.setTimeout(()=>reload(),180);
 },{passive:true});
 window.addEventListener('touchcancel',()=>{if(state!=='refreshing')reset();},{passive:true});
 return ring;
}

/** Both, wired to the page: the pull on touch screens, the version watch everywhere. */
export function installRefresh({window=globalThis.window,document=globalThis.document,reload=()=>window.location.reload(),blocked=()=>false}={}){
 if(!window||!document?.body)return null;
 if(window.matchMedia?.('(pointer: coarse)').matches)installPull({window,document,reload,blocked});
 const watch=createVersionWatch({document,fetcher:(...a)=>window.fetch(...a),reload,blocked});
 let hiddenAt=0;
 document.addEventListener('visibilitychange',()=>{
  if(document.hidden){hiddenAt=Date.now();return;}
  const away=hiddenAt?Date.now()-hiddenAt:0;hiddenAt=0;
  if(away>=AWAY||Date.now()-watch.last>=EVERY)watch.check(away>=AWAY);
 });
 window.setInterval(()=>{if(!document.hidden&&Date.now()-watch.last>=EVERY)watch.check(false);},60000);
 return watch;
}
