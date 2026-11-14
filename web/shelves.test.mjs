import {test} from 'node:test';import assert from 'node:assert/strict';
import {shelf,candidates,shelfQueries,SHELVES,freshRelease} from './shelves.mjs';
import {pickCard} from './holiday-films.mjs';

// Search cards as the catalogue gives them: the source's Russian name, «year, country, genre», a link with the year.
const card=(section,slug,title,year)=>({link:`https://hdrezka.sh/${section}/drama/${slug}-${year}.html`,title,poster:'https://static.hdrezka.ac/p.jpg',info:year+', США, Драмы',type:''});
const movie=(id,title,original,date,extra={})=>({id,title,original_title:original,release_date:date,genre_ids:[18],...extra});
const series=(id,name,original,date,extra={})=>({id,name,original_name:original,first_air_date:date,genre_ids:[18],...extra});
const NOW=Date.UTC(2026,10,14);
function tmdb(lists){const asked=[];return {asked,fetcher:async url=>{asked.push(url);const path=new URL(url).pathname;const results=lists[path];return {ok:!!results,status:results?200:404,json:async()=>({results})};}};}

test('the week\'s Top 10s are TMDB\'s trending films and trending series; cartoons and anime are this year\'s and last year\'s',()=>{
 assert.deepEqual(Object.keys(SHELVES).sort(),['anime','cartoons','documentary','films','new-documentary','series']);assert.equal(SHELVES.films.limit,10);assert.equal(SHELVES.series.limit,10);
 assert.deepEqual(shelfQueries('films',NOW).map(([k,p])=>[k,p.split('?')[0]]),[['movie','/trending/movie/week']]);
 assert.deepEqual(shelfQueries('series',NOW).map(([k,p])=>[k,p.split('?')[0]]),[['tv','/trending/tv/week']]);
 const [m,t]=shelfQueries('cartoons',NOW).map(([,p])=>new URLSearchParams(p.split('?')[1]));
 assert.equal(m.get('with_genres'),'16');assert.equal(m.get('primary_release_date.gte'),'2025-01-01');assert.equal(m.get('primary_release_date.lte'),'2026-11-14');
 assert.equal(t.get('first_air_date.gte'),'2025-01-01');assert.equal(m.get('sort_by'),'popularity.desc');assert.equal(m.get('language'),'ru-RU');
 assert.ok(shelfQueries('anime',NOW).every(([,p])=>new URLSearchParams(p.split('?')[1]).get('with_original_language')==='ja'));
 assert.throws(()=>shelfQueries('popular',NOW),/Невідома полиця/);
});
test('candidates alternate a film and a series, keep both names, and know a cartoon from a series',()=>{
 const lists=[{kind:'movie',results:[movie(1,'Одиссея','The Odyssey','2026-07-17'),movie(2,'Зверополис 2','Zootopia 2','2025-11-26',{genre_ids:[16,10751]}),movie(3,'Без даты','No Date',''),movie(4,'18+','Adult','2026-01-01',{adult:true})]},
  {kind:'tv',results:[series(10,'Очень странные дела','Stranger Things','2016-07-15'),series(11,'Аркейн','Arcane','2021-11-06',{genre_ids:[16]})]}];
 const out=candidates('films',lists);
 assert.deepEqual(out.map(e=>[e.id,e.year,e.kind]),[['movie:1',2026,'film'],['tv:10',2016,'series'],['movie:2',2025,'cartoon'],['tv:11',2021,'cartoon']]);
 assert.deepEqual(out[0].queries,['Одиссея','The Odyssey']);
 const anime=[{kind:'movie',results:[movie(5,'Клинок','Kimetsu','2025-07-18',{genre_ids:[16],original_language:'ja'}),movie(6,'Элио','Elio','2025-06-20',{genre_ids:[16],original_language:'en'})]}];
 assert.deepEqual(candidates('cartoons',anime).map(e=>e.id),['movie:6'],'cartoons leave Japanese animation to «Аніме»');
});
test('a series is found among series only, a film never in the series section',()=>{
 const st={year:2016,queries:['Очень странные дела','Stranger Things'],kind:'series'};
 assert.equal(pickCard(st,[card('films','1-ochen-strannye-dela','Очень странные дела',2016),card('series','2-ochen-strannye-dela','Очень странные дела',2016)]).card.link.includes('/series/'),true);
 assert.equal(pickCard(st,[card('films','1-ochen-strannye-dela','Очень странные дела',2016)]),null,'a film of the same name is not the series');
 assert.equal(pickCard({year:2026,queries:['Одиссея'],kind:'film'},[card('series','3-odisseya','Одиссея',2026)]),null,'a series is not the film');
});
test('the shelf keeps TMDB\'s order, leaves out what the catalogue lacks, stops at its limit and searches each title once a day',async()=>{
 const films=Array.from({length:12},(_,n)=>movie(100+n,'Фильм '+n,'Film '+n,'2026-0'+(1+n%9)+'-01'));
 const {fetcher,asked}=tmdb({'/3/trending/movie/week':films,'/3/trending/tv/week':[series(200,'Сериал','Show','2025-03-01')]});
 let calls=0;const lacking=new Set(['Фильм 1','Film 1']);
 const search=async({query})=>{calls++;if(lacking.has(query))return [];const m=query.match(/^(?:Фильм|Film) (\d+)$/);if(m)return [card('films',(300+Number(m[1]))+'-film',('Фильм '+m[1]),2026)];if(query==='Сериал')return [card('series','400-serial','Сериал',2025)];return [];};
 let clock=NOW;const cache=new Map(),ask=()=>shelf('films',{token:'t',fetcher,search,cache,now:()=>clock});
 const r=await ask();
 assert.deepEqual(r.items.map(i=>i.title),['Фильм 0','Фильм 2','Фильм 3','Фильм 4','Фильм 5','Фильм 6','Фильм 7','Фильм 8','Фильм 9','Фильм 10']);assert.equal(r.partial,false);
 assert.ok(asked.every(u=>u.includes('/trending/movie/week')),'the films Top 10 reads films only');
 assert.deepEqual((await shelf('series',{token:'t',fetcher,search,cache,now:()=>clock})).items.map(i=>i.title),['Сериал'],'the series Top 10 reads series only');
 calls=0;const first=calls;assert.deepEqual((await ask()).items.map(i=>i.title),r.items.map(i=>i.title));assert.equal(calls,first,'nothing is searched again the same day');
 clock+=86400000+1;await ask();assert.ok(calls>first,'a day later the titles are searched again');
});
test('a shelf out of time is partial and goes on from where it stopped; without TMDB it is unavailable',async()=>{
 const films=Array.from({length:6},(_,n)=>movie(500+n,'Кино '+n,'Movie '+n,'2026-02-01'));
 const {fetcher}=tmdb({'/3/trending/movie/week':films,'/3/trending/tv/week':[]});
 let clock=NOW;const cache=new Map(),search=async({query})=>{clock+=9000;return /^Кино \d$/.test(query)?[card('films','6'+query.slice(-1)+'-kino','Кино '+query.slice(-1),2026)]:[];};
 const r=await shelf('films',{token:'t',fetcher,search,cache,now:()=>clock,budget:20000});
 assert.equal(r.partial,true);assert.deepEqual(r.items.map(i=>i.title),['Кино 0','Кино 1','Кино 2']);
 const next=await shelf('films',{token:'t',fetcher,search,cache,now:()=>clock,budget:20000});
 assert.deepEqual(next.items.map(i=>i.title).slice(0,3),['Кино 0','Кино 1','Кино 2'],'found titles come from the cache, in order');
 await assert.rejects(shelf('films',{token:'',fetcher,search}),e=>e.status===503);
 await assert.rejects(shelf('popular',{token:'t',fetcher,search}),e=>e.status===400);
});

test('«Топ-10 документальних»: the documentaries among the week\'s trends first, then this year\'s and last year\'s most popular',()=>{
 const q=shelfQueries('documentary',NOW);assert.equal(SHELVES.documentary.limit,10);
 assert.deepEqual(q.filter(([,,tier])=>tier===0).map(([k,p])=>[k,p.split('?')[0],new URLSearchParams(p.split('?')[1]).get('page')]),
  [['movie','/trending/movie/week','1'],['tv','/trending/tv/week','1'],['movie','/trending/movie/week','2'],['tv','/trending/tv/week','2'],['movie','/trending/movie/week','3'],['tv','/trending/tv/week','3']]);
 const [dm,dt]=q.filter(([,,tier])=>tier===1).map(([k,p])=>[k,new URLSearchParams(p.split('?')[1])]);
 assert.deepEqual([dm[0],dt[0]],['movie','tv']);assert.equal(dm[1].get('with_genres'),'99');assert.equal(dm[1].get('primary_release_date.gte'),'2025-01-01');
 assert.equal(dt[1].get('without_genres'),'10764,10767,10763','reality, talk and news shows stay out');
 const doc=(id,name,date,genres=[99])=>movie(id,name,name,date,{genre_ids:genres}),show=(id,name,date,genres=[99])=>series(id,name,name,date,{genre_ids:genres});
 const lists=[{kind:'movie',tier:0,results:[doc(1,'Drama',`2026-01-01`,[18]),doc(2,'Doc A','2026-02-01')]},{kind:'tv',tier:0,results:[show(3,'Reality','2026-01-01',[99,10764]),show(4,'Doc B','2025-03-01')]},
  {kind:'movie',tier:0,results:[doc(5,'Doc C','2024-05-01')]},{kind:'movie',tier:1,results:[doc(2,'Doc A','2026-02-01'),doc(6,'Doc D','2025-01-01')]},{kind:'tv',tier:1,results:[show(7,'Doc E','2026-04-01')]}];
 assert.deepEqual(candidates('documentary',lists).map(e=>e.queries[0]),['Doc A','Doc B','Doc C','Doc E','Doc D'],'trends first (page 2 after page 1), then the fill in turns; a title once, no drama, no reality');
 assert.deepEqual(candidates('films',[{kind:'movie',results:[doc(1,'Drama','2026-01-01',[18])]}]).map(e=>e.queries[0]),['Drama'],'other shelves keep every genre');
});
test('«Нові документальні»: the source\'s newest documentary series and films of this year and last, in turns',async()=>{
 const doc=(section,n,year)=>({link:`https://hdrezka.sh/${section}/documentary/${n}-doc-${year}.html`,title:'Doc '+n,info:year+(section==='series'?' - ...':'')+', США, Документальные'});
 const pages={'series:1':[doc('series',1,2026),doc('series',2,1999),doc('series',3,2025)],'series:2':[doc('series',4,2026)],'series:3':[],
  'films:1':[doc('films',10,2026),doc('films',11,2012)],'films:2':[doc('films',12,2025),doc('films',10,2026)],'films:3':[]};
 const asked=[];const list=async({category,genre,filter,page})=>{asked.push([category,genre,filter,page]);return pages[category+':'+page]||[];};
 const r=await shelf('new-documentary',{list,now:()=>NOW});
 assert.deepEqual(r.items.map(i=>i.title),['Doc 1','Doc 10','Doc 3','Doc 12','Doc 4'],'a series and a film in turns, old ones and repeats left out');
 assert.equal(r.partial,false);assert.ok(asked.every(([,genre,filter])=>genre==='documentary'&&filter==='last'));
 assert.equal(freshRelease({info:'2025 - ..., США'},NOW),true);assert.equal(freshRelease({info:'2024, США'},NOW),false);assert.equal(freshRelease({info:'США'},NOW),false);
 const half=await shelf('new-documentary',{list:async p=>{if(p.category==='films')throw Error('down');return pages['series:'+p.page]||[];},now:()=>NOW});
 assert.deepEqual(half.items.map(i=>i.title),['Doc 1','Doc 3','Doc 4']);assert.equal(half.partial,true,'a failed section is not kept');
 await assert.rejects(shelf('new-documentary',{list:async()=>{throw Error('down');},now:()=>NOW}),/down/);
});
