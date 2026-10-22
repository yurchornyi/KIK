ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'user' CHECK(role IN ('user','admin'));
CREATE TABLE registration_access(email TEXT PRIMARY KEY COLLATE NOCASE, created_by TEXT REFERENCES users(id) ON DELETE SET NULL, created_at INTEGER NOT NULL);
CREATE TABLE profile_settings(profile_id TEXT PRIMARY KEY REFERENCES profiles(id) ON DELETE CASCADE, data TEXT NOT NULL, updated_at INTEGER NOT NULL);
CREATE TABLE admin_events(id TEXT PRIMARY KEY, actor_id TEXT REFERENCES users(id) ON DELETE SET NULL, action TEXT NOT NULL, target TEXT NOT NULL, created_at INTEGER NOT NULL);
CREATE INDEX admin_events_time ON admin_events(created_at DESC);
