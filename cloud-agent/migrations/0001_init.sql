-- Cloud jobs. Status flow: pending -> running -> succeeded | failed | cancelled
-- (a failed attempt goes running -> pending until max_attempts is used up, then failed).
CREATE TABLE jobs (
  id              TEXT PRIMARY KEY,
  type            TEXT NOT NULL CHECK (type IN ('ai_prompt', 'note')),
  status          TEXT NOT NULL CHECK (status IN ('pending', 'running', 'succeeded', 'failed', 'cancelled')),
  payload         TEXT NOT NULL CHECK (length(payload) <= 4096),
  result          TEXT CHECK (result IS NULL OR length(result) <= 8192),
  error_code      TEXT,
  attempts        INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  max_attempts    INTEGER NOT NULL DEFAULT 3 CHECK (max_attempts BETWEEN 1 AND 5),
  idempotency_key TEXT NOT NULL UNIQUE,
  source          TEXT NOT NULL CHECK (source IN ('app', 'telegram')),
  lease_until     INTEGER,
  created_at      INTEGER NOT NULL,
  updated_at      INTEGER NOT NULL
);
CREATE INDEX idx_jobs_status_created ON jobs (status, created_at);
CREATE INDEX idx_jobs_created ON jobs (created_at);

-- Processed webhook updates (replay / duplicate protection).
CREATE TABLE events (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  source      TEXT NOT NULL,
  external_id TEXT NOT NULL,
  received_at INTEGER NOT NULL,
  UNIQUE (source, external_id)
);

-- Audit trail: ids and action names only, never prompt text, results or secrets.
CREATE TABLE audit (
  id     INTEGER PRIMARY KEY AUTOINCREMENT,
  at     INTEGER NOT NULL,
  actor  TEXT NOT NULL,
  action TEXT NOT NULL,
  job_id TEXT
);
