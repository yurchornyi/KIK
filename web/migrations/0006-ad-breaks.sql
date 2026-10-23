-- Ad clips spliced into sponsored voice tracks (ColdFilm, 1win), found by the scanner PC; players jump over them.
CREATE TABLE IF NOT EXISTS ad_breaks (
  title_key TEXT NOT NULL,
  voice TEXT NOT NULL,
  season TEXT NOT NULL,
  episode TEXT NOT NULL,
  duration REAL NOT NULL,
  breaks TEXT NOT NULL,
  scanned_at INTEGER NOT NULL,
  PRIMARY KEY (title_key, voice, season, episode)
);
-- Episodes somebody started that the scanner has not seen yet.
CREATE TABLE IF NOT EXISTS ad_scan_queue (
  title_key TEXT NOT NULL,
  voice TEXT NOT NULL,
  season TEXT NOT NULL,
  episode TEXT NOT NULL,
  requested_at INTEGER NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (title_key, voice, season, episode)
);
