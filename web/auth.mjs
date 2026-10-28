const enc=new TextEncoder();
export const hex=b=>Array.from(new Uint8Array(b),x=>x.toString(16).padStart(2,"0")).join("");
export const randomToken=()=>hex(crypto.getRandomValues(new Uint8Array(32)));
export const hash=async s=>hex(await crypto.subtle.digest("SHA-256",enc.encode(s)));
export async function passwordHash(password,salt){const key=await crypto.subtle.importKey("raw",enc.encode(password),"PBKDF2",false,["deriveBits"]);return hex(await crypto.subtle.deriveBits({name:"PBKDF2",hash:"SHA-256",salt:enc.encode(salt),iterations:100000},key,256));}
export function equal(a,b){if(typeof a!=="string"||typeof b!=="string"||a.length!==b.length)return false;let diff=0;for(let i=0;i<a.length;i++)diff|=a.charCodeAt(i)^b.charCodeAt(i);return diff===0;}
// A sign-in has no fixed end while it is used: every day of use pushes the end 90 days ahead; 90 days without use sign it out.
export const SESSION_MS=90*86400000;
// The account may still sign in: an admin, a legacy account without email, or an email still on the admin's list.
const ALLOWED="(users.role='admin' OR users.email IS NULL OR EXISTS(SELECT 1 FROM registration_access a WHERE a.email=users.email COLLATE NOCASE))";
export function cookie(token,clear=false){return `kik_session=${clear?"":token}; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=${clear?0:SESSION_MS/1000}`;}
export function requestToken(request){return request.headers.get("Authorization")?.match(/^Bearer ([a-f0-9]{64})$/)?.[1]||request.headers.get("Cookie")?.match(/(?:^|;\s*)kik_session=([a-f0-9]{64})(?:;|$)/)?.[1];}
export async function remember(db,token){const now=Date.now();await db.prepare("UPDATE sessions SET expires_at=? WHERE token_hash=? AND expires_at>?").bind(now+SESSION_MS,await hash(token),now).run();}
export async function identity(request,db){const token=requestToken(request);if(!token)return null;
 // Access is checked on every request, so removing an email from the list ends its sign-ins at once.
 const user=await db.prepare("SELECT users.id,users.username,users.email,users.role,sessions.expires_at FROM sessions JOIN users ON users.id=sessions.user_id WHERE token_hash=? AND expires_at>? AND users.password_hash<>'' AND "+ALLOWED).bind(await hash(token),Date.now()).first();
 if(!user)return null;if(user.expires_at-Date.now()<SESSION_MS-86400000)await remember(db,token);return {id:user.id,username:user.username,email:user.email,role:user.role};
}
/**
 * A new sign-in, created only while the account may still sign in. With `verified`, also only while the password is
 * the one just checked: a revocation or password reset that lands between the check and here leaves no session.
 */
export async function session(db,user,verified=null,device=null){const token=randomToken(),now=Date.now();
 const r=await db.prepare("INSERT INTO sessions(token_hash,user_id,expires_at,created_at,device) SELECT ?1,users.id,?2,?3,?6 FROM users WHERE users.id=?4 AND users.password_hash<>'' AND (?5 IS NULL OR users.password_hash=?5) AND "+ALLOWED).bind(await hash(token),now+SESSION_MS,now,user,verified,device).run();
 if(!r.meta.changes)throw Object.assign(Error("Доступ до KIK для цієї пошти відкликано. Зверніться до адміністратора."),{status:403});return token;}
/** The name a TV gives for its sign-in, as the website lists it; a TV that sends none is just «Android TV». */
export function deviceName(value){const name=typeof value==='string'?value.replace(/[\u0000-\u001f]/g,' ').trim().slice(0,60):'';return name||'Android TV';}
export function validUsername(value){return typeof value==="string"&&/^[a-z0-9._-]{3,40}$/.test(value);}
export function normalizeEmail(value){return typeof value==='string'?value.trim().toLowerCase():'';}
export function validEmail(value){return typeof value==='string'&&value.length<=254&&value.length>=6&&!/[\s\r\n]/.test(value)&&/^[a-z0-9.!#$%&'*+\/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$/.test(value)&&value.split('@')[0].length<=64;}
