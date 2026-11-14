/**
 * The film shelf before each holiday (public/holidays.js shelfOn): fixed lists, found on the catalogue by the server and
 * kept at the edge (server.mjs /api/holiday-films), so the TV and the site never search the source for them themselves.
 *
 * An entry is the names to search, in order, the year and the kind. The original title goes first (the source finds a
 * film by it too, but its cards carry only the Russian name), then the source's Russian name, which also confirms the
 * card the first search found. A card counts only within a year of the entry's year; without a name that matches it,
 * the card must also be of the entry's kind (a film or a cartoon). A title not on the catalogue is left out.
 */
import {titleKey} from './sync.mjs';
import {nameScore} from './public/search-match.js';

const film=(year,...queries)=>({year,queries,kind:'film'}),cartoon=(year,...queries)=>({year,queries,kind:'cartoon'}),any=(year,...queries)=>({year,queries});
export const HOLIDAY_FILMS={
 nick:[cartoon(2019,'Klaus','Клаус'),cartoon(2004,'The Polar Express','Полярный экспресс'),cartoon(2011,'Arthur Christmas','Секретная служба Санта-Клауса'),cartoon(2018,'The Grinch','Гринч'),
  film(2003,'Elf','Эльф'),film(1994,'The Santa Clause','Санта Клаус'),film(1994,'Miracle on 34th Street','Чудо на 34-й улице'),film(2018,'The Christmas Chronicles','Рождественские хроники'),
  film(2019,'Noelle','Ноэль'),film(2020,'Jingle Jangle: A Christmas Journey','Джингл Джангл: Рождественское путешествие'),film(2007,'Fred Claus','Фред Клаус, брат Санты'),cartoon(2012,'Rise of the Guardians','Хранители снов')],
 xmas:[film(1990,'Home Alone','Один дома'),film(1992,'Home Alone 2: Lost in New York','Один дома 2: Затерянный в Нью-Йорке'),film(2003,'Love Actually','Реальная любовь'),film(2006,'The Holiday','Отпуск по обмену'),
  film(1946,'It\'s a Wonderful Life','Эта замечательная жизнь'),cartoon(2009,'A Christmas Carol','Рождественская история'),film(2000,'How the Grinch Stole Christmas','Гринч – похититель Рождества'),
  film(1989,'National Lampoon\'s Christmas Vacation','Рождественские каникулы'),film(2019,'Last Christmas','Рождество на двоих'),film(2005,'The Family Stone','Привет семье!'),film(2008,'Four Christmases','Четыре Рождества'),
  film(2000,'The Family Man','Семьянин'),film(1988,'Die Hard','Крепкий орешек')],
 ny:[film(2011,'New Year\'s Eve','Новогодняя ночь в Нью-Йорке'),film(1989,'When Harry Met Sally...','Когда Гарри встретил Салли'),film(1960,'The Apartment','Квартира'),film(2001,'Bridget Jones\'s Diary','Дневник Бриджит Джонс'),
  film(1983,'Trading Places','Поменяться местами'),film(2001,'Serendipity','Интуиция'),film(2020,'Holidate','Праздничный переполох'),film(1994,'The Hudsucker Proxy','Подручный Хадсакера'),film(1972,'The Poseidon Adventure','Приключение «Посейдона»')],
 val:[film(1997,'Titanic','Титаник'),film(2004,'The Notebook','Дневник памяти'),film(2016,'La La Land','Ла-Ла Ленд'),film(2005,'Pride & Prejudice','Гордость и предубеждение'),film(1999,'Notting Hill','Ноттинг Хилл'),
  film(1990,'Pretty Woman','Красотка'),film(1995,'Before Sunrise','Перед рассветом'),film(2013,'About Time','Бойфренд из будущего'),film(2011,'Crazy, Stupid, Love.','Эта дурацкая любовь'),
  film(2009,'(500) Days of Summer','500 дней лета'),film(2016,'Me Before You','До встречи с тобой'),film(1990,'Ghost','Привидение')],
 easter:[any(2011,'Hop','Бунт ушастых'),any(2018,'Peter Rabbit','Кролик Питер'),any(2021,'Peter Rabbit 2: The Runaway','Кролик Питер 2'),cartoon(1998,'The Prince of Egypt','Принц Египта'),film(2014,'Paddington','Приключения Паддингтона'),
  film(2017,'Paddington 2','Приключения Паддингтона 2'),film(2005,'Charlie and the Chocolate Factory','Чарли и шоколадная фабрика'),cartoon(2000,'Chicken Run','Побег из курятника'),cartoon(2016,'Zootopia','Зверополис'),
  film(1995,'Babe','Бэйб: Четвероногий малыш'),film(2004,'The Passion of the Christ','Страсти Христовы'),film(1959,'Ben-Hur','Бен-Гур')],
 // Ukrainian cinema: the source lists it under Russian names, so those go first.
 ind:[any(2023,'Довбуш'),any(2019,'Захар Беркут'),any(2017,'Киборги','Кіборги'),any(1964,'Тени забытых предков','Тіні забутих предків'),any(2019,'Мои мысли тихие','Мої думки тихі'),any(2019,'Атлантида'),
  any(2022,'Памфир','Памфір'),any(2021,'Стоп-Земля'),any(2019,'Черкассы','Черкаси'),any(2023,'20 дней в Мариуполе','20 днів у Маріуполі'),any(2021,'Дом «Слово»','Будинок Слово'),any(2023,'Мавка: Лесная песня','Мавка')],
 hw:[film(1993,'Hocus Pocus','Фокус-покус'),cartoon(2009,'Coraline','Коралина в Стране Кошмаров'),cartoon(1993,'The Nightmare Before Christmas','Кошмар перед Рождеством'),film(1988,'Beetlejuice','Битлджус'),
  film(1991,'The Addams Family','Семейка Аддамс'),cartoon(2005,'Corpse Bride','Труп невесты'),film(1984,'Ghostbusters','Охотники за привидениями'),film(1995,'Casper','Каспер'),cartoon(2012,'Hotel Transylvania','Монстры на каникулах'),
  cartoon(2012,'ParaNorman','Паранорман'),cartoon(2012,'Frankenweenie','Франкенвини'),film(1978,'Halloween','Хэллоуин'),film(1996,'Scream','Крик'),film(2013,'The Conjuring','Заклятие')]
};
const LIMIT=15,DAY=86400000,RETRY=600000,BUDGET=8000;
// The source's sections a kind may come from; a series section counts only for a series (shelves.mjs), holiday lists are of films.
const SECTIONS={film:['films','documentary'],cartoon:['cartoons','animation'],series:['series','documentary'],any:['films','cartoons','animation','documentary']};
const yearOf=(card,key)=>Number(String(card.info||'').match(/(?:18|19|20)\d{2}/)?.[0]||key.match(/-((?:18|19|20)\d{2})\.html$/)?.[1])||0;

/** The card of one search that is this entry: `named` when its name is one of the entry's (exactly, or as the start of the card's longer name). */
export function pickCard(entry,cards){
 let best=null,top=0;
 for(const card of cards||[]){
  let key;try{key=titleKey(card.link);}catch{continue;}
  const section=key.split('/')[0],year=yearOf(card,key);if((section==='series')!==(entry.kind==='series')&&(section==='series'||section==='films')||!year||Math.abs(year-entry.year)>1)continue;
  const name=Math.max(0,...String(card.title||'').split(/\s+\/\s+/).flatMap(part=>entry.queries.map(q=>nameScore(q,part)))),kind=SECTIONS[entry.kind||'any'].includes(section);
  if(name<.9&&!kind)continue;
  // A whole name beats the start of a longer one («Крик» over «Крик 2»), the kind and the exact year come next, then the source's order.
  const score=(name>=1?40:name>=.9?20:0)+(kind?2:0)+(year===entry.year?1:0);
  if(score>top){best=card;top=score;}
 }
 return best&&{card:best,named:top>=20};
}
/** One entry on the catalogue: the first search whose cards name it, else the best card of the first search that had one. */
export async function resolve(entry,search){
 let fallback=null;
 for(const query of entry.queries){const found=pickCard(entry,await search({query}));if(found?.named)return found.card;fallback??=found?.card||null;}
 return fallback;
}
// Settled answers only, per entry, for a day (a failed search for ten minutes): a Worker request cannot await another
// request's search, which never settles once that request is cancelled. A list that ran out of time goes on from here.
const memo=new Map();
/**
 * A holiday's shelf: its entries found on the catalogue, in the list's order, at most 15. One search at a time, as
 * background work on the source; no new search starts after `budget` ms, and a list missing an entry for that or for an
 * error is `partial`, so the server does not keep it.
 */
export async function holidayFilms(id,{search,now=Date.now,budget=BUDGET,cache=memo}={}){
 const list=Object.hasOwn(HOLIDAY_FILMS,id)?HOLIDAY_FILMS[id]:null;if(!list)throw Object.assign(Error('Невідоме свято'),{status:400});
 const started=now(),items=[],seen=new Set();let partial=false;
 for(const entry of list){
  const key=id+'|'+entry.year+'|'+entry.queries.join('|');let hit=cache.get(key);
  if(!hit||hit.until<=now()){
   if(now()-started>budget){partial=true;continue;}
   try{hit={card:await resolve(entry,search),until:now()+DAY};}catch(e){hit={failed:true,until:now()+RETRY};console.warn('KIK holiday films',id,entry.queries[0],e?.message);}
   if(cache.size>=400)cache.delete(cache.keys().next().value);cache.set(key,hit);
  }
  if(hit.failed){partial=true;continue;}
  const card=hit.card;if(!card)continue;const k=titleKey(card.link);if(seen.has(k))continue;seen.add(k);items.push(card);
  if(items.length>=LIMIT)break;
 }
 return {items,partial};
}
