const MAX=64*1024;
/** `signer` is the pinned SHA-256 of KIK's signing certificate: metadata naming any other signer is refused, and without a pin nothing is served. */
export function validateRelease(r,repo,signer){
 const u=new URL(r.apk_url);
 if(!/^[a-f0-9]{64}$/.test(signer||"")||String(r.signer_sha256).toLowerCase()!==signer)throw new Error("Unexpected release signer");
 if(r.schema!==1||r.application_id!=="kik.app"||!Number.isSafeInteger(r.version_code)||r.version_code<1||r.version_code>2147483647||typeof r.version_name!=="string"||!r.version_name||r.version_name.length>64||r.min_sdk!==24||!Number.isSafeInteger(r.size_bytes)||r.size_bytes<1||r.size_bytes>96*1024*1024||!/^\w[\w.-]+\.apk$/.test(r.apk_name)||!/^\b[a-f0-9]{64}\b$/i.test(r.sha256)||!/^\b[a-f0-9]{64}\b$/i.test(r.signer_sha256)||u.protocol!=="https:"||u.host!=="github.com"||u.username||u.password||u.search||u.hash||!u.pathname.startsWith(`/${repo}/releases/download/`)||!u.pathname.endsWith("/"+r.apk_name)||u.pathname.includes("%"))throw new Error("Invalid release metadata");
 return Object.fromEntries(["schema","application_id","version_code","version_name","min_sdk","apk_name","apk_url","size_bytes","sha256","signer_sha256"].map(k=>[k,r[k]]));
}
async function limitedJson(response){
 if(!response.ok)throw new Error("Release unavailable");
 const reader=response.body.getReader();let chunks=[],length=0;
 try{for(;;){let {done,value}=await reader.read();if(done)break;length+=value.byteLength;if(length>MAX)throw new Error("Metadata limit");chunks.push(value);}}
 finally{await reader.cancel();}
 const all=new Uint8Array(length);let at=0;for(const b of chunks){all.set(b,at);at+=b.length;}return JSON.parse(new TextDecoder().decode(all));
}
function json(value,status=200,ttl=0){return new Response(JSON.stringify(value),{status,headers:{"Content-Type":"application/json; charset=utf-8","Cache-Control":ttl?`public, max-age=${ttl}`:"no-store","X-Content-Type-Options":"nosniff"}});}
export async function handle(request,env,ctx,{cache=caches.default,fetcher=fetch}={}){
 const url=new URL(request.url),repo=env.REPOSITORY;
 if(!["GET","HEAD"].includes(request.method))return new Response(null,{status:405,headers:{Allow:"GET, HEAD"}});
 if(url.pathname==="/health")return json({service:"kik-updates",ok:true});
 if(url.pathname==="/")return Response.redirect(`https://github.com/${repo}`,302);
 if(!["/v1/update.json","/download"].includes(url.pathname))return json({error:"not_found"},404);
 const key=new Request(url.origin+"/v1/update.json");let response=await cache.match(key);
 if(!response){
  try{
   const upstream=await fetcher(`https://github.com/${repo}/releases/latest/download/update.json`,{headers:{"User-Agent":"KIK-Updates/1","Accept":"application/json"},signal:AbortSignal.timeout(10000),redirect:"follow"});
   const release=validateRelease(await limitedJson(upstream),repo,env.SIGNER_SHA256);response=json(release,200,300);ctx.waitUntil(cache.put(key,response.clone()));
  }catch{return json({error:"release_temporarily_unavailable"},503);}
 }
 if(url.pathname==="/download"){const release=await response.json();return Response.redirect(release.apk_url,302);}
 return request.method==="HEAD"?new Response(null,{status:response.status,headers:response.headers}):response;
}
export default {fetch:handle};
