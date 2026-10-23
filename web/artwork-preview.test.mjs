import {test} from 'node:test';import assert from 'node:assert/strict';
import {publicArtworkPreview,previewArtwork} from './artwork-preview.mjs';
test('only local public artwork requests can reuse the hosted service when no local key is set',()=>{
 assert.ok(publicArtworkPreview(new Request('http://localhost:8791/api/artwork/batch',{method:'POST'}),{}));assert.ok(!publicArtworkPreview(new Request('https://kiktv.pages.dev/api/artwork'),{}));assert.ok(!publicArtworkPreview(new Request('http://localhost:8791/api/settings'),{}));assert.ok(!publicArtworkPreview(new Request('http://localhost:8791/api/artwork'),{TMDB_READ_TOKEN:'local-key'}));
});
test('preview forwards public artwork only, never browser credentials or profile data',async()=>{
 let call;const request=new Request('http://localhost:8791/api/artwork/batch',{method:'POST',headers:{Cookie:'kik_session=private',Authorization:'Bearer private'},body:JSON.stringify({profile_id:'private-profile',items:[{link:'https://hdrezka.sh/films/fiction/2259-interstellar-2014.html',title:'Interstellar',password:'private-password'}]})});
 const r=await previewArtwork(request,async(url,init)=>{call={url,init};return Response.json({configured:true,results:{}});});assert.equal(call.url,'https://kiktv.pages.dev/api/artwork/batch');assert.ok(!call.init.body.includes('private'));assert.equal(call.init.headers.Cookie,undefined);assert.equal(call.init.headers.Authorization,undefined);assert.equal(r.headers.get('Cache-Control'),'no-store');
});
