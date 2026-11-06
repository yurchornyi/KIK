import {test} from 'node:test';import assert from 'node:assert/strict';
import {translatePublic} from './localization.mjs';import {aliases} from './metadata.mjs';import {createArtworkService} from './tmdb.mjs';
import {createStudioCatalogue} from './studio-catalogue.mjs';import {corrections} from './search-fix.mjs';import {synopsis} from './synopsis.mjs';
import {candidatesOf,recommendations} from './recommendations.mjs';

/**
 * A Worker request cannot await another request's I/O: once the first viewer closes the page its fetch never settles, and
 * neither does any promise built on it (06.11 audit). Each test lets the first call hang for good and checks that a second
 * call for the very same key still answers on its own; with an isolate-wide map of in-flight promises it waited forever.
 */
const never=()=>new Promise(()=>{});
const tick=()=>new Promise(r=>setTimeout(r,5));
function within(promise,ms=2000){let timer;return Promise.race([promise,new Promise((_,reject)=>{timer=setTimeout(()=>reject(Error('waited on another request\'s promise')),ms);})]).finally(()=>clearTimeout(timer));}
async function withFetch(fetcher,run){const original=globalThis.fetch;globalThis.fetch=fetcher;try{return await run();}finally{globalThis.fetch=original;}}
const sentra={SENTRA_URL:'https://sentra.test',SENTRA_TOKEN:'t'};
const sentraReply=content=>Response.json({choices:[{message:{content}}]});

test('public translation: a hung translator call does not hold the same texts for later pages',async()=>{
 const db={prepare(){return {bind(){return {async all(){return {results:[]};},async run(){return {};}};}};}};
 let calls=0;const fetcher=async()=>++calls===1?never():Response.json([['Тиха гавань']]);
 translatePublic(db,['Тихая гавань'],{fetcher});await tick();
 assert.equal((await within(translatePublic(db,['Тихая гавань'],{fetcher}))).get('Тихая гавань'),'Тиха гавань');assert.equal(calls,2);
});

test('other spellings: a hung Wikidata lookup does not hold the same query for later requests',async()=>{
 let hang=true;await withFetch(async()=>hang?never():Response.json({search:[],entities:{}}),async()=>{
  aliases('Тихая гавань');await tick();hang=false;
  assert.deepEqual(await within(aliases('Тихая гавань')),[]);
 });
});

test('artwork: a hung TMDB lookup neither holds the same title nor keeps a place in the limit for good',async()=>{
 const link='https://hdrezka.sh/films/fiction/2259-interstellar-2014.html';let clock=1_000_000,hang=true;
 const fetcher=async url=>{if(hang)return never();if(url.includes('/search/movie'))return Response.json({results:[{id:157336,title:'Интерстеллар',release_date:'2014-11-05'}]});return Response.json({posters:[{file_path:'/p.jpg',width:1000,height:1500,iso_639_1:'uk',vote_average:5}],backdrops:[]});};
 const service=createArtworkService({fetcher,now:()=>clock});
 // Twelve viewers left while their posters were loading: the limit is full, so a new title is told to come back later.
 for(let i=0;i<12;i++)service.lookup({link:`https://hdrezka.sh/films/drama/${i+1}-film-2020.html`,title:'Фільм '+i},{token:'secret'});
 await tick();assert.equal((await service.lookup({link,title:'Интерстеллар'},{token:'secret'})).retryAfter,10);
 // Those lookups never finish; a while later their places are free again and the same titles load.
 clock+=21_000;service.lookup({link,title:'Интерстеллар'},{token:'secret'});await tick();hang=false;
 const r=await within(service.lookup({link,title:'Интерстеллар'},{token:'secret'}));assert.equal(r.artwork.id,157336);
});

test('studio catalogue: a hung discovery page does not hold the same page for later requests',async()=>{
 let hang=true;const results=[{id:1,name:'Series 1',original_name:'Series 1',first_air_date:'2024-01-01'}];
 const fetcher=async value=>hang?never():new URL(value).pathname.endsWith('/search/company')?Response.json({results:[]}):Response.json({results,total_pages:1});
 const api=createStudioCatalogue({fetcher,search:async()=>[{link:'https://hdrezka.sh/series/drama/1-series-2024.html',title:'Series 1',info:'2024, США, Драма'}],genres:async()=>[],film:async()=>({genres:['Драма']})});
 api.lookup({studio:'hbo',category:'series',page:1},{token:'test-only'});await tick();hang=false;
 assert.equal((await within(api.lookup({studio:'hbo',category:'series',page:1},{token:'test-only'}))).items[0].title,'Series 1');
});

test('search fixes: a hung Sentra call does not hold the same query for later searches',async()=>{
 const db={prepare(){return {bind(){return {async first(){return {calls:1};},async run(){return {};}};}};}};
 let calls=0;await withFetch(async()=>++calls===1?never():sentraReply('{"titles":["Интерстеллар"]}'),async()=>{
  corrections(db,sentra,'интерстелар');await tick();
  assert.deepEqual(await within(corrections(db,sentra,'интерстелар')),['Интерстеллар']);assert.equal(calls,2);
 });
});

test('synopsis: a hung page load does not hold the same title for later viewers',async()=>{
 const db={prepare(sql){return {bind(){return {async first(){return sql.includes('ai_daily_budget')?{calls:1}:null;},async run(){return {};}};}};}};
 const page={title:'Святой Паисий',series:true,genres:['Драмы'],description:'История начинается с рождения святого Паисия, в миру Арсения Эзнепидиса, в Фарасах, Каппадокия. За несколько дней до того, как жители покинули село...'};
 const link='https://hdrezka.sh/series/drama/89179-svyatoy-paisiy-ot-farasy-na-nebesa-2022.html',good='Хлопчик з Каппадокії стає ченцем Паїсієм і крізь війни та вигнання шукає свій шлях до Бога.';
 let loads=0;const load=async()=>++loads===1?never():page;
 await withFetch(async()=>sentraReply(good),async()=>{
  synopsis(db,sentra,link,load,async(d,f)=>f);await tick();
  assert.equal((await within(synopsis(db,sentra,link,load,async(d,f)=>f))).summary,good);assert.equal(loads,2);
 });
});

test('recommendations: a hung ranking does not hold the profile for its next request',async()=>{
 const card=(id,title)=>({link:`https://hdrezka.sh/films/drama/${id}-film-2020.html`,title,info:'2020, США, Драми'});
 const items=[card(1,'Space journey'),card(2,'Space drama'),card(3,'Quiet history'),card(4,'Comedy day')];candidatesOf(items);
 const records=[{kind:'rating',updated_at:1,data:{...card(9,'Space story'),genre:'Космос, Драма',value:1}}];
 const db={prepare(sql){return {bind(){return this;},async all(){return {results:sql.includes('synopsis_cache')?[]:records.map(r=>({...r,data:JSON.stringify(r.data)}))};},
  async first(){if(sql.includes('ai_daily_budget'))return {calls:1};if(sql.includes('enhanced_recs'))return {enhanced_recs:1};return null;},async run(){return {};}};}};
 let calls=0;await withFetch(async()=>++calls===1?never():sentraReply('{"order":[2,0,1,3]}'),async()=>{
  const profile={id:'p1',name:'Юра',enhanced_recs:1};
  recommendations(db,profile,items,sentra);await tick();
  assert.equal((await within(recommendations(db,profile,items,sentra))).mode,'ai');assert.equal(calls,2);
 });
});

test('edge cache: a background refresh that never finished stops blocking that URL\'s refresh after two minutes',async()=>{
 // The studio catalogue keeps the fetch it was created with, so the Worker is loaded with one this test controls.
 const original={fetch:globalThis.fetch,caches:globalThis.caches,now:Date.now},store=new Map();let clock=Date.now(),sources=0;
 globalThis.fetch=async()=>{sources++;return never();};
 const {default:worker}=await import('./server.mjs');
 const url='https://kik.example/api/catalogue?studio=hbo&category=series&page=1';
 globalThis.caches={default:{async match(k){return store.get(k.url)?.clone();},async put(k,r){store.set(k.url,r);},async delete(k){store.delete(k.url);}}};
 store.set(url+'&__presentation=uk5',new Response(JSON.stringify({items:[]}),{headers:{'Content-Type':'application/json','X-KIK-Stored':String(clock-2*3600_000)}}));
 Date.now=()=>clock;
 const env={TMDB_READ_TOKEN:'test-only',DB:{prepare(){return {bind(){return {async first(){return null;},async all(){return {results:[]};},async run(){return {meta:{changes:1}};}};}};}}};
 const ask=()=>worker.fetch(new Request(url),env,{waitUntil(){}});
 try{
  assert.equal((await ask()).status,200);await tick();const first=sources;assert.ok(first>0,'the stale copy is refreshed in the background');
  await ask();await tick();assert.equal(sources,first,'a refresh under way is not started twice');
  clock+=3*60_000;await ask();await tick();assert.ok(sources>first,'a refresh that never finished no longer blocks the next one');
 }finally{globalThis.fetch=original.fetch;globalThis.caches=original.caches;Date.now=original.now;}
});
