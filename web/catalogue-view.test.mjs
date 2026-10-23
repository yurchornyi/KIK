import {test} from 'node:test';
import assert from 'node:assert/strict';
import {CataloguePages,catalogueSelection,selectionQuery,MAX_PAGE} from './public/catalogue-view.js';
import {cataloguePath,parseGenres} from './catalogue.mjs';
import {localizedGenres} from './localization.mjs';
const card=id=>({link:'https://hdrezka.sh/films/drama/'+id+'-film.html',title:'Film '+id});
const keyOf=link=>new URL(link).pathname;

test('a failed next page keeps the cards and retries the same page number',async()=>{
 const calls=[];let fail=true;const model=new CataloguePages(async params=>{calls.push(+params.page);if(params.page==='2'&&fail){fail=false;throw Error('offline');}return {items:[card(params.page)]};},keyOf);
 await model.more();await model.more();
 assert.equal(model.page,1);assert.equal(model.items.length,1);assert.equal(model.error,'offline');assert.equal(model.end,false);
 await model.more();assert.deepEqual(calls,[1,2,2]);assert.equal(model.items.length,2);assert.equal(model.page,2);assert.equal(model.error,'');
});
test('a response or error for an older selection changes nothing in the new one',async()=>{
 for(const failed of [false,true]){
  let finish,signal;const model=new CataloguePages((params,s)=>{signal=s;return new Promise((resolve,reject)=>{finish=()=>failed?reject(Error('old')):resolve({items:[card(1)]});});},keyOf);
  const request=model.more();model.reset('series','last','drama');assert.equal(signal.aborted,true,'the old request is aborted');finish();assert.equal(await request,false);
  assert.equal(model.category,'series');assert.equal(model.page,0);assert.equal(model.items.length,0);assert.equal(model.loading,false);assert.equal(model.error,'');
 }
});
test('a new selection can load while the old request is still pending',async()=>{
 const pending=[];const model=new CataloguePages(params=>new Promise(resolve=>pending.push({params,resolve})),keyOf);
 const old=model.more();model.reset('films','last');const fresh=model.more();
 assert.equal(pending.length,2);assert.equal(pending[1].params.filter,'last');
 pending[1].resolve({items:[card('new')]});await fresh;pending[0].resolve({items:[card('old')]});await old;
 assert.deepEqual(model.items.map(i=>i.title),['Film new']);assert.equal(model.page,1);assert.equal(model.loading,false);
});
test('repeated clicks send one request; a page of duplicates ends the list',async()=>{
 let finish,calls=0;const model=new CataloguePages(()=>{calls++;return new Promise(resolve=>{finish=resolve;});},keyOf);
 const request=model.more();assert.equal(await model.more(),false);assert.equal(calls,1);
 finish({items:[card(1),card(1)]});await request;assert.equal(model.items.length,1);assert.deepEqual(model.added.map(i=>i.title),['Film 1']);
 const second=model.more();finish({items:[card(1)]});await second;
 assert.equal(model.end,true);assert.equal(await model.more(),false);assert.equal(calls,2);
});
test('an empty page ends the list, a malformed one stays retryable, the genre goes with every page',async()=>{
 let sent;const model=new CataloguePages(async params=>{sent=params;return {items:[]};},keyOf);
 model.reset('cartoons','watching','adventures');await model.more();
 assert.equal(model.end,true);assert.equal(sent.genre,'adventures');assert.equal(sent.category,'cartoons');assert.equal(sent.filter,'watching');
 model.reset('films');model.fetchPage=async()=>({items:null});await model.more();
 assert.equal(model.page,0);assert.equal(model.end,false);assert.ok(model.error);
});
test('the last source page ends the list',async()=>{
 const model=new CataloguePages(async params=>({items:[card(params.page)]}),keyOf);model.page=MAX_PAGE-1;
 await model.more();assert.equal(model.page,MAX_PAGE);assert.equal(model.end,true);
});
test('route selections are validated and short',()=>{
 assert.deepEqual(catalogueSelection(new URLSearchParams('c=series&g=drama&s=last')),{category:'series',filter:'last',genre:'drama'});
 assert.deepEqual(catalogueSelection(new URLSearchParams('c=../admin&g=<b>&s=random')),{category:'films',filter:'popular',genre:''});
 assert.equal(selectionQuery({category:'films',filter:'popular',genre:''}),'');
 assert.equal(selectionQuery({category:'animation',filter:'watching',genre:'fantasy'}),'c=animation&g=fantasy&s=watching');
});
test('catalogue paths keep category, genre and whole page numbers and reject injected paths',()=>{
 assert.equal(cataloguePath({category:'films',genre:'drama',filter:'last',page:2}),'/films/drama/page/2/?filter=last');
 assert.equal(cataloguePath({category:'series',page:2.9}),'/series/page/2/?filter=popular');
 assert.equal(cataloguePath({page:500}),'/page/100/?filter=popular');
 assert.equal(cataloguePath({query:'Дюна',page:2}),'/search/?do=search&subaction=search&q=%D0%94%D1%8E%D0%BD%D0%B0&page=2');
 for(const input of [{category:'../admin'},{category:'films',genre:'../admin'},{category:'films',genre:'drama?filter=x'},{genre:'drama'},{filter:'random'}])assert.throws(()=>cataloguePath(input));
});
test('genres come from the chosen category only, one per slug',()=>{
 const html='<a href="/films/drama/">Драмы</a><a href="https://hdrezka.sh/films/drama/">Драмы</a><a href="/series/comedy/">Комедии</a><a href="/films/page/">Next</a><a href="/films/fiction/" title="x">Фантастика</a>';
 assert.deepEqual(parseGenres(html,'films'),[{slug:'drama',name:'Драмы'},{slug:'fiction',name:'Фантастика'}]);
 assert.throws(()=>parseGenres(html,''));
});
test('genre names are shown in Ukrainian from the dictionary without a network call',async()=>{
 const db={prepare:()=>({bind:()=>({all:async()=>({results:[]})})})};
 const out=await localizedGenres(db,[{slug:'drama',name:'Драмы'},{slug:'comedy',name:'Комедии'}]);
 assert.deepEqual(out.map(g=>[g.slug,g.name]),[['drama','Драми'],['comedy','Комедії']]);
});
