import {test} from 'node:test';import assert from 'node:assert/strict';
import {facetsOf,similarity,tasteModel,tastePages,affinity,rankByTaste,spread,titleId,fade,devotion,genreName} from './public/taste.js';
const link=(section,genre,id,year=2020)=>`https://hdrezka.sh/${section}/${genre}/${id}-nazva-${year}.html`;
const card=(section,genre,id,info,year=2020)=>({key:`${section}/${genre}/${id}-nazva-${year}.html`,link:link(section,genre,id,year),title:'Назва '+id,info});
const titles=list=>list.map(x=>x.item.title);

test('a title is read from its address and from any line it comes with',()=>{
 // A catalogue card: year, country, genre. The address already says the main genre, the section and the year.
 assert.deepEqual(facetsOf({link:'https://hdrezka.sh/films/comedy/833-odin-doma-1990-latest.html',info:'1990, США, Комедії'}),{id:'films/833',section:'films',slug:'comedy',genres:['comedy'],country:'сша',year:1990});
 // A genre list saved with a rating or «Мій список» has no year and no country: its first genre is a genre (it used to be read as the country and lost).
 assert.deepEqual(facetsOf({key:'films/comedy/833-odin-doma-1990-latest.html',info:'Комедії, Сімейні, Закордонні'}).genres,['comedy','family']);
 // The TV's line has no country; Russian and Ukrainian names are one genre; «how it was made» is no genre.
 assert.deepEqual(facetsOf({key:'cartoons/comedy/1768-simpsony-1989-latest.html',info:'1989-2024, Мультсериалы, Комедии, Для взрослых'}),{id:'cartoons/1768',section:'cartoons',slug:'comedy',genres:['comedy','adult'],country:'',year:1989});
 // Watching keeps nothing but the address: it is enough.
 assert.deepEqual(facetsOf({key:'series/documentary/30365-formula-1-gonyat-chtoby-vyzhivat-2019-latest.html'}),{id:'series/30365',section:'series',slug:'documentary',genres:['documentary'],country:'',year:2019});
 assert.deepEqual(facetsOf({key:'series/documentary/82378-9-2021.html'}).year,2021);
 // A name the table does not know still matches itself; a title of another source has no address to read.
 assert.deepEqual(facetsOf({key:'animation/shounen/5-x-2019.html',info:'Сьонен, Космос'}).genres,['shounen','~сьонен','~космос']);
 assert.deepEqual(facetsOf({link:'https://uaserials.com/123-nazva.html',info:'2021, Україна, Драми'}),{id:'',section:'',slug:'',genres:['drama'],country:'україна',year:2021});
 // A title found on a genre page has that genre too, and is one title under any address of its own.
 assert.deepEqual(facetsOf({key:'cartoons/comedy/2136-rik-i-morti-2013.html',info:'2013 - ..., США, Комедії',also:['adult','comedy','foreign']}).genres,['comedy','adult']);
 assert.equal(titleId('https://hdrezka.sh/films/fantasy/1045-garri-potter-i-uznik-azkabana-2004.html'),titleId('films/fantasy/1045-uznik-azkabana-2004-latest.html'));assert.equal(titleId('uaserials/5-x.html'),'');
 assert.equal(genreName('comedy'),'Комедії');assert.equal(genreName('nothing'),'');
});
test('alike: the same genre, then a neighbouring one, then the same kind of title; years and countries never make a match',()=>{
 const simpsons=facetsOf({key:'cartoons/comedy/1768-simpsony-1989-latest.html'}),of=(section,genre,info)=>similarity(facetsOf(card(section,genre,1,info)),simpsons);
 const sameKind=of('cartoons','comedy'),sitcom=of('series','comedy'),family=of('cartoons','family'),otherCartoon=of('cartoons','adventures'),drama=of('films','drama','1989, США, Драми');
 assert.ok(sameKind>sitcom&&sitcom>otherCartoon&&family>otherCartoon&&otherCartoon>0,[sameKind,sitcom,family,otherCartoon].join(' '));assert.equal(drama,0);
 // A genre counts by its place: the main genre of both is more than a genre further down the list, and that more than a neighbour.
 const potter=facetsOf({key:'films/fantasy/1044-garri-potter-2002.html',info:'Фентезі, Пригоди, Детективи, Сімейні'});
 assert.ok(similarity(facetsOf(card('films','fantasy',2)),potter)>similarity(facetsOf(card('films','adventures',3)),potter));assert.ok(similarity(facetsOf(card('films','adventures',3)),potter)>similarity(facetsOf(card('films','fiction',4)),potter));
 // A film that is mainly a crime story is little like a family comedy with a crime in it; a family comedy is the same thing.
 const homeAlone=facetsOf({key:'films/comedy/837-odin-doma-3-1997.html',info:'Комедії, Кримінал, Сімейні'}),crime=similarity(facetsOf(card('films','crime',5)),homeAlone),comedy=similarity(facetsOf(card('films','comedy',6)),homeAlone),familyComedy=similarity(facetsOf({...card('films','comedy',7),also:['family']}),homeAlone);
 assert.ok(crime<.3&&comedy>crime*1.8&&familyComedy>comedy,[crime,comedy,familyComedy].join(' '));
});
test('time and hours weigh a signal: watching fades over weeks, long watching counts for more',()=>{
 const now=Date.UTC(2026,10,11);assert.equal(fade(now,now),1);assert.ok(Math.abs(fade(now-7*864e5,now)-.8)<1e-9);assert.ok(Math.abs(fade(now-400*864e5,now)-1/3)<1e-9);
 assert.equal(devotion(0),1);assert.equal(devotion(undefined),1);assert.equal(devotion(20*3600000),1.5);assert.equal(devotion(500*3600000),1.5);
 const model=tasteModel([{key:'films/comedy/1-a-2020.html',w:4,at:now-28*864e5},{key:'films/drama/2-b-2020.html',w:6},{key:'films/horror/3-c-2020.html',w:-6},{key:'films/action/4-d-2020.html',w:0}],now);
 assert.deepEqual(model.liked.map(x=>x.w),[2,6]);assert.deepEqual(model.disliked.map(x=>x.w),[6]);assert.equal(model.sum,8);assert.equal(model.top,6);assert.equal(model.erotic,false);
 // One saved title is a hint: its weight never counts in full.
 assert.equal(tasteModel([{key:'films/comedy/1-a-2020.html',w:2}],now).top,3);
});
// The household of the 11.11 audit: Harry Potter, Home Alone and the Grinch to the end, one melodrama saved.
const household=[{key:'films/fantasy/1043-garri-potter-i-filosofskiy-kamen-2001-latest.html',info:'Фентезі, Пригоди, Зарубіжне кіно',w:3},{key:'films/fantasy/1044-garri-potter-i-taynaya-komnata-2002-latest.html',info:'Фентезі, Пригоди, Детективи, Сімейні, Зарубіжне кіно',w:4},{key:'cartoons/fantasy/29047-grinch-2018-latest.html',info:'Фентезі, Комедії, Сімейні, Повнометражні, Закордонні',w:4},{key:'films/comedy/833-odin-doma-1990-latest.html',info:'Комедії, Сімейні, Закордонні',w:4},{key:'films/comedy/836-odin-doma-2-1992-latest.html',info:'Комедії, Пригоди, Кримінал, Сімейні, Закордонні',w:4},{key:'films/melodrama/86799-lyubi-menya-2026.html',info:'Мелодрами, Драми, Зарубіжне кіно',w:2}];
const shelf=[card('films','drama',1,'1986, Італія, Драми',1986),card('films','action',2,'1995, Японія, Бойовики',1995),card('films','erotic',3,'2019, Франція, Еротика',2019),card('films','horror',4,'2020, США, Жахи'),card('films','melodrama',5,'2012, США, Мелодрами',2012),card('films','comedy',6,'1992, США, Комедії',1992),card('films','family',7,'2014, Велика Британія, Сімейні',2014),card('films','fantasy',8,'2005, США, Фентезі',2005),card('cartoons','comedy',9,'2019, США, Комедії',2019),card('films','adventures',10,'2017, США, Пригоди',2017)];
test('a family taste gets family titles first, an erotic title never, whatever came first in the catalogue',()=>{
 const ranked=rankByTaste(shelf,tasteModel(household)),order=titles(ranked),at=n=>order.indexOf('Назва '+n);
 assert.ok(!order.includes('Назва 3'),'erotic');assert.equal(order.length,9);
 // A drama and a melodrama keep a place (one melodrama was saved); action and horror are what this taste never watched.
 for(const fits of [6,7,8,9,10])for(const far of [2,4])assert.ok(at(fits)<at(far),fits+' before '+far);assert.ok(at(5)<at(2)&&at(5)<at(4));
 // The row opens with the whole taste (comedy, family, fantasy, an animated comedy), not with one genre of it.
 assert.equal(new Set(ranked.slice(0,4).map(x=>x.f.section+'/'+x.f.slug)).size,4);
 assert.equal(ranked[0].why.key.includes('odin-doma')||ranked[0].why.key.includes('garri-potter'),true);
 // A taste that has an erotic title sees them.
 assert.ok(titles(rankByTaste(shelf,tasteModel([...household,{key:'films/erotic/77-x-2010.html',w:6}]))).includes('Назва 3'));
});
test('the next part of a franchise goes first; what viewers of the taste also watch goes up, of a disliked title down',()=>{
 const model=tasteModel(household),next=card('films','fantasy',1045,'2004'),picked=card('films','detective',20,'2019, США, Детективи',2019),plain=card('films','comedy',21,'1995, США, Комедії',1995);
 const ranked=rankByTaste([...shelf,plain,picked,next],model,{sequels:new Map([[next.key,1]]),boost:new Map([[picked.key,8],[plain.key,-8]])}),order=titles(ranked);
 assert.equal(order[0],next.title);assert.equal(ranked[0].next,1);assert.ok(order.indexOf(picked.title)<4,'a pick of two liked titles is near the top: '+order.indexOf(picked.title));
 assert.ok(order.indexOf(plain.title)>order.indexOf('Назва 6'),'what viewers of a disliked title watch goes below its like');
 // What the profile watched, rated or saved is never offered again.
 assert.ok(!titles(rankByTaste(shelf,model,{excluded:new Set([shelf[5].key])})).includes('Назва 6'));
});
test('one dislike does not take a whole genre away; a taste of dislikes only loses what is like them',()=>{
 const comedies=[card('films','comedy',1),card('films','comedy',2),card('films','drama',3)];
 const mixed=titles(rankByTaste(comedies,tasteModel([{key:'films/comedy/50-liked-2019.html',w:6},{key:'films/comedy/51-disliked-2018.html',w:-6}])));
 assert.deepEqual(mixed.slice(0,2),['Назва 1','Назва 2'],'liked and disliked comedies: comedies still lead');
 // A drama sent first: one disliked drama (one broad genre says little) only puts it last; two take it out, and so does one
 // whose viewers watch this very title; a film given up early barely counts.
 const dramaFirst=[comedies[2],comedies[0],comedies[1]],disliked=n=>Array.from({length:n},(_,i)=>({key:`films/drama/${60+i}-disliked-2020.html`,w:-6}));
 assert.deepEqual(titles(rankByTaste(dramaFirst,tasteModel(disliked(1)))),['Назва 1','Назва 2','Назва 3']);
 assert.deepEqual(titles(rankByTaste(dramaFirst,tasteModel(disliked(2)))),['Назва 1','Назва 2']);
 assert.deepEqual(titles(rankByTaste(dramaFirst,tasteModel(disliked(1)),{boost:new Map([[comedies[2].key,-6]])})),['Назва 1','Назва 2']);
 assert.deepEqual(titles(rankByTaste(dramaFirst,tasteModel([{key:'films/drama/70-given-up-2020.html',w:-2},{key:'films/drama/71-given-up-2020.html',w:-2}]))),['Назва 1','Назва 2','Назва 3'],'given up twice: last, never out');
});
test('no taste yet: the catalogue order, with one genre not filling the row',()=>{
 const pool=[card('films','drama',1),card('films','drama',2),card('films','drama',3),card('films','drama',4),card('films','comedy',5),card('series','fantasy',6)];
 const order=titles(rankByTaste(pool,tasteModel([])));assert.equal(order[0],'Назва 1');assert.ok(order.indexOf('Назва 5')<order.indexOf('Назва 4'));assert.equal(order.length,6);
 assert.equal(titles(rankByTaste(pool,tasteModel([]),{limit:2})).length,2);
});
test('spread keeps an order but lets no more than two of a kind stand together',()=>{
 const kind=x=>x[0];assert.deepEqual(spread(['a1','a2','a3','a4','b1','a5','c1'],kind),['a1','a2','b1','a3','a4','c1','a5']);
 assert.deepEqual(spread(['a1','a2','a3'],kind),['a1','a2','a3'],'nothing else to let in');assert.deepEqual(spread([],kind),[]);assert.deepEqual(spread(['a1','b1','a2'],kind),['a1','b1','a2']);
});
test('a title against a taste: the closest liked title explains the pick',()=>{
 const a=affinity(facetsOf(card('films','comedy',6,'1992, США, Комедії',1992)),tasteModel(household));assert.ok(a.score>0&&a.best>0);assert.match(a.why.key,/odin-doma/);
 assert.equal(affinity(facetsOf(card('films','horror',4)),tasteModel([])).why,null);
});

test('the genre pages worth offering are the genres a taste watches most, wherever they stand in a title\'s list',()=>{
 assert.deepEqual(tastePages(tasteModel(household)),['films/comedy','films/fantasy','films/adventures','films/family']);
 // A page the section does not have, a format and an unknown genre ask for nothing; a weak hint asks for nothing either.
 assert.deepEqual(tastePages(tasteModel([{key:'cartoons/comedy/1768-simpsony-1989-latest.html',info:'Комедії, Для дорослих, Мультсеріали, Зарубіжне кіно, Сьонен',w:6},{key:'series/realtv/5-x-2020.html',info:'Реаліті-шоу, Для дорослих',w:4},{key:'films/horror/6-y-2020.html',w:1}])),['cartoons/comedy','series/realtv','cartoons/adult']);
 assert.deepEqual(tastePages(tasteModel([])),[]);assert.equal(tastePages(tasteModel(household),2).length,2);
});
