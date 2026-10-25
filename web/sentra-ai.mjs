// KIK's AI goes only through Sentra, the owner's AI centre (secrets SENTRA_URL and SENTRA_TOKEN, which Sentra writes
// itself): Sentra picks the model for a role, keeps limits and spend, falls back by itself (the free cloud, the owner's
// PC, Cloudflare's free models, Claude Haiku last) and learns each feature apart (x-sentra-skill). KIK keeps no AI of its
// own (SENTRA.md): when Sentra does not answer usably, the feature falls back to its plain, non-AI result.
export const sentraReady=env=>!!(env?.SENTRA_URL&&env?.SENTRA_TOKEN);
/** True when a model can answer: Sentra is set up. */
export const aiReady=sentraReady;
async function viaSentra(env,{skill,role,messages,maxTokens,temperature,json,timeout}){
 const r=await fetch(String(env.SENTRA_URL).replace(/\/+$/,'')+'/v1/chat/completions',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+String(env.SENTRA_TOKEN).trim(),'x-sentra-skill':skill,'x-sentra-deadline-ms':String(timeout)},body:JSON.stringify({model:'role:'+role,messages,max_tokens:maxTokens,temperature,...(json?{response_format:{type:'json_object'}}:{})}),signal:AbortSignal.timeout(timeout+2000)});
 if(!r.ok)throw Error('Sentra '+r.status);
 return String((await r.json())?.choices?.[0]?.message?.content??'');
}
/**
 * Sentra's answer as {value,by:'sentra'} when `accept` takes it (it returns the value, or null / throws when the answer is
 * unusable); null otherwise — the feature then keeps its plain result. A 429 (a limit, or Sentra's spend guard) is no error.
 */
export async function complete(env,{skill,role='fast',messages,maxTokens,temperature=0,json=false,timeout},accept,label){
 if(!sentraReady(env))return null;
 try{const value=accept(await viaSentra(env,{skill,role,messages,maxTokens,temperature,json,timeout}));if(value!=null)return {value,by:'sentra'};}catch(err){if(!/Sentra 429/.test(String(err?.message)))console.warn(label+': Sentra unavailable or unusable answer, plain result kept');}
 return null;
}
