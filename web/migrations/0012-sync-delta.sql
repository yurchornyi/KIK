-- Server time at which a record last changed. Clients send the server_time of their last sync and get only rows written since.
-- Client updated_at cannot serve: an offline device uploads edits whose updated_at is older than other devices' last sync.
ALTER TABLE records ADD COLUMN synced_at INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS records_synced ON records(profile_id,synced_at);
