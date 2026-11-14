import {test} from 'node:test';import assert from 'node:assert/strict';
import {shelf,candidates,shelfQueries,SHELVES} from './shelves.mjs';
import {pickCard} from './holiday-films.mjs';

// Search cards as the catalogue gives them: the source's Russian name, «year, country, genre», a link with the year.
const card=(section,slug,title,year)=>({link:`https://hdrezka.sh/${section}/drama/${slug}-${year}.html`,title,poster:'https://static.hdrezka.ac/p.jpg',info:year+', США, Драмы',type:''});
const movie=(id,title,original,date,extra={})=>({id,title,original_title:original,release_date:date,genre_ids:[18],...extra});
const series=(id,name,original,date,extra={})=>({id,name,original_name:original,first_air_date:date,genre_ids:[18],...extra});
const NOW=Date.UTC(2026,10,14);
function tmdb(lists){const asked=[];return {asked,fetcher:async url=>{asked.push(url);const path=new URL(url).pathname;const results=lists[path];return {ok:!!results,status:results?200:404,json:async()=>({results})};}};}

test('the week\'s Top 10 is TMDB\'s trending films and series in turns; cartoons and anime are this year\'s and last year\'s',()=>{
 assert.deepEqual(Object.keys(SHELVES).sort(),['anime','cartoons','trending']);assert.equal(SHELVES.trending.limit,10);
 assert.deepEqual(shelfQueries('trending',NOW).map(([k,p])=>[k,p.split('?')[0]]),[['movie','/trending/movie/week'],['tv','/trending/tv/week']]);
 const [m,t]=shelfQueries('cartoons',NOW).map(([,p])=>new URLSearchParams(p.split('?')[1]));
 assert.equal(m.get('with_genres'),'16');assert.equal(m.get('primary_release_date.gte'),'2025-01-01');assert.equal(m.get('primary_release_date.lte'),'2026-11-14');
 assert.equal(t.get('first_air_date.gte'),'2025-01-01');assert.equal(m.get('sort_by'),'popularity.desc');assert.equal(m.get('language'),'ru-RU');
 assert.ok(shelfQueries('anime',NOW).every(([,p])=>new URLSearchParams(p.split('?')[1]).get('with_original_language')==='ja'));
 assert.throws(()=>shelfQueries('popular',NOW),/Невідома полиця/);
});
test('candidates alternate a film and a series, keep both names, and know a cartoon from a series',()=>{
 const lists=[{kind:'movie',results:[movie(1,'Одиссея','The Odyssey','2026-07-17'),movie(2,'Зверополис 2','Zootopia 2','2025-11-26',{genre_ids:[16,10751]}),movie(3,'Без даты','No Date',''),movie(4,'18+','Adult','2026-01-01',{adult:true})]},
  {kind:'tv',results:[series(10,'Очень странные дела','Stranger Things','2016-07-15'),series(11,'Аркейн','Arcane','2021-11-06',{genre_ids:[16]})]}];
 const out=candidates('trending',lists);
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
 const {fetcher}=tmdb({'/3/trending/movie/week':films,'/3/trending/tv/week':[series(200,'Сериал','Show','2025-03-01')]});
 let calls=0;const lacking=new Set(['Фильм 1','Film 1']);
 const search=async({query})=>{calls++;if(lacking.has(query))return [];const m=query.match(/^(?:Фильм|Film) (\d+)$/);if(m)return [card('films',(300+Number(m[1]))+'-film',('Фильм '+m[1]),2026)];if(query==='Сериал')return [card('series','400-serial','Сериал',2025)];return [];};
 let clock=NOW;const cache=new Map(),ask=()=>shelf('trending',{token:'t',fetcher,search,cache,now:()=>clock});
 const r=await ask();
 assert.deepEqual(r.items.map(i=>i.title),['Фильм 0','Сериал','Фильм 2','Фильм 3','Фильм 4','Фильм 5','Фильм 6','Фильм 7','Фильм 8','Фильм 9']);assert.equal(r.partial,false);
 const first=calls;assert.deepEqual((await ask()).items.map(i=>i.title),r.items.map(i=>i.title));assert.equal(calls,first,'nothing is searched again the same day');
 clock+=86400000+1;await ask();assert.ok(calls>first,'a day later the titles are searched again');
});
test('a shelf out of time is partial and goes on from where it stopped; without TMDB it is unavailable',async()=>{
 const films=Array.from({length:6},(_,n)=>movie(500+n,'Кино '+n,'Movie '+n,'2026-02-01'));
 const {fetcher}=tmdb({'/3/trending/movie/week':films,'/3/trending/tv/week':[]});
 let clock=NOW;const cache=new Map(),search=async({query})=>{clock+=9000;return /^Кино \d$/.test(query)?[card('films','6'+query.slice(-1)+'-kino','Кино '+query.slice(-1),2026)]:[];};
 const r=await shelf('trending',{token:'t',fetcher,search,cache,now:()=>clock,budget:20000});
 assert.equal(r.partial,true);assert.deepEqual(r.items.map(i=>i.title),['Кино 0','Кино 1','Кино 2']);
 const next=await shelf('trending',{token:'t',fetcher,search,cache,now:()=>clock,budget:20000});
 assert.deepEqual(next.items.map(i=>i.title).slice(0,3),['Кино 0','Кино 1','Кино 2'],'found titles come from the cache, in order');
 await assert.rejects(shelf('trending',{token:'',fetcher,search}),e=>e.status===503);
 await assert.rejects(shelf('popular',{token:'t',fetcher,search}),e=>e.status===400);
});
