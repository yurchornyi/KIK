/** How a profile actually watched a title, from synced progress records; the same rule as RecommendationRanker.engagement on TV.
 *  `watched` is real playing time (seeks and skips never count), summed by the players per title. */
export const DAY=864e5,ABANDON_AFTER=3*DAY,ABANDON_MS=15*60000,GLANCE_MS=30000;
/** One title's progress records (any episodes) -> {watched, stoppedAt, finished episodes, state}. */
export function engagement(records,now=Date.now()){
 let watched=0,last=null,finished=0;
 for(const r of records){const d=r.data;if(!d)continue;watched=Math.max(watched,d.watched??(d.observed?d.pos||0:0));if(d.dur>0&&d.pos/d.dur>.93)finished++;if(!last||r.updated_at>last.updated_at)last=r;}
 if(!last)return null;const d=last.data,series=!!(d.season&&d.season!=='0');
 // Finished: a film to the credits, or a series with an episode watched to the end.
 const state=finished&&(series||d.pos/d.dur>.93)?'finished':watched<GLANCE_MS?'glance':now-last.updated_at>=ABANDON_AFTER&&watched<ABANDON_MS&&!(d.dur>0&&d.pos/d.dur>=.25)?'abandoned':'watching';
 return {watched,stoppedAt:d.pos||0,dur:d.dur||0,finished,series,state,updated_at:last.updated_at};
}
/** Implicit taste: watched to the end is a mild like, given up within the first minutes a mild dislike. */
export function engagementWeight(e){return e?.state==='finished'?4:e?.state==='abandoned'?-2:e?.state==='watching'?3:0;}
export function minutes(ms){const m=Math.round(ms/60000);return m<1?'менше хвилини':m<60?m+' хв':Math.floor(m/60)+' год'+(m%60?' '+m%60+' хв':'');}
/** «Переглянуто 1 год 12 хв · до кінця» / «… · кинули на 6-й хв». */
export function engagementLabel(e){if(!e)return '';const time='Переглянуто '+minutes(e.watched);return e.state==='finished'?time+(e.series?' · '+e.finished+' сер. до кінця':' · до кінця'):e.state==='abandoned'?time+' · кинули на '+Math.max(1,Math.round(e.stoppedAt/60000))+'-й хв':time;}
/** Groups progress records by title key. */
export function byTitle(records,keyOf){const out=new Map();for(const r of records){if(r.kind&&r.kind!=='progress'||!r.data)continue;const k=keyOf(r.data.link);if(!out.has(k))out.set(k,[]);out.get(k).push(r);}return out;}
