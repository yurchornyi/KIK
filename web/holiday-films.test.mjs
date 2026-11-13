import {test} from 'node:test';import assert from 'node:assert/strict';
import {holidayFilms,pickCard,HOLIDAY_FILMS} from './holiday-films.mjs';
import {HOLIDAYS} from './public/holidays.js';

// Search cards as the catalogue gives them: the source's Russian name, «year, country, genre», a link with the year.
const card=(section,slug,title,year)=>({link:`https://hdrezka.sh/${section}/comedy/${slug}-${year}.html`,title,poster:'https://static.hdrezka.ac/p.jpg',info:year+', США, Комедии',type:''});
const tick=()=>new Promise(r=>setTimeout(r,5));
function within(promise,ms=2000){let timer;return Promise.race([promise,new Promise((_,reject)=>{timer=setTimeout(()=>reject(Error('waited on another request\'s search')),ms);})]).finally(()=>clearTimeout(timer));}

test('every holiday has a fixed list of at most 15 films, each with a year and a name to search',()=>{
 assert.deepEqual(Object.keys(HOLIDAY_FILMS).sort(),[...HOLIDAYS].sort());
 for(const [id,list] of Object.entries(HOLIDAY_FILMS)){assert.ok(list.length>=8&&list.length<=15,id);
  for(const e of list){assert.ok(Number.isInteger(e.year)&&e.year>=1900&&e.year<=2030,id+' '+e.queries[0]);assert.ok(e.queries.length&&e.queries.every(q=>typeof q==='string'&&q.trim()),id);assert.ok([undefined,'film','cartoon'].includes(e.kind),id);}}
 assert.deepEqual(HOLIDAY_FILMS.ind.find(e=>e.year===2017).queries,['Киборги','Кіборги'],'Ukrainian cinema: the Russian name first');
});
test('a card counts within a year of the entry; its own name beats the source order and a longer name',()=>{
 const homeAlone={year:1990,queries:['Home Alone','Один дома'],kind:'film'};
 const found=pickCard(homeAlone,[card('films','1-papa-privedenie','Папа-привидение',1990),card('films','2-odin-doma','Один дома',1990),card('films','3-odin-doma-2','Один дома 2: Затерянный в Нью-Йорке',1992)]);
 assert.equal(found.card.title,'Один дома');assert.equal(found.named,true);
 assert.equal(pickCard(homeAlone,[card('films','3-odin-doma-2','Один дома 2',1992),card('series','4-odin-doma','Один дома',1990)]),null,'another year, or a series, is not the film');
 const scream=pickCard({year:1996,queries:['Scream','Крик'],kind:'film'},[card('films','5-krik-2','Крик 2',1997),card('films','6-krik','Крик',1996)]);
 assert.equal(scream.card.title,'Крик');
 assert.equal(pickCard({year:2012,queries:['ParaNorman','Паранорман'],kind:'cartoon'},[card('cartoons','7-paranorman','Паранорман, или Как приручить зомби',2012)]).named,true,'the start of the source\'s longer name');
});
test('without its name a card must be of the entry\'s kind, and the exact year goes first',()=>{
 const klaus={year:2019,queries:['Klaus','Клаус'],kind:'cartoon'};
 assert.equal(pickCard(klaus,[card('films','1-iks','Икс',2019)]),null);
 const guess=pickCard(klaus,[card('cartoons','2-igrek','Игрек',2020),card('animation','3-zet','Зет',2019)]);
 assert.deepEqual([guess.card.title,guess.named],['Зет',false]);
 assert.equal(pickCard({year:2019,queries:['Атлантида']},[card('films','4-atlantida','Атлантида',2019)]).named,true,'no kind: any film section');
});
test('the shelf keeps the list order, leaves out what the catalogue lacks and searches each title once a day',async()=>{
 const cards={'Home Alone':[card('films','10-papa','Папа-привидение',1990),card('films','11-odin-doma','Один дома',1990)],'Love Actually':[card('films','12-realnaya-lyubov','Реальная любовь',2003)],
  'The Holiday':[card('films','13-otpusk','Отпуск',2006)],'Отпуск по обмену':[card('films','14-otpusk-po-obmenu','Отпуск по обмену',2006)],'It\'s a Wonderful Life':[card('films','15-zhizn','Жизнь',1947)]};
 let clock=1_000_000,calls=0;const cache=new Map(),search=async({query})=>{calls++;return cards[query]||[];},ask=()=>holidayFilms('xmas',{search,cache,now:()=>clock});
 const r=await ask();
 assert.deepEqual(r.items.map(i=>i.title),['Один дома','Реальная любовь','Отпуск по обмену','Жизнь']);assert.equal(r.partial,false);
 // Found by its own name: one search. Not found, or found without its name: the next name too.
 assert.equal(calls,1+2+1+2+2*(HOLIDAY_FILMS.xmas.length-4));
 const first=calls;assert.deepEqual((await ask()).items.map(i=>i.title),r.items.map(i=>i.title));assert.equal(calls,first,'nothing is searched again the same day');
 clock+=86400001;await ask();assert.equal(calls,2*first,'the next day every title is looked up again');
 assert.ok((await holidayFilms('xmas',{search:async()=>Array.from({length:3},(_,i)=>card('films',(i+20)+'-x','Икс '+i,1990)),cache:new Map()})).items.length<=15);
});
test('a list cut short by time or by a failed search is partial; the next request goes on from where it stopped',async()=>{
 let clock=0,calls=0;const cache=new Map(),failing=async({query})=>{calls++;if(query==='Love Actually')throw Object.assign(Error('busy'),{sourceBusy:true});return [];};
 const ask=(options={})=>holidayFilms('xmas',{search:failing,cache,now:()=>clock,...options});
 assert.equal((await ask()).partial,true);const first=calls;
 assert.equal((await ask()).partial,true);assert.equal(calls,first,'a failed title waits ten minutes');
 clock+=11*60000;await ask();assert.equal(calls,first+1,'then only the failed title is searched again');
 calls=0;assert.deepEqual(await holidayFilms('nick',{search:failing,cache:new Map(),now:()=>clock,budget:-1}),{items:[],partial:true});assert.equal(calls,0,'no time left: nothing new starts');
});
test('an unknown holiday is refused',async()=>{
 for(const id of ['','birthday','__proto__'])await assert.rejects(holidayFilms(id,{search:async()=>[]}),e=>e.status===400);
});
test('a search that never settles does not hold the same shelf for a later request',async()=>{
 const cache=new Map();holidayFilms('val',{search:()=>new Promise(()=>{}),cache});await tick();
 const r=await within(holidayFilms('val',{search:async({query})=>query==='Titanic'?[card('films','30-titanik','Титаник',1997)]:[],cache}));
 assert.deepEqual(r.items.map(i=>i.title),['Титаник']);
});
