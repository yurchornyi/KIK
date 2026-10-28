import test from 'node:test';import assert from 'node:assert/strict';
import {engagement,engagementWeight,engagementLabel,byTitle,DAY} from './public/watching.js';
import {tasteOf,TASTE_AI} from './recommendations.mjs';
const now=Date.UTC(2026,9,23),link=n=>`https://hdrezka.sh/films/drama/${n}-film-2020.html`,rec=(n,d,ago=5*DAY)=>({kind:'progress',updated_at:now-ago,data:{link:link(n),title:'T'+n,...d}});
test('a film watched to the credits is finished, a mild like',()=>{const e=engagement([rec(1,{pos:6400000,dur:6600000,watched:6000000})],now);assert.equal(e.state,'finished');assert.equal(engagementWeight(e),4);assert.match(engagementLabel(e),/до кінця/);});
test('stopped within the first minutes and never resumed is abandoned, a mild dislike',()=>{const e=engagement([rec(2,{pos:400000,dur:6600000,watched:380000})],now);assert.equal(e.state,'abandoned');assert.equal(engagementWeight(e),-2);assert.equal(engagementLabel(e),'Переглянуто 6 хв · кинули на 7-й хв');});
test('a recent stop is still watching, not abandoned',()=>{assert.equal(engagement([rec(3,{pos:400000,dur:6600000,watched:380000},DAY)],now).state,'watching');});
test('a seek to the end is not watching: real time decides',()=>{const e=engagement([rec(4,{pos:3000000,dur:6600000,watched:10000})],now);assert.equal(e.state,'glance');assert.equal(engagementWeight(e),0);});
test('a series with a finished episode is not abandoned',()=>{const e=engagement([rec(5,{season:'1',episode:'1',pos:2600000,dur:2700000,watched:2600000},9*DAY),rec(5,{season:'1',episode:'2',pos:120000,dur:2700000,watched:2720000})],now);assert.equal(e.state,'finished');assert.equal(e.finished,1);});
test('records are grouped per title',()=>{const g=byTitle([rec(1,{}),rec(1,{episode:'2'}),rec(2,{}),{kind:'rating',data:{link:link(3)}}],l=>l);assert.equal(g.size,2);assert.equal(g.get(link(1)).length,2);});
test('server taste: abandoned titles weigh against, finished ones for',()=>{const t=tasteOf([rec(1,{pos:6400000,dur:6600000,watched:6000000}),rec(2,{pos:400000,dur:6600000,watched:380000})],now);const w=Object.fromEntries(t.signals.map(s=>[s.title,s.weight]));assert.equal(w.T1,4);assert.equal(w.T2,-2);assert.ok(t.excluded.has('films/drama/2-film-2020.html'));});
test('server taste says how much was watched, a rated title too; a glance says nothing',()=>{
 const t=tasteOf([rec(1,{pos:6400000,dur:6600000,watched:6000000}),rec(2,{pos:400000,dur:6600000,watched:380000}),rec(3,{pos:3000000,dur:6600000,watched:10000}),{kind:'rating',updated_at:now,data:{link:link(1),title:'Rated T1',value:1}},{kind:'rating',updated_at:now,data:{link:link(3),title:'Rated T3',value:1}}],now);
 const said=Object.fromEntries(t.signals.map(s=>[s.title,s.watched]));assert.deepEqual(said,{'Rated T1':'Переглянуто 1 год 40 хв · до кінця','Rated T3':'',T2:'Переглянуто 6 хв · кинули на 7-й хв'});
});
test('the model\'s taste holds every kind: the latest likes, then watching by strength and recency, then «Мій список»',()=>{
 const rating=n=>({kind:'rating',updated_at:now-n*1000,data:{link:link(100+n),title:'R'+n,value:1}}),later=n=>({kind:'later',updated_at:now-n*1000,data:{link:link(300+n),title:'L'+n}});
 const records=[...[9,8,7,6,5,4,3,2,1,0].map(rating),...[0,1,2,3].map(n=>rec(200+n,{pos:6400000,dur:6600000,watched:6000000},5*DAY+n*1000)),...[0,1,2,3].map(n=>rec(250+n,{pos:400000,dur:6600000,watched:380000},DAY+n*1000)),...[0,1].map(n=>rec(270+n,{pos:400000,dur:6600000,watched:380000},9*DAY+n*1000)),...[0,1,2,3,4].map(later)];
 const {signals}=tasteOf(records,now),top=signals.slice(0,TASTE_AI);
 assert.deepEqual(top.map(s=>s.title),['R0','R1','R2','R3','R4','R5','T200','T201','T202','T203','T250','T251','L0','L1','L2']);
 assert.deepEqual(signals.slice(TASTE_AI).map(s=>s.title),['R6','R7','R8','R9','T252','T253','T270','T271','L3','L4'],'the rest still shape the baseline');
 assert.deepEqual(tasteOf([...[0,1].map(rating),...[0,1].map(later)],now).signals.map(s=>s.title),['R0','R1','L0','L1'],'a short taste is all there');
});
