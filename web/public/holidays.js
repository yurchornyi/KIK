/**
 * The holidays KIK dresses up for, counted on the device, the same days as the TV (localization/java/kik/i18n/Holidays.java).
 * On the day itself: the holiday logo, the holiday intro before a video and a few quiet touches on the home page. A week or so
 * before and on the day: a shelf of films for it. Easter comes twice when the Western and the Orthodox dates differ.
 * The owner's «Свята» switch (admin panel «Для всіх», flags.js; on by default) turns all of it off for everyone.
 */
export const HOLIDAYS=['nick','xmas','ny','val','easter','ind','hw'];
/** Days before a holiday its film shelf comes; St Nicholas, Christmas and New Year share December between them. */
export const SHELF_DAYS=7;

/** Western (Gregorian) Easter Sunday: [month, day]. */
export function westernEaster(y){const a=y%19,b=Math.floor(y/100),c=y%100,d=Math.floor(b/4),e=b%4,f=Math.floor((b+8)/25),g=Math.floor((b-f+1)/3),h=(19*a+b-d-g+15)%30,i=Math.floor(c/4),k=c%4,l=(32+2*e+2*i-h-k)%7,m=Math.floor((a+11*h+22*l)/451),n=h+l-7*m+114;return[Math.floor(n/31),n%31+1];}
/** Orthodox (Julian) Easter Sunday in the Gregorian calendar, 1900–2099: [month, day]. */
export function orthodoxEaster(y){const a=y%4,b=y%7,c=y%19,d=(19*c+15)%30,e=(2*a+4*b-d+34)%7,n=d+e+114,day=dayNo(y,Math.floor(n/31),n%31+1)+13;return dateOf(day).slice(1);}

/** Days since 1970-01-01 for a calendar date, whatever the time zone. */
export const dayNo=(y,m,d)=>Math.floor(Date.UTC(y,m-1,d)/864e5);
const dateOf=n=>{const t=new Date(n*864e5);return[t.getUTCFullYear(),t.getUTCMonth()+1,t.getUTCDate()];};

/** The days of a holiday whose first day falls in year y, as [first, last] day numbers. */
function days(id,y){
 switch(id){
  case 'nick':return[[dayNo(y,12,6),dayNo(y,12,6)]];
  case 'xmas':return[[dayNo(y,12,24),dayNo(y,12,26)]];
  case 'ny':return[[dayNo(y,12,31),dayNo(y+1,1,1)]];
  case 'val':return[[dayNo(y,2,14),dayNo(y,2,14)]];
  case 'easter':{const w=dayNo(y,...westernEaster(y)),o=dayNo(y,...orthodoxEaster(y));return w===o?[[w,w]]:[[w,w],[o,o]];}
  case 'ind':return[[dayNo(y,8,24),dayNo(y,8,24)]];
  case 'hw':return[[dayNo(y,10,31),dayNo(y,10,31)]];
 }
 return[];
}
/** When a holiday's film shelf shows. */
function shelfDays(id,y){
 if(id==='nick')return[[dayNo(y,12,1),dayNo(y,12,6)]];
 if(id==='xmas')return[[dayNo(y,12,7),dayNo(y,12,26)]];
 if(id==='ny')return[[dayNo(y,12,27),dayNo(y+1,1,7)]];
 return days(id,y).map(([a,b])=>[a-SHELF_DAYS,b]);
}
const find=(spans,y,m,d)=>{const t=dayNo(y,m,d);for(const id of HOLIDAYS)for(const yy of[y-1,y])for(const[a,b]of spans(id,yy))if(t>=a&&t<=b)return id;return null;};

/** Today's holiday (logo, intro, decorations), or null. */
export const holidayOn=(y,m,d)=>find(days,y,m,d);
/** The holiday whose films the home shelf offers today, or null. */
export const shelfOn=(y,m,d)=>find(shelfDays,y,m,d);
/** Both for a Date in the viewer's own time zone. */
export function holidayToday(now=new Date()){const y=now.getFullYear(),m=now.getMonth()+1,d=now.getDate();return{holiday:holidayOn(y,m,d),shelf:shelfOn(y,m,d),year:y,month:m};}

/** The greeting over the hero on the day. */
export function greeting(id,year,month){
 if(id==='ny')return`З Новим ${month===12?year+1:year} роком`;
 return{nick:'Зі святом Миколая',xmas:'З Різдвом Христовим',val:'З Днем закоханих',easter:'Христос воскрес!',ind:'З Днем Незалежності',hw:'Щасливого Хелловіну'}[id]||'';
}
/** The film shelf's title. */
export const shelfTitle=id=>({nick:'Фільми на Миколая',xmas:'Різдвяні фільми',ny:'Новорічні фільми',val:'Фільми про кохання',easter:'Фільми на Великдень',ind:'Українське кіно',hw:'Фільми на Хелловін'}[id]||'');
/** The holiday's accent: the greeting and the focus ring. */
export const accent=id=>({nick:'#F0B54A',xmas:'#F6D37A',ny:'#F5C451',val:'#FF4D6D',easter:'#E8B25C',ind:'#FFD500',hw:'#FF7A1A'}[id]||'');
