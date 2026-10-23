/** Ad clips spliced into sponsored voice tracks, found by the scanner PC; same rules as PlaybackPolicy.adSkip on TV. */
export const AD_MIN=5,AD_MAX=150,AD_TOTAL=300;
/** Breaks as sorted [start,end] seconds that respect the limits; anything out of bounds is dropped, a suspicious total drops all. */
export function cleanBreaks(list,duration){
 if(!Array.isArray(list)||!(duration>0))return [];const out=[];
 const all=list.filter(b=>Array.isArray(b)&&b.length===2).map(b=>[Math.round(Number(b[0])*10)/10,Math.round(Number(b[1])*10)/10]).sort((a,b)=>a[0]-b[0]);
 for(const [s,e] of all){if(!Number.isFinite(s)||!Number.isFinite(e)||s<10||e>duration||e-s<AD_MIN||e-s>AD_MAX)continue;if(out.length&&s<out.at(-1)[1])continue;out.push([s,e]);}
 return out.length>8||out.reduce((n,[s,e])=>n+e-s,0)>AD_TOTAL?[]:out;
}
/** Where to jump from `time`, or null. Only when the playing file is the one that was scanned (same length to 2 s). */
export function adSkip(breaks,time,duration,scanned){
 if(!breaks?.length||!(Math.abs(duration-scanned)<=2))return null;
 for(const [s,e] of breaks)if(time>=s&&time<e-1.5)return e;
 return null;
}
