-- Whether each account's TV reaches the catalogue mirrors, and how it fails if not; one row per account.
CREATE TABLE IF NOT EXISTS site_reports(user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, status TEXT NOT NULL, host TEXT, detail TEXT, app_version TEXT, updated_at INTEGER NOT NULL);
