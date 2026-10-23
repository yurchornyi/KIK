/** A native <select> dressed as a pill that opens KIK's own attached option panel.
 *  The select stays in the page as the source of truth: its value, options, disabled state and change events work as before. */
const CHEVRON='<svg width="12" height="8" viewBox="0 0 12 8" aria-hidden="true"><path d="M1 1.5l5 5 5-5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>';
let openPanel=null,nextId=0;

export function dropdown(select,label='',settings={}){
 const doc=select.ownerDocument,make=(tag,cls)=>{const n=doc.createElement(tag);if(cls)n.className=cls;return n;};
 const wrap=make('span','dropdown'),trigger=make('button','dropdown-trigger'),caption=make('span','dropdown-label'),value=make('span','dropdown-value'),arrow=make('span','dropdown-arrow');
 if(typeof HTMLElement==='undefined'||!('showPopover' in HTMLElement.prototype)){wrap.append(select);return wrap;}
 trigger.type='button';trigger.setAttribute('aria-haspopup','listbox');trigger.setAttribute('aria-expanded','false');arrow.innerHTML=CHEVRON;caption.textContent=label;
 if(settings.icon){trigger.innerHTML=settings.icon;trigger.classList.add('studio-filter');wrap.classList.add('studio-dropdown');}else{if(label)trigger.append(caption);trigger.append(value,arrow);}
 select.tabIndex=-1;select.setAttribute('aria-hidden','true');select.classList.add('dropdown-native');
 const name=()=>select.getAttribute('aria-label')||label;
 const paint=()=>{const o=select.selectedOptions[0];if(o&&settings.preview)value.replaceChildren(...settings.preview(o));else value.textContent=o?(settings.value?settings.value(o):o.textContent):'—';trigger.disabled=select.disabled||!select.options.length;value.title=o?o.textContent:'';trigger.title=name()+': '+value.title;trigger.setAttribute('aria-label',trigger.title);};
 // Programmatic `select.value = …` fires no event, so the pill repaints itself.
 for(const key of ['value','selectedIndex']){const d=Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,key);Object.defineProperty(select,key,{configurable:true,get(){return d.get.call(this);},set(v){d.set.call(this,v);paint();}});}
 new MutationObserver(()=>{paint();if(openPanel?.select===select)openPanel.hidePopover();}).observe(select,{childList:true,subtree:true,characterData:true,attributes:true,attributeFilter:['disabled','hidden','selected','label']});
 select.addEventListener('change',paint);
 const title=()=>label||name();// A tap on the pill of the open panel: the browser closes the panel at pointerup (light dismiss), so the click that follows must
 // not reopen it; the state is read at pointerdown, before that. A click with the panel still open (keyboard) closes it here.
 trigger.addEventListener('pointerdown',()=>{trigger.dataset.closing=openPanel?.trigger===trigger&&openPanel.matches(':popover-open')?'1':'';});
 trigger.onclick=()=>{const closing=trigger.dataset.closing==='1';trigger.dataset.closing='';if(openPanel?.trigger===trigger&&openPanel.matches(':popover-open')){openPanel.hidePopover();return;}if(closing)return;open(select,trigger,title(),settings);};
 trigger.onkeydown=e=>{if(e.key==='ArrowDown'||e.key==='ArrowUp'){e.preventDefault();open(select,trigger,title(),settings);}};
 wrap.append(select,trigger);paint();return wrap;
}

function open(select,trigger,title,settings={}){
 if(openPanel?.matches(':popover-open')){const same=openPanel.trigger===trigger;openPanel.hidePopover();if(same)return;}
 const doc=select.ownerDocument,panel=doc.createElement('div'),list=doc.createElement('div'),head=doc.createElement('div');
 panel.className='dropdown-panel';panel.popover='auto';head.className='dropdown-head';head.textContent=title;
 list.className='dropdown-list';list.setAttribute('role','listbox');list.setAttribute('aria-label',title);
 list.id='kik-options-'+(++nextId);trigger.setAttribute('aria-controls',list.id);
 let restoreFocus=false,tabTarget=null;
 const dismiss=(restore=false)=>{restoreFocus=restore;panel.hidePopover();};
 const options=[...select.options].filter(o=>!o.hidden);if(options.length>12)list.classList.add('many');
 const items=options.map(o=>{const b=doc.createElement('button');b.type='button';b.className='dropdown-option';b.title=o.textContent;b.setAttribute('role','option');b.setAttribute('aria-selected',String(o.selected));b.disabled=o.disabled;if(settings.option){b.append(...settings.option(o));}else b.textContent=o.textContent;
  b.onclick=()=>{const changed=select.value!==o.value;select.value=o.value;dismiss(true);if(changed){select.dispatchEvent(new Event('input',{bubbles:true}));select.dispatchEvent(new Event('change',{bubbles:true}));}};return b;});
 list.append(...items);panel.append(head,list);
 // Inside an open modal everything outside it is inert, so the panel lives in the same dialog.
 (trigger.closest('dialog[open]')||doc.body).append(panel);
 const enabled=items.filter(b=>!b.disabled);
 const move=e=>{const at=enabled.indexOf(doc.activeElement);let next=null;if(e.key==='ArrowDown')next=enabled[Math.min(enabled.length-1,at+1)];else if(e.key==='ArrowUp')next=enabled[Math.max(0,at-1)];else if(e.key==='Home')next=enabled[0];else if(e.key==='End')next=enabled[enabled.length-1];else if(e.key==='Tab'){
  const scope=trigger.closest('dialog[open]')||doc,fields=[...scope.querySelectorAll('button,a[href],input,select,textarea,video[controls],[tabindex]')].filter(n=>n.tabIndex>=0&&!n.disabled&&!panel.contains(n)&&n.getClientRects().length&&getComputedStyle(n).visibility!=='hidden'),index=fields.indexOf(trigger)+(e.shiftKey?-1:1);tabTarget=fields[index]||null;
  if(tabTarget)e.preventDefault();trigger.focus({preventScroll:true});dismiss();return;
 }else if(e.key==='Escape'){e.preventDefault();e.stopPropagation();dismiss(true);return;}if(next){e.preventDefault();next.focus();}};
 panel.addEventListener('keydown',move);
 const close=()=>panel.hidePopover(),resize=()=>{if(trigger.isConnected)place(panel,trigger);else close();},scroll=e=>{if(panel.contains(e.target))return;const r=trigger.getBoundingClientRect();if(r.bottom<0||r.top>innerHeight)close();else place(panel,trigger);};
 panel.addEventListener('toggle',e=>{if(e.newState!=='closed')return;const another=openPanel&&openPanel!==panel,focused=panel.contains(doc.activeElement);if(openPanel===panel)openPanel=null;if(trigger.getAttribute('aria-controls')===list.id){trigger.setAttribute('aria-expanded','false');trigger.removeAttribute('aria-controls');}removeEventListener('resize',resize);removeEventListener('scroll',scroll,true);panel.remove();if(another)return;if(tabTarget?.isConnected)tabTarget.focus({preventScroll:true});else if(trigger.isConnected&&(restoreFocus||focused||doc.activeElement===doc.body))trigger.focus({preventScroll:true});});
 openPanel=panel;panel.trigger=trigger;panel.select=select;panel.showPopover();trigger.setAttribute('aria-expanded','true');addEventListener('resize',resize);addEventListener('scroll',scroll,true);
 place(panel,trigger);
 (enabled.find(b=>b.getAttribute('aria-selected')==='true')||enabled[0])?.focus({preventScroll:true});
 items.find(b=>b.getAttribute('aria-selected')==='true')?.scrollIntoView({block:'nearest'});
}

/** Prefer the field's lower edge; use the space above it when the full list will not fit. */
function place(panel,trigger){
 const r=trigger.getBoundingClientRect(),gap=8,margin=12,below=innerHeight-r.bottom-gap-margin,above=r.top-gap-margin;
 const many=panel.querySelector('.dropdown-list.many'),width=Math.min(many?Math.max(r.width,innerWidth<=720?360:520):r.width,innerWidth-margin*2);
 panel.style.width=panel.style.minWidth=width+'px';panel.style.maxHeight=(innerHeight-margin*2)+'px';
 const up=below<Math.min(panel.scrollHeight,280)&&above>below;panel.style.maxHeight=Math.max(80,up?above:below)+'px';
 const actualWidth=panel.offsetWidth,left=Math.min(Math.max(margin,r.left),innerWidth-actualWidth-margin);
 panel.style.left=left+'px';panel.style.top=panel.style.bottom='auto';panel.style[up?'bottom':'top']=(up?innerHeight-r.top+gap:r.bottom+gap)+'px';panel.classList.toggle('up',up);
}
