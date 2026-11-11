import {equal} from './auth.mjs';import {episodeOf} from './ad-breaks.mjs';import {cleanIntro,cleanCredits} from './public/episode-marks.js';
import {resolvePlayback} from './playback-sources.mjs';
/** Intro and closing credits of series episodes (public/episode-marks.js says how players use them). The scanner PC finds
 *  them by comparing the sound of an episode with two others of the same voice track (marks-detect.mjs). */
const QUEUE_MAX=300,BATCH=5;
// A scan that found nothing (no neighbour episode yet, an unusual cut) is tried again after three days.
const RESCAN_EMPTY=3*86400_000;
const fail=(message,status=400)=>{throw Object.assign(new Error(message),{status});};
/** Known marks for the players; an unknown series episode joins the scan queue (signed-in players only). */
export async function episodeMarks(db,params,now=Date.now(),{queue=true}={}){
 const e=episodeOf(params),args=[e.key,e.voice,e.season,e.episode];
 if(!e.season||!e.episode)return {intro:[]};
 const row=await db.prepare('SELECT duration,intro,credits,scanned_at FROM episode_marks WHERE title_key=? AND voice=? AND season=? AND episode=?').bind(...args).first();
 const intro=row?cleanIntro(JSON.parse(row.intro||'[]'),row.duration):[],credits=row?cleanCredits(row.credits,row.duration):null;
 const known=row?{duration:row.duration,intro,...credits!=null?{credits}:{}}:{intro:[]};
 if(row&&(intro.length||credits!=null||now-row.scanned_at<RESCAN_EMPTY))return known;
 if(!queue)return known;
 const queued=await db.prepare('SELECT COUNT(*) AS n FROM mark_scan_queue WHERE attempts<3').first();
 if((queued?.n||0)<QUEUE_MAX)await db.prepare('INSERT OR IGNORE INTO mark_scan_queue(title_key,voice,season,episode,requested_at) VALUES(?,?,?,?,?)').bind(...args,now).run();
 return {...known,pending:true};
}
/** The scanner PC's calls, behind the same secret as the ad scanner: take queued episodes, get their media links from
 *  KIK (Cloudflare asks the source, never the owner's home network), report what it found. */
export async function marksScanner(request,db,env,path,readBody,now=Date.now(),{resolve=resolvePlayback}={}){
 const token=request.headers.get('Authorization')?.match(/^Bearer (\S{32,200})$/)?.[1];
 if(!env.AD_SCANNER_TOKEN||!token||!equal(token,env.AD_SCANNER_TOKEN))fail('Не знайдено',404);
 if(request.method!=='POST')fail('Використайте POST',405);
 if(path==='/api/marks/queue'){
  await db.prepare('DELETE FROM mark_scan_queue WHERE requested_at<?').bind(now-30*86400_000).run();
  const rows=(await db.prepare('SELECT title_key,voice,season,episode FROM mark_scan_queue WHERE attempts<3 ORDER BY attempts,requested_at LIMIT ?').bind(BATCH).all()).results;
  if(rows.length)await db.batch(rows.map(r=>db.prepare('UPDATE mark_scan_queue SET attempts=attempts+1 WHERE title_key=? AND voice=? AND season=? AND episode=?').bind(r.title_key,r.voice,r.season,r.episode)));
  return {items:rows.map(r=>({link:'https://hdrezka.sh/'+r.title_key,voice:r.voice,season:r.season,episode:r.episode}))};
 }
 if(path==='/api/marks/source'){
  const b=await readBody(request,4096),e=episodeOf(b),link='https://hdrezka.sh/'+e.key;
  if(b.list===true)return {episodes:(await resolve(link,e.voice,'','',{method:'episodes'})).episodes||[]};
  if(!e.season||!e.episode)fail('Некоректна серія');
  const r=await resolve(link,e.voice,e.season,e.episode,{method:'stream'});
  return {qualities:(r.qualities||[]).map(q=>({label:q.label,url:q.url,alternatives:q.alternatives||[]}))};
 }
 if(path==='/api/marks/report'){
  const b=await readBody(request,65536);if(!Array.isArray(b.items)||b.items.length>50)fail('Некоректний звіт');let saved=0;
  for(const item of b.items){const e=episodeOf(item),duration=Number(item.duration);if(!e.season||!e.episode)fail('Некоректна серія');if(!(duration>60&&duration<6*3600))fail('Некоректна тривалість');
   const intro=cleanIntro(item.intro,duration),credits=cleanCredits(item.credits,duration),args=[e.key,e.voice,e.season,e.episode];
   await db.batch([db.prepare('INSERT INTO episode_marks(title_key,voice,season,episode,duration,intro,credits,scanned_at) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(title_key,voice,season,episode) DO UPDATE SET duration=excluded.duration,intro=excluded.intro,credits=excluded.credits,scanned_at=excluded.scanned_at').bind(...args,duration,JSON.stringify(intro),credits,now),
    db.prepare('DELETE FROM mark_scan_queue WHERE title_key=? AND voice=? AND season=? AND episode=?').bind(...args)]);saved++;}
  return {saved};
 }
 fail('Не знайдено',404);
}
