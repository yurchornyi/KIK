// KIK's text AI goes through Sentra, the owner's AI centre (secrets SENTRA_URL and SENTRA_TOKEN, which Sentra writes
// itself): Sentra picks the model for a role, keeps limits and spend, and learns each feature apart (x-sentra-skill).
// Workers AI stays the fallback: without Sentra, or when it fails or gives an unusable answer, the same messages go to
// the Workers AI models in order, each with its own timeout.
export const sentraReady=env=>!!(env?.SENTRA_URL&&env?.SENTRA_TOKEN);
/** True when some model can answer: Sentra or the Workers AI binding. */
export const aiReady=env=>sentraReady(env)||!!env?.AI;
async function viaSentra(env,{skill,role,messages,maxTokens,temperature,json,timeout}){
 const r=await fetch(String(env.SENTRA_URL).replace(/\/+$/,'')+'/v1/chat/completions',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+String(env.SENTRA_TOKEN).trim(),'x-sentra-skill':skill,'x-sentra-deadline-ms':String(timeout)},body:JSON.stringify({model:'role:'+role,messages,max_tokens:maxTokens,temperature,...(json?{response_format:{type:'json_object'}}:{})}),signal:AbortSignal.timeout(timeout+2000)});
 if(!r.ok)throw Error('Sentra '+r.status);
 return String((await r.json())?.choices?.[0]?.message?.content??'');
}
/**
 * The first answer `accept` takes (it returns the value, or null / throws when the answer is unusable), as {value,by}:
 * Sentra first, then each [model, timeout ms] of Workers AI; null when nothing answered usably.
 */
export async function complete(env,{skill,role='fast',messages,maxTokens,temperature=0,json=false,timeout},models,accept,label){
 if(sentraReady(env))try{const value=accept(await viaSentra(env,{skill,role,messages,maxTokens,temperature,json,timeout}));if(value!=null)return {value,by:'sentra'};}catch{}
 if(sentraReady(env))console.warn(label+': Sentra unavailable or unusable answer, Workers AI takes over');
 for(const [model,ms] of env.AI?models:[])try{let timer;const r=await Promise.race([env.AI.run(model,{messages,max_tokens:maxTokens,temperature}),new Promise((_,reject)=>{timer=setTimeout(()=>reject(Error('AI timeout')),ms);})]).finally(()=>clearTimeout(timer));const value=accept(r?.response??r);if(value!=null)return {value,by:model};}catch{console.warn(label+': '+model+' unavailable or unusable answer');}
 return null;
}
