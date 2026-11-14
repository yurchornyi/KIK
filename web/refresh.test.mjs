import {test} from 'node:test';
import assert from 'node:assert/strict';
import {versionOf,pullDistance,createVersionWatch,THRESHOLD,MAX} from './public/refresh.js';

const page=(app,css)=>`<link rel="stylesheet" href="/style.css?v=${css}"><link rel="modulepreload" href="/studios.js?v=s1"><script type="module" src="/app.js?v=${app}"></script><img src="/logo.svg">`;

test('a page version is the set of fingerprinted files it names',()=>{
 assert.equal(versionOf(page('a1','c1')),versionOf(page('a1','c1')));
 assert.notEqual(versionOf(page('a1','c1')),versionOf(page('a2','c1')));
 // A deploy that changed only the styles is a new version too.
 assert.notEqual(versionOf(page('a1','c1')),versionOf(page('a1','c2')));
 // A dev page without fingerprints has none: nothing to compare, nothing reloads.
 assert.equal(versionOf('<script src="/app.js"></script>'),'');
});

test('the pull follows the finger, slows past the threshold and stops at the maximum',()=>{
 assert.equal(pullDistance(-20),0);assert.equal(pullDistance(0),0);
 assert.ok(Math.abs(pullDistance(100)-55)<1e-9);
 assert.ok(pullDistance(THRESHOLD/.55+1)>THRESHOLD);assert.ok(pullDistance(400)<400*.55);assert.equal(pullDistance(5000),MAX);
});

function fakeDocument(html,{open=false}={}){
 const tags=[...html.matchAll(/(src|href)="([^"]+)"/g)].map(([,attr,value])=>({hasAttribute:a=>a===attr,getAttribute:a=>a===attr?value:null}));
 const body=[];
 return {hidden:false,body:{append:x=>body.push(x)},appended:body,querySelectorAll:()=>tags,querySelector:s=>s==='dialog[open]'&&open?{}:null,
  createElement:()=>({})};
}
const serve=html=>async()=>({ok:true,text:async()=>html});

test('back from the background after a deploy, KIK reloads by itself; the same version changes nothing',async()=>{
 let reloads=0;const reload=()=>reloads++;
 const same=createVersionWatch({document:fakeDocument(page('a1','c1')),fetcher:serve(page('a1','c1')),reload});
 assert.equal(await same.check(true),'same');assert.equal(reloads,0);
 const fresh=createVersionWatch({document:fakeDocument(page('a1','c1')),fetcher:serve(page('a2','c1')),reload});
 assert.equal(await fresh.check(true),'reload');assert.equal(reloads,1);
});

test('a deploy found while a window is open, a film is saving or KIK stayed on screen only shows the «Оновити» pill',async()=>{
 let reloads=0;const reload=()=>reloads++;
 const open=fakeDocument(page('a1','c1'),{open:true});
 assert.equal(await createVersionWatch({document:open,fetcher:serve(page('a2','c1')),reload}).check(true),'pill');
 assert.equal(open.appended.length,1);assert.match(open.appended[0].textContent,/Оновити/);
 assert.equal(await createVersionWatch({document:fakeDocument(page('a1','c1')),fetcher:serve(page('a2','c1')),reload,blocked:()=>true}).check(true),'pill');
 const watch=createVersionWatch({document:fakeDocument(page('a1','c1')),fetcher:serve(page('a2','c1')),reload});
 assert.equal(await watch.check(false),'pill');assert.equal(await watch.check(false),'pill');
 assert.equal(reloads,0);
 // The pill reloads on a tap, and only one pill is shown however often the check runs.
 watch.pill.onclick();assert.equal(reloads,1);
});

test('no answer, a failed answer or a page without fingerprints never reloads',async()=>{
 let reloads=0;const reload=()=>reloads++;
 assert.equal(await createVersionWatch({document:fakeDocument(page('a1','c1')),fetcher:async()=>{throw Error('offline');},reload}).check(true),'skip');
 assert.equal(await createVersionWatch({document:fakeDocument(page('a1','c1')),fetcher:async()=>({ok:false}),reload}).check(true),'skip');
 assert.equal(await createVersionWatch({document:fakeDocument('<script src="/app.js"></script>'),fetcher:serve(page('a2','c1')),reload}).check(true),'skip');
 assert.equal(reloads,0);
});
