-- Lets the Worker count today's AI calls cheaply (daily cap protects the free Workers AI allowance).
CREATE INDEX idx_audit_action_at ON audit (action, at);

-- A failed attempt is not retried before this time (backoff), so one bad provider answer cannot burn the allowance.
ALTER TABLE jobs ADD COLUMN run_after INTEGER NOT NULL DEFAULT 0;
