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
/** Sentra stubbed for one test: every request answered with reply(), counted; KIK has no AI of its own. */
async function withSentra(reply,run){const original=globalThis.fetch,skills=[];globalThis.fetch=async(url,opts)=>{skills.push(opts.headers['x-sentra-skill']);return Response.json({choices:[{message:{content:reply()}}]});};try{return await run({SENTRA_URL:'https://sentra.test',SENTRA_TOKEN:'t'},skills);}finally{globalThis.fetch=original;}}
const good='Хлопчик з Каппадокії стає ченцем Паїсієм і крізь війни та вигнання шукає шлях до Бога.';
test('synopsis is generated once per title and then served from the cache',()=>withSentra(()=>good,async(env,skills)=>{
 const db=fakeDb();
 const a=await synopsis(db,env,link,async()=>page,async(d,f)=>f),b=await synopsis(db,env,link,async()=>{throw Error('page must not be read again');},async(d,f)=>f);
 assert.match(a.summary,/Паїсієм/);assert.equal(b.summary,a.summary);assert.equal(b.cached,true);assert.equal(skills.length,1);
}));
test('synopsis returns null without Sentra, even with a Workers AI binding left behind',async()=>{
 let runs=0;const env={AI:{run:async()=>{runs++;return {response:good};}}};
 assert.equal((await synopsis(fakeDb(),env,link,async()=>page,async(d,f)=>f)).summary,null);assert.equal(runs,0);
 assert.equal((await synopsis(fakeDb(),{},link,async()=>page,async(d,f)=>f)).summary,null);
 await assert.rejects(()=>synopsis(fakeDb(),{SENTRA_URL:'https://sentra.test',SENTRA_TOKEN:'t'},'https://example.com/x',async()=>page,async(d,f)=>f),/Некоректне/);
});
test('synopsis is written by Sentra as its own skill; a non-Ukrainian answer is not taken',async()=>{
 let reply=good;
 await withSentra(()=>reply,async(env,skills)=>{
  assert.match((await synopsis(fakeDb(),env,link,async()=>page,async(d,f)=>f)).summary,/Паїсієм/);assert.deepEqual(skills,['kik-synopsis']);
  reply='Это история о монахе, который ищет веру и проходит испытания войной.';
  assert.equal((await synopsis(fakeDb(),env,link,async()=>page,async(d,f)=>f)).summary,null);
 });
});
