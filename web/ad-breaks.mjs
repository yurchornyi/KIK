import {titleKey} from './sync.mjs';import {equal} from './auth.mjs';import {cleanBreaks} from './public/ad-skip.js';import {complete,sentraReady} from './sentra-ai.mjs';
/** Voice tracks that splice betting ads into episodes: ColdFilm, 1win Studio, 1win Express. Only these are ever queued. */
export const SPONSORED=new Set(['35','623','641']);
// An episode scanned clean is retried after a week: the scanner's library of known clips keeps growing.
const RESCAN=7*86400_000,QUEUE_MAX=300,BATCH=5;
/** Frame check for clips no other signal can confirm: Sentra's vision role first (all frames of a call in one request);
 *  the fallback is Cloudflare's decision model, one small JPEG per call (about one Neuron).
 *  The daily cap keeps it well inside the free 10,000 Neurons, next to the synopses. */
export const AD_FRAMES_DAILY=3000,AD_FRAMES_PER_CALL=16;
const AD_QUESTIONS={
 ad:{type:'noul',instructions:'Is this frame from an inserted TV commercial (a betting, casino or app advertisement such as 1XBET), rather than a scene of the drama series itself?',criteria:{true:'An advertisement: product shots, phones with betting apps, brand logos or slogans filling the picture, staged commercial scenes.',false:'A normal scene of the series, even if a small channel or sponsor logo sits in a corner.'}},
 overlay:{type:'noul',instructions:'Is a scene of the drama series still visible behind or around advertising graphics laid over it (a banner, logo or animation on top of the film)?',criteria:{true:'Advertising graphics are drawn over a film scene that stays visible.',false:'No film scene is visible: the whole picture is the commercial or the film without graphics.'}}};
/** The same two questions for Sentra's vision role: all frames of a call in one request, a number per frame and question. */
const AD_PROMPT=n=>`${n} frames from TV series episodes, in order. Some episodes have betting commercials spliced into them or laid over the film.
For each frame give two probabilities from 0 to 1:
- ad: ${AD_QUESTIONS.ad.instructions} Yes: ${AD_QUESTIONS.ad.criteria.true} No: ${AD_QUESTIONS.ad.criteria.false}
- overlay: ${AD_QUESTIONS.overlay.instructions} Yes: ${AD_QUESTIONS.overlay.criteria.true} No: ${AD_QUESTIONS.overlay.criteria.false}
Answer only JSON: {"frames":[{"ad":0.0,"overlay":0.0}, ...]} with exactly ${n} items in the frames' order.`;
const share=v=>typeof v==='number'&&v>=0&&v<=1?v:null;
/** Images per Sentra request: Groq's image model (qwen3.8-27b) takes at most 3. */
const SENTRA_FRAMES=3;
/** Sentra first (skill kik-ad-frames, role vision), three frames a request: [ad, overlay] per frame, or null when any part is unusable. */
async function viaSentraFrames(env,frames){
 if(!sentraReady(env))return null;
 if(frames.length>SENTRA_FRAMES){
  const parts=await Promise.all(Array.from({length:Math.ceil(frames.length/SENTRA_FRAMES)},(_,i)=>viaSentraFrames(env,frames.slice(i*SENTRA_FRAMES,(i+1)*SENTRA_FRAMES))));
  return parts.every(Boolean)?parts.flat():null;
 }
 const messages=[{role:'user',content:[{type:'text',text:AD_PROMPT(frames.length)},...frames.map(url=>({type:'image_url',image_url:{url}}))]}];
 const accept=raw=>{const text=String(raw),list=JSON.parse(text.slice(text.indexOf('{'),text.lastIndexOf('}')+1))?.frames;if(!Array.isArray(list)||list.length!==frames.length)return null;const pairs=list.map(f=>[share(f?.ad),share(f?.overlay)]);return pairs.every(([a,o])=>a!==null&&o!==null)?pairs:null;};
 return (await complete(env,{skill:'kik-ad-frames',role:'vision',messages,maxTokens:40+30*frames.length,temperature:0,json:true,timeout:25000},accept,'ad frames'))?.value??null;
}
/** AI hints within the same limits, minus anything a confirmed break already covers. */
const hints=(list,breaks,duration)=>cleanBreaks(list||[],duration).filter(([s,e])=>!breaks.some(([a,b])=>s<b&&a<e));
const fail=(message,status=400)=>{throw Object.assign(new Error(message),{status});};
/** Validated identity of one episode of one voice track. */
export function episodeOf(p){
 const key=titleKey(String(p?.link||''));if(!key||key.startsWith('uaserials/'))fail('Некоректне посилання');
 const voice=String(p.voice??''),season=String(p.season??''),episode=String(p.episode??'');
 if(!/^\d{1,6}$/.test(voice)||!/^\d{0,4}$/.test(season)||!/^\d{0,4}$/.test(episode))fail('Некоректна серія');
 return {key,voice,season,episode};
}
/** Known breaks for the players; an unknown sponsored episode joins the scan queue. */
export async function adBreaks(db,params,now=Date.now(),{queue=true}={}){
 const e=episodeOf(params),args=[e.key,e.voice,e.season,e.episode];
 const row=await db.prepare('SELECT duration,breaks,suggested,scanned_at FROM ad_breaks WHERE title_key=? AND voice=? AND season=? AND episode=?').bind(...args).first();
 const breaks=row?cleanBreaks(JSON.parse(row.breaks),row.duration):[],suggested=row?hints(JSON.parse(row.suggested||'[]'),breaks,row.duration):[];
 if(row&&(breaks.length||now-row.scanned_at<RESCAN))return {duration:row.duration,breaks,...suggested.length?{suggested}:{}};
 // Only a signed-in player adds to the queue: anonymous requests cannot fill it with made-up episodes.
 if(!SPONSORED.has(e.voice)||!queue)return {breaks:[]};
 const queued=await db.prepare('SELECT COUNT(*) AS n FROM ad_scan_queue WHERE attempts<3').first();
 if((queued?.n||0)<QUEUE_MAX)await db.prepare('INSERT OR IGNORE INTO ad_scan_queue(title_key,voice,season,episode,requested_at) VALUES(?,?,?,?,?)').bind(...args,now).run();
 return {breaks:[],pending:true};
}
/** The scanner PC's two calls, behind its own secret: take queued episodes, report what it found. */
export async function adScanner(request,db,env,path,readBody,now=Date.now()){
 const token=request.headers.get('Authorization')?.match(/^Bearer (\S{32,200})$/)?.[1];
 if(!env.AD_SCANNER_TOKEN||!token||!equal(token,env.AD_SCANNER_TOKEN))fail('Не знайдено',404);
 if(path==='/api/ad-breaks/queue'&&request.method==='POST'){
  await db.prepare('DELETE FROM ad_scan_queue WHERE requested_at<?').bind(now-30*86400_000).run();
  const rows=(await db.prepare('SELECT title_key,voice,season,episode FROM ad_scan_queue WHERE attempts<3 ORDER BY attempts,requested_at LIMIT ?').bind(BATCH).all()).results;
  if(rows.length)await db.batch(rows.map(r=>db.prepare('UPDATE ad_scan_queue SET attempts=attempts+1 WHERE title_key=? AND voice=? AND season=? AND episode=?').bind(r.title_key,r.voice,r.season,r.episode)));
  return {items:rows.map(r=>({link:'https://hdrezka.sh/'+r.title_key,voice:r.voice,season:r.season,episode:r.episode}))};
 }
 if(path==='/api/ad-breaks/report'&&request.method==='POST'){
  const b=await readBody(request,65536);if(!Array.isArray(b.items)||b.items.length>50)fail('Некоректний звіт');let saved=0;
  for(const item of b.items){const e=episodeOf(item),duration=Number(item.duration);if(!(duration>60&&duration<6*3600))fail('Некоректна тривалість');
   const breaks=cleanBreaks(item.breaks,duration),suggested=hints(item.suggested,breaks,duration),args=[e.key,e.voice,e.season,e.episode];
   await db.batch([db.prepare('INSERT INTO ad_breaks(title_key,voice,season,episode,duration,breaks,suggested,scanned_at) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(title_key,voice,season,episode) DO UPDATE SET duration=excluded.duration,breaks=excluded.breaks,suggested=excluded.suggested,scanned_at=excluded.scanned_at').bind(...args,duration,JSON.stringify(breaks),JSON.stringify(suggested),now),
    db.prepare('DELETE FROM ad_scan_queue WHERE title_key=? AND voice=? AND season=? AND episode=?').bind(...args)]);saved++;}
  return {saved};
 }
 if(path==='/api/ad-breaks/classify'&&request.method==='POST'){
  const b=await readBody(request,2*1024*1024),frames=b.frames;
  if(!Array.isArray(frames)||!frames.length||frames.length>AD_FRAMES_PER_CALL||frames.some(f=>typeof f!=='string'||!/^data:image\/jpeg;base64,[A-Za-z0-9+/=]+$/.test(f)||f.length>200_000))fail('Некоректні кадри');
  if(!sentraReady(env))fail('ШІ недоступний',503);
  const day='adscan:'+new Date(now).toISOString().slice(0,10),reserved=await db.prepare('INSERT INTO ai_daily_budget(day,calls) VALUES(?,?) ON CONFLICT(day) DO UPDATE SET calls=calls+excluded.calls WHERE calls+excluded.calls<=? RETURNING calls').bind(day,frames.length,AD_FRAMES_DAILY).first();
  if(!reserved)fail('Денний ліміт ШІ вичерпано',429);
  // Only Sentra classifies (SENTRA.md: no AI of KIK's own): no usable answer leaves the frames unknown, the scanner tries later.
  const answers=await viaSentraFrames(env,frames)??frames.map(()=>null);
  return {ad:answers.map(a=>a?a[0]:null),overlay:answers.map(a=>a?a[1]:null)};
 }
 fail('Не знайдено',404);
}
