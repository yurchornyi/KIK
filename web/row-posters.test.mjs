import {test} from 'node:test';import assert from 'node:assert/strict';
import {holdFarRows} from './public/row-posters.js';

/** Just enough of the DOM for row-posters.js: elements with classes, attributes, a dataset, children and a top. */
class El{
 constructor(tag,cls='',attrs={},top=0){this.nodeType=1;this.tagName=tag;this.cls=cls;this.attrs={...attrs};this.dataset={};this.children=[];this.parent=null;this.top=top;this.isConnected=true;this.complete=false;this.naturalWidth=0;}
 add(...kids){for(const k of kids){k.parent=this;this.children.push(k);}return this;}
 all(){return this.children.flatMap(c=>[c,...c.all()]);}
 is(sel){return sel==='.shelf'?this.cls==='shelf':sel==='img'?this.tagName==='img':false;}
 matches(sel){return this.is(sel);}
 querySelectorAll(sel){
  if(sel==='[data-posters-held]')return this.all().filter(e=>e.dataset.postersHeld);
  if(sel==='img[data-held-src],img[data-held-srcset]')return this.all().filter(e=>e.is('img')&&('heldSrc' in e.dataset||'heldSrcset' in e.dataset));
  return this.all().filter(e=>e.is(sel));
 }
 closest(sel){for(let e=this;e;e=e.parent)if(e.is(sel))return e;return null;}
 getAttribute(n){return n in this.attrs?this.attrs[n]:null;}setAttribute(n,v){this.attrs[n]=String(v);}removeAttribute(n){delete this.attrs[n];}
 getBoundingClientRect(){return {top:this.top};}
}
function fakeWindow(){
 const w={innerHeight:800,observed:new Set()};
 w.IntersectionObserver=class{constructor(cb,opts){w.near=cb;w.opts=opts;}observe(e){w.observed.add(e);}unobserve(e){w.observed.delete(e);}};
 w.MutationObserver=class{constructor(cb){w.changed=cb;}observe(){}};
 w.setTimeout=(fn,ms)=>{w.timer=fn;w.wait=ms;return 1;};
 return w;
}
const row=(top,...imgs)=>new El('section','shelf',{},top).add(new El('div','shelf-line').add(...imgs));
const poster=(src,set)=>new El('img','',set?{src,srcset:set}:{src});

test('rows far below the screen keep their posters until they come near; rows on screen load at once',()=>{
 const w=fakeWindow(),main=new El('main');holdFarRows(main,w);
 const a=poster('https://p/a.jpg'),b=poster('https://p/b.jpg','https://p/b500.jpg 500w, https://p/b780.jpg 780w'),c=poster('https://p/c.jpg');
 const onScreen=row(300,a),justBelow=row(1250,c),far=row(1400,b);
 main.add(onScreen,justBelow,far);w.changed([{addedNodes:[onScreen,justBelow,far],removedNodes:[]}]);
 assert.equal(w.opts.rootMargin,'500px 0px');
 assert.equal(a.getAttribute('src'),'https://p/a.jpg','on screen');
 assert.equal(c.getAttribute('src'),'https://p/c.jpg','within the margin');
 assert.equal(b.getAttribute('src'),null);assert.equal(b.getAttribute('srcset'),null);assert.ok(w.observed.has(far));
 w.near([{isIntersecting:true,target:far}]);
 assert.equal(b.getAttribute('src'),'https://p/b.jpg');assert.equal(b.getAttribute('srcset'),'https://p/b500.jpg 500w, https://p/b780.jpg 780w');
 assert.equal(b.dataset.heldSrc,undefined);assert.equal(far.dataset.postersHeld,undefined);assert.ok(!w.observed.has(far));
});

test('a poster already shown is never taken away, a card repainted into a waiting row waits too, a removed row is let go',()=>{
 const w=fakeWindow(),main=new El('main');holdFarRows(main,w);
 const shown=poster('https://p/shown.jpg');shown.complete=true;shown.naturalWidth=300;
 const far=row(5000,shown);main.add(far);w.changed([{addedNodes:[far],removedNodes:[]}]);
 assert.equal(shown.getAttribute('src'),'https://p/shown.jpg');assert.equal(far.dataset.postersHeld,'1');
 const late=new El('div','poster').add(poster('https://p/late.jpg'));far.children[0].add(late);
 w.changed([{addedNodes:[late],removedNodes:[]}]);
 assert.equal(late.children[0].getAttribute('src'),null,'waits with its row');
 far.isConnected=false;w.changed([{addedNodes:[],removedNodes:[far]}]);assert.ok(!w.observed.has(far));
});

test('where nothing is drawn the observer never reports: after a while every row gets its posters and nothing more waits',()=>{
 const w=fakeWindow(),main=new El('main');holdFarRows(main,w);
 const a=poster('https://p/a.jpg'),far=row(3000,a);main.add(far);w.changed([{addedNodes:[far],removedNodes:[]}]);
 assert.equal(a.getAttribute('src'),null);assert.equal(w.wait,4000);
 w.timer();
 assert.equal(a.getAttribute('src'),'https://p/a.jpg');assert.ok(!w.observed.has(far));
 const b=poster('https://p/b.jpg'),later=row(5000,b);main.add(later);w.changed([{addedNodes:[later],removedNodes:[]}]);
 assert.equal(b.getAttribute('src'),'https://p/b.jpg','no longer held on this page');
});

test('once the observer has reported, the timer changes nothing',()=>{
 const w=fakeWindow(),main=new El('main');holdFarRows(main,w);
 const a=poster('https://p/a.jpg'),far=row(3000,a);main.add(far);w.changed([{addedNodes:[far],removedNodes:[]}]);
 w.near([{isIntersecting:false,target:far}]);w.timer();
 assert.equal(a.getAttribute('src'),null,'still waits for its row to come near');
});

test('without the observers (an old browser) or a page without #main nothing changes',()=>{
 assert.equal(holdFarRows(new El('main'),{}),null);
 assert.equal(holdFarRows(null,fakeWindow()),null);
});
