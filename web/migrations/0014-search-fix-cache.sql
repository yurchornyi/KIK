-- Search fixes written by Sentra (kik-search-fix), kept for good for every colo: a query is asked once, not once a month per data centre.
CREATE TABLE IF NOT EXISTS search_fix_cache(query_key TEXT PRIMARY KEY, fixes TEXT NOT NULL, updated_at INTEGER NOT NULL);
