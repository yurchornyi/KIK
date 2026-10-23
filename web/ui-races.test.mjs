import test from 'node:test';import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';import vm from 'node:vm';
import * as recovery from './public/playback-recovery.js';import * as ads from './public/ad-skip.js';import * as rating from './public/rate-prompt.js';import * as watching from './public/watching.js';
import * as identity from './public/catalogue-identity.js';import * as avatars from './public/avatars.js';import * as preferences from './public/preferences.js';import * as queue from './public/sync-queue.js';import * as catalogueView from './public/catalogue-view.js';

// The real browser controller (public/app.js) on a tiny DOM stand-in: no network, no production data.
class Node {
 constructor(tag='div',text=''){this.tag=tag;this.children=[];this.textContent=text;this.dataset={};this.style={};this.open=false;this.isConnected=true;this.listeners={};this.classList={add(){},remove(){},toggle(){}};}
 append(...c){this.children.push(...c);}prepend(...c){this.children.unshift(...c);}replaceChildren(...c){this.children=c;}
 setAttribute(n,v){this[n]=v;}getAttribute(n){return this[n];}removeAttribute(n){delete this[n];}
 addEventListener(n,fn){(this.listeners[n]||=[]).push(fn);}removeEventListener(){}
 querySelector(){return new Node();}querySelectorAll(){return [];}contains(){return false;}focus(){}
 showModal(){this.open=true;}close(){if(!this.open)return;this.open=false;for(const fn of this.listeners.close||[])fn();}
}
const source=(await readFile(new URL('./public/app.js',import.meta.url),'utf8')).replace(/^import .*;$/gm,'').replace(/^init\(\);$/m,'');
const modules={...recovery,...ads,...rating,...watching,...identity,...avatars,...preferences,...queue,...catalogueView,titleGroups:watching.byTitle};
function controller(){
 const nodes=new Map(),body=new Node(),document={body,activeElement:body,documentElement:new Node(),head:new Node(),querySelector:s=>{if(!nodes.has(s))nodes.set(s,new Node());return nodes.get(s);},querySelectorAll:()=>[],createElement:t=>new Node(t),createTextNode:t=>new Node('#text',t),addEventListener(){},removeEventListener(){}};
 const memory=new Map(),storage={getItem:k=>memory.get(k)??null,setItem:(k,v)=>{memory.set(k,v);return true;},removeItem:k=>memory.delete(k),keys:()=>[...memory.keys()]};
 const toasts=[],context=vm.createContext({...modules,Node,document,storage,toasts,window:{addEventListener(){},scrollTo(){}},matchMedia:()=>({matches:true}),history:{pushState(){},replaceState(){}},location:{hash:'',href:'http://localhost/',pathname:'/'},URL,URLSearchParams,AbortController,AbortSignal,Blob,performance,console,Promise,scrollY:0,setTimeout,clearTimeout,setInterval(){},navigator:{},fetch(){throw Error('Unexpected network');},
  createHeaderSearch:()=>({open(){},close(){},refresh(){}}),
  createManagement:()=>({load:async()=>{},settings:{quality:'1080p',speed:1,audio:'uk',autoplay:true},reset(){},renderSettings(){},renderAdmin(){}})});
 vm.runInContext(source,context);vm.runInContext("toast=m=>toasts.push(m);",context);
 return {run:code=>vm.runInContext(code,context),context,nodes};
}
function deferred(){let resolve,reject;const promise=new Promise((y,n)=>{resolve=y;reject=n;});return {promise,resolve,reject};}
const tick=()=>new Promise(r=>setImmediate(r));
const film=(title,n)=>({title,link:`https://hdrezka.sh/films/drama/${n}-${title.toLowerCase()}.html`,voices:[{id:'uk',name:'Українська',lang:'uk'}],episodes:[]});

test('a failed old detail request cannot replace a newer dialog',async()=>{
 const c=controller(),old=deferred();c.context.old=old.promise;c.run('getFilm=()=>old;');
 const task=c.run("openDetails({link:'https://hdrezka.sh/films/drama/1-old.html',title:'Old'})");
 c.run("showModal(el('h2',{},'New screen'))");old.reject(Error('Old failure'));await task;
 assert.equal(c.nodes.get('#modal-content').children[0].children[0].textContent,'New screen');
});

test('closing and reopening the player ignores the first title\'s late answer',async()=>{
 const c=controller(),a=deferred(),b=deferred();Object.assign(c.context,{a:a.promise,b:b.promise});
 c.run("state.user={id:'u'};state.profile={id:'p',name:'QA'};getFilm=item=>item.title==='A'?a:b;globalThis.built=[];buildPlayer=()=>built.push(player.film.title);loadStream=async()=>{};render=()=>{};");
 const old=c.run(`play(${JSON.stringify(film('A',1))})`);await tick();c.run('playerModal.close()');
 const current=c.run(`play(${JSON.stringify(film('B',2))})`);await tick();
 b.resolve(film('B',2));await current;a.resolve(film('A',1));await old;
 assert.deepEqual([...c.run('built')],['B']);
});

test('fast episode changes keep the episode picked last',async()=>{
 const c=controller(),a=deferred(),b=deferred();Object.assign(c.context,{a:a.promise,b:b.promise});
 c.run("player={film:{voices:[{id:'uk',name:'Українська'}]},voice:'uk',note:{}};voiceFor=(p,s,e)=>e==='1'?a:b;fillEpisodes=()=>{};fillVoices=()=>{};loadStream=async()=>{};");
 const old=c.run("goToEpisode(player,'1','1')"),current=c.run("goToEpisode(player,'1','2')");
 b.resolve('uk');await current;a.resolve('uk');await old;assert.equal(c.run('player.episode'),'2');
});

test('changing voice or episode while paused keeps the pause',async()=>{
 const c=controller();c.run("globalThis.wanted=null;api=async()=>({qualities:[{label:'1080p',url:'https://cdn.example/clip.mp4'}]});player={film:{link:'https://hdrezka.sh/films/drama/1-a.html',voices:[{id:'uk',name:'Українська'}]},voice:'uk',loadId:0,quality:'1080p',retry:{},note:{},qualitySelect:{replaceChildren(){}},video:{pause(){}}};setSource=(p,url,pos,play)=>{wanted=play;};");
 await c.run('loadStream(false)');assert.equal(c.run('wanted'),false);assert.equal(c.run('player.playWanted'),false);
 await c.run('loadStream()');assert.equal(c.run('wanted'),true);
});

test('another profile picked during a sync is synced right after it',async()=>{
 const c=controller(),old=deferred();c.context.old=old.promise;
 c.run("globalThis.requests=[];state.user={id:'u'};state.profile={id:'a'};api=(path,body)=>{requests.push(body.profile_id);return body.profile_id==='a'?old:Promise.resolve({server_time:Date.now(),records:[]});};render=()=>{};");
 const first=c.run('sync()');c.run("state.profile={id:'b'}");const second=c.run('sync()');
 old.resolve({server_time:Date.now(),records:[]});await Promise.all([first,second]);
 assert.deepEqual([...c.run('requests')],['a','b']);assert.equal(c.run('state.syncing'),false);
});

test('changes made during a sync go up without waiting for the 30-second timer',async()=>{
 const c=controller(),old=deferred();c.context.old=old.promise;
 c.run("globalThis.batches=[];state.user={id:'u'};state.profile={id:'a'};api=(path,body)=>{batches.push(body.records.length);return batches.length===1?old:Promise.resolve({server_time:Date.now(),records:body.records});};render=()=>{};");
 const first=c.run('sync()');c.run("state.pending.set('later\\nfilm',{kind:'later',key:'film',data:{title:'A'},updated_at:1})");const second=c.run('sync()');
 old.resolve({server_time:Date.now(),records:[]});await Promise.all([first,second]);
 assert.deepEqual([...c.run('batches')],[0,1]);assert.equal(c.run('state.pending.size'),0);
});

test('a large offline queue goes up in batches of 200 without losing records; offline does not retry in a loop',async()=>{
 const c=controller();c.run("globalThis.batches=[];state.user={id:'u'};state.profile={id:'a'};for(let i=0;i<450;i++)state.pending.set('later\\n'+i,{kind:'later',key:String(i),data:{title:'A'},updated_at:1});api=async(path,body)=>{batches.push(body.records.length);return {server_time:Date.now(),records:body.records};};render=()=>{};");
 await c.run('sync()');assert.deepEqual([...c.run('batches')],[200,200,50]);assert.equal(c.run('state.pending.size'),0);assert.equal(c.run('state.records.size'),450);
 c.run("batches.length=0;state.pending.set('later\\nx',{kind:'later',key:'x',data:{},updated_at:2});api=async(path,body)=>{batches.push(body.records.length);throw Error('offline');};");
 await Promise.all([c.run('sync()'),c.run('sync()'),c.run('sync()')]);assert.equal(c.run('batches.length'),1);assert.equal(c.run('state.pending.size'),1);
});

test('title details share in-flight work, expire after 3 minutes and keep at most 80 titles',async()=>{
 const c=controller(),pending=deferred();c.context.pending=pending.promise;
 c.run("globalThis.calls=0;globalThis.now=1000;Date.now=()=>now;api=async()=>{calls++;return pending;};globalThis.item={link:'https://hdrezka.sh/films/drama/1-a.html'};");
 const first=c.run('getFilm(item)'),same=c.run('getFilm(item)');pending.resolve({title:'A'});await Promise.all([first,same]);assert.equal(c.run('calls'),1);
 await c.run('getFilm(item)');assert.equal(c.run('calls'),1);c.run('now+=180001');await c.run('getFilm(item)');assert.equal(c.run('calls'),2);
 await c.run("Promise.all(Array.from({length:100},(_,i)=>getFilm({link:'https://hdrezka.sh/films/drama/'+(i+2)+'-a.html'})))");
 assert.equal(c.run('filmCache.size'),80);assert.equal(c.run('filmPending.size'),0);
});

test('a failing button action shows its message instead of failing silently',async()=>{
 const c=controller();const b=c.run("button('Go',async()=>{throw Error('Не вдалося')})");
 for(const fn of b.listeners.click)fn({});await tick();assert.deepEqual([...c.context.toasts],['Не вдалося']);
});
