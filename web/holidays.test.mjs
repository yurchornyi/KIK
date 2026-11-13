import {test} from 'node:test';import assert from 'node:assert/strict';
import {westernEaster,holidayOn,shelfOn,greeting,shelfTitle,accent,HOLIDAYS} from './public/holidays.js';

test('Easter: the Western (European) Sunday only, the same days as the TV (scripts/HolidayCheck.java)',()=>{
 assert.deepEqual([2025,2026,2027,2028,2029].map(westernEaster),[[4,20],[4,5],[3,28],[4,16],[4,1]]);
});

test('the holiday on the day: logo, intro and decorations',()=>{
 assert.equal(holidayOn(2026,12,6),'nick');assert.equal(holidayOn(2026,12,5),null);
 // Christmas and New Year start two days early (owner, 13.11).
 assert.equal(holidayOn(2026,12,21),null);for(const d of[22,23,24,25,26])assert.equal(holidayOn(2026,12,d),'xmas');
 assert.equal(holidayOn(2026,12,27),null);assert.equal(holidayOn(2026,12,28),null);
 for(const d of[29,30,31])assert.equal(holidayOn(2026,12,d),'ny');assert.equal(holidayOn(2027,1,1),'ny');assert.equal(holidayOn(2027,1,2),null);
 assert.equal(holidayOn(2027,2,14),'val');
 assert.equal(holidayOn(2027,3,28),'easter');assert.equal(holidayOn(2027,5,2),null);
 assert.equal(holidayOn(2028,4,16),'easter');
 assert.equal(holidayOn(2027,8,24),'ind');assert.equal(holidayOn(2027,8,23),null);
 assert.equal(holidayOn(2026,10,31),'hw');assert.equal(holidayOn(2026,11,13),null);
});

test('the film shelf comes a week before and stays to the last day; December is shared',()=>{
 assert.equal(shelfOn(2026,11,30),null);assert.equal(shelfOn(2026,12,1),'nick');assert.equal(shelfOn(2026,12,6),'nick');
 assert.equal(shelfOn(2026,12,7),'xmas');assert.equal(shelfOn(2026,12,26),'xmas');
 assert.equal(shelfOn(2026,12,27),'ny');assert.equal(shelfOn(2027,1,7),'ny');assert.equal(shelfOn(2027,1,8),null);
 assert.equal(shelfOn(2027,2,7),'val');assert.equal(shelfOn(2027,2,6),null);assert.equal(shelfOn(2027,2,15),null);
 assert.equal(shelfOn(2027,3,21),'easter');assert.equal(shelfOn(2027,3,29),null);assert.equal(shelfOn(2027,4,25),null);
 assert.equal(shelfOn(2027,8,17),'ind');assert.equal(shelfOn(2026,10,24),'hw');assert.equal(shelfOn(2026,10,23),null);
});

test('greetings, shelf titles and accents exist for every holiday',()=>{
 assert.equal(greeting('ny',2026,12),'З Новим 2027 роком');assert.equal(greeting('ny',2027,1),'З Новим 2027 роком');
 assert.equal(greeting('easter',2027,5),'Христос воскрес!');assert.equal(greeting(null,2027,5),'');
 for(const id of HOLIDAYS){assert.ok(greeting(id,2027,1));assert.ok(shelfTitle(id));assert.match(accent(id),/^#[0-9A-F]{6}$/);}
});
