-- meta: what reading the answer needs (a ranking: how many candidates it was given).
-- Background Sentra jobs (POST /v1/jobs) KIK waits on: a logline or a ranking the owner's PC writes after a live answer failed.
CREATE TABLE IF NOT EXISTS ai_jobs(job_key TEXT PRIMARY KEY, job_id TEXT NOT NULL, meta TEXT, created_at INTEGER NOT NULL);
