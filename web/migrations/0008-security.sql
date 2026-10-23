-- An allowed email is not proof that the registering person received an invitation.
ALTER TABLE registration_access ADD COLUMN invite_hash TEXT;
ALTER TABLE registration_access ADD COLUMN invite_expires_at INTEGER;
ALTER TABLE pairs ADD COLUMN approved_session_hash TEXT;
ALTER TABLE sessions ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0;
-- Preserve current sessions while establishing a finite lifetime for legacy rows.
UPDATE sessions SET created_at=MAX(0,MIN(CAST(strftime('%s','now') AS INTEGER)*1000,expires_at-7776000000));
CREATE INDEX sessions_expiry ON sessions(expires_at);
-- Enforce limits atomically, including concurrent requests; updates remain possible at capacity.
CREATE TRIGGER records_capacity BEFORE INSERT ON records
WHEN NOT EXISTS (SELECT 1 FROM records WHERE profile_id=NEW.profile_id AND kind=NEW.kind AND item_key=NEW.item_key)
AND (SELECT COUNT(*) FROM records WHERE profile_id=NEW.profile_id)>=5000
BEGIN SELECT RAISE(ABORT,'Profile record limit'); END;
CREATE TRIGGER profiles_capacity BEFORE INSERT ON profiles
WHEN (SELECT COUNT(*) FROM profiles WHERE user_id=NEW.user_id)>=8
BEGIN SELECT RAISE(ABORT,'Account profile limit'); END;
CREATE TRIGGER ad_queue_capacity BEFORE INSERT ON ad_scan_queue
WHEN (SELECT COUNT(*) FROM ad_scan_queue)>=300
BEGIN SELECT RAISE(IGNORE); END;
CREATE TRIGGER registration_capacity BEFORE INSERT ON registration_access
WHEN NOT EXISTS (SELECT 1 FROM registration_access WHERE email=NEW.email COLLATE NOCASE)
AND (SELECT COUNT(*) FROM registration_access)>=200
BEGIN SELECT RAISE(ABORT,'Invitation limit'); END;
