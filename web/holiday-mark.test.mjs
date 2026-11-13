import {test} from 'node:test';import assert from 'node:assert/strict';
import {holidayPlan,momentDue,fireworksDue,holidayLogo,holidayTop,MOMENT_EVERY,HOLIDAY_INTRO_MS} from './public/holiday-mark.js';
import {HOLIDAYS} from './public/holidays.js';

const ON={holidays:true,intro:true,loudness:true};
test('the site dresses up on the day and shows the shelf before it, only while the owner keeps «Свята» on',()=>{
 assert.deepEqual(holidayPlan(ON,new Date(2026,11,25,20)),{id:'xmas',greeting:'З Різдвом Христовим',accent:'#F6D37A',shelf:'xmas',shelfTitle:'Різдвяні фільми'});
 assert.deepEqual(holidayPlan(ON,new Date(2026,11,10)),{id:null,greeting:'',accent:'',shelf:'xmas',shelfTitle:'Різдвяні фільми'},'the shelf comes before the day');
 assert.equal(holidayPlan(ON,new Date(2026,10,13)),null,'an ordinary day');
 assert.equal(holidayPlan({...ON,holidays:false},new Date(2026,11,25)),null,'switched off for everyone');
 assert.equal(holidayPlan(ON,new Date(2027,0,1,0,5)).greeting,'З Новим 2027 роком');
});
test('the header mark plays at most every 30 minutes; the fireworks only in the first ten minutes of the year, once',()=>{
 const t=1_800_000_000_000;assert.equal(momentDue(0,t),true);assert.equal(momentDue(t-MOMENT_EVERY+1000,t),false);assert.equal(momentDue(t-MOMENT_EVERY,t),true);assert.equal(momentDue(t+60000,t),true,'a clock set back');
 assert.equal(fireworksDue(new Date(2027,0,1,0,0,5),null),true);assert.equal(fireworksDue(new Date(2027,0,1,0,9,59),'2026'),true);
 assert.equal(fireworksDue(new Date(2027,0,1,0,10),null),false);assert.equal(fireworksDue(new Date(2026,11,31,23,59),null),false);assert.equal(fireworksDue(new Date(2027,0,1,0,3),'2027'),false,'played this year');
 assert.ok(HOLIDAY_INTRO_MS>=3250&&HOLIDAY_INTRO_MS<=4000,'about as long as the plain intro');
});
test('every holiday has its mark; the still one never moves and each copy has its own gradient ids',()=>{
 for(const id of HOLIDAYS)for(const mode of ['intro','moment','still']){const svg=holidayLogo(id,mode);assert.match(svg,new RegExp(`^<svg class="hmark h-${id} m-${mode}" viewBox="0 0 102 48"`));assert.match(svg,/aria-hidden="true"/);assert.ok(svg.endsWith('</svg>'));}
 for(const id of HOLIDAYS)assert.doesNotMatch(holidayLogo(id,'still'),/<animate/,id);
 assert.match(holidayLogo('ind','moment'),/<animate/);
 const a=holidayLogo('easter','still'),b=holidayLogo('easter','still'),ids=s=>[...s.matchAll(/id="([^"]+)"/g)].map(m=>m[1]);assert.equal(ids(a).length,2);assert.ok(ids(a).every(x=>!ids(b).includes(x)));
 assert.match(holidayLogo('xmas','still',{label:'KIK'}),/role="img" aria-label="KIK"/);
 assert.match(holidayLogo('val','still'),/class="vh"/);assert.doesNotMatch(holidayLogo('val','still'),/M51,8L51,40/,'a heart in place of the I');
 assert.match(holidayLogo('ny','intro'),/ny-fuse/);assert.doesNotMatch(holidayLogo('ny','still'),/ny-fuse/);
});
test('one thin decoration at the top on Christmas, Easter and Independence Day; nothing on the other days',()=>{
 assert.match(holidayTop('xmas'),/^<svg width="3840" height="34"/);assert.doesNotMatch(holidayTop('xmas'),/animat/);
 assert.match(holidayTop('easter'),/pattern id="hm-stitch"/);assert.match(holidayTop('ind'),/holiday-flag/);
 for(const id of ['nick','ny','val','hw',null])assert.equal(holidayTop(id),'');
});
