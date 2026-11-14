/**
 * Posters of the rows far below the screen wait until their row comes near (14.11). The browser's own lazy loading starts
 * every poster within about 1250 px of the screen (2500 px on a slow network), so on a phone it took every row of Home at
 * once: 85 posters for about six on screen, the last of them after 5.1 s. Now a row asks for its posters when it is
 * within MARGIN of the screen; inside the row the browser's lazy loading goes on as before.
 * It only watches #main: a poster already shown, an image outside a row and the catalogue's grid are never touched.
 */
const MARGIN=500,HELD='heldSrc',HELD_SET='heldSrcset';

function hold(img){
 if(img.complete&&img.naturalWidth)return;
 const src=img.getAttribute('src'),set=img.getAttribute('srcset');if(!src&&!set)return;
 if(set!==null){img.dataset[HELD_SET]=set;img.removeAttribute('srcset');}
 if(src!==null){img.dataset[HELD]=src;img.removeAttribute('src');}
}
function release(row){
 delete row.dataset.postersHeld;
 for(const img of row.querySelectorAll('img[data-held-src],img[data-held-srcset]')){
  const set=img.dataset[HELD_SET],src=img.dataset[HELD];delete img.dataset[HELD_SET];delete img.dataset[HELD];
  if(set!==undefined)img.setAttribute('srcset',set);if(src!==undefined)img.setAttribute('src',src);
 }
}

export function holdFarRows(main=globalThis.document?.getElementById('main'),win=globalThis){
 if(!main||!('IntersectionObserver' in win)||!('MutationObserver' in win))return null;
 const near=new win.IntersectionObserver(entries=>{for(const e of entries)if(e.isIntersecting){near.unobserve(e.target);release(e.target);}},{rootMargin:MARGIN+'px 0px'});
 const watcher=new win.MutationObserver(records=>{
  const rows=new Set(),loose=new Set();
  // Home repaints often: a row gone from the page is no longer watched.
  for(const r of records)for(const node of r.removedNodes){
   if(node.nodeType!==1)continue;
   for(const row of node.matches('.shelf')?[node]:node.querySelectorAll('.shelf'))if(row.dataset.postersHeld&&!row.isConnected)near.unobserve(row);
  }
  for(const r of records)for(const node of r.addedNodes){
   if(node.nodeType!==1)continue;
   if(node.matches('.shelf'))rows.add(node);else for(const s of node.querySelectorAll('.shelf'))rows.add(s);
   // A picture added into a row that is still waiting (a card repainted in place) waits with it.
   const row=node.closest('.shelf');if(row?.dataset.postersHeld)loose.add(node);
  }
  if(rows.size){
   const below=win.innerHeight+MARGIN;
   for(const row of rows){
    if(!row.isConnected||row.dataset.postersHeld)continue;
    if(row.getBoundingClientRect().top<=below)continue;
    row.dataset.postersHeld='1';for(const img of row.querySelectorAll('img'))hold(img);near.observe(row);
   }
  }
  for(const node of loose)for(const img of node.matches('img')?[node]:node.querySelectorAll('img'))hold(img);
 });
 watcher.observe(main,{childList:true,subtree:true});
 return {near,watcher};
}

holdFarRows();
