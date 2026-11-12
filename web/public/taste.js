/**
 * KIK's regular recommendations: one ranker for the browser (the home rows) and the Worker (the candidates the AI reads,
 * and the order when it gives none). A title is read from its catalogue address (section, main genre, year) and from
 * its catalogue line or genre list; a taste is the titles a profile rated, watched and saved, each with a weight.
 */
const fold=s=>String(s||'').toLowerCase().replace(/ё/g,'е').replace(/[’ʼ'`]/g,'').replace(/\s+/g,' ').trim();
/** The source's genres by the slug in a title's address: KIK's Ukrainian name first, then how else it is spelled. */
const GENRES={
 action:'Бойовики|боевики|бойовик|боевик',adventures:'Пригоди|приключения',arthouse:'Арт-хаус|артхаус',biographical:'Біографічні|биографические|біографія|биография',
 comedy:'Комедії|комедии|комедія|комедия',concert:'Концерт|концерти|концерты',cognitive:'Пізнавальні|познавательные',crime:'Кримінал|криминал',
 detective:'Детективи|детективы|детектив',documentary:'Документальні|документальные|документальний|документальный',drama:'Драми|драмы|драма',erotic:'Еротика|эротика',
 family:'Сімейні|семейные|сімейний|семейный',fantasy:'Фентезі|фэнтези',fiction:'Фантастика',historical:'Історичні|исторические',horror:'Жахи|ужасы',kids:'Дитячі|детские',
 melodrama:'Мелодрами|мелодрамы|мелодрама',military:'Воєнні|военные|військові',musical:'Музичні|музыкальные|музичний|мюзиклы|мюзикли|мюзикл',realtv:'Реаліті-шоу|реальное тв',
 sport:'Спортивні|спортивные|спорт',standup:'Стендап',telecasts:'Телепередачі|телепередачи',theatre:'Театр',thriller:'Трилери|триллеры|трилер|триллер',travel:'Подорожі|путешествия',
 western:'Вестерни|вестерны|вестерн',adult:'Для дорослих|для взрослых',anime:'Аніме|аниме',fairytale:'Казки|сказки',everyday:'Повсякденність|повседневность',
 fighting:'Бойові мистецтва|боевые искусства',mystery:'Містичні|мистические|містика|мистика',parody:'Пародія|пародия',romance:'Романтичні|романтические',school:'Школа',
 foreign:'Зарубіжне кіно|зарубежные|закордонні|зарубіжні','full-length':'Повнометражні|полнометражные',multseries:'Мультсеріали|мультсериалы|мультфільми|мультфильмы',
 short:'Короткометражні|короткометражные',russian:'Російське кіно|русские|російські',ukrainian:'Українське кіно|украинские|українські',soyzmyltfilm:'Радянські|советские'};
const NAMES=new Map(),LABEL=new Map();
for(const [slug,names] of Object.entries(GENRES)){LABEL.set(slug,names.split('|')[0]);for(const name of names.split('|'))NAMES.set(fold(name),slug);}
/** These say how or where a title was made, not what it is like: no taste is read from them. */
const FORMAT=new Set(['foreign','full-length','multseries','short','russian','ukrainian','soyzmyltfilm']);
/** Genres one audience watches together: a neighbour counts for less than the genre itself. */
const NEAR=['fantasy fiction adventures','adventures action western military fighting','action thriller crime','thriller crime detective mystery','thriller horror mystery','drama melodrama romance','drama biographical historical arthouse','historical military','comedy family parody standup everyday','family kids fairytale adventures','documentary cognitive travel realtv telecasts','musical concert theatre'].map(g=>new Set(g.split(' ')));
const near=(a,b)=>NEAR.some(g=>g.has(a)&&g.has(b));
const ANIMATED=new Set(['cartoons','animation']);
/** Animation, anime and documentaries are a taste of their own, whatever the genre. */
const OWN={cartoons:.2,animation:.25,documentary:.2};
const ADDRESS=/(?:^|\/)(films|series|cartoons|animation|documentary)\/([a-z0-9-]+)\/(\d+)-[a-z0-9_-]*?(?:-((?:18|19|20)\d{2}))?(?:-latest)?\.html$/;
/** A genre counts by its place in a title's list: the main one in full, each next one for less. */
const PLACE=[1,.6,.45,.35];
/** The genre pages each catalogue section has (the source, 11.2026), without the format and origin ones. */
const PAGES=Object.fromEntries(Object.entries({
 films:'action adventures arthouse biographical cognitive comedy concert crime detective documentary drama family fantasy fiction historical horror kids melodrama military musical sport standup theatre thriller travel western',
 series:'action adventures arthouse biographical comedy crime detective documentary drama family fantasy fiction historical horror melodrama military musical realtv sport standup telecasts thriller western',
 cartoons:'action adult adventures anime arthouse biographical cognitive comedy crime detective documentary drama fairytale family fantasy fiction historical horror kids melodrama military musical sport thriller western',
 animation:'action adventures comedy detective drama educational everyday fairytale fantasy fiction fighting historical horror kids kodomo mahoushoujo mecha military musical mystery parody romance samurai school shoujo shounen sport thriller'}).map(([section,slugs])=>[section,new Set(slugs.split(' '))]));
/** One title under any address the source gives it («…-2004.html», «…-2004-latest.html», a renamed slug): its section and number, or '' for another source. */
export const titleId=address=>{const m=String(address||'').match(ADDRESS);return m?m[1]+'/'+m[3]:'';};
/** The Ukrainian name of a genre slug («comedy» → «Комедії»), or ''. */
export const genreName=slug=>LABEL.get(slug)||'';
/**
 * What the ranker knows of a title, from {link or key, info, also}. The address gives the section, the main genre and the
 * year; `info` is a catalogue line («2011, США, Комедії»; the TV sends «2011, Комедії, Драми») or a genre list saved with a
 * rating («Комедії, Сімейні»); `also` are the genre pages the title was found on. Only a line that starts with a year has
 * a country in it. `genres` are slugs, the main one first; a name this table does not know still matches itself («~name»).
 */
export function facetsOf(item){
 const m=String(item?.key||item?.link||'').match(ADDRESS),section=m?.[1]||'',slug=m&&!FORMAT.has(m[2])?m[2]:'',genres=slug?[slug]:[];
 const line=String(item?.info||item?.sourceInfo||item?.genre||''),parts=line.split(',').map(p=>p.trim()).filter(Boolean),dated=/^\d{4}/.test(parts[0]||'');
 let country='';const year=(dated?Number(parts[0].slice(0,4)):0)||Number(m?.[4])||Number(String(item?.year||'').match(/(?:18|19|20)\d{2}/)?.[0])||0;
 for(const part of parts){if(/^\d/.test(part))continue;const name=fold(part),known=NAMES.get(name);
  if(known){if(!FORMAT.has(known)&&!genres.includes(known))genres.push(known);}
  else if(dated){if(!country)country=name;}
  else if(!genres.includes('~'+name))genres.push('~'+name);}
 for(const g of item?.also||[])if(!FORMAT.has(g)&&!genres.includes(g))genres.push(g);
 return {id:m?m[1]+'/'+m[3]:'',section,slug,genres:genres.slice(0,4),country,year};
}
/**
 * How alike two titles are, 0…1: the genres they share against all they have, each by its place (a film that is mainly
 * a crime story is little like a family comedy with a crime in it), a neighbouring genre for less, and the kind of
 * title; the country and the years add a little to a match and never make one.
 */
export function similarity(a,b){
 let s=0;
 if(a.genres.length&&b.genres.length){let shared=0,all=0;const met=new Set();
  a.genres.forEach((g,i)=>{let j=b.genres.indexOf(g),part=1;if(j<0){j=b.genres.findIndex((x,k)=>!met.has(k)&&!a.genres.includes(x)&&near(g,x));part=.4;}
   if(j<0){all+=PLACE[i];return;}met.add(j);shared+=part*Math.min(PLACE[i],PLACE[j]);all+=Math.max(PLACE[i],PLACE[j]);});
  b.genres.forEach((g,j)=>{if(!met.has(j))all+=PLACE[j];});
  const cross=!a.section||!b.section||a.section===b.section?1:ANIMATED.has(a.section)!==ANIMATED.has(b.section)?.4:ANIMATED.has(a.section)?.6:.75;s=shared/all*cross;}
 if(a.section&&a.section===b.section)s=Math.max(s,OWN[a.section]||0);
 if(!s)return 0;
 if(a.country&&a.country===b.country)s+=.08;
 if(a.year&&b.year)s+=.06*Math.max(0,1-Math.abs(a.year-b.year)/20);
 return Math.min(1,s);
}
/** Watching and saves fade over weeks (a week 0.8, a month 0.5, never under a third); a rating stays as given. */
export const fade=(at,now=Date.now())=>1/(1+Math.min(8,Math.max(0,now-at)/(7*864e5))*.25);
/** Hours with one title add up to half to its weight: 20 hours of a series say as much as a like. */
export const devotion=watchedMs=>1+Math.min(.5,Math.max(0,watchedMs||0)/144e6);
/**
 * A taste from its signals {link or key, info, w, at?}: `w` above zero is liked, below is disliked; `at` (ms) lets it fade.
 * `top` is the weight that counts in full: the profile's strongest liking, so a taste of watching alone is as sure of its
 * best titles as a taste of likes, but never under 3 (one saved title is a hint, not a taste).
 */
export function tasteModel(signals,now=Date.now()){
 const liked=[],disliked=[];
 for(const signal of signals||[]){const w=signal.w*(signal.at?fade(signal.at,now):1);if(w)(w>0?liked:disliked).push({signal,f:facetsOf(signal),w:Math.abs(w)});}
 return {liked,disliked,sum:liked.reduce((n,x)=>n+x.w,0),top:Math.max(3,...liked.map(x=>x.w)),erotic:liked.some(x=>x.f.genres.includes('erotic'))};
}
/** The genre pages a taste watches most («cartoons/comedy», «films/family»), strongest first: their popular titles are worth offering. */
export function tastePages(model,count=4){
 const sum=new Map();
 for(const x of model.liked)x.f.genres.forEach((g,i)=>{if(PAGES[x.f.section]?.has(g)){const page=x.f.section+'/'+g;sum.set(page,(sum.get(page)||0)+x.w*PLACE[i]);}});
 return [...sum].filter(([,v])=>v>=2).sort((a,b)=>b[1]-a[1]).slice(0,count).map(([page])=>page);
}
/**
 * A title against a taste: the closest liked title counts most and the whole taste adds to it. What was disliked takes
 * away by how much of it is alike: one disliked drama only lowers dramas (a title of one broad genre says little), two
 * take them down as far as it goes. `why` is the closest liked signal.
 */
export function affinity(f,model){
 let best=0,why=null,total=0,against=0;
 for(const x of model.liked){const s=similarity(f,x.f);total+=x.w*s;const v=x.w/model.top*s;if(v>best){best=v;why=x.signal;}}
 for(const x of model.disliked)against+=Math.min(1,x.w/6)*similarity(f,x.f)/2;
 return {score:.6*best+.4*(model.sum?total/model.sum:0)-.5*Math.min(1,against),best,why};
}
/**
 * The candidates a profile should see, best first, as {item,key,f,score,why,next}. `boost` is what the viewers of its
 * titles also watch (key → the summed weight of the titles that named it, negative for disliked ones); `sequels` the
 * next parts of what it watched (key → 1 for the next part, less for the one after), which go first; `trust` is how
 * much the head of the given order counts (the popular order here, the sender's own ranking on the Worker). A title in
 * `excluded` is out under any address of its own; so is a title that is like nothing liked and like several disliked
 * ones, or that the viewers of a disliked title watch. Erotic titles reach only a taste that has them. Repeating one
 * main genre costs more each time, so one taste does not fill the row.
 */
export function rankByTaste(items,model,{keyOf=item=>item.key,excluded=new Set(),boost=new Map(),sequels=new Map(),limit=72,trust=.06}={}){
 const scored=[],gone=new Set([...excluded].map(key=>titleId(key)||key));
 items.forEach((item,i)=>{const key=keyOf(item),f=facetsOf(item);if(gone.has(f.id||key)||f.genres.includes('erotic')&&!model.erotic)return;
  const a=affinity(f,model),next=sequels.get(key)||0,score=a.score+.4*Math.max(-1,Math.min(1,(boost.get(key)||0)/6))+.9*next+trust/(1+i/8);
  if(score<-.35&&!next)return;scored.push({item,key,f,score,why:a.why,next});});
 const out=[],used=new Map();
 while(scored.length&&out.length<limit){let at=0,top=-Infinity;
  scored.forEach((x,i)=>{const kind=used.get(x.f.section+'/'+x.f.genres[0])||0,section=used.get(x.f.section)||0,v=x.next?x.score:x.score>0?x.score*.85**kind*.97**section:x.score-.05*kind;if(v>top){top=v;at=i;}});
  const [x]=scored.splice(at,1);out.push(x);for(const k of [x.f.section+'/'+x.f.genres[0],x.f.section])used.set(k,(used.get(k)||0)+1);}
 return out;
}
/** The same order with at most `run` neighbours of one kind: a longer run lets the next different title in first. */
export function spread(list,kindOf,run=2){
 const rest=[...list],out=[];let last,count=0;
 while(rest.length){let at=0;if(count>=run){const other=rest.findIndex(x=>kindOf(x)!==last);if(other>0)at=other;}
  const [x]=rest.splice(at,1),kind=kindOf(x);count=kind===last?count+1:1;last=kind;out.push(x);}
 return out;
}
