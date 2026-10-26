import {test} from 'node:test';
import assert from 'node:assert/strict';
import {studiosFor} from './public/studios.js';
import {createStudioCatalogue} from './studio-catalogue.mjs';
import {CataloguePages,catalogueSelection,selectionQuery} from './public/catalogue-view.js';

const token='test-only';
const titles=Array.from({length:20},(_,i)=>({id:i+1,name:'Series '+(i+1),original_name:'Series '+(i+1),first_air_date:'2024-01-01'}));
const card=i=>({link:'https://hdrezka.sh/series/drama/'+i+'-series-2024.html',title:'Series '+i,info:'2024, США, Драма'});
function service({results=titles,search=async({query})=>[card(Number(query.split(' ').at(-1)))],detail=async()=>({genres:['Драма','Комедия']}),total=1,fetcher}={}){
 const calls=[];
 return {calls,api:createStudioCatalogue({search,genres:async()=>[{slug:'comedy',name:'Комедия'}],film:detail,fetcher:fetcher|| (async value=>{const u=new URL(value);calls.push(u);if(u.pathname.endsWith('/search/company'))return Response.json({results:[{id:22,name:'Netflix Studios'},{id:23,name:'Netflix Streaming Availability'}]});return Response.json({results,total_pages:total});})})};
}
test('studio marks use original networks and exact production companies, never watch providers',()=>{
 assert.deepEqual(studiosFor({networks:[{id:49,name:'HBO'}]}),['hbo']);
 assert.deepEqual(studiosFor({production_companies:[{name:'Apple Studios'}]}),['apple']);
 assert.deepEqual(studiosFor({production_companies:[{name:'Not Netflix Studios'}],networks:[], 'watch/providers':{Netflix:true}}),[]);
 assert.deepEqual(studiosFor({production_companies:[{name:'Pixar'},{name:'Walt Disney Pictures'}]}),['disney','pixar']);
});
test('the studio selection is validated, round-trips in the URL and travels with every page',async()=>{
 const selection=catalogueSelection(new URLSearchParams('c=series&g=comedy&s=last&studio=hbo'));
 assert.equal(selection.studio,'hbo');assert.equal(selectionQuery(selection),'c=series&g=comedy&s=last&studio=hbo');
 assert.equal(catalogueSelection({studio:'<script>'}).studio,undefined);
 let sent;const model=new CataloguePages(async params=>{sent=params;return {items:[],hasMore:true};},x=>x,selection);
 await model.more();assert.equal(sent.studio,'hbo');assert.equal(model.end,false);assert.equal(model.page,1);
 assert.equal(model.matches({...selection,studio:'apple'}),false);
 model.reset('series','last','comedy','apple');assert.equal(model.studio,'apple');assert.equal(model.page,0);
});
test('an empty studio page does not hide later matches and an explicit final page ends the list',async()=>{
 const model=new CataloguePages(async p=>({items:p.page==='1'?[]:[card(6)],hasMore:p.page==='1'}),c=>c.link,{studio:'hbo'});
 await model.more();assert.equal(model.end,false);await model.more();assert.equal(model.end,true);assert.equal(model.items.length,1);
});
test('a response from an old studio cannot replace the newly selected one',async()=>{
 let finish;const model=new CataloguePages(()=>new Promise(r=>finish=r),c=>c.link,{category:'series',studio:'hbo'});
 const old=model.more();model.reset('series','popular','','apple');finish({items:[card(1)],hasMore:false});
 assert.equal(await old,false);assert.equal(model.items.length,0);assert.equal(model.studio,'apple');
});
test('network discovery pages the full brand catalogue independently of already loaded KIK cards',async()=>{
 const {api,calls}=service();const a=await api.lookup({studio:'hbo',category:'series',page:1},{token});const b=await api.lookup({studio:'hbo',category:'series',page:2},{token});
 assert.equal(a.items.length,5);assert.equal(b.items[0].title,'Series 6');assert.deepEqual(b.items[0].studios,['hbo']);assert.equal(a.hasMore,true);
 assert.equal(calls.length,1,'the provider page is shared across candidate windows');assert.equal(calls[0].searchParams.get('with_networks'),'49|3186');
 assert.equal(calls[0].searchParams.has('with_watch_providers'),false);
 const last=await api.lookup({studio:'hbo',category:'series',page:4},{token});assert.equal(last.hasMore,false);
});
test('a source failure keeps the page retryable rather than silently dropping a title',async()=>{
 const {api}=service({search:async()=>{throw Error('source unavailable');}});
 await assert.rejects(api.lookup({studio:'hbo',category:'series'},{token}),/source unavailable/);
});
test('secondary genres are checked on the matched title, not guessed from its URL',async()=>{
 const {api}=service({results:titles.slice(0,1)});const result=await api.lookup({studio:'hbo',category:'series',genre:'comedy'},{token});
 assert.equal(result.items.length,1);assert.match(result.items[0].link,/drama/);
 const other=service({results:titles.slice(0,1),detail:async()=>({genres:['Драма']})});assert.equal((await other.api.lookup({studio:'hbo',category:'series',genre:'comedy'},{token})).items.length,0);
});
test('remakes, missing years, wrong categories and ambiguous source matches get no studio label',async()=>{
 for(const cards of [[{...card(1),info:'1999, США'}],[{...card(1),info:'США'}],[{...card(1),link:card(1).link.replace('/series/','/films/')}],[card(1),{...card(2),title:'Series 1'}]]){
  const {api}=service({results:titles.slice(0,1),search:async()=>cards});assert.equal((await api.lookup({studio:'hbo',category:'series'},{token})).items.length,0);
 }
});
test('duplicate links on different source mirrors remain one identity',async()=>{
 const {api}=service({results:titles.slice(0,1),search:async()=>[card(1),{...card(1),link:card(1).link.replace('hdrezka.sh','rezka.ag')}]});assert.equal((await api.lookup({studio:'hbo',category:'series'},{token})).items.length,1);
});
test('movie discovery resolves exact company names and never adds unrelated search results',async()=>{
 const {api,calls}=service({results:[]});await api.lookup({studio:'netflix',category:'films'},{token});
 const discover=calls.find(u=>u.pathname.includes('/discover/'));assert.equal(discover.searchParams.get('with_companies'),'22');assert.equal(discover.searchParams.has('with_networks'),false);
});
test('unsupported filters and missing credentials fail without any upstream calls',async()=>{
 const {api,calls}=service();for(const params of [{studio:'evil'},{studio:'hbo',genre:'../'},{studio:'hbo',source:'uaserials'},{studio:'hbo',category:'unknown'}])await assert.rejects(api.lookup(params,{token}));
 await assert.rejects(api.lookup({studio:'hbo',category:'series'},{}),/тимчасово/);assert.equal(calls.length,0);
});
test('artwork can return a confirmed studio even when there is no suitable poster',async()=>{
 const {createArtworkService}=await import('./tmdb.mjs');const fetcher=async url=>Response.json(url.includes('/search/')?{results:[titles[0]]}:{networks:[{id:49}],images:{posters:[],backdrops:[]}});
 const result=await createArtworkService({fetcher}).lookup({...card(1),year:'2024'},{token});assert.deepEqual(result.artwork.studios,['hbo']);assert.equal(result.artwork.poster,undefined);
});
test('a genre narrows the brand discovery itself, so documentaries are found instead of filtered out of popular titles',async()=>{
 const {api,calls}=service({results:[]});await api.lookup({studio:'netflix',category:'films',genre:'documentary'},{token}).catch(()=>{});
 const discover=calls.find(u=>u.pathname.includes('/discover/'));assert.equal(discover.searchParams.get('with_genres'),'99');assert.equal(discover.searchParams.get('without_genres'),'16');
});
test('several studios search any of their networks at once and leave the badge to each title',async()=>{
 const {api,calls}=service();const result=await api.lookup({studio:'hbo,netflix',category:'series',page:1},{token});
 assert.equal(calls.find(u=>u.pathname.includes('/discover/')).searchParams.get('with_networks'),'49|3186|213');assert.deepEqual(result.items[0].studios,[]);
 const films=service({results:[]});await films.api.lookup({studio:'netflix,hbo',category:'films'},{token});
 assert.equal(films.calls.find(u=>u.pathname.includes('/discover/')).searchParams.get('with_companies'),'22');
 await assert.rejects(api.lookup({studio:'hbo,evil',category:'series'},{token}));
});
