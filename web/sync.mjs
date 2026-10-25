export function canonicalLink(key){return key.startsWith("uaserials/")?"https://uaserials.com/"+key.slice(10):"https://hdrezka.sh/"+key;}
export function titleKey(link){try{const u=new URL(link);if(u.origin==="https://uaserials.com"&&!u.username&&!u.password&&!u.search&&!u.hash&&/^\/\d+-[a-z0-9-]+\.html$/.test(u.pathname))return "uaserials/"+u.pathname.slice(1);if(!["hdrezka-home.tv","hdrezka.sh","hdrezka.website","hdrezka.ag","rezka.ag"].includes(u.hostname)||!/^\/(films|series|cartoons|animation|documentary)\/[a-z0-9/_-]+\.html$/.test(u.pathname))throw Error();return u.pathname.slice(1);}catch{throw new Error("Некоректне посилання на фільм");}}
const short=(s,max=500)=>typeof s==="string"?s.slice(0,max):null;
export function cleanRecord(input,now=Date.now()){
 if(!input||!["progress","later","rating","voice"].includes(input.kind)||typeof input.key!=="string"||input.key.length>1024||!Number.isSafeInteger(input.updated_at)||input.updated_at<1||input.updated_at>now+300000)throw new Error("Некоректний запис синхронізації");
 let data=null;if(input.data!==null){const d=input.data;if(!d||typeof d!=="object"||Array.isArray(d))throw Error("Некоректні дані");const path=titleKey(d.link);data={link:canonicalLink(path),title:short(d.title,300),poster:typeof d.poster==="string"&&/^https:\/\//.test(d.poster)?d.poster.slice(0,2048):null};
  if(input.kind==="progress"){
   for(const key of ["pos","dur","watched"]){const value=d[key]??0;if(!Number.isFinite(value)||value<0||value>1e12)throw Error("Некоректний час");data[key]=Math.floor(value);}
   for(const key of ["season","episode","voice","voiceName"])data[key]=short(d[key],100);data.ts=input.updated_at;data.observed=!!d.observed;
   const expected=path+"|"+(data.season||"0")+":"+(data.episode||"0");if(input.key!==expected)throw Error("Некоректний ключ прогресу");
  }else if(input.kind==="rating"){if(![-2,-1,1].includes(d.value))throw Error("Некоректна оцінка");data.value=d.value;data.genre=short(d.genre,200);data.time=input.updated_at;if(input.key!==path)throw Error("Некоректний ключ оцінки");}
  else if(input.kind==="voice"){if(typeof d.voice!=="string"||!/^\d{1,10}$/.test(d.voice))throw Error("Некоректна озвучка");data.voice=d.voice;data.voiceName=short(d.voiceName,100);
   // The quality and speed picked for this title, if any: the player opens with them next time.
   if(d.quality!=null){if(typeof d.quality!=="string"||!/^[\w .+-]{1,40}$/.test(d.quality))throw Error("Некоректна якість");data.quality=d.quality;}
   if(d.speed!=null){if(![.75,1,1.25,1.5,2].includes(d.speed))throw Error("Некоректна швидкість");data.speed=d.speed;}
   if(input.key!==path)throw Error("Некоректний ключ озвучки");}
  else{data.year=short(d.year,40);data.genre=short(d.genre,200);if(input.key!==path)throw Error("Некоректний ключ списку");}
 }else if(!/^(films|series|cartoons|animation|documentary|uaserials)\//.test(input.key))throw Error("Некоректне видалення");
 return {kind:input.kind,key:input.key,data,updated_at:input.updated_at};
}
export function canonical(value){if(value===null||typeof value!=="object")return JSON.stringify(value);if(Array.isArray(value))return "["+value.map(canonical).join(",")+"]";return "{"+Object.keys(value).sort().map(k=>JSON.stringify(k)+":"+canonical(value[k])).join(",")+"}";}
/** Rows written up to this long before a client's last server_time come again: concurrent syncs and clock drift between Worker machines never hide a change. */
export const SYNC_OVERLAP=120000;
/** Upserts the device's records and answers the profile's records: all of them, or with `since` (server_time of the device's last sync) only those written after it, tombstones included. */
export async function exchange(db,profile,records,since=null,now=Date.now()){
 if(!Array.isArray(records)||records.length>1000)throw Error("Забагато записів");const valid=records.map(r=>cleanRecord(r,now));
 // Parameterized last-write-wins upserts retain tombstones, so an offline device cannot revive a deletion. Only a winning write moves synced_at.
 for(let at=0;at<valid.length;at+=60){await db.batch(valid.slice(at,at+60).map(r=>db.prepare("INSERT INTO records(profile_id,kind,item_key,data,updated_at,synced_at) VALUES(?,?,?,?,?,?) ON CONFLICT(profile_id,kind,item_key) DO UPDATE SET data=excluded.data,updated_at=excluded.updated_at,synced_at=excluded.synced_at WHERE excluded.updated_at>records.updated_at OR (excluded.updated_at=records.updated_at AND COALESCE(excluded.data,'')>COALESCE(records.data,''))").bind(profile,r.kind,r.key,r.data===null?null:canonical(r.data),r.updated_at,now)));}
 const delta=Number.isSafeInteger(since)&&since>0;
 const result=await (delta?db.prepare("SELECT kind,item_key,data,updated_at FROM records WHERE profile_id=? AND synced_at>=? ORDER BY kind,item_key LIMIT 5001").bind(profile,Math.min(since,now)-SYNC_OVERLAP):db.prepare("SELECT kind,item_key,data,updated_at FROM records WHERE profile_id=? ORDER BY kind,item_key LIMIT 5001").bind(profile)).all();if(result.results.length>5000)throw Error("Ліміт записів профілю");
 return result.results.map(r=>({kind:r.kind,key:r.item_key,data:r.data===null?null:JSON.parse(r.data),updated_at:r.updated_at}));
}
