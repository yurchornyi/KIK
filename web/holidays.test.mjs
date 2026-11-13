import {test} from 'node:test';import assert from 'node:assert/strict';
import {westernEaster,orthodoxEaster,holidayOn,shelfOn,greeting,shelfTitle,accent,HOLIDAYS} from './public/holidays.js';

test('Easter: Western and Orthodox Sundays, the same days as the TV (scripts/HolidayCheck.java)',()=>{
 assert.deepEqual(westernEaster(2026),[4,5]);assert.deepEqual(orthodoxEaster(2026),[4,12]);
 assert.deepEqual(westernEaster(2027),[3,28]);assert.deepEqual(orthodoxEaster(2027),[5,2]);
 assert.deepEqual(westernEaster(2028),[4,16]);assert.deepEqual(orthodoxEaster(2028),[4,16]);
 assert.deepEqual(westernEaster(2025),[4,20]);assert.deepEqual(orthodoxEaster(2025),[4,20]);
 assert.deepEqual(orthodoxEaster(2029),[4,8]);assert.deepEqual(westernEaster(2029),[4,1]);
});

test('the holiday on the day: logo, intro and decorations',()=>{
 assert.equal(holidayOn(2026,12,6),'nick');assert.equal(holidayOn(2026,12,5),null);
 for(const d of[24,25,26])assert.equal(holidayOn(2026,12,d),'xmas');assert.equal(holidayOn(2026,12,27),null);
 assert.equal(holidayOn(2026,12,31),'ny');assert.equal(holidayOn(2027,1,1),'ny');assert.equal(holidayOn(2027,1,2),null);
 assert.equal(holidayOn(2027,2,14),'val');
 assert.equal(holidayOn(2027,3,28),'easter');assert.equal(holidayOn(2027,5,2),'easter');assert.equal(holidayOn(2027,4,15),null);
 assert.equal(holidayOn(2028,4,16),'easter');
 assert.equal(holidayOn(2027,8,24),'ind');assert.equal(holidayOn(2027,8,23),null);
 assert.equal(holidayOn(2026,10,31),'hw');assert.equal(holidayOn(2026,11,13),null);
});

test('the film shelf comes a week before and stays to the last day; December is shared',()=>{
 assert.equal(shelfOn(2026,11,30),null);assert.equal(shelfOn(2026,12,1),'nick');assert.equal(shelfOn(2026,12,6),'nick');
 assert.equal(shelfOn(2026,12,7),'xmas');assert.equal(shelfOn(2026,12,26),'xmas');
 assert.equal(shelfOn(2026,12,27),'ny');assert.equal(shelfOn(2027,1,7),'ny');assert.equal(shelfOn(2027,1,8),null);
 assert.equal(shelfOn(2027,2,7),'val');assert.equal(shelfOn(2027,2,6),null);assert.equal(shelfOn(2027,2,15),null);
 assert.equal(shelfOn(2027,3,21),'easter');assert.equal(shelfOn(2027,4,1),null);assert.equal(shelfOn(2027,4,25),'easter');assert.equal(shelfOn(2027,5,3),null);
 assert.equal(shelfOn(2027,8,17),'ind');assert.equal(shelfOn(2026,10,24),'hw');assert.equal(shelfOn(2026,10,23),null);
});

test('greetings, shelf titles and accents exist for every holiday',()=>{
 assert.equal(greeting('ny',2026,12),'З Новим 2027 роком');assert.equal(greeting('ny',2027,1),'З Новим 2027 роком');
 assert.equal(greeting('easter',2027,5),'Христос воскрес!');assert.equal(greeting(null,2027,5),'');
 for(const id of HOLIDAYS){assert.ok(greeting(id,2027,1));assert.ok(shelfTitle(id));assert.match(accent(id),/^#[0-9A-F]{6}$/);}
});
