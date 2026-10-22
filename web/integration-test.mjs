// Runs against the dedicated development DB or deployment. Creates only disposable QA accounts.
import assert from 'node:assert/strict';import {randomBytes} from 'node:crypto';import {writeFile} from 'node:fs/promises';
const base=process.env.KIK_TEST_URL||'http://localhost:8787',run='qa_'+Date.now().toString(36),password=randomBytes(24).toString('hex'),email=run+'@example.invalid';let checks=0;
async function req(path,data,cookie,headers={}){const r=await fetch(base+path,{method:data===undefined?'GET':'POST',headers:{...(data===undefined?{}:{'Content-Type':'application/json'}),...(cookie?{Cookie:cookie}:{}),...headers},body:data===undefined?undefined:JSON.stringify(data)});return {status:r.status,body:await r.json(),cookie:r.headers.get('set-cookie')?.split(';')[0],cookieOptions:r.headers.get('set-cookie')?.split(';').slice(1).join(';')};}
function check(ok,message){assert.ok(ok,message);checks++;}
const users=[];try{
 let a=await req('/api/auth/register',{email,password,name:'QA Sync'});check(a.status===201,'registration');check(a.cookieOptions.includes('Max-Age=7776000')&&a.cookieOptions.includes('HttpOnly')&&a.cookieOptions.includes('Secure'),'browser remembers secure session for 90 days');users.push(email);const cookie=a.cookie,profile=a.body.profiles[0].id;check((await req('/api/auth/register',{email:email.toUpperCase(),password})).status===409,'email normalization');check((await req('/api/auth/register',{email:'invalid-mail',password})).status===400,'invalid email rejected');check((await req('/api/auth/login',{email:email.toUpperCase(),password})).status===200,'email login ignores case');
 check((await req('/api/me',undefined,cookie)).body.user.email===email,'session identity');
 check((await req('/api/auth/login',{email,password:'incorrect'})).status===401,'wrong password');
 check((await req('/api/sync',{profile_id:profile,records:[]},cookie,{Origin:'https://evil.test'})).status===403,'CSRF origin');
 check((await req('/api/sync',{profile_id:profile,records:[]})).status===401,'anonymous history denied');
 const b=await req('/api/auth/register',{email:run+'_b@example.invalid',password,name:'QA Other'});users.push(run+'_b@example.invalid');check(b.status===201,'second tenant');
 check((await req('/api/sync',{profile_id:profile,records:[]},b.cookie)).status===404,'cross-account history denied');
 const link='https://hdrezka.sh/series/drama/646-vo-vse-tyazhkie-2008.html',key=new URL(link).pathname.slice(1)+'|1:2',now=Date.now(),r={kind:'progress',key,data:{link,title:'QA title',season:'1',episode:'2',pos:123000,dur:2400000,watched:60000},updated_at:now};
 let sync=await req('/api/sync',{profile_id:profile,records:[r]},cookie);check(sync.body.records[0].data.pos===123000,'progress uploaded');
 sync=await req('/api/sync',{profile_id:profile,records:[{...r,data:{...r.data,pos:1000},updated_at:now-1}]},cookie);check(sync.body.records[0].data.pos===123000,'older device cannot rewind new progress');
 const next={...r,key:key.replace('|1:2','|1:3'),data:{...r.data,episode:'3',pos:30000},updated_at:now+1};sync=await req('/api/sync',{profile_id:profile,records:[next]},cookie);check(sync.body.records.length===2,'episodes stored independently');
 sync=await req('/api/sync',{profile_id:profile,records:[{...r,data:null,updated_at:now+2}]},cookie);check(sync.body.records.find(x=>x.key===key).data===null,'history deletion tombstone');
 sync=await req('/api/sync',{profile_id:profile,records:[r]},cookie);check(sync.body.records.find(x=>x.key===key).data===null,'offline history cannot revive deletion');
 check((await req('/api/sync',{profile_id:profile,records:[{...r,data:{...r.data,link:'https://evil.test/a'}}]},cookie)).status===400,'invalid source rejected');
 const pair=await req('/api/pair/start',{});check(pair.body.user_code.length===8&&pair.body.device_code.length===64,'device authorization');
 check((await req('/api/pair/poll',{device_code:pair.body.device_code})).body.pending===true,'pair waits for website approval');
 check((await req('/api/pair/claim',{code:pair.body.user_code,profile_id:profile},cookie)).body.ok,'claim binds authenticated profile');
 const poll=await req('/api/pair/poll',{device_code:pair.body.device_code});check(poll.body.token.length===64&&poll.body.profile.id===profile,'TV receives bound session');
 check((await req('/api/pair/poll',{device_code:pair.body.device_code})).status===410,'pair consumed once');
 const device=await req('/api/sync',{profile_id:profile,records:[]},undefined,{Authorization:'Bearer '+poll.body.token});check(device.body.records.length===2,'TV token sees same history');
 check((await req('/api/sync',{profile_id:b.body.profiles[0].id,records:[]},undefined,{Authorization:'Bearer '+poll.body.token})).status===404,'TV token isolated from other account');
 await req('/api/auth/logout',{},cookie);check((await req('/api/me',undefined,cookie)).body.user===null,'logout revokes browser session');
 console.log(JSON.stringify({passed:true,checks,accounts:users,base}));
}finally{if(process.env.KIK_CLEANUP_FILE)await writeFile(process.env.KIK_CLEANUP_FILE,users.map(u=>`DELETE FROM users WHERE username='${u}';`).join('\n'));}
