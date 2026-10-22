import {test} from "node:test";
import assert from "node:assert/strict";
import {handle,validateRelease} from "./worker.mjs";
const repo="yurchornyi/KIK",fixture={schema:1,application_id:"rezkatv.web.app.uk",version_code:12,version_name:"1.2.11-uk",min_sdk:24,apk_name:"KIK-1.2.11-uk.apk",apk_url:`https://github.com/${repo}/releases/download/v1.2.11-uk/KIK-1.2.11-uk.apk`,size_bytes:25000000,sha256:"a".repeat(64),signer_sha256:"b".repeat(64)};
const env={REPOSITORY:repo};
test("only a bounded KIK release from its repository is accepted",()=>{
 assert.deepEqual(validateRelease({...fixture,unexpected:"discarded"},repo),fixture);
 for(const change of [{application_id:"evil"},{version_code:0},{version_code:2.5},{size_bytes:0},{size_bytes:100*1024*1024},{sha256:"bad"},{signer_sha256:"bad"},{apk_url:fixture.apk_url.replace(repo,"other/repo")},{apk_url:fixture.apk_url.replace("https:","http:")},{min_sdk:35},{apk_name:"other.apk"}])assert.throws(()=>validateRelease({...fixture,...change},repo));
});
test("fresh metadata is cached and query parameters cannot select an upstream",async()=>{
 let calls=0,saved=null;const waits=[],ctx={waitUntil:p=>waits.push(p)},cache={match:async()=>saved?.clone(),put:async(k,r)=>{saved=r;}};
 const fetcher=async url=>{calls++;assert.equal(url,`https://github.com/${repo}/releases/latest/download/update.json`);return Response.json(fixture);};
 for(let i=0;i<2;i++){const r=await handle(new Request("https://updates.test/v1/update.json?url=evil"),env,ctx,{cache,fetcher});assert.equal(r.status,200);assert.deepEqual(await r.json(),fixture);await Promise.all(waits);}
 assert.equal(calls,1);
 const download=await handle(new Request("https://updates.test/download"),env,ctx,{cache,fetcher});assert.equal(download.headers.get("Location"),fixture.apk_url);
 const head=await handle(new Request("https://updates.test/v1/update.json",{method:"HEAD"}),env,ctx,{cache,fetcher});assert.equal(await head.text(),"");
});
test("upstream errors and oversized metadata are never published or cached",async()=>{
 const cache={match:async()=>null,put:async()=>assert.fail("must not cache failures")},ctx={waitUntil:()=>{}};
 for(const fetcher of [async()=>new Response("no",{status:404}),async()=>Response.json({...fixture,sha256:"bad"}),async()=>new Response("x".repeat(65537)),async()=>{throw Error("offline");}]){
  const r=await handle(new Request("https://updates.test/v1/update.json"),env,ctx,{cache,fetcher});assert.equal(r.status,503);assert.equal(r.headers.get("cache-control"),"no-store");
 }
});
test("no write endpoint or proxy endpoint",async()=>{
 const context={cache:{},fetcher:()=>assert.fail()};
 assert.equal((await handle(new Request("https://updates.test/health"),env,{},context)).status,200);
 assert.equal((await handle(new Request("https://updates.test/v1/update.json",{method:"POST"}),env,{},context)).status,405);
 assert.equal((await handle(new Request("https://updates.test/private"),env,{},context)).status,404);
});
