-- Intro and closing credits of series episodes, found by the scanner PC (episode-marks.mjs): players offer
-- «Пропустити заставку» and «Наступна серія». intro is a JSON list of [start, end] seconds; credits is where they start.
CREATE TABLE IF NOT EXISTS episode_marks (
  title_key TEXT NOT NULL,
  voice TEXT NOT NULL,
  season TEXT NOT NULL,
  episode TEXT NOT NULL,
  duration REAL NOT NULL,
  intro TEXT NOT NULL DEFAULT '[]',
  credits REAL,
  scanned_at INTEGER NOT NULL,
  PRIMARY KEY (title_key, voice, season, episode)
);
-- Episodes a player started (or is about to reach) that the scanner has not looked at yet.
CREATE TABLE IF NOT EXISTS mark_scan_queue (
  title_key TEXT NOT NULL,
  voice TEXT NOT NULL,
  season TEXT NOT NULL,
  episode TEXT NOT NULL,
  requested_at INTEGER NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (title_key, voice, season, episode)
);
CREATE TRIGGER IF NOT EXISTS mark_queue_capacity BEFORE INSERT ON mark_scan_queue
WHEN (SELECT COUNT(*) FROM mark_scan_queue WHERE attempts<3)>=300 OR (SELECT COUNT(*) FROM mark_scan_queue)>=5000
BEGIN SELECT RAISE(IGNORE); END;
