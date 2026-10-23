import {test} from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readdirSync,readFileSync} from 'node:fs';

function database() {
  const db=new DatabaseSync(':memory:'),dir=new URL('./migrations/',import.meta.url);
  for(const file of readdirSync(dir).sort()) db.exec(readFileSync(new URL(file,dir),'utf8'));
  db.exec("INSERT INTO users(id,username,salt,password_hash,created_at) VALUES('qa','qa','s','h',1);INSERT INTO profiles(id,user_id,name,created_at) VALUES('profile','qa','QA',1)");
  return db;
}
test('database limits retain readable and updatable records and bound profile creation',()=>{
  const db=database();
  try {
    db.exec("WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x+1 FROM n WHERE x<5000) INSERT INTO records SELECT 'profile','later',CAST(x AS TEXT),NULL,1 FROM n");
    assert.throws(()=>db.exec("INSERT INTO records VALUES('profile','later','overflow',NULL,1)"),/Profile record limit/);
    db.exec("INSERT INTO records VALUES('profile','later','1',NULL,2) ON CONFLICT(profile_id,kind,item_key) DO UPDATE SET updated_at=excluded.updated_at");
    assert.equal(db.prepare('SELECT COUNT(*) AS n FROM records').get().n,5000);
    assert.equal(db.prepare("SELECT updated_at FROM records WHERE item_key='1'").get().updated_at,2);
    db.exec("WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x+1 FROM n WHERE x<7) INSERT INTO profiles SELECT CAST(x AS TEXT),'qa','QA','bear',1,0,0 FROM n");
    assert.throws(()=>db.exec("INSERT INTO profiles(id,user_id,name,created_at) VALUES('overflow','qa','QA',1)"),/Account profile limit/);
    assert.equal(db.prepare('SELECT COUNT(*) AS n FROM profiles').get().n,8);
  } finally {db.close();}
});
test('queue and invitation caps are enforced even if a request passes a stale count check',()=>{
  const db=database();
  try {
    db.exec("WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x+1 FROM n WHERE x<300) INSERT INTO ad_scan_queue SELECT CAST(x AS TEXT),'35','1','1',1,0 FROM n");
    db.exec("INSERT INTO ad_scan_queue VALUES('overflow','35','1','1',1,0)");
    assert.equal(db.prepare('SELECT COUNT(*) AS n FROM ad_scan_queue').get().n,300);
    db.exec("WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x+1 FROM n WHERE x<200) INSERT INTO registration_access(email,created_at) SELECT 'qa'||x||'@example.invalid',1 FROM n");
    assert.throws(()=>db.exec("INSERT INTO registration_access(email,created_at) VALUES('overflow@example.invalid',1)"),/Invitation limit/);
    db.exec("INSERT INTO registration_access(email,created_at) VALUES('qa1@example.invalid',2) ON CONFLICT(email) DO UPDATE SET created_at=excluded.created_at");
    assert.equal(db.prepare('SELECT COUNT(*) AS n FROM registration_access').get().n,200);
  } finally {db.close();}
});
