/**
 * Rows start at their first card. A row keeps another place only when the viewer moved that row by hand: a finger, a
 * wheel or trackpad over it, or the keyboard. A place the browser picked by itself is put back. Safari on iPhone
 * re-snaps rows after a layout change and scrolls a row to the card that gets focus back from a closed window, and the
 * repaint kept that place, so rows opened on the 4th or 5th card (owner, 14.11: «не з 1 починається і так всюда»).
 */
export const HAND_MS=2500,SETTLE_MS=900,DRIFT_PX=40,MAX_RESETS=4;
const LINES='.shelf-line,.similar-line';

/** Where the viewer left this row by hand, 0 when they never moved it. */
export function handPlace(line){return Number(line?.dataset?.left)||0;}
/** Put a kept place back on a freshly drawn row. */
export function keepPlace(line,left){if(!line||!left)return;line.dataset.left=String(left);line.scrollLeft=left;}

export function watchRows(root=document,win=window,now=()=>Date.now()){
 // The row a gesture started on, and until when its scrolling counts as the viewer's (momentum included).
 let handLine=null,handUntil=0;const resets=new WeakMap();
 const start=e=>{handLine=e.type==='keydown'?(e.key==='Escape'?null:'any'):e.target?.closest?.(LINES)||null;handUntil=now()+HAND_MS;};
 for(const type of ['touchstart','pointerdown','wheel','keydown'])win.addEventListener(type,start,{capture:true,passive:true});
 win.addEventListener('touchmove',()=>{if(handLine)handUntil=now()+HAND_MS;},{capture:true,passive:true});
 root.addEventListener('scroll',e=>{
  const line=e.target;if(!line?.matches?.(LINES))return;
  if(now()<handUntil&&(handLine===line||handLine==='any')){handUntil=Math.max(handUntil,now()+SETTLE_MS);line.dataset.left=String(Math.round(line.scrollLeft));return;}
  const want=handPlace(line);if(Math.abs(line.scrollLeft-want)<=DRIFT_PX)return;
  // A browser that keeps snapping somewhere else is let be after a few tries rather than fought frame after frame.
  const r=resets.get(line)||{n:0,at:0};if(now()-r.at>3000){r.n=0;r.at=now();}if(++r.n>MAX_RESETS)return;resets.set(line,r);
  line.scrollLeft=want;
 },{capture:true,passive:true});
}
