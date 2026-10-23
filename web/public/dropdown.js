/** A native <select> dressed as a pill that opens KIK's own option panel (a sheet on phones).
 *  The select stays in the page as the source of truth: its value, options, disabled state and change events work as before. */
const CHEVRON='<svg width="12" height="8" viewBox="0 0 12 8" aria-hidden="true"><path d="M1 1.5l5 5 5-5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>';
let openPanel=null;

export function dropdown(select,label=''){
 const doc=select.ownerDocument,make=(tag,cls)=>{const n=doc.createElement(tag);if(cls)n.className=cls;return n;};
 const wrap=make('span','dropdown'),trigger=make('button','dropdown-trigger'),caption=make('span','dropdown-label'),value=make('span','dropdown-value'),arrow=make('span','dropdown-arrow');
 if(typeof HTMLElement==='undefined'||!('showPopover' in HTMLElement.prototype)){wrap.append(select);return wrap;}
 trigger.type='button';trigger.setAttribute('aria-haspopup','listbox');trigger.setAttribute('aria-expanded','false');arrow.innerHTML=CHEVRON;caption.textContent=label;
 if(label)trigger.append(caption);trigger.append(value,arrow);
 select.tabIndex=-1;select.setAttribute('aria-hidden','true');select.classList.add('dropdown-native');
 const name=()=>select.getAttribute('aria-label')||label;
 const paint=()=>{const o=select.selectedOptions[0];value.textContent=o?o.textContent:'';trigger.disabled=select.disabled;trigger.setAttribute('aria-label',name()+': '+value.textContent);};
 // Programmatic `select.value = …` fires no event, so the pill repaints itself.
 for(const key of ['value','selectedIndex']){const d=Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,key);Object.defineProperty(select,key,{configurable:true,get(){return d.get.call(this);},set(v){d.set.call(this,v);paint();}});}
 new MutationObserver(paint).observe(select,{childList:true,subtree:true,characterData:true,attributes:true,attributeFilter:['disabled']});
 select.addEventListener('change',paint);
 const title=()=>label||name();trigger.onclick=()=>open(select,trigger,title());
 trigger.onkeydown=e=>{if(e.key==='ArrowDown'||e.key==='ArrowUp'){e.preventDefault();open(select,trigger,title());}};
 wrap.append(select,trigger);paint();return wrap;
}

function open(select,trigger,title){
 if(openPanel){const same=openPanel.trigger===trigger;openPanel.hidePopover();if(same)return;}
 const doc=select.ownerDocument,panel=doc.createElement('div'),list=doc.createElement('div'),head=doc.createElement('div');
 panel.className='dropdown-panel';panel.popover='auto';head.className='dropdown-head';head.textContent=title;
 list.className='dropdown-list';list.setAttribute('role','listbox');list.setAttribute('aria-label',title);
 const options=[...select.options].filter(o=>!o.hidden);if(options.length>12)list.classList.add('many');
 const items=options.map(o=>{const b=doc.createElement('button');b.type='button';b.className='dropdown-option';b.setAttribute('role','option');b.setAttribute('aria-selected',String(o.selected));b.disabled=o.disabled;b.textContent=o.textContent;
  b.onclick=()=>{const changed=select.value!==o.value;select.value=o.value;panel.hidePopover();if(changed)select.dispatchEvent(new Event('change',{bubbles:true}));};return b;});
 list.append(...items);panel.append(head,list);
 // Inside an open modal everything outside it is inert, so the panel lives in the same dialog.
 (trigger.closest('dialog[open]')||doc.body).append(panel);
 const move=e=>{const at=items.indexOf(doc.activeElement);let next=null;if(e.key==='ArrowDown')next=items[Math.min(items.length-1,at+1)];else if(e.key==='ArrowUp')next=items[Math.max(0,at-1)];else if(e.key==='Home')next=items[0];else if(e.key==='End')next=items[items.length-1];else if(e.key==='Tab'){panel.hidePopover();return;}else if(e.key==='Escape'){e.preventDefault();e.stopPropagation();panel.hidePopover();return;}if(next){e.preventDefault();next.focus();}};
 panel.addEventListener('keydown',move);
 const close=()=>panel.hidePopover();
 let refocus=false;panel.addEventListener('beforetoggle',e=>{if(e.newState==='closed')refocus=panel.contains(doc.activeElement);});
 panel.addEventListener('toggle',e=>{if(e.newState!=='closed')return;openPanel=null;trigger.setAttribute('aria-expanded','false');removeEventListener('resize',close);panel.remove();if(refocus||doc.activeElement===doc.body)trigger.focus({preventScroll:true});});
 openPanel=panel;panel.trigger=trigger;panel.showPopover();trigger.setAttribute('aria-expanded','true');addEventListener('resize',close);
 place(panel,trigger);
 (items.find(b=>b.getAttribute('aria-selected')==='true')||items[0])?.focus({preventScroll:true});
 items.find(b=>b.getAttribute('aria-selected')==='true')?.scrollIntoView({block:'nearest'});
}

/** Under the pill (or above it when the screen ends), never wider than the window; phones get a bottom sheet from CSS. */
function place(panel,trigger){
 if(matchMedia('(max-width:720px)').matches)return;
 const r=trigger.getBoundingClientRect(),gap=8,margin=12,below=innerHeight-r.bottom-gap-margin,above=r.top-gap-margin,up=below<240&&above>below;
 panel.style.minWidth=r.width+'px';panel.style.maxHeight=Math.max(160,up?above:below)+'px';
 const width=panel.offsetWidth,left=Math.min(Math.max(margin,r.left),innerWidth-width-margin);
 panel.style.left=left+'px';panel.style[up?'bottom':'top']=(up?innerHeight-r.top+gap:r.bottom+gap)+'px';panel.classList.toggle('up',up);
}
