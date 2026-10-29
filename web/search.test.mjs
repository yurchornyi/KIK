import test from 'node:test';import assert from 'node:assert/strict';
import {nameScore,searchMatches,distance} from './public/search-match.js';
import {layoutSwaps,cleanFixes,corrections,searchAlternatives} from './search-fix.mjs';

test('titles match through typos, Ukrainian letters and words run together',()=>{
 assert.ok(nameScore('шерлак','Шерлок')>=.6);assert.ok(nameScore('интерстелар','Інтерстеллар')>=.6);assert.ok(nameScore('фарсаж','Форсаж')>=.6);
 assert.ok(nameScore('гарри потер','Гаррі Поттер та Дари Смерті')>=.6);assert.ok(nameScore('пиратыкарибского моря','Пірати Карибського моря: На краю Світу')>=.6);
 assert.equal(distance('шерлко','шерлок'),1);
});
test('unrelated results that only share a word count as a miss',()=>{
 assert.ok(nameScore('брейкінг бед','Бедуїн')<.6);assert.ok(nameScore('кот','Кіт у чоботях')<.6);
 assert.equal(searchMatches('breking bad',[{title:'Погані хлопці 2'},{title:'Погані хлопці'}]),false);
 assert.equal(searchMatches('шерлак',[{title:'Шерлок Холмс'}]),true);
});
test('keyboard layout swaps both ways',()=>{
 assert.deepEqual(layoutSwaps('ithkjr'),['шерлок']);assert.deepEqual(layoutSwaps('икуфлштп ифв'),['breaking bad']);assert.deepEqual(layoutSwaps('vfnhbwf'),['матрица']);assert.deepEqual(layoutSwaps('Матрица 2'),['vfnhbwf 2']);
});
test('model answers become at most three plain titles',()=>{
 assert.deepEqual(cleanFixes('Sure: {"titles":["Во все тяжкие","Breaking Bad","брейкінг бед","<b>x</b>","https://a.b"]}','брейкінг бед'),['Во все тяжкие','Breaking Bad']);
 assert.deepEqual(cleanFixes('not json','x'),[]);assert.deepEqual(cleanFixes(['Aa','Bb','Cc','Dd'],'q'),['Aa','Bb','Cc']);
});
/** D1 with the day's budget (`limit` calls) and search_fix_cache in `saved` (query key → {fixes,updated_at}). */
function fakeDb(limit=10,saved=new Map()){let calls=0;return {calls:()=>calls,saved,prepare:sql=>({bind:(...a)=>({
 first:async()=>sql.includes('FROM search_fix_cache')?saved.get(a[0])||null:++calls<=limit?{calls}:null,
 run:async()=>{if(sql.includes('INTO search_fix_cache'))saved.set(a[0],{fixes:a[1],updated_at:a[2]});}})})};}
/** Sentra stubbed for one test, answering every request with `titles`; KIK has no AI of its own. */
async function withSentra(titles,run){const original=globalThis.fetch;let runs=0;globalThis.fetch=async()=>{runs++;return Response.json({choices:[{message:{content:JSON.stringify({titles})}}]});};try{return await run({SENTRA_URL:'https://sentra.test',SENTRA_TOKEN:'t'},()=>runs);}finally{globalThis.fetch=original;}}
test('corrections ask the model once per query, keep the answer in D1 and respect the daily budget',()=>withSentra(['Во все тяжкие','Breaking Bad'],async(env,runs)=>{
 const db=fakeDb();
 assert.deepEqual(await corrections(db,env,'брейкінг бед'),['Во все тяжкие','Breaking Bad']);
 assert.deepEqual(await corrections(fakeDb(),env,'Брейкінг  бед'),['Во все тяжкие','Breaking Bad']);assert.equal(runs(),1);
 assert.deepEqual(JSON.parse(db.saved.get('бреикинг бед').fixes),['Во все тяжкие','Breaking Bad']);
 // Any colo, any later day: a fix in D1 is answered without Sentra, however old; «not sure» is asked again after a month.
 const old=Date.now()-400*86400000,kept=fakeDb(10,new Map([['шерлок холмс',{fixes:'["Шерлок"]',updated_at:old}],['невидомо що',{fixes:'[]',updated_at:old}]]));
 assert.deepEqual(await corrections(kept,env,'Шерлок Холмс'),['Шерлок']);assert.equal(runs(),1);
 assert.deepEqual(await corrections(kept,env,'невідомо що'),['Во все тяжкие','Breaking Bad']);assert.equal(runs(),2);
 assert.deepEqual(await corrections(fakeDb(0),env,'zzzz qqqq'),[]);assert.equal(runs(),2);
 assert.deepEqual(await corrections(fakeDb(),{},'без моделі'),[]);
 // A Workers AI binding left behind is never asked: only Sentra answers.
 assert.deepEqual(await corrections(fakeDb(),{AI:{run:async()=>({response:'{"titles":["X"]}'})}},'без сентри'),[]);
}));
test('alternatives prefer exact Wikidata names, then swapped layout, then the model',()=>withSentra(['Интерстеллар'],async(env)=>{
 assert.deepEqual(await searchAlternatives(fakeDb(),env,'breaking bad',async q=>q==='breaking bad'?['Во все тяжкие']:[]),['Во все тяжкие']);
 assert.deepEqual(await searchAlternatives(fakeDb(),env,'vfnhbwf',async q=>q==='матрица'?['The Matrix']:[]),['матрица','The Matrix']);
 assert.deepEqual(await searchAlternatives(fakeDb(),env,'интерстелар фильм',async()=>[]),['Интерстеллар']);
}));
import {similar,sameWork} from './similar.mjs';
test('similar titles: TMDB recommendations found on the catalogue first, then the source «see also»',async()=>{
 const page={link:'https://hdrezka.sh/series/thriller/646-vo-vse-tyazhkie-2008.html',imdb:'tt0903747',series:true,related:[{link:'https://hdrezka.sh/films/drama/1-war-and-peace-2016.html',title:'Война и мир',info:'2016, Великобритания, Драмы'},{link:'https://hdrezka.sh/series/thriller/646-vo-vse-tyazhkie-2008.html',title:'Во все тяжкие',info:'2008'}]};
 const tmdbCalls=[],fetcher=async url=>{tmdbCalls.push(url);const body=url.includes('/find/')?{tv_results:[{id:1396}]}:url.includes('/recommendations')?{results:[{id:60059,name:'Лучше звоните Солу',original_name:'Better Call Saul',first_air_date:'2015-02-08'},{id:1,name:'Нет на сайте',first_air_date:'2010-01-01'},{id:2,name:'Озарк',original_name:'Ozark',first_air_date:'2017-07-21'},{id:3,name:'Озарк',first_air_date:'1990-01-01'}]}:{results:[{id:4,name:'Нарко',original_name:'Narcos',first_air_date:'2015-08-28'}]};return new Response(JSON.stringify(body));};
 const catalogue={'Лучше звоните Солу':[{link:'https://hdrezka.sh/series/drama/1-luchshe-zvonite-solu-2015.html',title:'Лучше звоните Солу',info:'2015-2022, США, Драмы'}],'Озарк':[{link:'https://hdrezka.sh/films/drama/9-ozark-2017.html',title:'Озарк',info:'2017, США'},{link:'https://hdrezka.sh/series/drama/2-ozark-2017.html',title:'Озарк',info:'2017-2022, США, Драмы'}],'Нарко':[{link:'https://hdrezka.sh/series/drama/3-narko-2015.html',title:'Нарко',info:'2015-2017'}]};
 const items=await similar(page.link,{token:'a'.repeat(32),load:async()=>page,search:async({query})=>catalogue[query]||[],fetcher});
 assert.deepEqual(items.map(i=>i.title),['Лучше звоните Солу','Озарк','Нарко','Война и мир']);
 assert.ok(tmdbCalls.every(u=>u.startsWith('https://api.themoviedb.org/3/')&&u.includes('api_key=')));
 assert.deepEqual((await similar(page.link,{token:'',load:async()=>page,search:async()=>[]})).map(i=>i.title),['Война и мир']);
 assert.equal(sameWork({name:'Озарк',first_air_date:'2017',media_type:'tv'},{link:'https://hdrezka.sh/films/drama/9-ozark-2017.html',title:'Озарк',info:'2017'}),false);
 assert.equal(sameWork({name:'Ван Пис',first_air_date:'1999',media_type:'tv'},{link:'https://hdrezka.sh/animation/adventures/10-van-pis-1999.html',title:'Ван Пис',info:'1999'}),true);
 assert.equal(sameWork({title:'Ван Пис',release_date:'1999',media_type:'movie'},{link:'https://hdrezka.sh/series/comedy/11-van-pis-1999.html',title:'Ван Пис',info:'1999'}),false);
});
test('search fixes come from Sentra; its «not sure» is an answer, and Workers AI is never asked',async()=>{
 const original=globalThis.fetch,sent=[];let reply='{"titles":["Интерстеллар"]}';
 globalThis.fetch=async(url,opts)=>{sent.push(opts);return Response.json({choices:[{message:{content:reply}}]});};
 try{
  let runs=0;const env={SENTRA_URL:'https://sentra.test',SENTRA_TOKEN:'t',AI:{run:async()=>{runs++;return {response:'{"titles":["Wrong"]}'};}}};
  assert.deepEqual(await corrections(fakeDb(),env,'интерстелар'),['Интерстеллар']);assert.equal((await corrections(fakeDb(),env,'интерстелар')).failed,undefined);assert.equal(sent[0].headers['x-sentra-skill'],'kik-search-fix');assert.equal(JSON.parse(sent[0].body).model,'role:fast');
  reply='{"titles":[]}';assert.deepEqual(await corrections(fakeDb(),env,'qqqq zzzz'),[]);assert.equal(runs,0);
 }finally{globalThis.fetch=original;}
});
test('search fixes are not cached twice by Sentra; a failure is not kept as «no fixes», and a 429 gives the call back',async()=>{
 const original=globalThis.fetch,sent=[];let status=503;
 globalThis.fetch=async(url,opts)=>{sent.push(opts);return status===200?Response.json({choices:[{message:{content:'{"titles":["Интерстеллар"]}'}}]}):new Response('x',{status});};
 const budget={calls:0},saved=[],db={prepare:sql=>({bind:(...a)=>({first:async()=>sql.includes('FROM search_fix_cache')?null:({calls:++budget.calls}),run:async()=>{if(sql.startsWith('UPDATE ai_daily_budget'))budget.calls-=a[0];if(sql.includes('INTO search_fix_cache'))saved.push(a[0]);}})})};
 try{
  const env={SENTRA_URL:'https://sentra.test',SENTRA_TOKEN:'t'};
  // KIK keeps fixes in D1 itself: no x-sentra-cache on top (SENTRA.md, rule 6).
  assert.deepEqual(await corrections(db,env,'Сентра Збій'),[]);assert.equal(sent[0].headers['x-sentra-cache'],undefined);
  // The failure is marked, so the edge cache of /api/search-aliases does not keep it for a day either.
  assert.equal((await corrections(db,env,'Сентра Збій')).failed,true);assert.equal((await searchAlternatives(db,env,'Сентра Збій',async()=>[])).failed,true);assert.equal(sent.length,1,'the next ten minutes ask nobody');
  assert.equal(JSON.parse(JSON.parse(sent[0].body).messages[1].content).query,'сентра збій');assert.deepEqual(saved,[]);assert.equal(budget.calls,1);
  status=429;assert.deepEqual(await corrections(db,env,'ліміт сентри'),[]);assert.equal(budget.calls,1);assert.deepEqual(saved,[]);
 }finally{globalThis.fetch=original;}
});
