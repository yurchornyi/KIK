// KIK's AI goes only through Sentra, the owner's AI centre (secrets SENTRA_URL and SENTRA_TOKEN, which Sentra writes
// itself): Sentra picks the model for a role, keeps limits and spend, falls back by itself (the free cloud, the owner's
// PC, Cloudflare's free models, Claude Haiku last) and learns each feature apart (x-sentra-skill). KIK keeps no AI of its
// own (SENTRA.md): when Sentra does not answer usably, the feature falls back to its plain, non-AI result.
export const sentraReady=env=>!!(env?.SENTRA_URL&&env?.SENTRA_TOKEN);
/** True when a model can answer: Sentra is set up. */
export const aiReady=sentraReady;
/** Sentra's daily AI limit for KIK (429 project_daily_limit above it). KIK's own caps together stay under it (sentra-ai.test). */
export const SENTRA_DAILY=600;
/** Gives back calls reserved in KIK's own ai_daily_budget when Sentra spent nothing on them (a 429, or no answer for frames). */
export const giveBack=(db,day,calls=1)=>db.prepare('UPDATE ai_daily_budget SET calls=MAX(0,calls-?) WHERE day=?').bind(calls,day).run();
let warnedOff=false;
async function viaSentra(env,{skill,role,messages,maxTokens,temperature,json,timeout,cache,who}){
 const r=await fetch(String(env.SENTRA_URL).replace(/\/+$/,'')+'/v1/chat/completions',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+String(env.SENTRA_TOKEN).trim(),'x-sentra-skill':skill,'x-sentra-deadline-ms':String(timeout),...(cache?{'x-sentra-cache':String(cache)}:{})},body:JSON.stringify({model:'role:'+role,messages,max_tokens:maxTokens,temperature,...(json?{response_format:{type:'json_object'}}:{}),...(who?{sentra:{who}}:{})}),signal:AbortSignal.timeout(timeout+2000)});
 if(!r.ok)throw Object.assign(Error('Sentra '+r.status),{status:r.status});
 return {content:String((await r.json())?.choices?.[0]?.message?.content??''),sample:r.headers.get('x-sentra-sample-id')||''};
}
/** Tells Sentra an answer was unusable (POST /v1/feedback, «rejected»): it counts against the model on the skill's ladder
 * and into its next lesson. Best effort: a lost report changes nothing for the viewer. */
async function reportRejected(env,sample,note){
 if(!sample)return;
 await fetch(String(env.SENTRA_URL).replace(/\/+$/,'')+'/v1/feedback',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+String(env.SENTRA_TOKEN).trim()},body:JSON.stringify({sample,signal:'rejected',note:String(note).slice(0,200)}),signal:AbortSignal.timeout(3000)}).catch(()=>{});
}
/**
 * Sentra's answer as {value,by:'sentra'} when `accept` takes it (it returns the value, or null / throws when the answer is
 * unusable). Otherwise {value:null,why}: 'off' (no SENTRA_URL/TOKEN), 'limit' (a 429: a limit or Sentra's spend guard, no
 * error — the caller gives its own budget back), 'error' (Sentra failed or timed out) or 'rejected' (an unusable answer).
 * The feature then keeps its plain result. `cache` asks Sentra to keep the answer that many seconds (x-sentra-cache).
 * `who` tells the owner whom the answer was for («профіль Юра», at most 40 characters) in Sentra's own `sentra` field of the
 * body, which Sentra takes out before the cache or any model sees the request.
 */
export async function complete(env,{skill,role='fast',messages,maxTokens,temperature=0,json=false,timeout,cache,who},accept,label){
 if(!sentraReady(env)){if(!warnedOff){warnedOff=true;console.warn('KIK AI: SENTRA_URL or SENTRA_TOKEN is missing, AI features keep their plain results');}return {value:null,why:'off'};}
 let raw,sample;
 try{({content:raw,sample}=await viaSentra(env,{skill,role,messages,maxTokens,temperature,json,timeout,cache,who}));}
 catch(err){if(err?.status===429)return {value:null,why:'limit'};console.warn(label+': Sentra '+(err?.status?'HTTP '+err.status:err?.name==='TimeoutError'?'timed out':'unreachable')+', plain result kept');return {value:null,why:'error'};}
 let why='no usable value';
 try{const value=accept(raw);if(value!=null)return {value,by:'sentra'};}catch(err){why=err?.message||why;}
 console.warn(label+': Sentra answered unusably, plain result kept');await reportRejected(env,sample,label+': '+why);return {value:null,why:'rejected'};
}
