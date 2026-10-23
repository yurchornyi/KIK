-- Short Ukrainian synopses that fit three lines on the TV title page, one per catalogue title.
CREATE TABLE IF NOT EXISTS synopsis_cache (
  title_key TEXT PRIMARY KEY,
  summary TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
