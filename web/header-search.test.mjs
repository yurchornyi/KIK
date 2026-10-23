import {test} from 'node:test';
import assert from 'node:assert/strict';
import {searchHistoryKey,readSearchHistory,rememberSearch} from './public/header-search.js';
const memory=()=>{const data=new Map();return {getItem:k=>data.get(k),setItem:(k,v)=>data.set(k,v)};};
test('query history belongs to one profile, account and guest context',()=>{
 const storage=memory(),a=searchHistoryKey({userId:'alice',profileId:'one'}),b=searchHistoryKey({userId:'alice',profileId:'two'}),c=searchHistoryKey({userId:'bob',profileId:'one'}),guest=searchHistoryKey();
 assert.equal(new Set([a,b,c,guest]).size,4);rememberSearch(storage,a,'Дюна');assert.deepEqual(readSearchHistory(storage,a),['Дюна']);for(const key of [b,c,guest])assert.deepEqual(readSearchHistory(storage,key),[]);
});
test('history keeps the eight most recent queries and moves repeated searches to the front',()=>{
 const storage=memory();for(let i=0;i<12;i++)rememberSearch(storage,'history','Film '+i);
 assert.equal(readSearchHistory(storage,'history').length,8);rememberSearch(storage,'history','  FILM 7 ');assert.deepEqual(readSearchHistory(storage,'history').slice(0,2),['FILM 7','Film 11']);assert.equal(readSearchHistory(storage,'history').filter(q=>q.toLowerCase()==='film 7').length,1);
});
test('corrupt storage, blank queries and oversized strings do not break search',()=>{
 const storage=memory();storage.setItem('history','{');assert.deepEqual(readSearchHistory(storage,'history'),[]);assert.equal(rememberSearch(storage,'history','  '),'');
 rememberSearch(storage,'history','x'.repeat(200));assert.equal(readSearchHistory(storage,'history')[0].length,150);
 storage.setItem('history',JSON.stringify([null,42,{},'',' Good ']));assert.deepEqual(readSearchHistory(storage,'history'),['Good']);
});
test('search still returns the submitted query when browser storage is blocked',()=>{
 const storage={getItem(){throw Error('blocked');},setItem(){throw Error('blocked');}};assert.equal(rememberSearch(storage,'history','Шерлок'),'Шерлок');
});
