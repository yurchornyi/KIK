import {test} from 'node:test';import assert from 'node:assert/strict';
import {artworkIdentity,matchTitle,selectImages,createArtworkService} from './tmdb.mjs';

const link='https://hdrezka.sh/films/fiction/2259-interstellar-2014.html';
const poster={file_path:'/poster.jpg',width:1000,height:1500,iso_639_1:'uk',vote_average:5};
const backdrop={file_path:'/wide.jpg',width:3840,height:2160,iso_639_1:null,vote_average:5};
function mockTmdb(routes){const calls=[];const fetcher=async(url,init)=>{calls.push({url,init});for(const [part,body] of routes)if(url.includes(part))return typeof body==='number'?new Response('{}',{status:body}):Response.json(body);return new Response('{}',{status:404});};return {fetcher,calls};}

test('identity comes from link, title and year; series links are tv',()=>{
 assert.deepEqual(artworkIdentity({link,title:' Интерстеллар '}),{title:'Интерстеллар',original:'',year:2014,kind:'movie',aliases:['Интерстеллар']});
 const alt=artworkIdentity({link:'https://hdrezka.sh/series/thriller/1-sopranos-1999.html',title:'Клан Сопрано / Сопрано',year:'1999-2007'});assert.deepEqual(alt.aliases,['Клан Сопрано','Сопрано']);assert.equal(matchTitle(alt,[{id:1398,name:'Клан Сопрано',first_air_date:'1999-01-10'}]).id,1398);
 assert.equal(artworkIdentity({link:'https://hdrezka.sh/series/drama/1-friends.html',title:'Друзья',year:'1994-2004'}).kind,'tv');
 assert.throws(()=>artworkIdentity({link:'https://evil.example/films/x/1-x.html',title:'X'}));
 assert.throws(()=>artworkIdentity({link,title:''}));
});

test('matching needs an exact title and close year, and refuses ties',()=>{
 const id={title:'Интерстеллар',original:'Interstellar',year:2014,kind:'movie'};
 assert.equal(matchTitle(id,[{id:1,title:'Interstellar 2',release_date:'2014-01-01'},{id:2,title:'Интерстеллар',release_date:'2014-11-05'}]).id,2);
 assert.equal(matchTitle(id,[{id:3,title:'Interstellar',release_date:'2001-01-01'}]),null);
 assert.equal(matchTitle(id,[{id:4,title:'Interstellar',release_date:'2014-01-01'},{id:5,original_title:'Interstellar',release_date:'2014-06-01'}]),null);
 assert.equal(matchTitle(id,[{id:6,title:'Interstellar',release_date:'2014-01-01',adult:true}]),null);
});

test('images: Ukrainian poster first, large landscape backdrop, nothing unsafe',()=>{
 const a=selectImages({posters:[{...poster,iso_639_1:'en',file_path:'/en.jpg'},poster],backdrops:[{...backdrop,width:800,height:450,file_path:'/small.jpg'},backdrop]},2,'movie');
 assert.equal(a.poster,'https://image.tmdb.org/t/p/w500/poster.jpg');
 assert.equal(a.posterLarge,'https://image.tmdb.org/t/p/w780/poster.jpg');
 assert.equal(a.backdrop,'https://image.tmdb.org/t/p/w1280/wide.jpg');
 assert.equal(a.backdropWidth,3840);
 assert.equal(selectImages({posters:[{...poster,file_path:'../x.jpg'}],backdrops:[]},2,'movie'),null);
});

test('without a token the site keeps its own artwork and never calls TMDB',async()=>{
 const {fetcher,calls}=mockTmdb([]);const r=await createArtworkService({fetcher}).lookup({link,title:'Интерстеллар'},{token:''});
 assert.equal(r.configured,false);assert.equal(r.artwork,null);assert.equal(calls.length,0);
});

test('lookup searches, picks images, sends the token only in the header and caches',async()=>{
 const {fetcher,calls}=mockTmdb([['/search/movie',{results:[{id:157336,title:'Интерстеллар',release_date:'2014-11-05'}]}],['/movie/157336/images',{posters:[poster],backdrops:[backdrop]}]]);
 const service=createArtworkService({fetcher});
 const [a,b]=await Promise.all([service.lookup({link,title:'Интерстеллар'},{token:'secret'}),service.lookup({link,title:'Интерстеллар'},{token:'secret'})]);
 assert.equal(a.artwork.id,157336);assert.equal(a.artwork.page,'https://www.themoviedb.org/movie/157336');assert.deepEqual(a,b);
 assert.equal(calls.length,2);
 for(const c of calls){assert.equal(c.init.headers.Authorization,'Bearer secret');assert.ok(['follow','manual'].includes(c.init.redirect),'Workers reject other redirect modes');assert.ok(!c.url.includes('secret'));}
 assert.match(calls[0].url,/language=ru-RU/);assert.match(calls[0].url,/year=2014/);
 await service.lookup({link,title:'Интерстеллар'},{token:'secret'});assert.equal(calls.length,2);
});

test('rejected credentials pause TMDB requests instead of hammering it',async()=>{
 let t=1000;const {fetcher,calls}=mockTmdb([['/search/',401]]);const service=createArtworkService({fetcher,now:()=>t});
 const r=await service.lookup({link,title:'Интерстеллар'},{token:'bad'});
 assert.equal(r.artwork,null);assert.equal(r.retryAfter,60);
 await service.lookup({link:'https://hdrezka.sh/films/drama/5-other-2020.html',title:'Other'},{token:'bad'});
 assert.equal(calls.length,1);
});

test('v3 API keys and pasted Bearer prefixes both work without exposing credentials to clients',async()=>{
 for(const token of ['a'.repeat(32),'Bearer read-access-token']){
  const {fetcher,calls}=mockTmdb([['/search/movie',{results:[{id:157336,title:'Интерстеллар',release_date:'2014-11-05'}]}],['/movie/157336/images',{posters:[poster],backdrops:[backdrop]}]]);
  const a=await createArtworkService({fetcher}).lookup({link,title:'Интерстеллар'},{token});assert.equal(a.artwork.id,157336);assert.ok(!JSON.stringify(a).includes(token));
  for(const c of calls)if(token.startsWith('Bearer')){assert.equal(c.init.headers.Authorization,'Bearer read-access-token');assert.ok(!c.url.includes('read-access-token'));}else{assert.equal(new URL(c.url).searchParams.get('api_key'),token);assert.equal(c.init.headers.Authorization,undefined);}
 }
});

test('small source posters cannot be promoted to high-resolution artwork',()=>{
 const a=selectImages({posters:[{...poster,width:300,height:450}],backdrops:[]},2,'movie');assert.equal(a,null);
 const b=selectImages({posters:[poster],backdrops:[]},2,'movie');assert.equal(b.posterWidth,1000);assert.equal(b.posterOriginal,'https://image.tmdb.org/t/p/original/poster.jpg');
});

test('regional release dates can differ without choosing an unrelated film',async()=>{
 const calls=[];const fetcher=async url=>{calls.push(url);if(url.includes('/images'))return Response.json({posters:[poster],backdrops:[backdrop]});return Response.json({results:new URL(url).searchParams.has('year')?[]:[{id:77,title:'Exact title',release_date:'2025-12-31'},{id:78,title:'Other title',release_date:'2026-01-01'}]});};
 const a=await createArtworkService({fetcher}).lookup({link:'https://hdrezka.sh/films/concert/1-exact-2026.html',title:'Exact title'},{token:'secret'});assert.equal(a.artwork.id,77);assert.equal(calls.length,3);
});

test('an English international title requests English translations even when the catalogue title is Russian',async()=>{
 const calls=[];const fetcher=async url=>{calls.push(url);if(url.includes('/images'))return Response.json({backdrops:[backdrop],posters:[poster]});const lang=new URL(url).searchParams.get('language');return Response.json({results:lang==='en-US'?[{id:81,title:'International Title',original_title:'한국 영화',release_date:'2026-01-01'}]:[]});};
 const result=await createArtworkService({fetcher}).lookup({link:'https://hdrezka.sh/films/concert/81-test-2026.html',title:'Название фильма',original:'International Title'},{token:'secret'});assert.equal(result.artwork.id,81);assert.equal(new URL(calls[0]).searchParams.get('language'),'en-US');assert.ok(result.artwork.backdrop);
});
