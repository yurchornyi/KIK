-- Per-profile switch for jumping over ad clips in sponsored voice tracks; the administrator turns it on.
ALTER TABLE profiles ADD COLUMN ad_skip INTEGER NOT NULL DEFAULT 0 CHECK(ad_skip IN (0,1));
