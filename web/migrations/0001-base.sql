PRAGMA foreign_keys=ON;
CREATE TABLE IF NOT EXISTS users(id TEXT PRIMARY KEY, username TEXT NOT NULL UNIQUE, salt TEXT NOT NULL, password_hash TEXT NOT NULL, created_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS sessions(token_hash TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE, expires_at INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS session_user ON sessions(user_id);
CREATE TABLE IF NOT EXISTS profiles(id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE, name TEXT NOT NULL, avatar TEXT NOT NULL DEFAULT 'bear', created_at INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS profile_user ON profiles(user_id);
CREATE TABLE IF NOT EXISTS records(profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE, kind TEXT NOT NULL, item_key TEXT NOT NULL, data TEXT, updated_at INTEGER NOT NULL, PRIMARY KEY(profile_id,kind,item_key));
CREATE TABLE IF NOT EXISTS pairs(device_hash TEXT PRIMARY KEY, user_code TEXT NOT NULL UNIQUE, user_id TEXT, profile_id TEXT, expires_at INTEGER NOT NULL, consumed INTEGER NOT NULL DEFAULT 0);
CREATE INDEX IF NOT EXISTS pair_expiry ON pairs(expires_at);
