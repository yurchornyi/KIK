/**
 * The Home rows the website asks KIK's service for on their own: the two Top 10s, «Нові фільми», «Нові серіали»,
 * «Топ-10 документальних цього тижня», «Нові документальні», «Мультфільми» and «Аніме» (14.11, owner: «то десь видно, то десь там не видно»). They came after the page, each at its
 * own time, pushing the rows under them down («1» of a Top 10 jumping up), and a request that failed once left its row
 * out until the next visit. Now:
 *  - the last good answer of each row is kept in this browser for KEEP_FOR and shown at once, then refreshed quietly;
 *  - a row still on its way is `pending`: Home draws its place (app.js placeholderShelf), so nothing below it moves;
 *  - a failed request is asked again after RETRIES; only then does a row with nothing kept leave the page;
 *  - an empty answer counts as a failed one (14.11: a shelf the service was still putting together came back empty, and
 *    «Топ-10 серіалів цього тижня» left the page instead of being asked again, its kept answer replaced by nothing).
 */
export const KEEP_FOR=12*3600000,RETRIES=[3000,10000,30000,60000],KEY='kik-home-rows';

export function createRowStore({storage,load,onChange=()=>{},now=Date.now,wait=(fn,ms)=>setTimeout(fn,ms)}){
 let kept={};try{kept=JSON.parse(storage.getItem(KEY)||'{}')||{};}catch{kept={};}
 const rows=new Map();
 for(const [id,v] of Object.entries(kept))if(v&&Array.isArray(v.items)&&v.items.length&&now()-v.at<KEEP_FOR)rows.set(id,{items:v.items,pending:false,asked:false});
 function save(){
  const out={};for(const [id,v] of rows)if(v.items?.length&&v.at)out[id]={items:v.items,at:v.at};
  for(const [id,v] of Object.entries(kept))if(!out[id]&&v&&now()-v.at<KEEP_FOR)out[id]=v;
  try{storage.setItem(KEY,JSON.stringify(out));}catch{}
 }
 function attempt(id,tries){
  const row=rows.get(id);
  Promise.resolve().then(()=>load(id)).then(items=>{
   if(!Array.isArray(items)||!items.length)throw Error('Row '+id);
   const before=JSON.stringify(row.items||[]);row.items=items;row.at=now();row.pending=false;kept[id]={items,at:row.at};save();
   if(JSON.stringify(items)!==before)onChange(id);
  }).catch(()=>{
   if(tries<RETRIES.length){wait(()=>attempt(id,tries+1),RETRIES[tries]);return;}
   // Given up for this visit: a kept answer stays on screen, a row with nothing kept leaves the page.
   const was=row.pending;row.pending=false;if(was&&!row.items?.length)onChange(id);
  });
 }
 return {
  /** Asks the service once per visit for each row; a kept answer stays on screen meanwhile. */
  ask(ids){for(const id of ids){let row=rows.get(id);if(!row){row={items:null,pending:true,asked:false};rows.set(id,row);}if(row.asked)continue;row.asked=true;if(!row.items?.length)row.pending=true;attempt(id,0);}},
  /** What Home shows for a row now: its items (kept or fresh), and whether its place is still being filled. */
  view(id){const row=rows.get(id);return {items:row?.items||[],pending:!!row?.pending&&!row?.items?.length};},
 };
}
