import {dropdown} from './dropdown.js';
import {STUDIOS,studioById,studioBadge} from './studios.js';
/** The website's Catalogue section: categories, source genres, sorting and explicit pagination. */
export const CATEGORIES=[['films','Фільми'],['series','Серіали'],['cartoons','Мультфільми'],['animation','Аніме']];
export const SORTS=[['popular','Популярні'],['last','Нові надходження'],['watching','Зараз дивляться']];
export const MAX_PAGE=100;
const known=(list,value,fallback)=>list.some(([key])=>key===value)?value:fallback;

/** Catalogue selection from a route query (`c`, `g`, `s`); unknown values fall back to the defaults. */
export function catalogueSelection(params={}){
 const get=k=>params instanceof URLSearchParams?params.get(k):params[k];
 const genre=String(get('g')||'');
 const studio=studioById(get('studio'))?.id;
 return {category:known(CATEGORIES,get('c'),'films'),filter:studio&&get('s')==='watching'?'popular':known(SORTS,get('s'),'popular'),genre:/^[a-z0-9-]{1,60}$/.test(genre)?genre:'',...(studio?{studio}:{})};
}
/** Route query for a selection; defaults are left out so `#catalogue` stays short. */
export function selectionQuery({category='films',filter='popular',genre='',studio=''}){
 const q=new URLSearchParams();if(category!=='films')q.set('c',category);if(genre)q.set('g',genre);if(filter!=='popular')q.set('s',filter);if(studioById(studio))q.set('studio',studio);return q.toString();
}

/**
 * Pages of one selection. Only a successful page moves the page number; a failed one keeps the cards and is retried.
 * A response for an older selection (after reset) changes nothing. A page with no new titles ends the list.
 */
export class CataloguePages {
 constructor(fetchPage,keyOf,selection={}){this.fetchPage=fetchPage;this.keyOf=keyOf;this.version=0;this.reset(selection.category||'films',selection.filter||'popular',selection.genre||'',selection.studio||'');}
 reset(category,filter='popular',genre='',studio=''){
  this.controller?.abort();this.controller=null;this.version++;
  this.category=category;this.filter=filter;this.genre=genre||'';this.studio=studioById(studio)?.id||'';
  this.items=[];this.keys=new Set();this.page=0;this.loading=false;this.end=false;this.error='';this.added=[];
 }
 matches({category,filter,genre,studio}){return this.category===category&&this.filter===filter&&this.genre===(genre||'')&&this.studio===(studio||'');}
 /** Loads the next page; false when nothing was committed (busy, finished, failed or obsolete). */
 async more(){
  if(this.loading||this.end)return false;
  const version=this.version,next=this.page+1,params={category:this.category,filter:this.filter,page:String(next),...(this.genre?{genre:this.genre}:{}),...(this.studio?{studio:this.studio}:{})};
  const controller=this.controller=new AbortController();this.loading=true;this.error='';
  try{
   const result=await this.fetchPage(params,controller.signal);
   if(version!==this.version)return false;
   if(!Array.isArray(result?.items))throw Error('Некоректна відповідь каталогу');
   const added=[];for(const item of result.items){const key=item?.link?this.keyOf(item.link):'';if(key&&!this.keys.has(key)){this.keys.add(key);this.items.push(item);added.push(item);}}
   this.page=next;this.added=added;this.end=(typeof result.hasMore==='boolean'?!result.hasMore:!added.length)||next>=(this.studio?4000:MAX_PAGE);return true;
  }catch(e){if(version===this.version)this.error=e?.message||'Не вдалося завантажити каталог';return false;}
  finally{if(version===this.version){this.loading=false;if(this.controller===controller)this.controller=null;}}
 }
}

/**
 * Catalogue page inside `main`. `visible` applies the device's country filter; a page it hides completely is followed
 * by the next one (a few at most), the way the TV keeps paging while the source still has titles.
 */
export function createCatalogueView({main,el,button,card,keyOf,api,visible=items=>items,isActive,onSelection=()=>{},skip=4}){
 const models=new Map(),genreCache=new Map();
 let model,section,list,note,more,count,genreSelect,genreRetry,sort,categories,studioButton,studioChip,studioSelect,studioControl,shownKeys=new Set(),painted=0,genreRequest=0,watcher=null;
 const fetchPage=(params,signal)=>api('/api/catalogue?'+new URLSearchParams(params),undefined,{signal,timeout:params.studio?60000:25000});
 const live=()=>!!section?.isConnected&&isActive();
 const label=category=>CATEGORIES.find(([key])=>key===category)?.[1]||'Каталог';
 function modelFor(selection){
  let m=models.get(selection.category);
  if(!m){m=new CataloguePages(fetchPage,keyOf,selection);models.set(selection.category,m);}
  else if(!m.matches(selection))m.reset(selection.category,selection.filter,selection.genre,selection.studio);
  return m;
 }
 /** Appends cards not painted yet; returns the first new card. */
 function append(){
  let first=null;const fresh=visible(model.items.slice(painted));painted=model.items.length;
  for(const item of fresh){const key=keyOf(item.link);if(shownKeys.has(key))continue;shownKeys.add(key);const node=card(item);list.append(node);first||=node;}
  return first;
 }
 function paint(){
  if(!live())return null;const first=append(),shown=list.children.length;
  list.setAttribute('aria-busy',String(model.loading));count.textContent=shown?'Показано: '+shown:'';
  note.textContent=model.error?(shown?'Не вдалося завантажити наступну сторінку. Список збережено.':'Не вдалося завантажити каталог. Спробуйте ще раз.')
   :model.loading?(shown?'Завантажуємо ще…':'Завантажуємо каталог…')
   :!shown&&model.page?(model.end?'Тут поки нічого немає. Спробуйте інший жанр або порядок.':model.studio?'Шукаємо відповідні назви. Натисніть «Показати ще».':'На цих сторінках усе приховано фільтром країн.')
   :model.end&&shown?'Усі доступні результати завантажено.':'';
  note.classList.toggle('error',!!model.error);
  more.hidden=(model.end&&!model.error)||!model.page&&model.loading;more.disabled=model.loading;
  more.textContent=model.loading?'Завантаження…':model.error?'Спробувати ще':'Показати ще';
  if(!model.loading&&typeof requestAnimationFrame==='function')requestAnimationFrame(autoMore);
  return first;
 }
 /** One user request: the next page, plus a few more while the country filter hides everything new. */
 async function load(focusNew=false){
  const current=model;let first=null;
  for(let tries=0;tries<=(current.studio?1:skip);tries++){
   const pending=current.more();paint();const ok=await pending;if(current!==model)return;
   const node=paint();first||=node;
   if(!ok||first||current.end)break;
  }
  if(focusNew&&first&&live())first.focus({preventScroll:false});
 }
 async function loadGenres(){
  const category=model.category,target=genreSelect,retry=genreRetry,request=++genreRequest;
  target.disabled=true;retry.hidden=true;target.replaceChildren(el('option',{value:''},'Завантаження жанрів…'));
  try{
   let items=genreCache.get(category);
   if(!items){const body=await api('/api/genres?category='+category);if(!Array.isArray(body?.items))throw Error('genres');items=body.items.filter(g=>g&&/^[a-z0-9-]{1,60}$/.test(g.slug)&&g.name);genreCache.set(category,items);}
   if(request!==genreRequest||target!==genreSelect||model.category!==category||!section?.isConnected)return;
   target.replaceChildren(el('option',{value:''},'Усі жанри'),...items.map(g=>el('option',{value:g.slug},g.name)));
   // A genre from an old link that the source no longer lists still shows as chosen.
   if(model.genre&&!items.some(g=>g.slug===model.genre))target.append(el('option',{value:model.genre},model.genre));
   target.value=model.genre;target.disabled=false;
  }catch{if(request!==genreRequest||target!==genreSelect||model.category!==category||!section?.isConnected)return;target.replaceChildren(el('option',{value:model.genre},model.genre||'Жанри недоступні'));retry.hidden=false;}
 }
 function restart(){shownKeys=new Set();painted=0;list.replaceChildren();onSelection(selection());}
 function selection(){return {category:model.category,filter:model.filter,genre:model.genre,...(model.studio?{studio:model.studio}:{})};}
 function select(next){
  const changedCategory=next.category!==model.category;model=modelFor(next);
  restart();if(changedCategory){for(const b of categories.children)b.setAttribute('aria-pressed',String(b.dataset.category===model.category));loadGenres();}
  sort.value=model.filter;paintStudio();section.querySelector('h1').textContent=label(model.category);
  paint();if(!model.page&&!model.loading)load();else if(model.loading)watch(model);
 }
 /** Infinite scroll: the next page comes while «Показати ще» is still a screen away; the button stays for errors and keyboards. */
 function autoMore(){
  if(!live()||!model.page||model.loading||model.end||model.error||more.hidden)return;
  if(more.getBoundingClientRect().top>innerHeight+900)return;
  load();
 }
 function paintStudio(){
  const studio=studioById(model.studio);studioSelect.value=model.studio;sort.querySelector('[value=watching]').hidden=!!studio;studioButton.classList.toggle('active',!!studio);studioButton.setAttribute('aria-label','Студія / платформа'+(studio?': '+studio.name:''));studioButton.title='Студія / платформа';
  studioChip.hidden=!studio;studioChip.textContent=studio?studio.name+' ×':'';studioChip.setAttribute('aria-label',studio?'Скинути фільтр '+studio.name:'Скинути студію');
 }
 /** Repaints when a request started before the page was rebuilt finishes. */
 async function watch(current){while(current.loading&&current===model)await new Promise(r=>setTimeout(r,120));if(current===model)paint();}
 /** Shows the catalogue for a selection; the same selection already on screen is kept as is (scroll, focus, cards). */
 function open(wanted){
  if(live()&&model?.matches(wanted))return false;
  model=modelFor(wanted);
  categories=el('div',{class:'catalogue-categories',role:'group','aria-label':'Тип відео'});
  for(const [key,name] of CATEGORIES){const b=button(name,()=>{if(key!==model.category)select({...selection(),category:key,genre:''});});b.dataset.category=key;b.setAttribute('aria-pressed',String(key===model.category));categories.append(b);}
  sort=el('select',{'aria-label':'Порядок каталогу'},SORTS.map(([value,name])=>el('option',{value},name)));sort.value=model.filter;
  sort.onchange=()=>select({...selection(),filter:sort.value});
  genreSelect=el('select',{'aria-label':'Жанр каталогу'});genreSelect.onchange=()=>select({...selection(),genre:genreSelect.value});
  studioSelect=el('select',{'aria-label':'Студія / платформа'},el('option',{value:''},'Усі студії'),STUDIOS.map(s=>el('option',{value:s.id},s.name)));studioSelect.value=model.studio;
  studioSelect.onchange=()=>select({...selection(),studio:studioSelect.value,filter:studioSelect.value&&model.filter==='watching'?'popular':model.filter});
  studioControl=dropdown(studioSelect,'Студія / платформа',{icon:'<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16"/><path d="M8 3v6m8 0v6M9 15v6"/></svg>',option:o=>[studioBadge([o.value])||el('span',{class:'studio-all'},'○'),el('span',{},o.textContent)]});
  studioButton=studioControl.querySelector('button')||studioSelect;
  studioChip=button('',()=>{select({...selection(),studio:''});studioButton.focus();},'studio-chip');
  genreRetry=button('Повторити жанри',loadGenres,'text-button');genreRetry.hidden=true;
  list=el('div',{class:'grid catalogue-grid','aria-label':'Результати каталогу'});note=el('p',{class:'catalogue-note',role:'status','aria-live':'polite'});count=el('span',{class:'subtle'});
  more=button('Показати ще',()=>load(true),'pill primary');
  section=el('section',{class:'grid-page catalogue-page'},
   el('div',{class:'page-head'},el('div',{},el('div',{class:'eyebrow'},'КАТАЛОГ KIK'),el('h1',{},label(model.category))),count),
   el('div',{class:'catalogue-toolbar'},categories,el('div',{class:'catalogue-selects'},dropdown(genreSelect),dropdown(sort,'',{value:o=>o.value==='last'?'Нові':o.textContent}),studioControl,studioChip,genreRetry)),
   list,el('div',{class:'catalogue-bottom'},note,more));
  shownKeys=new Set();painted=0;main.replaceChildren(section);onSelection(selection());paintStudio();paint();loadGenres();
  watcher?.disconnect();if(typeof IntersectionObserver!=='undefined'){watcher=new IntersectionObserver(e=>{if(e.some(x=>x.isIntersecting))autoMore();},{rootMargin:'0px 0px 900px 0px'});watcher.observe(more);}
  if(!model.page&&!model.loading)load();else if(model.loading)watch(model);
  return true;
 }
 return {open,selection:()=>model&&selection()};
}
