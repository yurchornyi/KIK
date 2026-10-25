import {test} from 'node:test';import assert from 'node:assert/strict';import {readFileSync,readdirSync} from 'node:fs';import {DatabaseSync} from 'node:sqlite';
import {exchange,SYNC_OVERLAP} from './sync.mjs';
// A minimal D1 over node:sqlite with the real migrations, so sync runs against the production schema.
function d1(){const db=new DatabaseSync(':memory:');db.exec('PRAGMA foreign_keys=ON');for(const f of readdirSync(new URL('./migrations/',import.meta.url)).sort())db.exec(readFileSync(new URL('./migrations/'+f,import.meta.url),'utf8'));
 for(const id of ['u1','u2'])db.prepare("INSERT INTO users(id,username,email,salt,password_hash,created_at) VALUES(?,?,?,'s','h',1)").run(id,id,id+'@example.invalid');
 for(const [id,user] of [['p1','u1'],['p2','u2']])db.prepare("INSERT INTO profiles(id,user_id,name,avatar,created_at) VALUES(?,?,'QA','bear',1)").run(id,user);
 const stmt=(sql,args=[])=>({bind:(...a)=>stmt(sql,a),all:async()=>({results:db.prepare(sql).all(...args)}),run:async()=>({meta:{changes:Number(db.prepare(sql).run(...args).changes)}})});
 return {prepare:sql=>stmt(sql),batch:async list=>{for(const s of list)await s.run();}};}
const link='https://hdrezka.sh/films/drama/1-qa.html',later=(key,at,title='A')=>({kind:'later',key,data:{link:'https://hdrezka.sh/'+key,title},updated_at:at});
const keys=list=>list.map(r=>r.key).sort();

test('with since only records written after the last sync come back, tombstones included',async()=>{
 const db=d1(),t0=1_800_000_000_000;
 await exchange(db,'p1',[later('films/drama/1-qa.html',t0-5000),later('films/drama/2-qa.html',t0-5000)],null,t0);
 assert.equal((await exchange(db,'p1',[],null,t0+1)).length,2,'no since: whole history for older TV releases');
 const since=t0+SYNC_OVERLAP+10;
 assert.deepEqual(await exchange(db,'p1',[],since,since+1),[],'nothing changed since the last sync');
 const removed=await exchange(db,'p1',[{kind:'later',key:'films/drama/1-qa.html',data:null,updated_at:since}],since,since+2);
 assert.deepEqual(removed.map(r=>[r.key,r.data]),[['films/drama/1-qa.html',null]],'deletion travels as a tombstone');
 assert.deepEqual(keys(await exchange(db,'p1',[],since+1,since+3)),['films/drama/1-qa.html'],'another device gets the tombstone');
});

test('an offline device uploading old edits still reaches the devices that synced later',async()=>{
 const db=d1(),t0=1_800_000_000_000,phone=t0+SYNC_OVERLAP*3;
 await exchange(db,'p1',[],null,t0);
 // The TV was offline: its edit carries an updated_at long before the phone's last sync.
 await exchange(db,'p1',[later('films/drama/3-qa.html',t0+1000)],null,phone+5000);
 assert.deepEqual(keys(await exchange(db,'p1',[],phone,phone+6000)),['films/drama/3-qa.html']);
});

test('a losing write is not reported as a change and cannot revive a deletion',async()=>{
 const db=d1(),t0=1_800_000_000_000,later1=t0+SYNC_OVERLAP*3;
 await exchange(db,'p1',[{kind:'later',key:'films/drama/1-qa.html',data:null,updated_at:t0}],null,t0);
 assert.deepEqual(await exchange(db,'p1',[later('films/drama/1-qa.html',t0-1)],later1,later1+1),[]);
 assert.equal((await exchange(db,'p1',[],null,later1+2))[0].data,null);
});

test('delta sync never leaks another account profile',async()=>{
 const db=d1(),t0=1_800_000_000_000;
 await exchange(db,'p2',[later('films/drama/9-qa.html',t0)],null,t0);
 assert.deepEqual(await exchange(db,'p1',[],t0-SYNC_OVERLAP,t0+1),[]);
 assert.deepEqual(await exchange(db,'p1',[],null,t0+1),[]);
});

test('since from the future or of the wrong type is clamped or ignored',async()=>{
 const db=d1(),t0=1_800_000_000_000;
 await exchange(db,'p1',[later('films/drama/1-qa.html',t0)],null,t0);
 assert.equal((await exchange(db,'p1',[],Number.MAX_SAFE_INTEGER,t0+1)).length,1,'future since is clamped to now: a fresh write still shows');
 for(const since of ['1',-1,1.5,0])assert.equal((await exchange(db,'p1',[],since,t0+SYNC_OVERLAP*9)).length,1,String(since));
});
