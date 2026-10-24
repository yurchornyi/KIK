import {avatarSvg} from './public/avatars.js';
import {createServer} from 'node:http';
import {readFile, mkdir, writeFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

// Deterministic UI checks: no production accounts, catalogue or media are mutated.
const require=createRequire(import.meta.url);
const {chromium,webkit}=require(process.env.KIK_PLAYWRIGHT_MODULE||'playwright');
const root=path.dirname(fileURLToPath(import.meta.url));
const output=path.resolve(process.env.KIK_AUDIT_OUTPUT||path.join(root,'../outputs/ui-audit'));
await mkdir(output,{recursive:true});
const image='<svg xmlns="http://www.w3.org/2000/svg" width="600" height="900"><defs><linearGradient id="g" x2="1" y2="1"><stop stop-color="#50394a"/><stop offset="1" stop-color="#151b2d"/></linearGradient></defs><path fill="url(#g)" d="M0 0h600v900H0z"/><circle cx="300" cy="380" r="150" fill="#ffffff15"/></svg>';
const items=Array.from({length:48},(_,i)=>({link:`https://hdrezka.sh/${i%2?'series':'films'}/drama/${i+1}-audit-2026.html`,title:i===0?'Неймовірно довга назва фільму про подорож крізь простір і час':`Історія ${i+1}: новий початок`,poster:'/fixture.svg',info:'2026, США, Драма',year:'2026'}));
const profile={id:'audit-profile',name:'Юрій з дуже довгим іменем профілю',avatar:'bear',enhanced_recs:0};
const user={id:'audit-user',email:'long-account-name-for-layout@example.test',role:'admin'};
const records=[{kind:'later',key:items[4].link.split('.sh/')[1],data:items[4],updated_at:Date.now()},{kind:'progress',key:items[2].link.split('.sh/')[1]+'|0:0',data:{...items[2],pos:1200000,dur:5400000},updated_at:Date.now()}];
const server=createServer(async(req,res)=>{
 const pathname=new URL(req.url,'http://localhost').pathname;
 if(pathname.startsWith('/avatars/')){res.writeHead(200,{'content-type':'image/svg+xml'});res.end(avatarSvg(path.basename(pathname,'.svg')));return;}
 if(pathname==='/fixture.svg'){res.writeHead(200,{'content-type':'image/svg+xml'});res.end(image);return;}
 // HLS itself is covered by playback regression tests; UI fixtures exercise native video controls.
 if(pathname==='/hls.min.js'){res.writeHead(200,{'content-type':'application/javascript'});res.end('');return;}
 try{const file=path.resolve(root,'public','.'+(pathname==='/'?'/index.html':pathname));if(!file.startsWith(path.join(root,'public')+path.sep))throw Error();const data=await readFile(file);res.writeHead(200,{'content-type':({'.html':'text/html','.js':'application/javascript','.css':'text/css','.svg':'image/svg+xml','.png':'image/png'})[path.extname(file)]||'application/octet-stream'});res.end(data);}catch{res.writeHead(404);res.end();}
});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
const origin=`http://127.0.0.1:${server.address().port}`;
const report={mode:'local deterministic API fixtures; viewport emulation, not physical Apple devices',samples:[],failures:[]};
let activePage,activeErrors=[];
async function trackedPage(context){const page=await context.newPage(),errors=[];activePage=page;activeErrors=errors;page.on('pageerror',e=>errors.push(e.message));return page;}
async function fixture(context,{guest=false,delay=0,failCatalogue=false,slowSeries=0,filmDelay=0,settingsGate,profileCount=1,profileName=profile.name}={}){
 const profiles=Array.from({length:profileCount},(_,i)=>({...profile,id:i?`audit-profile-${i+1}`:profile.id,name:i?`Профіль ${i+1}`:profileName,avatar:i%2?'ninja':profile.avatar}));
 const lifecycle={seriesComplete:false};await context.route('**/api/**',async route=>{
  const url=new URL(route.request().url()),p=url.pathname;
  if(delay&&p==='/api/catalogue')await new Promise(r=>setTimeout(r,delay));
  if(slowSeries&&p==='/api/catalogue'&&url.searchParams.get('category')==='series')await new Promise(r=>setTimeout(r,slowSeries));
  if(filmDelay&&p==='/api/film')await new Promise(r=>setTimeout(r,filmDelay));
  if(settingsGate&&p==='/api/settings'&&route.request().method()==='GET')await settingsGate;
  let body={};
  if(p==='/api/me')body={user:guest?null:user,profiles:guest?[]:profiles,server_time:Date.now()};
  else if(p==='/api/settings')body={settings:{quality:'1080p',audio:'uk',speed:1,autoplay:true}};
  else if(p==='/api/sync')body={records,profile:profiles.find(p=>p.id===route.request().postDataJSON()?.profile_id)||profiles[0],server_time:Date.now()};
  else if(p==='/api/catalogue'){if(failCatalogue){await route.fulfill({status:503,json:{error:'Offline fixture'}});return;}body={items:url.searchParams.get('category')==='series'?items.filter((_,i)=>i%2):url.searchParams.get('category')==='films'?items.filter((_,i)=>!(i%2)):items};}
  else if(p==='/api/film'){const item=items.find(i=>i.link===url.searchParams.get('link'))||items[0];body={...item,description:'Це довгий опис історії. '.repeat(18),genres:['Драма','Пригоди','Наукова фантастика'],duration:'120 хв',rating:'8.2',series:item.link.includes('/series/'),voices:[{id:'1',name:'Українська — дуже довга назва студії озвучення',lang:'uk'},{id:'3',name:'Новий канал (українська)',lang:'uk'},{id:'2',name:'Original',lang:'en'}],episodes:[{season:'1',episode:'1',title:'Серія 1: дуже довга назва першої серії'}],related:[]};}
  else if(p==='/api/episodes')body={episodes:[{season:'1',episode:'1',title:'Серія 1: дуже довга назва першої серії'}]};
  else if(p==='/api/stream')body={qualities:[{label:'1080p',url:origin+'/missing.mp4'}]};
  else if(p==='/api/artwork/batch')body={configured:true,results:Object.fromEntries(items.map(i=>[i.link.split('.sh/')[1],{poster:origin+'/fixture.svg',posterLarge:origin+'/fixture.svg',backdrop:origin+'/fixture.svg',studios:['hbo']}]))};
  else if(p==='/api/genres')body={items:[{slug:'drama',name:'Драми'},{slug:'comedy',name:'Комедії з дуже довгою назвою жанру'}]};
  else if(p==='/api/search-aliases')body={aliases:[]};
  else if(p==='/api/metadata')body={credits:[]};
  else if(p==='/api/admin/overview')body={accounts:12,profiles:34,invitations:56,events:[{action:'allow',target:user.email,created_at:Date.now()}]};
  else if(p==='/api/admin/access')body={emails:[{email:user.email,registered:true}]};
  else if(p==='/api/admin/users')body={users:[{id:'fixture-member',email:'another-very-long-account-email@example.test',profiles:3,created_at:Date.now()},{...user,profiles:1,created_at:Date.now()}],next:null};
  else if(p==='/api/admin/profiles')body={profiles:[profile]};
  if(p==='/api/catalogue'&&url.searchParams.get('category')==='series')lifecycle.seriesComplete=true;await route.fulfill({json:body});
 });return lifecycle;
}
async function inspect(page,label,extra={}){
 await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));
 const result=await page.evaluate(()=>{
  const visible=e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none';};
  const dialog=document.querySelector('dialog[open]'),scope=dialog||document;
  const overflow=[...scope.querySelectorAll('input,select,button,h1,h2,.settings-card,.grid,.hero-copy')].filter(visible).filter(e=>{
   // Horizontal shelves, the navigation strip and the player's option row intentionally scroll within their own bounds.
   if(e.closest('.shelf-line,.header nav,.player-tools'))return false;
   const r=e.getBoundingClientRect();return r.left< -1||r.right>innerWidth+1;
  }).map(e=>({tag:e.tagName,text:e.textContent.slice(0,60),x:Math.round(e.getBoundingClientRect().x),width:Math.round(e.getBoundingClientRect().width)}));
  const header=document.querySelector('.header'),actions=document.querySelector('.header-actions'),nav=header?.querySelector('nav');
  let headerOverlap=false;if(!dialog&&nav&&actions){const n=nav.getBoundingClientRect(),a=actions.getBoundingClientRect(),end=getComputedStyle(nav).overflowX==='visible'?nav.lastElementChild.getBoundingClientRect().right:n.right;headerOverlap=n.top<a.bottom&&a.top<n.bottom&&end>a.left+1;}
  const tools=document.querySelector('#player-modal[open] .player-tools'),fields=tools?[...tools.children].map(e=>e.getBoundingClientRect()):[],lastTop=fields.length?Math.max(...fields.map(r=>Math.round(r.top))):0,last=fields.filter(r=>Math.abs(r.top-lastTop)<2),emptyPlayerCells=last.length>0&&Math.max(...last.map(r=>r.right))-Math.min(...last.map(r=>r.left))<tools.clientWidth-12;const dialogMetrics=dialog?{client:dialog.clientWidth,scroll:dialog.scrollWidth,children:[...dialog.querySelectorAll('*')].filter(visible).filter(e=>e.scrollWidth>e.clientWidth+1).map(e=>({tag:e.tagName,cls:e.className,client:e.clientWidth,scroll:e.scrollWidth}))}:null;return {emptyPlayerCells,dialogMetrics,width:innerWidth,height:innerHeight,documentOverflow:document.documentElement.scrollWidth>innerWidth+1,overflow,headerOverlap,dialogOverflow:!!dialog&&dialog.scrollWidth>dialog.clientWidth+1,mainHeading:document.querySelector('#main h1')?.textContent,scrollY,focused:document.activeElement?.textContent?.slice(0,40)};
 });
 const sample={label,...extra,...result};report.samples.push(sample);if(result.dialogOverflow)await page.screenshot({path:path.join(output,`${extra.engine}-${result.width}-${label}-overflow.png`),animations:'disabled'});
 if(result.documentOverflow||result.overflow.length||result.headerOverlap||result.dialogOverflow)report.failures.push(sample);
 return sample;
}
const sizes=process.env.KIK_AUDIT_SIZES?process.env.KIK_AUDIT_SIZES.split(',').map(s=>s.split('x').map(Number)):[[320,568],[375,667],[390,844],[430,932],[667,375],[768,1024],[820,1180],[1024,768],[1180,820],[1280,720],[1440,900],[1920,1080],[2560,1440],[3840,2160]];
const shots=new Set();
function check(engine,label,passed,details={}){const sample={engine,label,passed,...details};report.samples.push(sample);if(!passed){report.failures.push(sample);const name=`${engine}-${Math.round(details.width||0)}-${label}-failed.png`;if(activePage&&!shots.has(name)){shots.add(name);activePage.screenshot({path:path.join(output,name),animations:'disabled'}).catch(()=>{});}}}
const dropdownTrigger=(page,label)=>page.getByRole('button',{name:new RegExp('^'+label+':')});
async function choose(page,label,value){await dropdownTrigger(page,label).click();await page.getByRole('option',{name:value,exact:true}).click();await page.locator('.dropdown-panel').waitFor({state:'detached'});}
async function auditSettings(page,engine,width){
 check(engine,'settings-use-custom-dropdowns',await page.locator('.settings-fields .dropdown').count()===3&&await page.locator('.settings-fields select:not(.dropdown-native)').count()===0,{width});
 for(const label of ['Якість відео','Бажана мова','Швидкість']){await dropdownTrigger(page,label).click();await page.locator('.dropdown-panel').waitFor();await inspect(page,'settings-options-'+label,{engine});if(label==='Якість відео'&&engine==='chromium'&&(width===390||width===1440))await page.screenshot({path:path.join(output,`${engine}-${width}-settings-sheet.png`),animations:'disabled'});await page.keyboard.press('Escape');await page.locator('.dropdown-panel').waitFor({state:'detached'});check(engine,'settings-dropdown-restores-focus',await dropdownTrigger(page,label).evaluate(e=>e===document.activeElement),{width,field:label});}
 await dropdownTrigger(page,'Якість відео').focus();await page.keyboard.press('ArrowDown');await page.locator('.dropdown-panel').waitFor();await page.keyboard.press('Tab');await page.locator('.dropdown-panel').waitFor({state:'detached'});check(engine,'dropdown-tab-moves-to-next-field',await dropdownTrigger(page,'Бажана мова').evaluate(e=>e===document.activeElement),{width});
 await choose(page,'Якість відео','HD · 720p');await choose(page,'Бажана мова','Англійська');await choose(page,'Швидкість','1.5×');const sent=page.waitForRequest(r=>r.url().endsWith('/api/settings')&&r.method()==='POST');await page.getByRole('button',{name:'Зберегти налаштування',exact:true}).click();const body=(await sent).postDataJSON();check(engine,'custom-settings-submit-selected-values',body.profile_id===profile.id&&body.settings.quality==='720p'&&body.settings.audio==='en'&&body.settings.speed===1.5,{width});
}
async function auditPair(page,engine,width){
 await dropdownTrigger(page,'Профіль для TV').click();await page.locator('.dropdown-panel').waitFor();await inspect(page,'tv-profile-options',{engine});check(engine,'tv-profile-panel-stays-in-dialog',await page.locator('.dropdown-panel').evaluate(p=>p.closest('dialog')?.id==='modal'&&p.matches(':popover-open')),{width});
 const menu=await page.locator('.dropdown-panel').evaluate(p=>{const t=document.querySelector('select[aria-label="Профіль для TV"]').closest('.dropdown').querySelector('button').getBoundingClientRect(),r=p.getBoundingClientRect();return {width:r.width,fieldWidth:t.width,attached:Math.min(Math.abs(r.top-t.bottom-8),Math.abs(t.top-r.bottom-8))<1,avatars:p.querySelectorAll('.dropdown-avatar').length,handle:getComputedStyle(p,'::before').content};});check(engine,'profile-menu-is-compact-with-avatars',menu.attached&&Math.abs(menu.width-menu.fieldWidth)<1&&menu.avatars===2&&['none','normal'].includes(menu.handle),{width,...menu});
 if(engine==='chromium'&&(width===390||width===1440))await page.screenshot({path:path.join(output,`${engine}-${width}-tv-profile-sheet.png`),animations:'disabled'});await page.getByRole('option',{name:'Профіль 2',exact:true}).click();await page.locator('.dropdown-panel').waitFor({state:'detached'});await page.getByRole('textbox',{name:'Код із TV',exact:true}).fill('ABCD EFGH');const sent=page.waitForRequest(r=>r.url().endsWith('/api/pair/claim'));await page.locator('#modal').getByRole('button',{name:'Прив’язати TV',exact:true}).click();const body=(await sent).postDataJSON();check(engine,'pairing-uses-chosen-profile',body.profile_id==='audit-profile-2'&&body.code==='ABCD EFGH',{width});await page.getByRole('heading',{name:'TV прив’язано',exact:true}).waitFor();
}
async function auditPlayer(page,engine,width,series){
 check(engine,'player-uses-custom-dropdowns',await page.locator('.player-tools .dropdown:not([hidden]):not(.sleep-dropdown)').count()===(series?5:3)&&await page.locator('.player-tools select:not(.dropdown-native)').count()===0,{width,series});
 for(const label of ['Озвучення',...(series?['Сезон','Серія']:[]),'Якість','Швидкість']){await dropdownTrigger(page,label).click();await page.locator('.dropdown-panel').waitFor();await inspect(page,'player-options-'+label,{engine});if(label==='Озвучення'){check(engine,'keyboard-skips-unavailable-voice',await page.getByRole('option',{name:'Російська',exact:true}).isDisabled(),{width,series});await page.keyboard.press('Home');await page.keyboard.press('ArrowDown');check(engine,'keyboard-reaches-next-available-voice',await page.getByRole('option',{name:'Англійська',exact:true}).evaluate(e=>e===document.activeElement),{width,series});}if(label==='Серія'&&engine==='chromium'&&(width===390||width===1440))await page.screenshot({path:path.join(output,`${engine}-${width}-episode-sheet.png`),animations:'disabled'});await page.keyboard.press('Escape');await page.locator('.dropdown-panel').waitFor({state:'detached'});check(engine,'player-sheet-escape-keeps-player-open',await page.locator('#player-modal').evaluate(d=>d.open)&&await dropdownTrigger(page,label).evaluate(e=>e===document.activeElement),{width,field:label,series});}
 await choose(page,'Швидкість','1.25×');check(engine,'custom-player-speed-changes-playback-rate',await page.locator('#player-modal video').evaluate(v=>v.playbackRate===1.25),{width,series});
 for(const label of ['Таймер сну','Студія']){if(label==='Студія')await choose(page,'Озвучення','Українська');const trigger=dropdownTrigger(page,label);if(!await trigger.isVisible().catch(()=>false))continue;await trigger.click();const panel=page.locator('.dropdown-panel');await panel.waitFor();const box=await panel.evaluate(e=>{const opts=[...e.querySelectorAll('.dropdown-option')];return {width:e.getBoundingClientRect().width,tallest:Math.max(...opts.map(o=>o.getBoundingClientRect().height)),longest:Math.max(...opts.map(o=>o.scrollWidth-o.clientWidth))};});if(engine==='chromium'&&(width===390||width===1440))await page.screenshot({path:path.join(output,`${engine}-${width}-player-panel-${label==='Студія'?'studio':'sleep'}.png`),animations:'disabled'});check(engine,'player-menu-fits-its-choices-on-one-line',box.tallest<=50&&box.longest<=0&&(label!=='Таймер сну'||box.width<=200),{width,field:label,...box});await page.keyboard.press('Escape');await panel.waitFor({state:'detached'});}
 await choose(page,'Таймер сну','15 хв');check(engine,'player-sleep-timer-confirms-the-choice',await page.locator('#toast').evaluate(t=>t.textContent.includes('15 хв')),{width,series});if(engine==='chromium'&&(width===390||width===1440)){await page.waitForTimeout(400);await page.screenshot({path:path.join(output,`${engine}-${width}-player-sleep.png`),animations:'disabled'});}check(engine,'player-sleep-timer-is-one-round-button',await page.locator('.player-tools .sleep-dropdown .dropdown-trigger.active').count()===1&&await page.locator('.player-tools .sleep-dropdown').evaluate(e=>e.getBoundingClientRect().width<=40),{width,series});
}
try{
 const engines=(process.env.KIK_AUDIT_ENGINES||'chromium,webkit').split(',');
 for(const [engine,type] of Object.entries({chromium,webkit}).filter(([name])=>engines.includes(name))){
  const browser=await type.launch({headless:true});
  for(const [width,height] of sizes){
   const context=await browser.newContext({viewport:{width,height},deviceScaleFactor:width<1200?2:1,hasTouch:width<1200,isMobile:width<768,reducedMotion:'reduce'});await fixture(context,{profileCount:2});
   const page=await trackedPage(context),errors=[];activePage=page;activeErrors=errors;page.on('pageerror',e=>errors.push(e.message));
   const started=performance.now();await page.goto(origin);await page.locator('.shelf').first().waitFor();await page.locator('[data-tab=admin]').waitFor();
   await inspect(page,'home',{engine,readyMs:Math.round(performance.now()-started)});
   if((width===390||width===820||width===1440)&&engine==='chromium')await page.screenshot({path:path.join(output,`${engine}-${width}-home.png`),animations:'disabled'});
   check(engine,'hero-has-three-vector-actions',await page.locator('.hero-actions button').count()===3&&await page.locator('.hero-actions .hero-action-icon svg').count()===3,{width});
   const heroTitle=await page.locator('.hero h1').textContent();await page.locator('.hero-list').click();
   const heroState=await page.evaluate(title=>{const e=document.querySelector('.hero-list');return {pressed:e.getAttribute('aria-pressed'),text:e.textContent,focused:e===document.activeElement,saved:!!JSON.parse(localStorage.getItem('kik:audit-user:audit-profile')||'{}').records?.some(r=>r.kind==='later'&&r.data?.title===title),shown:document.querySelector('.hero h1').textContent};},heroTitle);check(engine,'hero-adds-to-my-list',heroState.pressed==='true'&&heroState.text==='У списку'&&heroState.focused&&heroState.saved,{width,heroTitle,...heroState});
   check(engine,'saving-keeps-the-featured-title',(await page.locator('.hero h1').textContent())===heroTitle,{width});await page.locator('.hero-list').click();
   check(engine,'hero-removes-from-my-list',await page.locator('.hero-list').evaluate(e=>e.getAttribute('aria-pressed')==='false'&&e.textContent==='Мій список')&&await page.evaluate(title=>!JSON.parse(localStorage.getItem('kik:audit-user:audit-profile')||'{}').records?.some(r=>r.kind==='later'&&r.data?.title===title),heroTitle),{width});
   await page.locator('.hero-details').click();await page.locator('.detail-actions').waitFor();check(engine,'hero-info-opens-title-details',(await page.locator('#modal h2').textContent())===heroTitle,{width});await page.locator('#modal .close').click();
   for(const tab of ['catalogue','later','history','settings','admin']){
    const t=performance.now();await page.locator(`[data-tab=${tab}]`).click();await page.locator(tab==='admin'?'.admin-stats':tab==='settings'?'.settings-card':'.grid-page').first().waitFor();await inspect(page,tab,{engine,switchMs:Math.round(performance.now()-t)});if(tab==='settings')await auditSettings(page,engine,width);
   }
   await page.locator('[data-tab=catalogue]').click();await page.locator('.studio-filter').click();await page.locator('.dropdown-panel').waitFor();await inspect(page,'studio-picker',{engine});
   if(engine==='chromium'&&(width===390||width===1440))await page.screenshot({path:path.join(output,`${engine}-${width}-studio-picker.png`),animations:'disabled'});
   await page.locator('.dropdown-option').filter({hasText:'HBO'}).click();await page.locator('.studio-chip').waitFor();
   check(engine,'studio-route',new URL(page.url()).hash.includes('studio=hbo'),{width});
   await page.getByRole('button',{name:/^Жанр каталогу:/}).click();await page.getByRole('option',{name:'Драми',exact:true}).click();await page.getByRole('button',{name:/^Порядок каталогу:/}).click();await page.getByRole('option',{name:'Нові надходження',exact:true}).click();
   check(engine,'studio-combined-filters',new URL(page.url()).hash.includes('studio=hbo')&&new URL(page.url()).hash.includes('g=drama')&&new URL(page.url()).hash.includes('s=last'),{width});
   await inspect(page,'studio-selected',{engine});
   if(engine==='chromium'&&(width===390||width===1440)){await page.screenshot({path:path.join(output,`${engine}-${width}-studio-selected.png`),animations:'disabled'});await page.locator('.catalogue-toolbar').screenshot({path:path.join(output,`${engine}-${width}-studio-toolbar.png`),animations:'disabled'});}
   await page.locator('.studio-filter').click();await page.keyboard.press('Escape');await page.locator('.dropdown-panel').waitFor({state:'detached'});check(engine,'studio-focus-restored',await page.locator('.studio-filter').evaluate(e=>e===document.activeElement),{width,focus:await page.evaluate(()=>document.activeElement?.outerHTML?.slice(0,200))});
   await page.locator('.studio-chip').click();check(engine,'studio-reset',!new URL(page.url()).hash.includes('studio='),{width});
   await page.getByRole('button',{name:/^Жанр каталогу:/}).click();await page.getByRole('option',{name:'Комедії з дуже довгою назвою жанру',exact:true}).click();
   const fullGenre=await page.getByRole('button',{name:/^Жанр каталогу:/}).evaluate(e=>{const value=e.querySelector('.dropdown-value');return value.scrollHeight<=value.clientHeight+1&&getComputedStyle(value).whiteSpace==='nowrap'&&e.title.includes('Комедії з дуже довгою назвою жанру');});check(engine,'single-line-genre-caption',fullGenre,{width});await inspect(page,'long-genre',{engine});
   if(engine==='chromium'&&width===390)await page.screenshot({path:path.join(output,'chromium-390-long-genre.png'),animations:'disabled'});

   for(const [label,open] of [
    ['profiles',()=>page.locator('#account').click()],
    ['pair',()=>page.locator('#connect-tv').click()],
    ['countries',async()=>{await page.locator('[data-tab=settings]').click();await page.getByRole('button',{name:'Приховані країни',exact:true}).click();}],
    ['avatars',()=>page.getByRole('button',{name:'Змінити аватарку',exact:true}).click()],
    ['taste',async()=>{await page.locator('[data-tab=settings]').click();await page.getByRole('button',{name:'Налаштувати смак',exact:true}).click();}],
   ]){await open();await page.locator('#modal[open]').waitFor();await inspect(page,label,{engine});if(label==='pair')await auditPair(page,engine,width);await page.locator('#modal .close').click();}
   await page.evaluate(()=>localStorage.setItem('kik:search:audit-user:audit-profile',JSON.stringify(['Дюна','Шерлок'])));await page.locator('#search-open').click();await page.locator('.header-search-panel').waitFor();await page.waitForFunction(()=>document.querySelector('#header-search-input')?.getBoundingClientRect().width>80);await inspect(page,'header-search',{engine});
   check(engine,'header-search-focus',await page.locator('#header-search-input').evaluate(e=>e===document.activeElement),{width});check(engine,'search-has-history-and-posters',await page.locator('.search-history-row').count()===2&&await page.locator('.search-suggestion').count()===3,{width});
   if(width>=1280){const metrics=await page.evaluate(()=>{const form=document.querySelector('.header-search-form').getBoundingClientRect(),nav=document.querySelector('.header nav').getBoundingClientRect(),panel=document.querySelector('.header-search-panel').getBoundingClientRect();return {gap:form.left-nav.right,width:form.width,panelLeft:panel.left,formLeft:form.left};});check(engine,'search-expands-next-to-navigation',metrics.gap>=16&&metrics.gap<=32&&metrics.width>300&&Math.abs(metrics.panelLeft-metrics.formLeft)<1,{width,...metrics});}
   if(engine==='chromium'&&(width===390||width===1440)){await page.screenshot({path:path.join(output,`${engine}-${width}-header-search.png`),animations:'disabled'});const clip=await page.evaluate(()=>{const a=document.querySelector('.header-search-form').getBoundingClientRect(),b=document.querySelector('.header-search-panel').getBoundingClientRect(),x=Math.max(0,Math.min(a.left,b.left)-8),y=Math.max(0,a.top-8);return {x,y,width:Math.min(innerWidth-x,Math.max(a.right,b.right)-x+8),height:Math.min(innerHeight-y,b.bottom-y+8)};});await page.screenshot({path:path.join(output,`${engine}-${width}-header-search-detail.png`),clip,animations:'disabled'});}

   await page.keyboard.press('Escape');await page.locator('.header-search-panel').waitFor({state:'hidden'});check(engine,'header-search-escape',await page.locator('#search-open').evaluate(e=>e===document.activeElement),{width});
   await page.locator('#search-open').click();await page.getByRole('button',{name:'Прибрати запит «Дюна»',exact:true}).click();check(engine,'remove-search-history',await page.locator('.search-history-row').count()===1,{width});await page.locator('.search-history-pick').click();await page.waitForFunction(()=>document.querySelector('#main h1')?.textContent==='Результати для «Шерлок»');check(engine,'repeat-saved-query',new URL(page.url()).hash.includes(encodeURIComponent('Шерлок')),{width});await page.locator('#search-open').click();await page.locator('.search-clear-history').click();check(engine,'clear-search-history',await page.locator('.search-history-row').count()===0,{width});await page.mouse.click(2,height-2);check(engine,'search-outside-click',await page.locator('.header-search-panel').evaluate(e=>e.hidden),{width});
   await page.locator('[data-tab=catalogue]').click();await page.locator('.catalogue-categories [data-category=films]').click();await page.locator('.grid .poster').first().click();await page.locator('.detail-actions').waitFor();await inspect(page,'details',{engine});await page.getByRole('button',{name:/^▶ /}).first().click();await page.locator('#player-modal video').waitFor();await inspect(page,'player',{engine});await auditPlayer(page,engine,width,false);await page.locator('#player-modal .close').click();
   await page.locator('#search-open').click();await page.locator('#header-search-input').fill('Історія');await page.locator('#header-search-input').press('Enter');await page.waitForFunction(()=>document.querySelector('#main h1')?.textContent.startsWith('Результати'));await inspect(page,'search-results',{engine});check(engine,'search-query-remembered',await page.evaluate(()=>JSON.parse(localStorage.getItem('kik:search:audit-user:audit-profile')||'[]')[0])==='Історія',{width});
   await page.locator('[data-tab=catalogue]').click();await page.locator('.catalogue-categories [data-category=series]').click();await page.locator('.grid .poster').first().click();await page.locator('.detail-actions').waitFor();await page.getByRole('button',{name:/^▶ /}).first().click();await page.locator('#player-modal video').waitFor();await inspect(page,'series-player',{engine});await auditPlayer(page,engine,width,true);await page.locator('#player-modal .close').click();await page.goto(origin+'/sources.html');await inspect(page,'sources',{engine});
   if(errors.length)report.failures.push({engine,width,height,label:'runtime',errors});
   await context.close();
  }
  {
   let release;const settingsGate=new Promise(r=>release=r),context=await browser.newContext({viewport:{width:390,height:844},reducedMotion:'reduce'});await fixture(context,{settingsGate});const page=await trackedPage(context),loaded=page.waitForResponse(r=>r.url().includes('/api/settings')&&r.request().method()==='GET');await page.goto(origin);await page.locator('.hero-list').waitFor();await page.locator('[data-tab=settings]').click();await choose(page,'Якість відео','HD · 720p');release();await loaded;await page.waitForTimeout(60);check(engine,'late-settings-load-keeps-user-selection',await dropdownTrigger(page,'Якість відео').textContent().then(t=>t.includes('HD · 720p')));await context.close();
  }
  // Sample actual animation frames: reduced-motion checks cannot catch a moving anchor.
  for(const width of [390,820,1440]){
   const context=await browser.newContext({viewport:{width,height:900},reducedMotion:'no-preference'});await fixture(context);const page=await trackedPage(context);await page.goto(origin);await page.locator('.shelf').first().waitFor();await page.locator('[data-tab=admin]').waitFor();
   await page.evaluate(()=>localStorage.setItem('kik:search:audit-user:audit-profile',JSON.stringify(['Дюна','Шерлок'])));
   const frames=await page.evaluate(async()=>{
    const trigger=document.querySelector('#search-open'),root=document.querySelector('.header-search'),panel=document.querySelector('.header-search-panel'),form=document.querySelector('.header-search-form'),before=trigger.getBoundingClientRect(),samples=[],started=performance.now();trigger.click();
    do{const p=panel.getBoundingClientRect(),a=root.getBoundingClientRect(),f=form.getBoundingClientRect();samples.push({ms:performance.now()-started,left:p.left,top:p.top,right:p.right,bottom:p.bottom,anchorLeft:a.left,anchorBottom:a.bottom,formLeft:f.left,opacity:Number(getComputedStyle(panel).opacity),inside:p.left>=0&&p.right<=innerWidth&&p.top>=0&&p.bottom<=innerHeight});await new Promise(r=>requestAnimationFrame(r));}while(performance.now()-started<350);
    return {before:before.left,samples};
   });
   const samples=frames.samples,lefts=samples.map(s=>s.left),anchors=samples.map(s=>s.anchorLeft),last=samples.at(-1);
   check(engine,'search-panel-stays-anchored-during-animation',Math.max(...lefts)-Math.min(...lefts)<1&&Math.max(...anchors)-Math.min(...anchors)<1&&samples.every(s=>s.inside&&s.top-s.anchorBottom>=4&&s.top-s.anchorBottom<=10),{width,frames:samples.length,leftRange:Math.max(...lefts)-Math.min(...lefts)});
   check(engine,'search-opening-finishes-without-a-snap',Math.abs(samples[0].formLeft-frames.before)<2&&Math.abs(last.formLeft-last.anchorLeft)<1&&last.opacity===1,{width});
   const closingFrames=await page.evaluate(async()=>{
    const form=document.querySelector('.header-search-form'),panel=document.querySelector('.header-search-panel'),width=form.getBoundingClientRect().width,samples=[],started=performance.now();document.querySelector('.header-search-close').click();
    do{const style=getComputedStyle(form),clip=style.clipPath.match(/^inset\(\S+\s+([\d.]+)/);samples.push({hidden:panel.hidden,clip:clip?Number(clip[1]):0,opacity:Number(getComputedStyle(panel).opacity)});await new Promise(r=>requestAnimationFrame(r));}while(performance.now()-started<300);
    return {width,samples,focus:document.activeElement.id};
   });
   check(engine,'search-collapses-smoothly-to-icon',closingFrames.samples.some(s=>s.clip>1&&s.clip<closingFrames.width-45)&&closingFrames.samples.some(s=>!s.hidden&&s.opacity>0&&s.opacity<1)&&closingFrames.samples.at(-1).hidden&&closingFrames.focus==='search-open',{width});await page.locator('#search-open').click();
   await page.evaluate(()=>{document.querySelector('.header-search-close').click();document.querySelector('#search-open').click();});await page.waitForTimeout(40);await page.evaluate(()=>{document.querySelector('.header-search-close').click();document.querySelector('#search-open').click();});await page.waitForTimeout(280);
   check(engine,'search-can-reopen-during-animation',await page.locator('.header-search-panel').evaluate(p=>!p.hidden&&p.matches(':popover-open')&&document.activeElement.id==='header-search-input'),{width});
   await page.setViewportSize({width:width===390?820:390,height:844});await inspect(page,'search-resize-during-session',{engine,width});await page.keyboard.press('Escape');await page.locator('#search-open').click();await page.keyboard.press('ArrowDown');check(engine,'search-keyboard-history',await page.locator('.search-history-pick').first().evaluate(e=>e===document.activeElement),{width});await page.keyboard.press('Escape');
   await page.evaluate(()=>localStorage.removeItem('kik:search:audit-user:audit-profile'));await page.locator('#search-open').click();await page.keyboard.press('ArrowDown');check(engine,'search-keyboard-recommendation',await page.locator('.search-suggestion').first().evaluate(e=>e===document.activeElement),{width});await page.keyboard.press('Enter');await page.locator('.detail-actions').waitFor();await page.locator('.header-search-panel').waitFor({state:'hidden'});check(engine,'search-recommendation-opens-details',await page.locator('.header-search-panel').evaluate(e=>e.hidden),{width});await page.locator('#modal .close').click();
   await page.locator('#search-open').click();await page.locator('#header-search-input').fill('Історія');await page.locator('#header-search-input').press('Enter');await page.getByRole('button',{name:'Змінити запит',exact:true}).click();
   check(engine,'search-reopens-from-scrolled-results',await page.locator('.header-search-panel').evaluate(p=>!p.hidden&&p.matches(':popover-open')&&scrollY===0&&document.activeElement.id==='header-search-input'),{width});
   await page.clock.install();await page.mouse.move(2,842);await page.locator('#header-search-input').fill('');await page.clock.fastForward(11900);check(engine,'empty-search-waits-before-collapsing',await page.locator('.header-search-panel').evaluate(p=>!p.hidden),{width});await page.clock.fastForward(200);await page.locator('.header-search-panel').waitFor({state:'hidden'});check(engine,'unused-empty-search-returns-to-icon',await page.locator('#search-open').evaluate(e=>e===document.activeElement),{width});
   await page.locator('#search-open').click();await page.locator('#header-search-input').fill('Дюна');await page.mouse.move(2,842);await page.clock.fastForward(60000);check(engine,'search-keeps-an-unfinished-query',await page.locator('#header-search-input').evaluate(e=>!e.disabled&&e.value==='Дюна'&&e===document.activeElement),{width});await context.close();
  }
  for(const state of [{guest:true},{failCatalogue:true},{delay:900}]){
   const context=await browser.newContext({viewport:{width:375,height:667}});await fixture(context,state);const page=await trackedPage(context);await page.goto(origin);
   if(state.guest){await page.locator('.hero-list').waitFor();await page.locator('.hero-list').click();await page.locator('input[type=email]').waitFor();check(engine,'guest-hero-list-requests-sign-in',await page.locator('.hero-list').getAttribute('aria-pressed')==='false'&&await page.evaluate(()=>!localStorage.getItem('kik:audit-user:audit-profile')));await inspect(page,'login',{engine});await page.getByRole('button',{name:'Вперше тут? Створити обліковий запис',exact:true}).click();await inspect(page,'register',{engine});}
   else if(state.failCatalogue){await page.getByRole('button',{name:'Спробувати ще',exact:true}).waitFor();await inspect(page,'offline',{engine});}
   else{await inspect(page,'loading',{engine});await page.locator('.shelf').first().waitFor();}
   await context.close();
  }
  for(const profileCount of [1,8])for(const width of [320,390,1440]){
   const context=await browser.newContext({viewport:{width,height:width===1440?900:844},reducedMotion:'reduce'});await fixture(context,{profileCount,profileName:profileCount===1?'Yuriy':profile.name});const page=await trackedPage(context);await page.goto(origin);await page.locator('[data-tab=admin]').waitFor();await page.locator('#account').click();await page.locator('.profile-modal').waitFor();
   await inspect(page,'profile-picker-'+profileCount,{engine,width});check(engine,'profile-picker-count',await page.locator('.profile-tile[aria-pressed]').count()===profileCount,{width,profileCount});
   check(engine,'profile-picker-focus',await page.locator('.profile-tile[aria-pressed=true]').evaluate(e=>e===document.activeElement),{width,profileCount});
   check(engine,'profile-limit',await page.locator('.profile-tile-add').count()===(profileCount<8?1:0),{width,profileCount});
   if(engine==='chromium'&&profileCount===1&&(width===390||width===1440))await page.locator('#modal').screenshot({path:path.join(output,`chromium-${width}-profile-picker.png`),animations:'disabled'});
   if(profileCount===8){await page.locator('.profile-tile').filter({hasText:'Профіль 2'}).click();await page.waitForFunction(()=>document.querySelector('#account')?.getAttribute('aria-label')==='Профіль: Профіль 2');check(engine,'profile-switch',await page.evaluate(()=>localStorage.getItem('kik-profile:audit-user'))==='audit-profile-2',{width});await page.locator('#account').click();}
   else{await page.locator('.profile-tile-add').click();check(engine,'new-profile-clears-picker-layout',await page.locator('#modal').evaluate(d=>!d.classList.contains('profile-modal')&&!d.hasAttribute('aria-labelledby')),{width});await page.locator('#modal .close').click();await page.locator('#account').click();}
   await page.locator('#modal .close').click();
   // The profile switch syncs and repaints the home behind the picker: the footer button is tapped once the page is still.
   await page.waitForLoadState('networkidle');await page.locator('#connect-tv').scrollIntoViewIfNeeded();await page.waitForTimeout(400);await page.locator('#connect-tv').click();check(engine,'pairing-clears-picker-layout',await page.locator('#modal').evaluate(d=>!d.classList.contains('profile-modal')),{width});await page.locator('#modal .close').click();await page.locator('#account').click();
   await page.locator('.profile-dialog-tools').getByRole('button',{name:'Налаштування',exact:true}).click();await page.locator('.settings-card').first().waitFor();check(engine,'profile-settings-navigation',await page.locator('#modal').evaluate(d=>!d.open),{width});await page.locator('#account').click();
   await page.locator('.profile-dialog-tools').getByRole('button',{name:'Вийти',exact:true}).click();await page.waitForFunction(()=>document.querySelector('#account')?.getAttribute('aria-label')==='Увійти до KIK');check(engine,'profile-logout',await page.locator('#modal').evaluate(d=>!d.open),{width});await context.close();
  }
  // Behavior checks catch failures that cannot be found by bounding boxes alone.
  {
   const context=await browser.newContext({viewport:{width:390,height:844},reducedMotion:'reduce'});const lifecycle=await fixture(context,{slowSeries:3000,filmDelay:400});const page=await trackedPage(context);let hlsDownloads=0;page.on('request',r=>{if(r.url().endsWith('/hls.min.js'))hlsDownloads++;});
   const started=performance.now();await page.goto(origin);await page.locator('.shelf').first().waitFor();const firstShelfMs=Math.round(performance.now()-started);check(engine,'first catalogue usable while another category is slow',!lifecycle.seriesComplete,{firstShelfMs});
   await page.locator('[data-tab=catalogue]').click();await page.locator('.catalogue-categories [data-category=series]').click();await page.locator('.grid .poster').first().waitFor();
   await page.locator('[data-tab=home]').click();await page.locator('.shelf-line .poster').first().focus();await page.evaluate(()=>window.auditFocused=document.activeElement);
   const synced=page.waitForResponse(r=>r.url().endsWith('/api/sync'));await page.evaluate(()=>document.dispatchEvent(new Event('visibilitychange')));await synced;await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));
   check(engine,'background sync preserves focused card',await page.evaluate(()=>window.auditFocused.isConnected&&document.activeElement===window.auditFocused));
   check(engine,'catalogue navigation never downloads HLS',hlsDownloads===0,{hlsDownloads});
   await page.locator('[data-tab=catalogue]').click();await page.locator('.grid .poster').first().click();await page.locator('#modal .close').click();await page.locator('#account').click();await page.waitForTimeout(500);check(engine,'late film response preserves profile dialog',(await page.locator('#modal h2').textContent())==='Хто дивиться?');await page.keyboard.press('Escape');
   check(engine,'Escape closes dialog',await page.locator('#modal').evaluate(d=>!d.open));
   await page.locator('#search-open').click();await page.locator('#header-search-input').fill('Історія');await page.locator('#header-search-input').press('Enter');await page.waitForFunction(()=>document.querySelector('#main h1')?.textContent.startsWith('Результати'));
   check(engine,'search has a restorable URL',new URL(page.url()).hash.startsWith('#search?q='));
   await page.locator('[data-tab=settings]').click();await page.goBack();await page.waitForFunction(()=>document.querySelector('#main h1')?.textContent.startsWith('Результати'));check(engine,'browser Back restores search query',(await page.locator('#main h1').textContent()).includes('Історія'));
   await page.reload();await page.waitForFunction(()=>document.querySelector('#main h1')?.textContent.startsWith('Результати'));check(engine,'reload restores search results',(await page.locator('#main h1').textContent()).includes('Історія'));
   await context.close();
  }
  {
   const context=await browser.newContext({viewport:{width:375,height:667},reducedMotion:'reduce'});await fixture(context,{failCatalogue:true});const page=await trackedPage(context);await page.goto(origin+'/#search?q=offline');await page.getByRole('button',{name:'Спробувати ще',exact:true}).waitFor();check(engine,'search failure offers retry',await page.getByRole('button',{name:'Спробувати ще',exact:true}).isVisible());await context.close();
  }
  await browser.close();
 }
}catch(error){if(activePage&&!activePage.isClosed()){report.failures.push({label:'audit-exception',error:error.message,errors:activeErrors,state:await activePage.evaluate(()=>({url:location.href,heading:document.querySelector('#main h1')?.textContent,focus:document.activeElement?.outerHTML?.slice(0,200),dialogs:[...document.querySelectorAll('dialog[open]')].map(d=>d.id)}))});await activePage.screenshot({path:path.join(output,'audit-failure.png'),animations:'disabled'});}throw error;}finally{await new Promise(r=>server.close(r));await writeFile(path.join(output,'report.json'),JSON.stringify(report,null,2));}
console.log(JSON.stringify({samples:report.samples.length,failures:report.failures.length,details:report.failures.slice(0,12),output},null,2));
if(report.failures.length)process.exitCode=1;
