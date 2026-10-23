/** «Як вам?» after a title; the same rules as PlaybackPolicy.creditsAt/askRating on TV. Times in seconds, watched in ms. */
export const LIKED=6,DISLIKED=-6;
/** Where the closing credits are taken to start: the last 5% of the file, 45 s to 6 min. */
export function creditsAt(duration){return duration>0?duration-Math.max(45,Math.min(360,duration/20)):Infinity;}
/** A film watched for real (20 min or 40% in this sitting, not a seek to the end or a 60-second preview); a series at its last episode or after three. */
export function askRating({time,duration,watched,series=false,last=false,finished=0}){
 if(!(duration>=180)||!(time>=creditsAt(duration))||!(watched>=Math.min(1200000,duration*400)))return false;
 return !series||last||finished>=3;
}
/** What a tap did, in the same words as RatingStore.said on TV. */
export function said(value){return value>0?'Подобається · покажемо більше схожого':value<-1?'Не цікавить · більше не покажемо':value<0?'Не подобається · сховаємо це й схоже':'Оцінку скасовано';}
/** Titles this browser already asked about, per account profile: never asked twice, whatever the answer. */
const KEY='kik-rate-asked:';
function list(store,owner){try{const v=JSON.parse(store?.getItem(KEY+owner)||'[]');return Array.isArray(v)?v:[];}catch{return [];}}
export function asked(owner,key,store=globalThis.localStorage){return list(store,owner).includes(key);}
export function markAsked(owner,key,store=globalThis.localStorage){try{store?.setItem(KEY+owner,JSON.stringify([...list(store,owner).filter(k=>k!==key),key].slice(-500)));}catch{}}
