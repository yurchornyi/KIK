import {hash,randomToken,passwordHash,equal,cookie,identity,session,validUsername,normalizeEmail,validEmail,remember,requestToken} from "./auth.mjs";
import {exchange} from "./sync.mjs";
import {catalogue,film,episodes,stream} from "./catalogue.mjs";
const json=(body,status=200,headers={})=>new Response(JSON.stringify(body),{status,headers:{"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store",...headers}});
const fail=(message,status=400)=>{throw Object.assign(new Error(message),{status});};
async function body(request,max=1024*1024){if(Number(request.headers.get("Content-Length"))>max)fail("Завеликий запит",413);const reader=request.body?.getReader();if(!reader)fail("Порожній запит");let data=[],size=0;try{for(;;){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>max)fail("Завеликий запит",413);data.push(value);}}finally{await reader.cancel();}const bytes=new Uint8Array(size);let at=0;for(const b of data){bytes.set(b,at);at+=b.length;}try{return JSON.parse(new TextDecoder().decode(bytes));}catch{fail("Некоректний JSON");}}
function origin(request){const o=request.headers.get("Origin");if(o&&o!==new URL(request.url).origin)fail("Запит з іншого сайту відхилено",403);if(request.headers.get("Sec-Fetch-Site")==="cross-site")fail("Запит з іншого сайту відхилено",403);}
async function owned(db,user,id){if(typeof id!=="string")fail("Виберіть профіль");const p=await db.prepare("SELECT id,name,avatar FROM profiles WHERE id=? AND user_id=?").bind(id,user.id).first();if(!p)fail("Профіль недоступний",404);return p;}
async function profiles(db,user){return (await db.prepare("SELECT id,name,avatar FROM profiles WHERE user_id=? ORDER BY created_at,id").bind(user.id).all()).results;}
async function api(request,env,ctx){
 const url=new URL(request.url),path=url.pathname,db=env.DB,ip=request.headers.get("CF-Connecting-IP")||"local";
 if(path==="/api/health")return json({ok:true,service:"kik-web"});
 if(!["GET","POST"].includes(request.method))return json({error:"Метод недоступний"},405);
 if(request.method==="POST")origin(request);
 const limit=path.startsWith("/api/auth/")||path==="/api/pair/start"?env.AUTH_LIMIT:env.API_LIMIT;
 if(limit&&!(await limit.limit({key:ip})).success)fail("Забагато запитів. Зачекайте хвилину.",429);
 if(path==="/api/auth/register"&&request.method==="POST"){
  const b=await body(request,8192),email=normalizeEmail(b.email);if(!validEmail(email)||typeof b.password!=="string"||b.password.length<10||b.password.length>256)fail("Введіть коректну email-адресу. Пароль: від 10 символів.");
  const exists=await db.prepare("SELECT id FROM users WHERE email=?").bind(email).first();if(exists)fail("Ця пошта вже зареєстрована",409);
  const id=crypto.randomUUID(),salt=randomToken(),password_hash=await passwordHash(b.password,salt),now=Date.now(),name=typeof b.name==="string"&&b.name.trim()?b.name.trim().slice(0,40):email.split("@")[0].slice(0,40);
  try{await db.batch([db.prepare("INSERT INTO users(id,username,email,salt,password_hash,created_at) VALUES(?,?,?,?,?,?)").bind(id,email,email,salt,password_hash,now),db.prepare("INSERT INTO profiles(id,user_id,name,avatar,created_at) VALUES(?,?,?,?,?)").bind(crypto.randomUUID(),id,name,"bear",now)]);}catch{fail("Ця пошта вже зареєстрована",409);}
  const token=await session(db,id);return json({user:{id,email},profiles:await profiles(db,{id})},201,{"Set-Cookie":cookie(token)});
 }
 if(path==="/api/auth/login"&&request.method==="POST"){
  const b=await body(request,8192),email=normalizeEmail(b.email),legacy=typeof b.username==="string"?b.username.trim().toLowerCase():"";if((!validEmail(email)&&!validUsername(legacy))||typeof b.password!=="string"||b.password.length>256)fail("Неправильна пошта або пароль",401);
  const user=await db.prepare("SELECT * FROM users WHERE email=? OR (email IS NULL AND username=?)").bind(email,legacy).first();const digest=await passwordHash(b.password,user?.salt||"invalid-account-timing-padding");if(!user||!equal(digest,user.password_hash))fail("Неправильна пошта або пароль",401);
  const token=await session(db,user.id);return json({user:{id:user.id,email:user.email,username:user.email?undefined:user.username},profiles:await profiles(db,user)},200,{"Set-Cookie":cookie(token)});
 }
 const user=await identity(request,db);
 if(path==="/api/auth/logout"&&request.method==="POST"){
  const token=request.headers.get("Cookie")?.match(/(?:^|;\s*)kik_session=([a-f0-9]{64})/)?.[1]||request.headers.get("Authorization")?.slice(7);if(token)await db.prepare("DELETE FROM sessions WHERE token_hash=?").bind(await hash(token)).run();return json({ok:true},200,{"Set-Cookie":cookie("",true)});
 }
 if(path==="/api/me"){const token=requestToken(request);if(user&&token)await remember(db,token);return json({user,profiles:user?await profiles(db,user):[],server_time:Date.now()},200,user&&token?{"Set-Cookie":cookie(token)}:{});}
 if(path==="/api/pair/start"&&request.method==="POST"){
  const device=randomToken(),code=Array.from(crypto.getRandomValues(new Uint8Array(8)),n=>"ABCDEFGHJKLMNPQRSTUVWXYZ23456789"[n%32]).join("");
  await db.batch([db.prepare("DELETE FROM pairs WHERE expires_at<?").bind(Date.now()),db.prepare("INSERT INTO pairs(device_hash,user_code,expires_at) VALUES(?,?,?)").bind(await hash(device),code,Date.now()+10*60_000)]);
  return json({device_code:device,user_code:code,verification_uri:url.origin+"/?pair="+code,expires_in:600});
 }
 if(path==="/api/pair/poll"&&request.method==="POST"){
  const b=await body(request,8192);if(typeof b.device_code!=="string"||!/^[a-f0-9]{64}$/.test(b.device_code))fail("Некоректний код");const device=await hash(b.device_code),pair=await db.prepare("SELECT * FROM pairs WHERE device_hash=? AND expires_at>?").bind(device,Date.now()).first();if(!pair||pair.consumed)fail("Код прострочений",410);if(!pair.user_id)return json({pending:true});
  const changed=await db.prepare("UPDATE pairs SET consumed=1 WHERE device_hash=? AND consumed=0 RETURNING user_id,profile_id").bind(device).first();if(!changed)fail("Код уже використано",410);
  const token=await session(db,changed.user_id);return json({token,profile:await owned(db,{id:changed.user_id},changed.profile_id)});
 }
 if(path==="/api/catalogue"){
  const key=new Request(url.origin+path+url.search),cached=await caches.default.match(key);if(cached)return cached;const data=await catalogue(Object.fromEntries(url.searchParams));const r=json({items:data});r.headers.set("Cache-Control","public, max-age=180");ctx.waitUntil(caches.default.put(key,r.clone()));return r;
 }
 if(path==="/api/film"){const f=await film(url.searchParams.get("link"));delete f.favs;delete f.origin;return json(f);}
 if(!user)fail("Увійдіть у свій обліковий запис",401);
 if(path==="/api/pair/claim"&&request.method==="POST"){
  const b=await body(request,8192);await owned(db,user,b.profile_id);const code=String(b.code||"").replace(/[\s-]/g,"").toUpperCase();if(!/^[A-Z2-9]{8}$/.test(code))fail("Введіть 8 символів із TV");
  const result=await db.prepare("UPDATE pairs SET user_id=?,profile_id=? WHERE user_code=? AND expires_at>? AND user_id IS NULL AND consumed=0").bind(user.id,b.profile_id,code,Date.now()).run();if(!result.meta.changes)fail("Код недійсний або вже використаний",410);return json({ok:true});
 }
 if(path==="/api/profiles"&&request.method==="POST"){
  const b=await body(request,8192),name=typeof b.name==="string"?b.name.trim().slice(0,40):"";if(!name)fail("Введіть ім’я профілю");if((await profiles(db,user)).length>=8)fail("Доступно до 8 профілів");const id=crypto.randomUUID();await db.prepare("INSERT INTO profiles(id,user_id,name,avatar,created_at) VALUES(?,?,?,?,?)").bind(id,user.id,name,"bear",Date.now()).run();return json({id,name,avatar:"bear"},201);
 }
 if(path==="/api/sync"&&request.method==="POST"){
  const b=await body(request);const p=await owned(db,user,b.profile_id);return json({profile:p,records:await exchange(db,p.id,b.records||[]),server_time:Date.now()});
 }
 if(path==="/api/episodes"||path==="/api/stream"){
  if(request.method!=="POST")fail("Використайте POST",405);const b=await body(request,8192),f=await film(b.link);return json(path.endsWith("episodes")?{episodes:await episodes(f,String(b.voice))}:await stream(f,String(b.voice),String(b.season||""),String(b.episode||"")));
 }
 fail("Не знайдено",404);
}
function secure(response){const r=new Response(response.body,response);r.headers.set("X-Content-Type-Options","nosniff");r.headers.set("Referrer-Policy","no-referrer");r.headers.set("X-Frame-Options","DENY");r.headers.set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' https: data:; media-src 'self' https: blob:; connect-src 'self' https:; worker-src 'self' blob:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'");return r;}
export default {async fetch(request,env,ctx){try{const path=new URL(request.url).pathname;return secure(path.startsWith("/api/")?await api(request,env,ctx):await env.ASSETS.fetch(request));}catch(e){const status=e.status||400;console.warn("KIK request failed",new URL(request.url).pathname,status,e.status?"handled":e.message?.slice(0,180));return secure(json({error:e.status?e.message:"Не вдалося виконати запит. Спробуйте ще раз."},status));}}};
