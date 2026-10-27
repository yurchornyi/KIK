import {test} from 'node:test';import assert from 'node:assert/strict';import {readFileSync,readdirSync} from 'node:fs';import {DatabaseSync} from 'node:sqlite';
import worker from './server.mjs';
// A minimal D1 over node:sqlite with the real migrations, so the TV sign-in runs against the actual schema.
function d1(){const db=new DatabaseSync(':memory:');db.exec('PRAGMA foreign_keys=ON');for(const f of readdirSync(new URL('./migrations/',import.meta.url)).sort())db.exec(readFileSync(new URL('./migrations/'+f,import.meta.url),'utf8'));
 // node:sqlite before Node 22.23 cannot bind numbered parameters (?1, ?2) that D1 takes: they become plain ? in their order.
 const plain=(sql,args)=>{const order=[];return [sql.replace(/\?(\d+)/g,(_,n)=>{order.push(args[n-1]);return '?';}),order.length?order:args];};
 const stmt=(sql,args=[])=>{const [q,a]=plain(sql,args);return {bind:(...v)=>stmt(sql,v),first:async()=>db.prepare(q).get(...a)??null,all:async()=>({results:db.prepare(q).all(...a)}),run:async()=>{const r=db.prepare(q).run(...a);return {meta:{changes:Number(r.changes)}};}};};
 return {raw:db,prepare:sql=>stmt(sql),batch:async list=>{db.exec('BEGIN');try{const out=[];for(const s of list)out.push(await s.run());db.exec('COMMIT');return out;}catch(e){db.exec('ROLLBACK');throw e;}}};}
const env=DB=>({DB,ASSETS:{fetch:async()=>new Response('')}});
// The admin panel adds an email to the allow-list; that alone lets the person sign up.
const allow=(db,email)=>db.raw.prepare('INSERT INTO registration_access(email,created_at) VALUES(?,?)').run(email,Date.now());
async function call(e,path,data,token){const r=await worker.fetch(new Request('https://kik.test'+path,{method:data?'POST':'GET',headers:{'Content-Type':'application/json',...token?{Authorization:'Bearer '+token}:{}},body:data&&JSON.stringify(data)}),e,{waitUntil(){}});return {status:r.status,body:await r.json()};}

test('TV sign-in: unknown email is refused, allowed email creates a password, then a profile',async()=>{
 const db=d1(),e=env(db);allow(db,'tv@example.invalid');
 const denied=await call(e,'/api/auth/check',{email:'stranger@example.invalid'});assert.equal(denied.status,403);assert.match(denied.body.error,/не має доступу/);
 assert.equal((await call(e,'/api/auth/check',{email:'not-an-email'})).status,400);
 assert.deepEqual((await call(e,'/api/auth/check',{email:' TV@Example.invalid '})).body,{email:'tv@example.invalid',state:'create'});
 const created=await call(e,'/api/auth/register',{email:'tv@example.invalid',password:'secret-1',profile:false,device:true});assert.equal(created.status,201);assert.deepEqual(created.body.profiles,[]);assert.match(created.body.token,/^[a-f0-9]{64}$/);
 assert.equal((await call(e,'/api/auth/check',{email:'tv@example.invalid'})).body.state,'password');
 const profile=await call(e,'/api/profiles',{name:'Юра',avatar:'fox'},created.body.token);assert.equal(profile.status,201);assert.equal(profile.body.avatar,'fox');
 const me=await call(e,'/api/me',undefined,created.body.token);assert.equal(me.body.user.email,'tv@example.invalid');assert.deepEqual(me.body.profiles.map(p=>p.name),['Юра']);
});
test('TV sign-in: removing the email revokes the account; wrong password is refused; strangers cannot sign up',async()=>{
 const db=d1(),e=env(db);allow(db,'old@example.invalid');
 const web=await call(e,'/api/auth/register',{email:'old@example.invalid',password:'secret-1',name:'Мама'});assert.equal(web.status,201);assert.equal(web.body.token,undefined);assert.equal(web.body.profiles.length,1);
 assert.equal((await call(e,'/api/auth/check',{email:'old@example.invalid'})).body.state,'password');
 assert.equal((await call(e,'/api/auth/login',{email:'old@example.invalid',password:'wrong-1',device:true})).status,401);
 const tv=await call(e,'/api/auth/login',{email:'old@example.invalid',password:'secret-1',device:true});assert.equal(tv.status,200);assert.match(tv.body.token,/^[a-f0-9]{64}$/);assert.deepEqual(tv.body.profiles.map(p=>p.name),['Мама']);
 db.raw.prepare('DELETE FROM registration_access').run();
 const revoked=await call(e,'/api/auth/check',{email:'old@example.invalid'});assert.equal(revoked.status,403);assert.match(revoked.body.error,/відкликано/);
 assert.equal((await call(e,'/api/auth/login',{email:'old@example.invalid',password:'secret-1',device:true})).status,403);
 assert.equal((await call(e,'/api/auth/register',{email:'new@example.invalid',password:'secret-1',profile:false})).status,403);
});
test('TV sign-in: after an admin reset the person sets a new password and keeps profiles; 6 characters is the minimum',async()=>{
 const db=d1(),e=env(db);allow(db,'reset@example.invalid');
 assert.equal((await call(e,'/api/auth/register',{email:'reset@example.invalid',password:'12345',profile:false})).status,400);
 assert.equal((await call(e,'/api/auth/register',{email:'reset@example.invalid',password:'123456',name:'Тато'})).status,201);
 db.raw.prepare("UPDATE users SET password_hash='' WHERE email='reset@example.invalid'").run();
 assert.equal((await call(e,'/api/auth/check',{email:'reset@example.invalid'})).body.state,'create');
 const old=await call(e,'/api/auth/login',{email:'reset@example.invalid',password:'123456'});assert.equal(old.status,403);assert.match(old.body.error,/скинув/);
 const again=await call(e,'/api/auth/register',{email:'reset@example.invalid',password:'new-pass',device:true});assert.equal(again.status,200);assert.deepEqual(again.body.profiles.map(p=>p.name),['Тато']);
 assert.equal((await call(e,'/api/auth/check',{email:'reset@example.invalid'})).body.state,'password');
 assert.equal((await call(e,'/api/auth/register',{email:'reset@example.invalid',password:'other-1'})).status,409);
});
