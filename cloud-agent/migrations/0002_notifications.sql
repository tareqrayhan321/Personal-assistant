-- Completion notifications for jobs submitted from Telegram (sent by the cron, bounded to 3 tries per job).
ALTER TABLE jobs ADD COLUMN notified_at INTEGER;
ALTER TABLE jobs ADD COLUMN notify_attempts INTEGER NOT NULL DEFAULT 0;
CREATE INDEX idx_jobs_notify ON jobs (updated_at)
  WHERE notified_at IS NULL AND source = 'telegram' AND status IN ('succeeded', 'failed');
