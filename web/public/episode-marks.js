/** Intro and closing credits of one series episode, as the scanner PC found them (episode-marks.mjs):
 *  «Пропустити заставку» while an intro plays, «Наступна серія» from the credits. Same rules as PlaybackPolicy on the TV.
 *  Marks belong to the very file that was scanned: another length (a re-upload, another cut) gets none. */
/** Seconds the «Наступна серія» card counts down before the next episode starts by itself. */
export const NEXT_COUNTDOWN=10;
const MAX_INTROS=4,MIN_INTRO=3,MAX_INTRO=180;
const sameFile=(marks,duration)=>!!marks&&marks.duration>0&&duration>0&&Math.abs(marks.duration-duration)<=2;
/** Intro ranges from the scanner, made safe: inside the first third (at most 12 min), 3–180 s each, apart, at most four. */
export function cleanIntro(list,duration){
 if(!Array.isArray(list)||!(duration>0))return [];const out=[];
 for(const r of list){if(!Array.isArray(r))continue;const s=Number(r[0]),e=Number(r[1]);
  if(!Number.isFinite(s)||!Number.isFinite(e)||s<0||e-s<MIN_INTRO||e-s>MAX_INTRO||s>Math.min(720,duration/3)||e>duration-60)continue;
  if(out.some(([a,b])=>s<b&&a<e))continue;out.push([Math.round(s*10)/10,Math.round(e*10)/10]);}
 return out.sort((a,b)=>a[0]-b[0]).slice(0,MAX_INTROS);
}
/** Where the closing credits start: in the last 10 minutes and the last 40% of the episode, or null. */
export function cleanCredits(at,duration){const t=Number(at);return at!=null&&Number.isFinite(t)&&duration>0&&t>=Math.max(duration*0.6,duration-600)&&t<=duration-5?Math.round(t*10)/10:null;}
/** A channel or studio logo the episode opens with (the first 3 s, 15 s at most, e.g. Netflix): playback starts after it,
 *  as after a voice studio's ident (studioIntro). 0 when there is none. */
export function openingLogo(marks,duration){
 if(!sameFile(marks,duration))return 0;const first=(marks.intro||[])[0];
 return first&&first[0]<=3&&first[1]-first[0]<=15?first[1]:0;
}
/** End of the intro playing now, in seconds, or null: the button shows from its start to 1.5 s before its end. */
export function introSkip(marks,position,duration){
 if(!sameFile(marks,duration))return null;
 for(const [s,e] of marks.intro||[])if(position>=s&&position<e-1.5)return e;
 return null;
}
/** Without a scan «Наступна серія» shows this many seconds before the end: a series' credits run 40 s and more, and a
 *  guess must never cover the last scene (the last 5% put it 2 min early). Same as PlaybackPolicy.NEXT_GUESS_MS on the TV. */
export const NEXT_GUESS=40;
/** When «Наступна серія» appears. `sure` (scanned credits) counts down and goes on by itself; without a scan it shows
 *  over the last 40 s as a button only. A stream under 3 min (a 60-second preview) gets no guess. */
export function nextOffer(marks,duration){
 if(!(duration>0))return null;
 const credits=sameFile(marks,duration)?cleanCredits(marks.credits,duration):null;
 if(credits!=null)return {at:credits,sure:true};
 return duration>=180?{at:duration-NEXT_GUESS,sure:false}:null;
}
