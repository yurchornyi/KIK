/** Where a sponsored voice track splices ad clips into an episode. Runs on the scanner PC, never in the Worker.
 *  An episode is {key, title, duration, audio: fingerprint, gray: 12×9 thumbnails}; a creative is a known ad clip {id, audio, gray}.
 *  Only clips proven to be foreign to the episode are reported, so the players can never jump over the film itself:
 *  - picture that another voice of the same episode does not have (the timeline grew by exactly that much), or
 *  - a clip whose sound AND picture also play inside a different title (a sponsor read over the film's own scenes fails the picture test). */
import {locate,shared,FPS as AUDIO_FPS} from './ad-fingerprint.mjs';
import {frames,distance,insertions,pictureMatch,FPS as VIDEO_FPS,SIZE} from './ad-align.mjs';
import {cleanBreaks} from './public/ad-skip.js';
export const IDENT=30,CREDITS=90,PICTURE=0.5;
// Edges are exact to a quarter second (ad-fingerprint solid): keep that much more of the film on both sides.
export const TRIM=0.3;
const view=e=>e.frames||=frames(e.gray);
const clip=(e,start,end)=>({audio:e.audio.slice(Math.round(start*AUDIO_FPS),Math.round(end*AUDIO_FPS)),gray:e.gray.slice(Math.round(start*VIDEO_FPS)*SIZE,Math.round(end*VIDEO_FPS)*SIZE)});
const samePicture=(e,start,c,from,length)=>pictureMatch(view(e),Math.round(start*VIDEO_FPS),view(c),Math.round(from*VIDEO_FPS),Math.round(length*VIDEO_FPS))>=PICTURE;
/** Known creatives inside the episode. */
export function fromLibrary(e,library){const out=[];
 for(const c of library)for(const f of locate(e.audio,c.audio,{minSeconds:8})){const start=(f.at+f.from)/AUDIO_FPS,end=(f.at+f.to)/AUDIO_FPS;
  if(samePicture(e,start,c,f.from/AUDIO_FPS,end-start))out.push({start:start+TRIM,end:end-TRIM,creative:c.id});}
 return out;}
/** Clips the episode shares with episodes of other titles; each becomes a new creative. */
export function discover(e,others){const breaks=[],creatives=[],elsewhere=[];
 for(const o of others){if(o.title===e.title)continue;
  for(const r of shared(e.audio,o.audio,{minSeconds:12})){const start=r.a/AUDIO_FPS,from=r.b/AUDIO_FPS,length=r.length/AUDIO_FPS;
   // Studio idents open every video and end credits repeat within a show: neither is an ad break.
   if(start<IDENT||from<IDENT||start+length>e.duration-CREDITS||from+length>o.duration-CREDITS)continue;
   if(!samePicture(e,start,o,from,length))continue;
   breaks.push({start:start+TRIM,end:start+length-TRIM});elsewhere.push({key:o.key,start:from+TRIM,end:from+length-TRIM});creatives.push({...clip(e,start,start+length),found:[e.key,o.key]});}}
 return {breaks,creatives,elsewhere};}
/** Picture the sponsored track has and another voice of the same episode lacks. */
export function fromReference(e,ref){view(e);view(ref);return insertions({gray:e.gray,frames:e.frames},{gray:ref.gray,frames:ref.frames}).filter(b=>b.start>=IDENT/2).map(b=>({start:b.start,end:b.end,creative:null,clip:clip(e,b.start,b.end)}));}
/** Overlapping finds joined, then the same limits the players apply. */
export function merge(found,duration){const list=found.map(b=>[b.start,b.end]).sort((a,b)=>a[0]-b[0]),out=[];
 for(const [s,t] of list){const last=out.at(-1);if(last&&s<=last[1]+0.5)last[1]=Math.max(last[1],t);else out.push([s,t]);}
 return cleanBreaks(out,duration);}
/** Ranges the AI rated as a spliced-in commercial: [{start,end}] for the «Пропустити рекламу» button, never skipped on their own.
 *  samples are {t, p, o} every few seconds: p = an ad, o = ad graphics over a film scene that stays visible (never offered:
 *  skipping it would skip the film). A range needs a sure frame, spreads over neighbours that still look like a commercial,
 *  bridges one doubtful sample between two sure ones, and both edges move to the hard cut between the samples. */
export const SURE=0.8,LIKELY=0.4,OVERLAY=0.35;
export function suggest(e,samples,confirmed=[]){
 const f=view(e),out=[],jump=i=>{const a=f[i],b=f[i+1];if(!a&&!b)return 0;if(!a||!b)return 1.5;return distance(a,b);};
 const cut=(from,to)=>{let best=-1,at=null;for(let i=Math.max(0,Math.floor(from*VIDEO_FPS));i<Math.min(f.length-1,Math.ceil(to*VIDEO_FPS));i++){const j=jump(i);if(j>best){best=j;at=(i+1.5)/VIDEO_FPS;}}return best>=0.6?at:null;};
 const s=samples.filter(x=>typeof x.p==='number'&&typeof x.o==='number').sort((a,b)=>a.t-b.t),sure=x=>x&&x.p>=SURE&&x.o<OVERLAY,likely=x=>x&&x.p>=LIKELY&&x.o<OVERLAY+0.15;
 for(let i=0;i<s.length;i++){if(!sure(s[i]))continue;let a=i,b=i;
  for(;;){if(likely(s[a-1]))a--;else if(sure(s[a-2]))a-=2;else break;}
  for(;;){if(likely(s[b+1]))b++;else if(sure(s[b+2]))b+=2;else break;}
  const before=a>0?s[a-1].t:s[a].t-4,after=b<s.length-1?s[b+1].t:s[b].t+4,start=cut(before,s[a].t),end=cut(s[b].t,after);i=b;
  // A banner over the film flickers between «ad» and «graphics over a scene»: on average the range must look like a whole-screen clip.
  const part=s.slice(a,b+1).filter(x=>x.p>=LIKELY);if(part.reduce((n,x)=>n+x.o,0)/part.length>=0.3)continue;
  if(start===null||end===null||end-start<8||end-start>150)continue;if(confirmed.some(([x,y])=>start<y&&x<end))continue;out.push({start,end});}
 return out;
}
