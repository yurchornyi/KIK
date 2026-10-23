/** Typo-tolerant title matching shared by the server and the browser. Mirrors Site.matches on TV. */
export const foldQuery=s=>String(s||'').normalize('NFKD').toLowerCase().replace(/\p{M}/gu,'').replace(/[ії]/g,'и').replace(/[єэ]/g,'е').replace(/ґ/g,'г').replace(/ё/g,'е').replace(/ы/g,'и').replace(/['’ʼ]/g,'').replace(/[^\p{L}\p{N}]+/gu,' ').trim();
/** Edit distance with swaps of neighbours ("шерлко"), stopped early once it exceeds max. */
export function distance(a,b,max=3){if(Math.abs(a.length-b.length)>max)return max+1;let prev2=null,prev=Array.from({length:b.length+1},(_,j)=>j);for(let i=1;i<=a.length;i++){const row=[i];let best=i;for(let j=1;j<=b.length;j++){let d=Math.min(prev[j]+1,row[j-1]+1,prev[j-1]+(a[i-1]===b[j-1]?0:1));if(prev2&&i>1&&j>1&&a[i-1]===b[j-2]&&a[i-2]===b[j-1])d=Math.min(d,prev2[j-2]+1);row.push(d);if(d<best)best=d;}if(best>max)return max+1;prev2=prev;prev=row;}return prev[b.length];}
/** Allowed typos per word: none for short words, one up to six letters, two after. */
const slack=w=>w.length<4?0:w.length<7?1:2;
const wordHit=(w,words)=>words.some(x=>x.startsWith(w)||w.length>3&&distance(w,x.slice(0,w.length+1),slack(w))<=slack(w)||distance(w,x,slack(w))<=slack(w));
/** 0..1: how well one title name answers the query; every meaningful query word must be found. */
export function nameScore(query,name){const q=foldQuery(query),t=foldQuery(name);if(!q||!t)return 0;if(t===q)return 1;if(t.startsWith(q))return .9;const words=q.split(' ').filter(w=>w.length>1),title=t.split(' ');if(!words.length)return 0;const found=words.filter(w=>wordHit(w,title)).length;if(found===words.length)return .7;
 // Words run together ("пиратыкарибского") or split apart still match letter by letter.
 const a=q.replace(/ /g,''),b=t.replace(/ /g,'');if(a.length>5&&distance(a,b.slice(0,a.length),slack(a))<=slack(a))return .6;return .5*found/words.length;}
const names=item=>[item.title,item.original,item.sourceTitle].filter(Boolean).flatMap(n=>String(n).split(/\s+\/\s+/));
export const itemScore=(query,item)=>Math.max(0,...names(item).map(n=>nameScore(query,n)));
/** Does at least one of the first results really answer the query? If not, the search asks for other spellings. */
export const searchMatches=(query,items,limit=12)=>(items||[]).slice(0,limit).some(i=>itemScore(query,i)>=.6);
