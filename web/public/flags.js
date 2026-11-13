/**
 * The owner's switches for everyone (admin panel, card «Для всіх»): holidays, the intro before a video and the TV's even
 * loudness. All on until the owner turns one off. The server keeps each change as an admin event (management.mjs) and
 * hands out `{"holidays":true,"intro":true,"loudness":true}` at /api/flags; the TV reads the same answer.
 */
export const FLAG_KEYS=['holidays','intro','loudness'];
export const FLAG_DEFAULTS=Object.freeze({holidays:true,intro:true,loudness:true});
export const FLAG_LABELS={holidays:'Свята',intro:'Заставка перед відео',loudness:'Однакова гучність фільмів'};
export const FLAG_HINTS={holidays:'Святковий логотип, заставка й прикраси, полиця фільмів до свята. На ТБ і сайті.',intro:'Коротка заставка KIK перед кожним фільмом. На ТБ і сайті.',loudness:'ТБ вирівнює гучність різних фільмів.'};
const EVENT=/^([a-z]+):(on|off)$/;
/** Any answer as the three switches: what is missing or not a boolean stays on. */
export function cleanFlags(raw){const out={...FLAG_DEFAULTS};if(raw&&typeof raw==='object')for(const key of FLAG_KEYS)if(typeof raw[key]==='boolean')out[key]=raw[key];return out;}
/** Admin event targets (`holidays:off`), newest first: the first one per switch is its state. */
export function flagsFrom(targets){const out={...FLAG_DEFAULTS},seen=new Set();for(const target of targets||[]){const m=EVENT.exec(String(target??''));if(!m||seen.has(m[1])||!FLAG_KEYS.includes(m[1]))continue;seen.add(m[1]);out[m[1]]=m[2]==='on';}return out;}
/** One change in the admin log, readable: «Свята: вимкнено для всіх». */
export function flagEvent(target){const m=EVENT.exec(String(target??''));return m&&FLAG_LABELS[m[1]]?FLAG_LABELS[m[1]]+': '+(m[2]==='on'?'увімкнено':'вимкнено')+' для всіх':'Перемикач для всіх';}

let known=null,asking=null;
/** The switches as last heard on this page; all on before the answer and whenever there is none. */
export const currentFlags=()=>known||FLAG_DEFAULTS;
/** Asked once per page and never waited for by the page itself: no answer in 8 s, an error or no connection leaves everything on. */
export function loadFlags(fetcher=globalThis.fetch){
 return asking??=(async()=>{try{const r=await fetcher('/api/flags',{credentials:'same-origin',signal:AbortSignal.timeout?.(8000)});known=cleanFlags(r.ok?await r.json():null);}catch{known=cleanFlags(null);}return known;})();
}
/** The admin just changed a switch: this page follows the server's answer at once. */
export function setFlags(value){known=cleanFlags(value);return known;}
