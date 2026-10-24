-- Sign-ins now end after 90 days without use (was 400): existing ones get the same idle limit from today.
UPDATE sessions SET expires_at=MIN(expires_at,CAST(strftime('%s','now') AS INTEGER)*1000+7776000000);
-- Episodes the scanner gave up on (three attempts) stay as a marker but no longer fill the queue.
DROP TRIGGER IF EXISTS ad_queue_capacity;
CREATE TRIGGER ad_queue_capacity BEFORE INSERT ON ad_scan_queue
WHEN (SELECT COUNT(*) FROM ad_scan_queue WHERE attempts<3)>=300 OR (SELECT COUNT(*) FROM ad_scan_queue)>=5000
BEGIN SELECT RAISE(IGNORE); END;
