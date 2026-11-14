import {test} from 'node:test';import assert from 'node:assert/strict';
import {watchRows,handPlace,keepPlace,HAND_MS} from './public/row-scroll.js';

/** A row that scrolls, a page that dispatches events, and a clock. */
function setup(){
 const listeners={};let clock=1000;
 const on=(type,fn)=>{(listeners[type]||=[]).push(fn);};
 const win={addEventListener:on},root={addEventListener:on};
 const fire=(type,e)=>{for(const fn of listeners[type]||[])fn({type,...e});};
 const row=()=>{const line={dataset:{},scrollLeft:0,matches:s=>s.includes('.shelf-line'),closest(s){return s.includes('.shelf-line')?line:null;}};
  line.scrollTo=x=>{line.scrollLeft=x;fire('scroll',{target:line});};return line;};
 watchRows(root,win,()=>clock);
 return {fire,row,tick:ms=>{clock+=ms;}};
}

test('a row the browser moved by itself goes back to its first card',()=>{
 const {row}=setup(),line=row();
 line.scrollTo(820);
 assert.equal(line.scrollLeft,0);
 assert.equal(handPlace(line),0);
});

test('a row the viewer swiped keeps its place, momentum included',()=>{
 const {fire,row,tick}=setup(),line=row(),inner={closest:()=>line};
 fire('touchstart',{target:inner});
 line.scrollTo(300);tick(HAND_MS-100);line.scrollTo(480);tick(600);line.scrollTo(520);
 assert.equal(line.scrollLeft,520);
 assert.equal(handPlace(line),520);
 tick(10000);line.scrollTo(900);
 assert.equal(line.scrollLeft,520,'a later move by the browser goes back to the viewer’s place');
});

test('a tap elsewhere (closing a window) does not make a row’s focus scroll count as the viewer’s',()=>{
 const {fire,row}=setup(),line=row();
 fire('pointerdown',{target:{closest:()=>null}});
 line.scrollTo(700);
 assert.equal(line.scrollLeft,0);
 fire('keydown',{key:'Escape',target:{closest:()=>null}});
 line.scrollTo(700);
 assert.equal(line.scrollLeft,0);
 fire('keydown',{key:'Tab',target:{closest:()=>null}});
 line.scrollTo(700);
 assert.equal(line.scrollLeft,700,'moving through cards with the keyboard does');
});

test('a browser that keeps snapping elsewhere is not fought forever, and a kept place comes back after a repaint',()=>{
 const {row}=setup(),line=row();
 let sets=0;for(let i=0;i<10;i++){line.scrollTo(400);if(line.scrollLeft===0)sets++;}
 assert.ok(sets>=1&&sets<=4);
 const fresh=row();keepPlace(fresh,360);
 assert.equal(fresh.scrollLeft,360);
 assert.equal(handPlace(fresh),360);
});
