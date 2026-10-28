/** Real D1/Worker tests use local fixtures only; no production users or credentials. */
import assert from 'node:assert/strict';import {randomUUID} from 'node:crypto';import {writeFile,mkdir} from 'node:fs/promises';import {execFileSync} from 'node:child_process';import {fileURLToPath} from 'node:url';import {passwordHash,randomToken} from './auth.mjs';
const base=process.env.KIK_TEST_URL||'http://localhost:8791';if(!/^http:\/\/(localhost|127\.0\.0\.1):\d+$/.test(base))throw Error('Admin fixtures must run against local Worker');
const root=fileURLToPath(new URL('../',import.meta.url)),run='qa_admin_'+Date.now().toString(36),password=randomToken(),adminEmail=run+'@example.invalid',guestEmail=run+'_member@example.invalid',deniedEmail=run+'_denied@example.invalid',adminId=randomUUID(),salt=randomToken();let checks=0;
const sqlFile=root+'work/admin-test.sql';await mkdir(root+'work',{recursive:true});
async function sql(value){await writeFile(sqlFile,value);return JSON.parse(execFileSync('npm',['exec','--prefix','web','--','wrangler','d1','execute','kik-data','--local','--config','web/wrangler.jsonc','--file',sqlFile,'--json'],{cwd:root,stdio:'pipe'}).toString());}
async function req(path,data,cookie,headers={}){const r=await fetch(base+path,{method:data===undefined?'GET':'POST',headers:{...(data===undefined?{}:{'Content-Type':'application/json'}),...(cookie?{Cookie:cookie}:{}),...headers},body:data===undefined?undefined:JSON.stringify(data)});return {status:r.status,body:await r.json(),cookie:r.headers.get('set-cookie')?.split(';')[0]};}
function check(value,name){assert.ok(value,name);checks++;console.log('PASS '+name);}
await sql(`INSERT INTO users(id,username,email,salt,password_hash,created_at,role) VALUES('${adminId}','${adminEmail}','${adminEmail}','${salt}','${await passwordHash(password,salt)}',${Date.now()},'admin'); INSERT INTO profiles(id,user_id,name,avatar,created_at) VALUES('${randomUUID()}','${adminId}','QA Admin','bear',${Date.now()});`);
let adminCookie,memberCookie,memberId;try{
 check((await req('/api/auth/register',{email:deniedEmail,password})).status===403,'uninvited registration denied');
 const login=await req('/api/auth/login',{email:adminEmail,password});adminCookie=login.cookie;check(login.status===200&&login.body.user.role==='admin','admin can sign in');check((await req('/api/me',undefined,adminCookie)).body.user.role==='admin','session retains server role');
 check((await req('/api/admin/users')).status===401,'anonymous admin denied');
 const invitation=await req('/api/admin/access',{email:guestEmail.toUpperCase(),action:'allow'},adminCookie);check(invitation.status===200,'allow email normalized');
 check((await req('/api/admin/access',{email:deniedEmail,action:'allow'},adminCookie,{Origin:'https://evil.test'})).status===403,'admin CSRF denied');
 const member=await req('/api/auth/register',{email:guestEmail,password,role:'admin'});memberCookie=member.cookie;memberId=member.body.user.id;check(member.status===201&&member.body.user.role==='user','invited registration cannot grant its own role');
 const p=member.body.profiles[0].id,ownerProfile=login.body.profiles[0].id;
 check((await req('/api/admin/users',undefined,memberCookie)).status===403,'ordinary account cannot list users');check((await req('/api/admin/access',{email:deniedEmail,action:'allow'},memberCookie)).status===403,'ordinary account cannot invite');check((await req('/api/admin/users/delete',{id:adminId,confirm:adminEmail},memberCookie)).status===403,'ordinary account cannot delete');
 check((await req('/api/admin/access',undefined,adminCookie)).body.emails.some(a=>a.email===guestEmail&&a.registered===1),'registered invite is shown');
 check((await req('/api/settings?profile_id='+p)).status===401,'anonymous settings denied');check((await req('/api/settings?profile_id='+p,undefined,adminCookie)).status===404,'settings isolated by account');check((await req('/api/settings',{profile_id:ownerProfile,settings:{}},memberCookie)).status===404,'settings cannot overwrite another account');
 check((await req('/api/settings?profile_id='+p,undefined,memberCookie)).body.settings.quality==='1080p','default settings');
 const prefs={quality:'720p',speed:1.5,audio:'en',autoplay:false};check((await req('/api/settings',{profile_id:p,settings:prefs},memberCookie)).status===200,'save valid settings');
 assert.deepEqual((await req('/api/settings?profile_id='+p,undefined,memberCookie)).body.settings,prefs);checks++;
 check((await req('/api/settings',{profile_id:p,settings:{role:'admin'}},memberCookie)).status===400,'arbitrary settings rejected');
 check((await req('/api/profiles/update',{profile_id:p,name:'Renamed QA'},memberCookie)).body.name==='Renamed QA','profile rename');check((await req('/api/profiles/update',{profile_id:ownerProfile,name:'Bad'},memberCookie)).status===404,'profile rename isolated');
 check((await req('/api/avatars')).body.avatars.length===24,'avatar catalogue has 24 choices');
 check((await req('/api/profiles/update',{profile_id:p,avatar:'dragon'},memberCookie)).body.avatar==='dragon','avatar can be changed');
 check((await req('/api/me',undefined,memberCookie)).body.profiles[0].avatar==='dragon','avatar persists after profile reload');
 check((await req('/api/profiles/update',{profile_id:p,avatar:'../bad'},memberCookie)).status===400,'invalid avatar rejected');
 check((await req('/api/profiles/update',{profile_id:ownerProfile,avatar:'cat'},memberCookie)).status===404,'avatar changes are account isolated');
 check((await req('/api/admin/profiles?user_id='+memberId,undefined,memberCookie)).status===403,'members cannot list profiles through admin API');
 check((await req('/api/admin/profiles?user_id='+memberId,undefined,adminCookie)).body.profiles[0].id===p,'admin can inspect account profiles');
 check((await req('/api/admin/profiles/recommendations',{profile_id:p,enabled:true},memberCookie)).status===403,'members cannot activate enhanced mode');
 check((await req('/api/profiles/update',{profile_id:p,enhanced_recs:1},memberCookie)).body.enhanced_recs===0,'profile edit cannot self-enable enhanced mode');
 check((await req('/api/admin/profiles/recommendations',{profile_id:p,enabled:'yes'},adminCookie)).status===400,'enhanced mode needs a boolean');
 check((await req('/api/admin/profiles/recommendations',{profile_id:'missing',enabled:true},adminCookie)).status===404,'missing profile toggle rejected');
 check((await req('/api/admin/profiles/recommendations',{profile_id:p,enabled:true},adminCookie)).body.enabled===true,'admin activates one profile');
 const me=await req('/api/me',undefined,memberCookie);check(me.body.profiles.find(x=>x.id===p).enhanced_recs===1&&login.body.profiles[0].enhanced_recs===0,'enhanced mode stays profile scoped');
 const candidates=[1,2,3,4].map(n=>({link:'https://hdrezka.sh/films/drama/'+n+'-film-2020.html',title:'Film '+n,info:'2020, США, Драма'}));
 check((await req('/api/recommendations',{profile_id:ownerProfile,items:candidates},memberCookie)).status===404,'recommendations cannot read another profile');
 check((await req('/api/recommendations',{profile_id:p,items:candidates})).status===401,'anonymous personalized recommendations denied');
 const personal=await req('/api/recommendations',{profile_id:p,items:candidates},memberCookie);check(personal.status===200&&personal.body.mode==='standard','empty taste safely uses regular recommendations');
 check((await req('/api/admin/profiles/recommendations',{profile_id:p,enabled:false},adminCookie)).body.enabled===false,'admin deactivates enhanced mode');
 check((await req('/api/admin/profiles/ad-skip',{profile_id:p,enabled:true},memberCookie)).status===403,'members cannot turn on ad skipping');
 check((await req('/api/admin/profiles/ad-skip',{profile_id:p,enabled:1},adminCookie)).status===400,'ad skipping needs a boolean');
 check((await req('/api/admin/profiles/ad-skip',{profile_id:p,enabled:true},adminCookie)).body.enabled===true,'admin turns on ad skipping');
 check((await req('/api/sync',{profile_id:p,source_schema:2,records:[]},memberCookie)).body.profile.ad_skip===1&&(await req('/api/me',undefined,memberCookie)).body.profiles.find(x=>x.id===p).ad_skip===1,'ad skipping reaches the profile on web and TV');
 check((await req('/api/admin/profiles/ad-skip',{profile_id:p,enabled:false},adminCookie)).body.enabled===false,'admin turns off ad skipping');
 const tv=await req('/api/auth/login',{email:guestEmail,password,device:true,device_name:'QA TV'}),signins=(await req('/api/devices',undefined,memberCookie)).body.devices,tvRow=signins.find(d=>d.tv&&d.name==='QA TV');check(tvRow&&signins.some(d=>d.current),'the website lists the TV by name next to this browser');
 check((await req('/api/devices/revoke',{id:tvRow.id},memberCookie)).status===200&&(await req('/api/me',undefined,undefined,{Authorization:'Bearer '+tv.body.token})).body.user===null,'the owner signs one TV out from the website');
 check((await req('/api/me',undefined,memberCookie)).body.user.id===memberId,'signing a TV out keeps this browser');
 const second=await req('/api/auth/login',{email:guestEmail,password});check((await req('/api/auth/logout-others',{},memberCookie)).status===200,'end other sessions');check((await req('/api/me',undefined,second.cookie)).body.user===null,'other session revoked');check((await req('/api/me',undefined,memberCookie)).body.user.id===memberId,'current session preserved');
 check((await req('/api/admin/users/delete',{id:adminId,confirm:adminEmail},adminCookie)).status===403,'admin cannot delete itself');
 check((await req('/api/admin/users/delete',{id:memberId,confirm:'wrong@example.invalid'},adminCookie)).status===400,'deletion needs exact email confirmation');
 check((await req('/api/sync',{profile_id:p,records:[{kind:'later',key:'films/fiction/981-matrica-1999.html',data:{link:'https://hdrezka.sh/films/fiction/981-matrica-1999.html',title:'QA History'},updated_at:Date.now()}]},memberCookie)).status===200,'deletion fixture includes saved records');
 const pair=await req('/api/pair/start',{});await req('/api/pair/claim',{code:pair.body.user_code,profile_id:p},memberCookie);
 check((await req('/api/admin/users/delete',{id:memberId,confirm:guestEmail},adminCookie)).status===200,'delete disposable member');
 const cascade=(await sql(`SELECT (SELECT COUNT(*) FROM users WHERE id='${memberId}') AS users,(SELECT COUNT(*) FROM profiles WHERE id='${p}') AS profiles,(SELECT COUNT(*) FROM profile_settings WHERE profile_id='${p}') AS settings,(SELECT COUNT(*) FROM records WHERE profile_id='${p}') AS records,(SELECT COUNT(*) FROM sessions WHERE user_id='${memberId}') AS sessions,(SELECT COUNT(*) FROM pairs WHERE user_id='${memberId}') AS pairs;`))[0].results[0];check(Object.values(cascade).every(n=>n===0),'deletion cascades through every private table');
 check((await req('/api/me',undefined,memberCookie)).body.user===null,'deletion revokes member session');check((await req('/api/auth/login',{email:guestEmail,password})).status===401,'deleted member cannot sign in');check((await req('/api/pair/poll',{device_code:pair.body.device_code})).status===410,'deletion revokes pending TV pairing');
 check(!(await req('/api/admin/access',undefined,adminCookie)).body.emails.some(a=>a.email===guestEmail),'deletion removes registration permission');check((await req('/api/auth/register',{email:guestEmail,password})).status===403,'deleted account cannot register again without permission');
 await req('/api/admin/access',{email:deniedEmail,action:'allow'},adminCookie);check((await req('/api/admin/access',{email:deniedEmail,action:'revoke'},adminCookie)).status===200,'revoke unused invite');check((await req('/api/auth/register',{email:deniedEmail,password})).status===403,'revoked invitation denied');
 check((await req('/api/admin/overview',undefined,adminCookie)).body.events.some(e=>e.action==='delete'&&e.target===guestEmail),'management action logged');
 console.log(JSON.stringify({passed:true,checks,environment:'local'}));
}finally{await sql(`DELETE FROM pairs WHERE user_id IN (SELECT id FROM users WHERE id='${adminId}' OR email='${guestEmail}'); DELETE FROM registration_access WHERE email IN ('${guestEmail}','${deniedEmail}'); DELETE FROM admin_events WHERE actor_id='${adminId}'; DELETE FROM users WHERE id='${adminId}' OR email='${guestEmail}';`);}
