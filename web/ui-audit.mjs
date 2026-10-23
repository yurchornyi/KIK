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
 if(pathname==='/fixture.svg'||pathname.startsWith('/avatars/')){res.writeHead(200,{'content-type':'image/svg+xml'});res.end(image);return;}
 // HLS itself is covered by playback regression tests; UI fixtures exercise native video controls.
 if(pathname==='/hls.min.js'){res.writeHead(200,{'content-type':'application/javascript'});res.end('');return;}
 try{const file=path.resolve(root,'public','.'+(pathname==='/'?'/index.html':pathname));if(!file.startsWith(path.join(root,'public')+path.sep))throw Error();const data=await readFile(file);res.writeHead(200,{'content-type':({'.html':'text/html','.js':'application/javascript','.css':'text/css','.svg':'image/svg+xml','.png':'image/png'})[path.extname(file)]||'application/octet-stream'});res.end(data);}catch{res.writeHead(404);res.end();}
});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
const origin=`http://127.0.0.1:${server.address().port}`;
const report={mode:'local deterministic API fixtures; viewport emulation, not physical Apple devices',samples:[],failures:[]};
async function fixture(context,{guest=false,delay=0,failCatalogue=false,slowSeries=0,filmDelay=0}={}){
 const lifecycle={seriesComplete:false};await context.route('**/api/**',async route=>{
  const url=new URL(route.request().url()),p=url.pathname;
  if(delay&&p==='/api/catalogue')await new Promise(r=>setTimeout(r,delay));
  if(slowSeries&&p==='/api/catalogue'&&url.searchParams.get('category')==='series')await new Promise(r=>setTimeout(r,slowSeries));
  if(filmDelay&&p==='/api/film')await new Promise(r=>setTimeout(r,filmDelay));
  let body={};
  if(p==='/api/me')body={user:guest?null:user,profiles:guest?[]:[profile],server_time:Date.now()};
  else if(p==='/api/settings')body={settings:{quality:'1080p',audio:'uk',speed:1,autoplay:true}};
  else if(p==='/api/sync')body={records,profile,server_time:Date.now()};
  else if(p==='/api/catalogue'){if(failCatalogue){await route.fulfill({status:503,json:{error:'Offline fixture'}});return;}body={items:url.searchParams.get('category')==='series'?items.filter((_,i)=>i%2):url.searchParams.get('category')==='films'?items.filter((_,i)=>!(i%2)):items};}
  else if(p==='/api/film'){const item=items.find(i=>i.link===url.searchParams.get('link'))||items[0];body={...item,description:'Це довгий опис історії. '.repeat(18),genres:['Драма','Пригоди','Наукова фантастика'],duration:'120 хв',rating:'8.2',series:item.link.includes('/series/'),voices:[{id:'1',name:'Українська — дуже довга назва студії озвучення',lang:'uk'}],episodes:[{season:'1',episode:'1',title:'Серія 1: дуже довга назва першої серії'}],related:[]};}
  else if(p==='/api/episodes')body={episodes:[{season:'1',episode:'1',title:'Серія 1: дуже довга назва першої серії'}]};
  else if(p==='/api/stream')body={qualities:[{label:'1080p',url:origin+'/missing.mp4'}]};
  else if(p==='/api/artwork/batch')body={configured:true,results:Object.fromEntries(items.map(i=>[i.link.split('.sh/')[1],{poster:origin+'/fixture.svg',posterLarge:origin+'/fixture.svg',backdrop:origin+'/fixture.svg'}]))};
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
   // Horizontal shelves and navigation intentionally scroll within their own bounds.
   if(e.closest('.shelf-line,.header nav'))return false;
   const r=e.getBoundingClientRect();return r.left< -1||r.right>innerWidth+1;
  }).map(e=>({tag:e.tagName,text:e.textContent.slice(0,60),x:Math.round(e.getBoundingClientRect().x),width:Math.round(e.getBoundingClientRect().width)}));
  const header=document.querySelector('.header'),actions=document.querySelector('.header-actions'),nav=header?.querySelector('nav');
  let headerOverlap=false;if(!dialog&&nav&&actions){const n=nav.getBoundingClientRect(),a=actions.getBoundingClientRect(),end=getComputedStyle(nav).overflowX==='visible'?nav.lastElementChild.getBoundingClientRect().right:n.right;headerOverlap=n.top<a.bottom&&a.top<n.bottom&&end>a.left+1;}
  const tools=document.querySelector('#player-modal[open] .player-tools'),fields=tools?[...tools.children].map(e=>e.getBoundingClientRect()):[],lastTop=fields.length?Math.max(...fields.map(r=>Math.round(r.top))):0,last=fields.filter(r=>Math.abs(r.top-lastTop)<2),emptyPlayerCells=last.length>0&&Math.max(...last.map(r=>r.right))-Math.min(...last.map(r=>r.left))<tools.clientWidth-12;const dialogMetrics=dialog?{client:dialog.clientWidth,scroll:dialog.scrollWidth,children:[...dialog.querySelectorAll('*')].filter(visible).filter(e=>e.scrollWidth>e.clientWidth+1).map(e=>({tag:e.tagName,cls:e.className,client:e.clientWidth,scroll:e.scrollWidth}))}:null;return {emptyPlayerCells,dialogMetrics,width:innerWidth,height:innerHeight,documentOverflow:document.documentElement.scrollWidth>innerWidth+1,overflow,headerOverlap,dialogOverflow:!!dialog&&dialog.scrollWidth>dialog.clientWidth+1,mainHeading:document.querySelector('#main h1')?.textContent,scrollY,focused:document.activeElement?.textContent?.slice(0,40)};
 });
 const sample={label,...extra,...result};report.samples.push(sample);if(result.dialogOverflow)await page.screenshot({path:path.join(output,`${extra.engine}-${result.width}-${label}-overflow.png`),animations:'disabled'});
 if(result.documentOverflow||result.overflow.length||result.headerOverlap||result.dialogOverflow||result.emptyPlayerCells)report.failures.push(sample);
 return sample;
}
const sizes=process.env.KIK_AUDIT_SIZES?process.env.KIK_AUDIT_SIZES.split(',').map(s=>s.split('x').map(Number)):[[320,568],[375,667],[390,844],[430,932],[667,375],[768,1024],[820,1180],[1024,768],[1180,820],[1280,720],[1440,900],[1920,1080],[2560,1440],[3840,2160]];
function check(engine,label,passed,details={}){const sample={engine,label,passed,...details};report.samples.push(sample);if(!passed)report.failures.push(sample);}
try{
 for(const [engine,type] of Object.entries({chromium,webkit})){
  const browser=await type.launch({headless:true});
  for(const [width,height] of sizes){
   const context=await browser.newContext({viewport:{width,height},deviceScaleFactor:width<1200?2:1,hasTouch:width<1200,isMobile:width<768,reducedMotion:'reduce'});await fixture(context);
   const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
   const started=performance.now();await page.goto(origin);await page.locator('.shelf').first().waitFor();await page.locator('[data-tab=admin]').waitFor();
   await inspect(page,'home',{engine,readyMs:Math.round(performance.now()-started)});
   if((width===390||width===820||width===1440)&&engine==='chromium')await page.screenshot({path:path.join(output,`${engine}-${width}-home.png`),animations:'disabled'});
   for(const tab of ['films','series','later','history','settings','admin']){
    const t=performance.now();await page.locator(`[data-tab=${tab}]`).click();await page.locator(tab==='admin'?'.admin-stats':tab==='settings'?'.settings-card':'.grid-page').first().waitFor();await inspect(page,tab,{engine,switchMs:Math.round(performance.now()-t)});
   }
   for(const [label,open] of [
    ['profiles',()=>page.locator('#account').click()],
    ['search',()=>page.locator('#search-open').click()],
    ['pair',()=>page.locator('#connect-tv').click()],
    ['countries',async()=>{await page.locator('[data-tab=settings]').click();await page.getByRole('button',{name:'Приховані країни',exact:true}).click();}],
    ['avatars',()=>page.getByRole('button',{name:'Змінити аватарку',exact:true}).click()],
    ['taste',()=>page.getByRole('button',{name:'Налаштувати смак',exact:true}).click()],
   ]){await open();await page.locator('#modal[open]').waitFor();await inspect(page,label,{engine});await page.locator('#modal .close').click();}
   await page.locator('[data-tab=films]').click();await page.locator('.grid .poster').first().click();await page.locator('.detail-actions').waitFor();await inspect(page,'details',{engine});await page.getByRole('button',{name:/^▶ /}).first().click();await page.locator('#player-modal video').waitFor();await inspect(page,'player',{engine});await page.locator('#player-modal .close').click();
   await page.locator('#search-open').click();await page.locator('#modal input').fill('Історія');await page.locator('#modal form').evaluate(f=>f.requestSubmit());await page.waitForFunction(()=>document.querySelector('#main h1')?.textContent.startsWith('Результати'));await inspect(page,'search-results',{engine});
   await page.locator('[data-tab=series]').click();await page.locator('.grid .poster').first().click();await page.locator('.detail-actions').waitFor();await page.getByRole('button',{name:/^▶ /}).first().click();await page.locator('#player-modal video').waitFor();await inspect(page,'series-player',{engine});await page.locator('#player-modal .close').click();await page.goto(origin+'/sources.html');await inspect(page,'sources',{engine});
   if(errors.length)report.failures.push({engine,width,height,label:'runtime',errors});
   await context.close();
  }
  for(const state of [{guest:true},{failCatalogue:true},{delay:900}]){
   const context=await browser.newContext({viewport:{width:375,height:667}});await fixture(context,state);const page=await context.newPage();await page.goto(origin);
   if(state.guest){await page.locator('#account').click();await inspect(page,'login',{engine});await page.getByRole('button',{name:'Вперше тут? Створити обліковий запис',exact:true}).click();await inspect(page,'register',{engine});}
   else if(state.failCatalogue){await page.getByRole('button',{name:'Спробувати ще',exact:true}).waitFor();await inspect(page,'offline',{engine});}
   else{await inspect(page,'loading',{engine});await page.locator('.shelf').first().waitFor();}
   await context.close();
  }
  // Behavior checks catch failures that cannot be found by bounding boxes alone.
  {
   const context=await browser.newContext({viewport:{width:390,height:844},reducedMotion:'reduce'});const lifecycle=await fixture(context,{slowSeries:3000,filmDelay:400});const page=await context.newPage();let hlsDownloads=0;page.on('request',r=>{if(r.url().endsWith('/hls.min.js'))hlsDownloads++;});
   const started=performance.now();await page.goto(origin);await page.locator('.shelf').first().waitFor();const firstShelfMs=Math.round(performance.now()-started);check(engine,'first catalogue usable while another category is slow',!lifecycle.seriesComplete,{firstShelfMs});
   await page.locator('[data-tab=series]').click();await page.locator('.grid .poster').first().waitFor();
   await page.locator('[data-tab=home]').click();await page.locator('.shelf-line .poster').first().focus();await page.evaluate(()=>window.auditFocused=document.activeElement);
   const synced=page.waitForResponse(r=>r.url().endsWith('/api/sync'));await page.evaluate(()=>document.dispatchEvent(new Event('visibilitychange')));await synced;await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));
   check(engine,'background sync preserves focused card',await page.evaluate(()=>window.auditFocused.isConnected&&document.activeElement===window.auditFocused));
   check(engine,'catalogue navigation never downloads HLS',hlsDownloads===0,{hlsDownloads});
   await page.locator('[data-tab=films]').click();await page.locator('.grid .poster').first().click();await page.locator('#modal .close').click();await page.locator('#account').click();await page.waitForTimeout(500);check(engine,'late film response preserves profile dialog',(await page.locator('#modal h2').textContent())==='Хто дивиться?');await page.keyboard.press('Escape');
   check(engine,'Escape closes dialog',await page.locator('#modal').evaluate(d=>!d.open));
   await page.locator('#search-open').click();await page.locator('#modal input').fill('Історія');await page.locator('#modal form').evaluate(f=>f.requestSubmit());await page.waitForFunction(()=>document.querySelector('#main h1')?.textContent.startsWith('Результати'));
   check(engine,'search has a restorable URL',new URL(page.url()).hash.startsWith('#search?q='));
   await page.locator('[data-tab=settings]').click();await page.goBack();await page.waitForFunction(()=>document.querySelector('#main h1')?.textContent.startsWith('Результати'));check(engine,'browser Back restores search query',(await page.locator('#main h1').textContent()).includes('Історія'));
   await page.reload();await page.waitForFunction(()=>document.querySelector('#main h1')?.textContent.startsWith('Результати'));check(engine,'reload restores search results',(await page.locator('#main h1').textContent()).includes('Історія'));
   await context.close();
  }
  {
   const context=await browser.newContext({viewport:{width:375,height:667},reducedMotion:'reduce'});await fixture(context,{failCatalogue:true});const page=await context.newPage();await page.goto(origin+'/#search?q=offline');await page.getByRole('button',{name:'Спробувати ще',exact:true}).waitFor();check(engine,'search failure offers retry',await page.getByRole('button',{name:'Спробувати ще',exact:true}).isVisible());await context.close();
  }
  await browser.close();
 }
}finally{await new Promise(r=>server.close(r));await writeFile(path.join(output,'report.json'),JSON.stringify(report,null,2));}
console.log(JSON.stringify({samples:report.samples.length,failures:report.failures.length,details:report.failures.slice(0,12),output},null,2));
if(report.failures.length)process.exitCode=1;
