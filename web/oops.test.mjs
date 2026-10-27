import {test} from 'node:test';import assert from 'node:assert/strict';
import {errorKind,retryWait,oopsCopy,RETRY_STEPS} from './public/oops.js';

test('«Упс» screens: the cause picks the words, and only temporary causes retry by themselves',()=>{
 assert.equal(errorKind({offline:true}),'offline');assert.equal(errorKind({status:400},false),'offline');
 assert.equal(errorKind({status:503,retry:true}),'source');assert.equal(errorKind({slow:true}),'slow');
 assert.equal(errorKind({status:502,server:true}),'server');assert.equal(errorKind({status:404}),'missing');assert.equal(errorKind(Error('x')),'other');
 for(const kind of ['offline','source','server','slow','missing','other'])assert.match(oopsCopy(kind)[0],/^Упс/);
 assert.match(oopsCopy('source')[1],/вже працюємо/);
});
test('automatic retries of the same thing wait longer each time and start over after a quiet spell',()=>{
 const t=1e12;assert.deepEqual([0,1,2,3,4].map(i=>retryWait('film:x',t+i*1000)),[...RETRY_STEPS,RETRY_STEPS.at(-1)]);
 assert.equal(retryWait('film:x',t+600000),RETRY_STEPS[0]);assert.equal(retryWait('',t),RETRY_STEPS[0]);assert.equal(retryWait('film:y',t),RETRY_STEPS[0]);
});
