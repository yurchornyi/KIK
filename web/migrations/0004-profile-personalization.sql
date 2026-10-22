ALTER TABLE profiles ADD COLUMN enhanced_recs INTEGER NOT NULL DEFAULT 0 CHECK(enhanced_recs IN (0,1));
CREATE TABLE public_translations(source_hash TEXT PRIMARY KEY, translated TEXT NOT NULL, updated_at INTEGER NOT NULL);
CREATE TABLE recommendation_cache(profile_id TEXT PRIMARY KEY REFERENCES profiles(id) ON DELETE CASCADE,fingerprint TEXT NOT NULL,ranking TEXT NOT NULL,mode TEXT NOT NULL,updated_at INTEGER NOT NULL);
CREATE TABLE ai_daily_budget(day TEXT PRIMARY KEY,calls INTEGER NOT NULL DEFAULT 0);
