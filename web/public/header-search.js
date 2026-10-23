/** Search history stays local and belongs to the current account and profile. */
export function searchHistoryKey({userId,profileId}={}){return userId?'kik:search:'+userId+':'+(profileId||'none'):'kik:search:guest';}
export function readSearchHistory(storage,key){
 try{const value=JSON.parse(storage.getItem(key)||'[]');return Array.isArray(value)?value.filter(q=>typeof q==='string'&&q.trim()).map(q=>q.trim().slice(0,150)).slice(0,8):[];}catch{return [];}
}
export function rememberSearch(storage,key,value){
 const query=String(value||'').trim().slice(0,150);if(!query)return '';
 const old=readSearchHistory(storage,key),normalized=query.toLocaleLowerCase('uk');
 try{storage.setItem(key,JSON.stringify([query,...old.filter(q=>q.toLocaleLowerCase('uk')!==normalized)].slice(0,8)));}catch{}return query;
}

/** One stable header field and an attached, non-modal suggestion panel. */
export function createHeaderSearch({trigger,header,el,button,storage,context,suggestions,card,onSearch,onSelect}){
 const root=el('div',{class:'header-search'}),input=el('input',{id:'header-search-input',type:'search',placeholder:'Фільм або серіал','aria-label':'Пошук фільмів і серіалів',maxlength:150,autocomplete:'off',enterkeyhint:'search',required:true});
 const panel=el('section',{id:'header-search-panel',class:'header-search-panel','aria-label':'Історія пошуку та рекомендації'});
 const usePopover='showPopover' in panel;panel.hidden=true;if(usePopover)panel.popover='auto';
 let opened=false,owner='',draftOwner='',positionFrame=0;
 const key=()=>searchHistoryKey(context());
 const closeButton=button('×',()=>close(true),'header-search-close');closeButton.setAttribute('aria-label','Закрити пошук');
 trigger.before(root);
 const form=el('form',{class:'header-search-form',role:'search'},trigger,el('div',{class:'header-search-field'},input),closeButton);
 trigger.type='button';trigger.setAttribute('aria-controls',panel.id);trigger.setAttribute('aria-expanded','false');
 input.disabled=true;input.setAttribute('aria-controls',panel.id);input.setAttribute('aria-expanded','false');
 root.append(form,panel);
 function close(focus=false){
  if(!opened)return;opened=false;root.classList.remove('is-open');header.classList.remove('search-active');
  if(usePopover&&panel.matches(':popover-open'))panel.hidePopover();panel.hidden=true;
  input.disabled=true;input.setAttribute('aria-expanded','false');trigger.setAttribute('aria-expanded','false');
  if(focus)trigger.focus({preventScroll:true});
 }
 function place(){
  positionFrame=0;if(!opened)return;const rect=form.getBoundingClientRect(),viewport=window.visualViewport;
  const left=viewport?.offsetLeft||0,bottom=(viewport?.offsetTop||0)+(viewport?.height||innerHeight),margin=12,width=Math.min(470,(viewport?.width||innerWidth)-margin*2);
  if(rect.bottom<0||rect.top>bottom){close(false);return;}
  panel.style.width=width+'px';panel.style.left=Math.max(left+margin,Math.min(rect.left,left+(viewport?.width||innerWidth)-width-margin))+'px';
  panel.style.top=(rect.bottom+9)+'px';panel.style.maxHeight=Math.max(120,bottom-rect.bottom-9-margin)+'px';
 }
 function schedulePosition(){if(opened&&!positionFrame)positionFrame=requestAnimationFrame(place);}
 function search(value){const query=rememberSearch(storage,key(),value);if(!query)return;input.value=query;close(false);onSearch(query);}
 function renderPanel(){
  const history=readSearchHistory(storage,key()),historyHeading=el('h2',{},'Останні запити');
  const clear=button('Очистити',()=>{storage.removeItem(key());renderPanel();input.focus();},'search-clear-history');clear.hidden=!history.length;
  const recent=el('div',{class:'header-search-history'});
  if(history.length)for(const query of history){
   const clock=el('span',{class:'search-history-clock','aria-hidden':'true'});clock.innerHTML='<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><circle cx="12" cy="12" r="8"/><path d="M12 7v5l3 2"/></svg>';
   const pick=button([clock,el('span',{class:'search-history-query'},query)],()=>search(query),'search-history-pick');
   const remove=button('×',()=>{storage.setItem(key(),JSON.stringify(readSearchHistory(storage,key()).filter(q=>q!==query)));renderPanel();input.focus();},'search-history-remove');remove.setAttribute('aria-label','Прибрати запит «'+query+'»');
   recent.append(el('div',{class:'search-history-row'},pick,remove));
  }else recent.append(el('p',{class:'search-history-empty'},'Тут з’являться ваші останні запити.'));
  const items=suggestions().slice(0,3),recommended=el('section',{class:'header-search-recommendations'},el('h2',{},'Рекомендуємо'));
  if(items.length)recommended.append(el('div',{class:'header-search-posters'},items.map(item=>{const node=card(item,false,()=>{close(false);onSelect(item);});node.classList.add('search-suggestion');return node;})));
  else recommended.append(el('p',{class:'search-history-empty'},'Добірка з’явиться, коли завантажиться каталог.'));
  panel.replaceChildren(el('section',{class:'header-search-recents'},el('div',{class:'search-section-head'},historyHeading,clear),recent),recommended);schedulePosition();
 }
 function open(value){
  const next=key();if(draftOwner!==next){input.value='';draftOwner=next;}
  owner=next;if(typeof value==='string')input.value=value;
  if(!opened){opened=true;input.disabled=false;root.classList.add('is-open');header.classList.add('search-active');panel.hidden=false;renderPanel();if(usePopover)panel.showPopover();
   input.setAttribute('aria-expanded','true');trigger.setAttribute('aria-expanded','true');}
  else renderPanel();
  place();input.focus({preventScroll:true});if(value)input.select();
 }
 trigger.onclick=()=>{if(opened&&input.value.trim())search(input.value);else open();};
 form.onsubmit=event=>{event.preventDefault();search(input.value);};
 root.addEventListener('keydown',event=>{if(event.key==='Escape'&&opened){event.preventDefault();event.stopPropagation();close(true);}else if(event.key==='ArrowDown'&&event.target===input&&opened){const first=panel.querySelector('.search-history-pick,.search-suggestion');if(first){event.preventDefault();first.focus();}}});
 root.addEventListener('focusout',()=>{requestAnimationFrame(()=>{if(opened&&!root.contains(document.activeElement))close(false);});});
 panel.addEventListener('toggle',event=>{if(event.newState==='closed'&&opened&&(!usePopover||!panel.matches(':popover-open')))close(false);});
 document.addEventListener('pointerdown',event=>{if(opened&&!root.contains(event.target))close(false);});
 addEventListener('resize',schedulePosition);addEventListener('scroll',schedulePosition,{passive:true});window.visualViewport?.addEventListener('resize',schedulePosition);
 form.addEventListener('transitionend',schedulePosition);
 function refresh(){if(!opened)return;const next=key();if(owner!==next){owner=next;draftOwner=next;input.value='';renderPanel();input.focus();}else if(!panel.contains(document.activeElement))renderPanel();}
 return {open,close,refresh};
}
