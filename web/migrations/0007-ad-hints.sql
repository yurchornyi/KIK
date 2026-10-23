-- Ad clips only the AI saw: players offer a «Пропустити рекламу» button instead of skipping on their own.
ALTER TABLE ad_breaks ADD COLUMN suggested TEXT NOT NULL DEFAULT '[]';
