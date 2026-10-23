/** Adversarial regression tests against disposable localhost D1 fixtures only. */
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {writeFile, mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {hash, passwordHash, randomToken} from './auth.mjs';

const base = process.env.KIK_TEST_URL || 'http://127.0.0.1:8791';
if (!/^http:\/\/(localhost|127\.0\.0\.1):\d+$/.test(base)) throw Error('Security fixtures require a local Worker');
const root = fileURLToPath(new URL('../', import.meta.url));
const run = 'qa_security_' + Date.now().toString(36), password = randomToken(), salt = randomToken();
const adminId = randomUUID(), memberId = randomUUID(), profileId = randomUUID(), adminProfile = randomUUID();
const adminEmail = run + '_admin@example.invalid', memberEmail = run + '_member@example.invalid', invitedEmail = run + '_invite@example.invalid';
const sqlFile = root + 'work/security-test.sql'; let checks = 0, invitedId;
await mkdir(root + 'work', {recursive:true});
async function sql(value) {
  await writeFile(sqlFile, value);
  return JSON.parse(execFileSync('npm', ['exec','--prefix','web','--','wrangler','d1','execute','kik-data','--local','--config','web/wrangler.jsonc','--file',sqlFile,'--json'], {cwd:root,stdio:'pipe'}));
}
async function req(path, data, cookie, headers = {}, method) {
  const response = await fetch(base + path, {method: method || (data === undefined ? 'GET' : 'POST'),
    headers: {...(data === undefined ? {} : {'Content-Type':'application/json'}), ...(cookie ? {Cookie:cookie} : {}), ...headers},
    body: data === undefined ? undefined : JSON.stringify(data)});
  const text = await response.text(); let body; try { body = JSON.parse(text); } catch { body = text; }
  return {status:response.status,body,headers:response.headers,cookie:response.headers.get('set-cookie')?.split(';')[0]};
}
function check(value, label) { assert.ok(value, label); checks++; console.log('PASS ' + label); }
const digest = await passwordHash(password, salt),memberPassword='123456',memberDigest=await passwordHash(memberPassword,salt);
await sql(`INSERT INTO users(id,username,email,salt,password_hash,created_at,role) VALUES
('${adminId}','${adminEmail}','${adminEmail}','${salt}','${digest}',${Date.now()},'admin'),
('${memberId}','${memberEmail}','${memberEmail}','${salt}','${memberDigest}',${Date.now()},'user');
INSERT INTO profiles(id,user_id,name,avatar,created_at) VALUES
('${profileId}','${memberId}','QA security','bear',${Date.now()}),('${adminProfile}','${adminId}','QA admin','bear',${Date.now()});`);
try {
  const admin = await req('/api/auth/login',{email:adminEmail,password});
  const member = await req('/api/auth/login',{email:memberEmail,password:memberPassword});
  check(admin.status === 200 && member.status === 200, 'existing six-character passwords still log in after the new registration policy');
  const owner = member.cookie, privileged = admin.cookie;
  check((await req('/api/admin/overview')).status === 401, 'anonymous admin access rejected');
  check((await req('/api/admin/usage?user_id='+memberId)).status===401,'anonymous account usage access rejected');
  check((await req('/api/admin/usage?user_id='+memberId,undefined,owner)).status===403,'ordinary account cannot read administrative usage');
  check((await req('/api/admin/users?role=admin',undefined,owner)).status === 403, 'forged admin role rejected');
  check((await req('/api/admin/users?q='+encodeURIComponent("' OR 1=1 --"),undefined,privileged)).body.users.length === 0, 'SQL metacharacters are data in admin search');
  check((await req('/api/auth/login',{username:"' OR 1=1 --",password})).status === 401, 'SQL injection cannot authenticate');

  let invite = await req('/api/admin/access',{email:invitedEmail,action:'reissue'},privileged);
  const originalInvite = invite.body.invitation.token;
  check(invite.status === 200 && originalInvite.length === 64, 'admin creates a cryptographic invitation');
  const stored = (await sql(`SELECT invite_hash,invite_expires_at FROM registration_access WHERE email='${invitedEmail}';`))[0].results[0];
  check(stored.invite_hash === await hash(originalInvite) && stored.invite_hash !== originalInvite, 'only the invitation hash is stored');
  check((await req('/api/auth/register',{email:invitedEmail,password})).status === 403, 'knowing an invited email cannot squat the account');
  check((await req('/api/auth/register',{email:invitedEmail,password,invitation:randomToken()})).status === 403, 'wrong invitation rejected');
  check((await req('/api/auth/register',{email:memberEmail,password,invitation:originalInvite})).status === 403, 'invitation is email-bound');
  await sql(`UPDATE registration_access SET invite_expires_at=${Date.now()-1000} WHERE email='${invitedEmail}';`);
  check((await req('/api/auth/register',{email:invitedEmail,password,invitation:originalInvite})).status === 403, 'expired invitation rejected');
  invite = await req('/api/admin/access',{email:invitedEmail,action:'reissue'},privileged);
  check((await req('/api/auth/register',{email:invitedEmail,password,invitation:originalInvite})).status === 403, 'reissuing an invitation revokes the old token');
  check((await req('/api/auth/register',{email:invitedEmail,password:'123456',invitation:invite.body.invitation.token})).status === 400, 'new weak passwords rejected without changing existing passwords');
  const registration = await req('/api/auth/register',{email:invitedEmail,password,invitation:invite.body.invitation.token,role:'admin'});
  invitedId = registration.body.user?.id;
  check(registration.status === 201 && registration.body.user.role === 'user', 'a valid invitation registers only an ordinary account');
  check((await req('/api/auth/register',{email:invitedEmail,password,invitation:invite.body.invitation.token})).status === 403, 'invitation cannot be replayed');
  check(!JSON.stringify(registration.body).includes(digest) && !JSON.stringify(registration.body).includes(salt), 'password hashes and salts stay out of API responses');

  for (const [path,data] of [
    ['/api/sync',{profile_id:adminProfile,records:[]}],
    ['/api/settings',{profile_id:adminProfile,settings:{}}],
    ['/api/profiles/update',{profile_id:adminProfile,name:'attacker'}],
    ['/api/pair/claim',{profile_id:adminProfile,code:'AAAAAAAA'}]
  ]) check((await req(path,data,owner)).status === 404, path+' enforces ownership');
  check((await req('/api/settings?profile_id='+adminProfile,undefined,owner)).status === 404, 'settings reads enforce ownership');
  for (const headers of [{Origin:'https://attacker.example'},{'Sec-Fetch-Site':'cross-site'}]) {
    check((await req('/api/profiles/update',{profile_id:profileId,name:'CSRF'},owner,headers)).status === 403, 'CSRF profile mutation denied');
    check((await req('/api/ad-breaks?link=x',undefined,undefined,headers)).status === 403, 'cross-site GET cannot mutate the ad queue');
  }
  for (const path of ['/api/stream','/api/episodes']) check((await req(path,{})).status === 401, 'anonymous '+path+' denied');
  check((await req('/api/stream',{},'kik_session='+randomToken())).status === 401, 'invented session cannot resolve a stream');
  check((await req('/api/media/bogus.mp4',undefined,undefined,{},'HEAD')).status === 401, 'anonymous media HEAD denied');
  for (const path of ['/api/ad-breaks/queue','/api/ad-breaks/report','/api/ad-breaks/classify'])
    check((await req(path,{},owner)).status === 404, 'ordinary session cannot use scanner '+path);
  check((await req('/api/profiles/update',null,owner)).status === 400, 'null JSON is safely rejected');
  check((await req('/api/profiles/update',[],owner)).status === 400, 'array JSON is safely rejected');
  check((await req('/api/sync',{profile_id:profileId,records:[],padding:'x'.repeat(1024*1024)},owner)).status === 413, 'oversized request rejected');

  const pair = await req('/api/pair/start',{});
  await req('/api/pair/claim',{profile_id:profileId,code:pair.body.user_code},owner);
  await req('/api/auth/logout-others',{},owner);
  check((await req('/api/pair/poll',{device_code:pair.body.device_code})).status === 410, 'logout-others revokes approved but unconsumed TV codes');
  const second = await req('/api/auth/login',{email:memberEmail,password:memberPassword});
  const pending = await req('/api/pair/start',{});
  await req('/api/pair/claim',{profile_id:profileId,code:pending.body.user_code},second.cookie);
  await req('/api/auth/logout',{},second.cookie);
  check((await req('/api/pair/poll',{device_code:pending.body.device_code})).status === 410, 'logout revokes approvals made by that session');
  const once = await req('/api/pair/start',{});
  await req('/api/pair/claim',{profile_id:profileId,code:once.body.user_code},owner);
  const raced = await Promise.all([req('/api/pair/poll',{device_code:once.body.device_code}),req('/api/pair/poll',{device_code:once.body.device_code})]);
  check(raced.filter(r=>r.status===200).length===1 && raced.filter(r=>r.status===410).length===1, 'concurrent polling issues exactly one TV session');
  const tvToken = raced.find(r=>r.status===200).body.token;
  check((await req('/api/sync',{profile_id:adminProfile,records:[]},undefined,{Authorization:'Bearer '+tvToken})).status===404, 'TV bearer sessions cannot access another account');
  await req('/api/auth/logout',{},privileged,{Authorization:'Bearer '+tvToken});
  check((await req('/api/me',undefined,undefined,{Authorization:'Bearer '+tvToken})).body.user===null, 'logout uses the same Bearer precedence as authentication');
  check((await req('/api/me',undefined,privileged)).body.user.id===adminId, 'conflicting cookie does not revoke the wrong session');

  const creates=await Promise.all(Array.from({length:8},(_,i)=>req('/api/profiles',{name:'Concurrent '+i},owner)));
  check(creates.filter(r=>r.status===201).length===7 && (await req('/api/me',undefined,owner)).body.profiles.length===8, 'concurrent requests cannot exceed eight profiles');
  await sql(`WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x+1 FROM n WHERE x<5000)
INSERT INTO records(profile_id,kind,item_key,data,updated_at) SELECT '${profileId}','later','films/drama/'||x||'-qa.html',NULL,1 FROM n;`);
  const overflow={kind:'later',key:'films/drama/5001-qa.html',data:null,updated_at:Date.now()};
  check((await req('/api/sync',{profile_id:profileId,records:[overflow]},owner)).status===400, 'record overflow is rejected before persistent poisoning');
  check((await req('/api/sync',{profile_id:profileId,records:[]},owner)).body.records.length===5000, 'profile remains readable at capacity');
  check((await req('/api/sync',{profile_id:profileId,records:[{...overflow,key:'films/drama/1-qa.html'}]},owner)).status===200, 'updates still work at capacity');

  await sql(`UPDATE sessions SET created_at=${Date.now()-181*86400000},expires_at=${Date.now()+86400000} WHERE user_id='${memberId}';`);
  check((await req('/api/me',undefined,owner)).body.user===null, 'absolute session lifetime cannot be extended indefinitely');
  check((await req('/api/sync',{profile_id:profileId,records:[]},owner)).status===401, 'absolute-expired session cannot use private endpoints');
  const headers=(await req('/api/health')).headers;
  check(headers.get('Strict-Transport-Security')==='max-age=31536000' && headers.get('X-Frame-Options')==='DENY', 'HTTPS and framing policies present');
  check(headers.get('Content-Security-Policy').includes("media-src 'self' blob:"), 'browser media must pass through the authenticated endpoint');
  console.log(JSON.stringify({passed:true,checks,environment:'local'}));
} finally {
  await sql(`DELETE FROM pairs WHERE user_id IN ('${adminId}','${memberId}'${invitedId?",'"+invitedId+"'":''});
DELETE FROM registration_access WHERE email='${invitedEmail}'; DELETE FROM admin_events WHERE actor_id='${adminId}';
DELETE FROM users WHERE id IN ('${adminId}','${memberId}'${invitedId?",'"+invitedId+"'":''});`);
}
