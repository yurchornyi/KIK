import test from 'node:test';import assert from 'node:assert/strict';import {cleanSynopsis,synopsis,SYNOPSIS_MAX} from './synopsis.mjs';
function fakeDb(){const cache=new Map(),budget=new Map();return {cache,prepare(sql){return {bind(...a){return {
 async first(){if(sql.startsWith('SELECT summary'))return cache.has(a[0])?{summary:cache.get(a[0])}:null;if(sql.startsWith('INSERT INTO ai_daily_budget')){const n=(budget.get(a[0])||0)+1;if(n>a[1])return null;budget.set(a[0],n);return {calls:n};}return null;},
 async run(){if(sql.startsWith('INSERT INTO synopsis_cache'))cache.set(a[0],a[1]);}};}};}};}
const page={title:'Святой Паисий',series:true,genres:['Драмы'],description:'История начинается с рождения святого Паисия, в миру Арсения Эзнепидиса, в Фарасах, Каппадокия. За несколько дней до того, как жители покинули село...'};
const link='https://hdrezka.sh/series/drama/89179-svyatoy-paisiy-ot-farasy-na-nebesa-2022.html';
test('synopsis is cleaned, cut at a sentence and must be Ukrainian',()=>{
 assert.equal(cleanSynopsis('«Хлопчик з Каппадокії стає ченцем і шукає свій шлях до Бога серед війни та вигнання.»'),'Хлопчик з Каппадокії стає ченцем і шукає свій шлях до Бога серед війни та вигнання.');
 const long='Молодий чернець виростає серед вигнання і війни, шукаючи віру. '.repeat(6);assert.ok(cleanSynopsis(long).length<=SYNOPSIS_MAX);assert.ok(cleanSynopsis(long).endsWith('.'));
 assert.equal(cleanSynopsis('Это история о монахе, который ищет веру и проходит испытания войной.'),null);
 assert.equal(cleanSynopsis('Коротко.'),null);
 assert.equal(cleanSynopsis('Життя святого Паїсія починається в Каппадокії, де його хрестить святой Арсений і дає йому ім’я.'),null);
});
test('synopsis is generated once per title and then served from the cache',async()=>{
 const db=fakeDb();let calls=0;const env={AI:{run:async()=>{calls++;return {response:'Хлопчик з Каппадокії стає ченцем Паїсієм і крізь війни та вигнання шукає шлях до Бога.'};}}};
 const a=await synopsis(db,env,link,async()=>page,async(d,f)=>f),b=await synopsis(db,env,link,async()=>{throw Error('page must not be read again');},async(d,f)=>f);
 assert.match(a.summary,/Паїсієм/);assert.equal(b.summary,a.summary);assert.equal(b.cached,true);assert.equal(calls,1);
});
test('synopsis falls back to the small model and returns null without AI',async()=>{
 const db=fakeDb();const models=[];const env={AI:{run:async m=>{models.push(m);if(models.length===1)throw Error('busy');return {response:'Хлопчик з Каппадокії стає ченцем і крізь війни шукає шлях до Бога.'};}}};
 assert.ok((await synopsis(db,env,link,async()=>page,async(d,f)=>f)).summary);assert.equal(models.length,2);
 assert.equal((await synopsis(fakeDb(),{},link,async()=>page,async(d,f)=>f)).summary,null);
 await assert.rejects(()=>synopsis(db,env,'https://example.com/x',async()=>page,async(d,f)=>f),/Некоректне/);
});
test('synopsis is written by Sentra first; a non-Ukrainian answer from it goes to Workers AI',async()=>{
 const original=globalThis.fetch,skills=[];let reply='Хлопчик з Каппадокії стає ченцем Паїсієм і крізь війни та вигнання шукає шлях до Бога.';
 globalThis.fetch=async(url,opts)=>{skills.push(opts.headers['x-sentra-skill']);return Response.json({choices:[{message:{content:reply}}]});};
 try{
  const models=[],env={SENTRA_URL:'https://sentra.test',SENTRA_TOKEN:'t',AI:{run:async m=>{models.push(m);return {response:'Хлопчик з Каппадокії стає ченцем і крізь війни шукає шлях до Бога.'};}}};
  assert.match((await synopsis(fakeDb(),env,link,async()=>page,async(d,f)=>f)).summary,/Паїсієм/);assert.deepEqual(skills,['kik-synopsis']);assert.equal(models.length,0);
  reply='Это история о монахе, который ищет веру и проходит испытания войной.';
  assert.ok((await synopsis(fakeDb(),env,link,async()=>page,async(d,f)=>f)).summary);assert.equal(models.length,1);
  assert.equal((await synopsis(fakeDb(),{SENTRA_URL:'https://sentra.test',SENTRA_TOKEN:'t'},link,async()=>page,async(d,f)=>f)).summary,null);
 }finally{globalThis.fetch=original;}
});
