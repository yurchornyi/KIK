import test from 'node:test';import assert from 'node:assert/strict';
import {translatePublic,localizedRecords} from './localization.mjs';
import {hash} from './auth.mjs';
test('translations already known in this isolate cost no D1 query; D1 answers are remembered',async()=>{
 const stored=new Map([[await hash('Тайна старого дома'),'Таємниця старого будинку']]),queries=[];
 const db={prepare(sql){return {bind(...ids){return {async all(){queries.push(ids);return {results:ids.filter(id=>stored.has(id)).map(id=>({source_hash:id,translated:stored.get(id)}))};},async run(){return {};}};}};}};
 const fetcher=async()=>assert.fail('nothing goes to the translator');
 assert.equal((await translatePublic(db,['Драма','Тайна старого дома'],{fetcher})).get('Тайна старого дома'),'Таємниця старого будинку');
 assert.equal(queries.length,1);assert.equal(queries[0].length,1,'the dictionary word is not looked up in D1');
 const again=await translatePublic(db,['Тайна старого дома','Драма'],{fetcher});
 assert.equal(again.get('Тайна старого дома'),'Таємниця старого будинку');assert.equal(again.get('Драма'),'Драма');assert.equal(queries.length,1,'a known title is not asked again');
 const records=[{kind:'later',key:'films/drama/1-a.html',data:{title:'Тайна старого дома'},updated_at:1}];
 assert.equal((await localizedRecords(db,records))[0].data.displayTitle,'Таємниця старого будинку');assert.equal(queries.length,1,'a sync with known titles reads no translations');
});
