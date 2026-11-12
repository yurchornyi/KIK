import {shared,FPS} from './ad-fingerprint.mjs';
/** Intro and closing credits of an episode from its sound alone (scanner PC, episode-marks.mjs): what plays the same in
 *  this episode and in other episodes of the same voice track. The edges of each episode are fingerprinted
 *  (ad-fingerprint.mjs): `start` from 0 s, `end` from `endFrom` s; `duration` in seconds.
 *  A recap («Раніше в серіалі») shares sound with one other episode only, so an intro must be shared with two.
 *  Near the end nothing but the credits repeats, so one other episode is enough there: some episodes carry another
 *  version of the credits sting (measured on «Formula 1: Drive to Survive» S8, 2026-11). */
export const START_WINDOW=600,END_WINDOW=420;
const span=(runs,side,offset=0)=>runs.map(r=>[offset+r[side]/FPS,offset+(r[side]+r.length)/FPS]);
/** Overlapping ranges merged; ranges closer than `gap` seconds join. */
export function union(list,gap=1){const out=[];for(const [s,e] of list.slice().sort((a,b)=>a[0]-b[0])){const last=out.at(-1);if(last&&s<=last[1]+gap)last[1]=Math.max(last[1],e);else out.push([s,e]);}return out;}
/** The parts covered by both lists. */
export function intersect(a,b){const out=[];for(const [s,e] of a)for(const [t,u] of b){const x=Math.max(s,t),y=Math.min(e,u);if(y>x)out.push([x,y]);}return union(out,0);}
/** Ranges of `cur` heard in at least two of `others` (in only one when there is just one other). */
function common(sets){
 if(sets.length<2)return sets[0]||[];
 const out=[];for(let i=0;i<sets.length;i++)for(let j=i+1;j<sets.length;j++)out.push(...intersect(sets[i],sets[j]));
 return union(out,0);
}
/** {intro:[[start,end]…], credits} in seconds; credits null when no other episode shares anything near the end. */
export function detectMarks(cur,others,{minIntro=3,minCredits=3}={}){
 others=others.filter(o=>o&&o.start&&o.end);
 if(!cur?.start||!others.length)return {intro:[],credits:null};
 const introSets=others.map(o=>union(span(shared(cur.start,o.start,{minSeconds:minIntro}),'a'),1.5));
 // With a single other episode only the very start counts (a studio ident, the opening): a recap there is rare.
 let intro=union(common(introSets),2).filter(([s,e])=>e-s>=minIntro&&s<Math.min(START_WINDOW,cur.duration/3)&&(others.length>1||s<180));
 intro=intro.map(([s,e])=>[Math.max(0,s),Math.min(e,s+180)]).slice(0,4);
 let credits=null;
 if(cur.end){
  const endSets=others.map(o=>union(span(shared(cur.end,o.end,{minSeconds:minCredits}),'a',cur.endFrom),5));
  // The last thing every episode shares near its end is the credits (their theme, a channel or studio sting inside them):
  // the last scene of an episode is never the same as another's.
  const last=union(endSets.flat(),5).filter(([s,e])=>e-s>=minCredits&&s>=cur.duration-END_WINDOW&&s>=cur.duration*0.6).at(-1);
  if(last)credits=last[0];
 }
 return {intro:intro.map(([s,e])=>[Math.round(s*10)/10,Math.round(e*10)/10]),credits:credits==null?null:Math.round(credits*10)/10};
}
/** Which other episodes of the season to compare with: the nearest four, on both sides. */
export function neighbours(list,season,episode){
 const all=(list||[]).filter(e=>e&&e.season!=null&&e.episode!=null).map(e=>({season:String(e.season),episode:String(e.episode)}))
  .sort((a,b)=>Number(a.season)-Number(b.season)||Number(a.episode)-Number(b.episode));
 const i=all.findIndex(e=>e.season===String(season)&&e.episode===String(episode));if(i<0)return [];
 return [all[i-1],all[i+1],all[i-2],all[i+2],all[i+3],all[i-3]].filter(e=>e&&e.season===String(season)).slice(0,4);
}
