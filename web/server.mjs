import {resolvePlayback} from './playback-sources.mjs';
import {localizedCards,localizedFilm,localizedRecords} from './localization.mjs';
import {recommendations} from './recommendations.mjs';
import {validAvatar,AVATARS} from './public/avatars.js';
import {publicArtworkPreview,previewArtwork} from './artwork-preview.mjs';
import {admin,registrationAllowed,cleanSettings,storedSettings,DEFAULT_SETTINGS,logoutOtherSessions} from './management.mjs';
import {hash,randomToken,passwordHash,equal,cookie,identity,session,validUsername,normalizeEmail,validEmail,remember,requestToken} from "./auth.mjs";
import {exchange,titleKey} from "./sync.mjs";
import {uaSearch,uaFilmPlayable} from "./uaserials.mjs";
import {aliases,metadata} from "./metadata.mjs";
import {catalogue,film,episodes,stream} from "./catalogue.mjs";
import {createArtworkService} from "./tmdb.mjs";
import {synopsis} from "./synopsis.mjs";
const artworkService=createArtworkService();
const json=(body,status=200,headers={})=>new Response(JSON.stringify(body),{status,headers:{"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store",...headers}});
const fail=(message,status=400)=>{throw Object.assign(new Error(message),{status});};
async function body(request,max=1024*1024){if(Number(request.headers.get("Content-Length"))>max)fail("Завеликий запит",413);const reader=request.body?.getReader();if(!reader)fail("Порожній запит");let data=[],size=0;try{for(;;){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>max)fail("Завеликий запит",413);data.push(value);}}finally{await reader.cancel();}const bytes=new Uint8Array(size);let at=0;for(const b of data){bytes.set(b,at);at+=b.length;}try{return JSON.parse(new TextDecoder().decode(bytes));}catch{fail("Некоректний JSON");}}
function origin(request){const o=request.headers.get("Origin");if(o&&o!==new URL(request.url).origin)fail("Запит з іншого сайту відхилено",403);if(request.headers.get("Sec-Fetch-Site")==="cross-site")fail("Запит з іншого сайту відхилено",403);}
async function owned(db,user,id){if(typeof id!=="string")fail("Виберіть профіль");const p=await db.prepare("SELECT id,name,avatar,enhanced_recs FROM profiles WHERE id=? AND user_id=?").bind(id,user.id).first();if(!p)fail("Профіль недоступний",404);return p;}
async function profiles(db,user){return (await db.prepare("SELECT id,name,avatar,enhanced_recs FROM profiles WHERE user_id=? ORDER BY created_at,id").bind(user.id).all()).results;}
async function api(request,env,ctx){
 const url=new URL(request.url),path=url.pathname,db=env.DB,ip=request.headers.get("CF-Connecting-IP")||"local";
 if(path==="/api/avatars")return json({avatars:AVATARS});
 if(path==="/api/health")return json({ok:true,service:"kik-web"});
 if(!["GET","POST"].includes(request.method))return json({error:"Метод недоступний"},405);
 if(request.method==="POST")origin(request);
 const limit=path.startsWith("/api/auth/")||path==="/api/pair/start"?env.AUTH_LIMIT:env.API_LIMIT;
 if(limit&&!(await limit.limit({key:ip})).success)fail("Забагато запитів. Зачекайте хвилину.",429);
 if(publicArtworkPreview(request,env))return previewArtwork(request);
 if(path==="/api/artwork"&&request.method==="GET")return json(await artworkService.lookup(Object.fromEntries(url.searchParams),{token:env.TMDB_READ_TOKEN,cache:caches.default,origin:url.origin}));
 // One request per batch of visible cards keeps the page inside the per-IP API limit.
 if(path==="/api/artwork/batch"&&request.method==="POST"){
  const b=await body(request,32768);if(!Array.isArray(b.items)||b.items.length>24)fail("Некоректний запит обкладинок");
  const options={token:env.TMDB_READ_TOKEN,cache:caches.default,origin:url.origin},results={},queue=[...b.items];let configured=true;
  await Promise.all(Array.from({length:4},async()=>{for(let item;(item=queue.shift());)try{const key=titleKey(item.link),r=await artworkService.lookup(item,options);configured&&=r.configured;results[key]=r.artwork;}catch{}}));
  return json({configured,results});
 }
 if(path==="/api/metadata"){const m=await metadata(url.searchParams.get("imdb"),env,url.searchParams.get("kind")||''),t=await localizedFilm(db,{title:m.title,description:m.overview});return json({...m,title:t.title,overview:t.description});}
 // Three-line synopsis for the TV title page, generated once per title from the catalogue page itself.
 if(path==="/api/synopsis"&&request.method==="GET")return json(await synopsis(db,env,url.searchParams.get("link")||""));
 if(path==="/api/search-aliases")return json({aliases:await aliases(url.searchParams.get("query"))});
 if(path==="/api/sources")return json({uaserials:true,tvmaze:true,wikidata:true,tmdb:!!env.TMDB_READ_TOKEN});
 if(path==="/api/auth/register"&&request.method==="POST"){
  const b=await body(request,8192),email=normalizeEmail(b.email);if(!validEmail(email)||typeof b.password!=="string"||b.password.length<6||b.password.length>256)fail("Введіть коректну email-адресу. Пароль: від 6 символів.");
  if(!await registrationAllowed(db,email))fail("Реєстрація лише за дозволом адміністратора. Попросіть додати вашу пошту.",403);
  const exists=await db.prepare("SELECT id FROM users WHERE email=?").bind(email).first();if(exists)fail("Ця пошта вже зареєстрована",409);
  const avatar=b.avatar===undefined?"bear":b.avatar;if(!validAvatar(avatar))fail("Виберіть аватарку зі списку");
  const id=crypto.randomUUID(),salt=randomToken(),password_hash=await passwordHash(b.password,salt),now=Date.now(),name=typeof b.name==="string"&&b.name.trim()?b.name.trim().slice(0,40):email.split("@")[0].slice(0,40);
  try{await db.batch([db.prepare("INSERT INTO users(id,username,email,salt,password_hash,created_at) SELECT ?,?,?,?,?,? WHERE EXISTS(SELECT 1 FROM registration_access WHERE email=?)").bind(id,email,email,salt,password_hash,now,email),db.prepare("INSERT INTO profiles(id,user_id,name,avatar,created_at) VALUES(?,?,?,?,?)").bind(crypto.randomUUID(),id,name,avatar,now)]);}catch{fail("Ця пошта вже зареєстрована",409);}
  const token=await session(db,id);return json({user:{id,email,role:"user"},profiles:await profiles(db,{id})},201,{"Set-Cookie":cookie(token)});
 }
 if(path==="/api/auth/login"&&request.method==="POST"){
  const b=await body(request,8192),email=normalizeEmail(b.email),legacy=typeof b.username==="string"?b.username.trim().toLowerCase():"";if((!validEmail(email)&&!validUsername(legacy))||typeof b.password!=="string"||b.password.length>256)fail("Неправильна пошта або пароль",401);
  const user=await db.prepare("SELECT * FROM users WHERE email=? OR (email IS NULL AND username=?)").bind(email,legacy).first();const digest=await passwordHash(b.password,user?.salt||"invalid-account-timing-padding");if(!user||!equal(digest,user.password_hash))fail("Неправильна пошта або пароль",401);
  const token=await session(db,user.id);return json({user:{id:user.id,email:user.email,username:user.email?undefined:user.username,role:user.role},profiles:await profiles(db,user)},200,{"Set-Cookie":cookie(token)});
 }
 const user=await identity(request,db);
 if(path.startsWith("/api/admin/"))return json(await admin(request,db,user,body,{rebuild:id=>env.AI&&ctx.waitUntil((async()=>{const p=await db.prepare("SELECT id,enhanced_recs FROM profiles WHERE id=?").bind(id).first();if(!p?.enhanced_recs)return;const pages=await Promise.allSettled([catalogue({category:"films",filter:"popular"}),catalogue({category:"series",filter:"popular"})]);await recommendations(db,p,pages.flatMap(r=>r.value||[]).slice(0,100),env);})().catch(()=>console.warn("KIK recommendation rebuild delayed")))}));
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
  const key=new Request(url.origin+path+url.search+(url.search?"&":"?")+"__presentation=uk4"),cached=await caches.default.match(key);if(cached)return cached;const params=Object.fromEntries(url.searchParams);if(params.source&&!["rezka","uaserials"].includes(params.source))fail("Джерело недоступне");const data=await (params.source==="uaserials"?uaSearch(params):catalogue(params));const r=json({items:await localizedCards(db,data)});r.headers.set("Cache-Control","public, max-age=180");ctx.waitUntil(caches.default.put(key,r.clone()));return r;
 }
 if(path==="/api/film"){const link=url.searchParams.get("link"),f=new URL(link).hostname==="uaserials.com"?await uaFilmPlayable(link,film):await film(link);delete f.favs;delete f.origin;return json(await localizedFilm(db,f));}
 if(!user)fail("Увійдіть у свій обліковий запис",401);
 if(path==="/api/recommendations"&&request.method==="POST"){const b=await body(request,65536),p=await owned(db,user,b.profile_id);return json(await recommendations(db,p,b.items,env));}
 if(path==="/api/auth/logout-others"&&request.method==="POST")return json(await logoutOtherSessions(db,user,requestToken(request)));
 if(path==="/api/settings"){
  const b=request.method==="POST"?await body(request,8192):null,p=await owned(db,user,b?.profile_id||url.searchParams.get("profile_id"));
  if(b){const settings=cleanSettings(b.settings);await db.prepare("INSERT INTO profile_settings(profile_id,data,updated_at) VALUES(?,?,?) ON CONFLICT(profile_id) DO UPDATE SET data=excluded.data,updated_at=excluded.updated_at").bind(p.id,JSON.stringify(settings),Date.now()).run();return json({settings});}
  const row=await db.prepare("SELECT data FROM profile_settings WHERE profile_id=?").bind(p.id).first();return json({settings:row?storedSettings(row.data):DEFAULT_SETTINGS});
 }
 if(path==="/api/profiles/update"&&request.method==="POST"){
  const b=await body(request,8192),p=await owned(db,user,b.profile_id),name=b.name===undefined?p.name:typeof b.name==="string"?b.name.trim().slice(0,40):"",avatar=b.avatar===undefined?p.avatar:b.avatar;if(!name)fail("Введіть ім’я профілю");if(!validAvatar(avatar))fail("Виберіть аватарку зі списку");await db.prepare("UPDATE profiles SET name=?,avatar=? WHERE id=?").bind(name,avatar,p.id).run();return json({...p,name,avatar});
 }
 if(path==="/api/pair/claim"&&request.method==="POST"){
  const b=await body(request,8192);await owned(db,user,b.profile_id);const code=String(b.code||"").replace(/[\s-]/g,"").toUpperCase();if(!/^[A-Z2-9]{8}$/.test(code))fail("Введіть 8 символів із TV");
  const result=await db.prepare("UPDATE pairs SET user_id=?,profile_id=? WHERE user_code=? AND expires_at>? AND user_id IS NULL AND consumed=0").bind(user.id,b.profile_id,code,Date.now()).run();if(!result.meta.changes)fail("Код недійсний або вже використаний",410);return json({ok:true});
 }
 if(path==="/api/profiles"&&request.method==="POST"){
  const b=await body(request,8192),name=typeof b.name==="string"?b.name.trim().slice(0,40):"";if(!name)fail("Введіть ім’я профілю");if((await profiles(db,user)).length>=8)fail("Доступно до 8 профілів");const avatar=b.avatar===undefined?"bear":b.avatar;if(!validAvatar(avatar))fail("Виберіть аватарку зі списку");const id=crypto.randomUUID();await db.prepare("INSERT INTO profiles(id,user_id,name,avatar,created_at) VALUES(?,?,?,?,?)").bind(id,user.id,name,avatar,Date.now()).run();return json({id,name,avatar,enhanced_recs:0},201);
 }
 if(path==="/api/sync"&&request.method==="POST"){
  const b=await body(request);const p=await owned(db,user,b.profile_id);return json({profile:p,records:await localizedRecords(db,(await exchange(db,p.id,b.records||[])).filter(r=>b.source_schema>=2||!r.key.startsWith("uaserials/"))),server_time:Date.now()});
 }
 if(path==="/api/episodes"||path==="/api/stream"){
  if(request.method!=="POST")fail("Використайте POST",405);const b=await body(request,8192);return json(await resolvePlayback(b.link,String(b.voice),String(b.season||""),String(b.episode||""),{attempt:b.recovery_attempt??0,method:path.endsWith("episodes")?"episodes":"stream"}));
 }
 fail("Не знайдено",404);
}
function secure(response){const r=new Response(response.body,response);r.headers.set("Permissions-Policy","camera=(), microphone=(), geolocation=(), payment=()");r.headers.set("Cross-Origin-Opener-Policy","same-origin");r.headers.set("X-Content-Type-Options","nosniff");r.headers.set("Referrer-Policy","no-referrer");r.headers.set("X-Frame-Options","DENY");r.headers.set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' https: data:; media-src 'self' https: blob:; connect-src 'self' https:; worker-src 'self' blob:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; frame-src 'none'; form-action 'self'");return r;}
export default {async fetch(request,env,ctx){try{const path=new URL(request.url).pathname;return secure(path.startsWith("/api/")?await api(request,env,ctx):await env.ASSETS.fetch(request));}catch(e){const status=e.status||400;console.warn("KIK request failed",new URL(request.url).pathname,status,e.status?"handled":e.message?.slice(0,180));return secure(json({error:e.status?e.message:"Не вдалося виконати запит. Спробуйте ще раз."},status));}}};
