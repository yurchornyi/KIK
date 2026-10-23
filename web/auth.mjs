const enc=new TextEncoder();
export const hex=b=>Array.from(new Uint8Array(b),x=>x.toString(16).padStart(2,"0")).join("");
export const randomToken=()=>hex(crypto.getRandomValues(new Uint8Array(32)));
export const hash=async s=>hex(await crypto.subtle.digest("SHA-256",enc.encode(s)));
export async function passwordHash(password,salt){const key=await crypto.subtle.importKey("raw",enc.encode(password),"PBKDF2",false,["deriveBits"]);return hex(await crypto.subtle.deriveBits({name:"PBKDF2",hash:"SHA-256",salt:enc.encode(salt),iterations:100000},key,256));}
export function equal(a,b){if(typeof a!=="string"||typeof b!=="string"||a.length!==b.length)return false;let diff=0;for(let i=0;i<a.length;i++)diff|=a.charCodeAt(i)^b.charCodeAt(i);return diff===0;}
// A sign-in lasts as long as it is used: every day of use pushes the end 400 days ahead (the longest a browser keeps a cookie).
export const SESSION_MS=400*86400000;
export function cookie(token,clear=false){return `kik_session=${clear?"":token}; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=${clear?0:SESSION_MS/1000}`;}
export function requestToken(request){return request.headers.get("Authorization")?.match(/^Bearer ([a-f0-9]{64})$/)?.[1]||request.headers.get("Cookie")?.match(/(?:^|;\s*)kik_session=([a-f0-9]{64})(?:;|$)/)?.[1];}
export async function remember(db,token){const now=Date.now();await db.prepare("UPDATE sessions SET expires_at=? WHERE token_hash=? AND expires_at>?").bind(now+SESSION_MS,await hash(token),now).run();}
export async function identity(request,db){const token=requestToken(request);if(!token)return null;
 const user=await db.prepare("SELECT users.id,users.username,users.email,users.role,sessions.expires_at FROM sessions JOIN users ON users.id=sessions.user_id WHERE token_hash=? AND expires_at>?").bind(await hash(token),Date.now()).first();
 if(!user)return null;if(user.expires_at-Date.now()<SESSION_MS-86400000)await remember(db,token);return {id:user.id,username:user.username,email:user.email,role:user.role};
}
export async function session(db,user){const token=randomToken(),now=Date.now();await db.prepare("INSERT INTO sessions(token_hash,user_id,expires_at,created_at) VALUES(?,?,?,?)").bind(await hash(token),user,now+SESSION_MS,now).run();return token;}
export function validUsername(value){return typeof value==="string"&&/^[a-z0-9._-]{3,40}$/.test(value);}
export function normalizeEmail(value){return typeof value==='string'?value.trim().toLowerCase():'';}
export function validEmail(value){return typeof value==='string'&&value.length<=254&&value.length>=6&&!/[\s\r\n]/.test(value)&&/^[a-z0-9.!#$%&'*+\/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$/.test(value)&&value.split('@')[0].length<=64;}
