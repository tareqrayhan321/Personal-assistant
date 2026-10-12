// Job storage and state transitions on D1. Every transition is a single guarded UPDATE, so two
// concurrent callers can never both win (the loser sees changes = 0).

export const RETRY_BACKOFF_MS = 120_000; // x attempts so far
export const TYPES = ['ai_prompt', 'note'];
export const STATUSES = ['pending', 'running', 'succeeded', 'failed', 'cancelled'];

const text = (v, max) => (typeof v === 'string' && v.trim().length > 0 && v.trim().length <= max ? v.trim() : null);

function onlyKeys(obj, keys) {
  const extra = Object.keys(obj).find((k) => !keys.includes(k));
  return extra === undefined ? null : `unknown field: ${extra.slice(0, 40)}`;
}

/** Strict validation: unknown fields (e.g. a client-supplied owner id or approval flag) are rejected. */
export function validateJobInput(body) {
  const topError = onlyKeys(body, ['type', 'payload']);
  if (topError) return { error: topError };
  if (!TYPES.includes(body.type)) return { error: 'type must be one of: ' + TYPES.join(', ') };
  const p = body.payload;
  if (p === null || typeof p !== 'object' || Array.isArray(p)) return { error: 'payload must be an object' };
  if (body.type === 'ai_prompt') {
    const e = onlyKeys(p, ['prompt']);
    if (e) return { error: e };
    const prompt = text(p.prompt, 2000);
    if (!prompt) return { error: 'payload.prompt must be 1-2000 characters' };
    return { type: 'ai_prompt', payload: { prompt } };
  }
  const e = onlyKeys(p, ['text']);
  if (e) return { error: e };
  const t = text(p.text, 500);
  if (!t) return { error: 'payload.text must be 1-500 characters' };
  return { type: 'note', payload: { text: t } };
}

export function publicJob(row, withPayload = true) {
  if (!row) return null;
  const job = {
    id: row.id,
    type: row.type,
    status: row.status,
    attempts: row.attempts,
    max_attempts: row.max_attempts,
    error_code: row.error_code ?? null,
    created_at: row.created_at,
    updated_at: row.updated_at,
  };
  if (withPayload) {
    job.payload = JSON.parse(row.payload);
    job.result = row.result ?? null;
  }
  return job;
}

async function audit(db, now, actor, action, jobId) {
  await db.prepare('INSERT INTO audit (at, actor, action, job_id) VALUES (?, ?, ?, ?)').bind(now, actor, action, jobId ?? null).run();
}

export const getJob = (db, id) => db.prepare('SELECT * FROM jobs WHERE id = ?').bind(id).first();

export async function listJobs(db, { status, limit }) {
  const sql = status
    ? 'SELECT * FROM jobs WHERE status = ? ORDER BY created_at DESC LIMIT ?'
    : 'SELECT * FROM jobs ORDER BY created_at DESC LIMIT ?';
  const stmt = status ? db.prepare(sql).bind(status, limit) : db.prepare(sql).bind(limit);
  return (await stmt.all()).results;
}

/** Creates the job, or returns the existing one for a repeated idempotency key. */
export async function createJob(db, { type, payload, key, source }, now) {
  const payloadJson = JSON.stringify(payload);
  const id = crypto.randomUUID();
  const res = await db
    .prepare(
      `INSERT INTO jobs (id, type, status, payload, attempts, max_attempts, idempotency_key, source, created_at, updated_at)
       VALUES (?, ?, 'pending', ?, 0, 3, ?, ?, ?, ?)
       ON CONFLICT (idempotency_key) DO NOTHING`,
    )
    .bind(id, type, payloadJson, key, source, now, now)
    .run();
  if (res.meta.changes === 1) {
    await audit(db, now, source, 'job_created', id);
    return { created: true, job: await getJob(db, id) };
  }
  const existing = await db.prepare('SELECT * FROM jobs WHERE idempotency_key = ?').bind(key).first();
  if (!existing || existing.type !== type || existing.payload !== payloadJson) return { conflict: true };
  return { created: false, job: existing };
}

/** pending|running -> cancelled. */
export async function cancelJob(db, id, now) {
  const res = await db
    .prepare("UPDATE jobs SET status = 'cancelled', lease_until = NULL, updated_at = ? WHERE id = ? AND status IN ('pending', 'running')")
    .bind(now, id)
    .run();
  if (res.meta.changes === 1) await audit(db, now, 'app', 'job_cancelled', id);
  return res.meta.changes === 1;
}

/** pending -> running with a lease. Used by the executor (Phase 5); returns the claimed row or null. */
export function claimNext(db, now, leaseMs) {
  return db
    .prepare(
      `UPDATE jobs SET status = 'running', attempts = attempts + 1, lease_until = ?, updated_at = ?
       WHERE id = (SELECT id FROM jobs WHERE status = 'pending' AND run_after <= ? ORDER BY created_at LIMIT 1) AND status = 'pending'
       RETURNING *`,
    )
    .bind(now + leaseMs, now, now)
    .first();
}

/** running -> succeeded. */
export async function completeJob(db, id, result, now) {
  const res = await db
    .prepare("UPDATE jobs SET status = 'succeeded', result = ?, error_code = NULL, lease_until = NULL, updated_at = ? WHERE id = ? AND status = 'running'")
    .bind(String(result).slice(0, 8000), now, id)
    .run();
  return res.meta.changes === 1;
}

/** running -> pending (retry, after a backoff) while attempts remain, otherwise running -> failed. */
export async function failJob(db, id, errorCode, now) {
  const res = await db
    .prepare(
      `UPDATE jobs SET status = CASE WHEN attempts < max_attempts THEN 'pending' ELSE 'failed' END,
         run_after = CASE WHEN attempts < max_attempts THEN ? + ? * attempts ELSE run_after END,
         error_code = ?, lease_until = NULL, updated_at = ?
       WHERE id = ? AND status = 'running'`,
    )
    .bind(now, RETRY_BACKOFF_MS, String(errorCode).slice(0, 64), now, id)
    .run();
  return res.meta.changes === 1;
}

/** Jobs whose lease ran out (the Worker died mid-run) go back to pending, or fail when out of attempts. */
export async function requeueExpired(db, now) {
  const res = await db
    .prepare(
      `UPDATE jobs SET
         status = CASE WHEN attempts < max_attempts THEN 'pending' ELSE 'failed' END,
         error_code = CASE WHEN attempts < max_attempts THEN error_code ELSE 'lease_expired' END,
         lease_until = NULL, updated_at = ?
       WHERE status = 'running' AND lease_until IS NOT NULL AND lease_until < ?`,
    )
    .bind(now, now)
    .run();
  return res.meta.changes;
}

/** True the first time (source, externalId) is seen, false for a duplicate. */
export async function recordEvent(db, source, externalId, now) {
  const res = await db
    .prepare('INSERT INTO events (source, external_id, received_at) VALUES (?, ?, ?) ON CONFLICT (source, external_id) DO NOTHING')
    .bind(source, externalId, now)
    .run();
  return res.meta.changes === 1;
}

/** Undo recordEvent when handling crashed, so Telegram's retry is processed instead of dropped as a duplicate. */
export async function deleteEvent(db, source, externalId) {
  await db.prepare('DELETE FROM events WHERE source = ? AND external_id = ?').bind(source, externalId).run();
}

export const countActive = async (db) =>
  (await db.prepare("SELECT COUNT(*) AS n FROM jobs WHERE status IN ('pending', 'running')").bind().first()).n;

/** Finds a job by full id or by a 6-36 char hex prefix (what the Telegram list shows). Returns {job} | {ambiguous} | {}. */
export async function findJob(db, ref) {
  const r = String(ref ?? '').toLowerCase();
  if (!/^[0-9a-f-]{6,36}$/.test(r)) return {};
  const rows = (await db.prepare("SELECT * FROM jobs WHERE id LIKE ? ESCAPE '\\' ORDER BY created_at DESC LIMIT 2").bind(r + '%').all()).results;
  if (rows.length > 1) return { ambiguous: true };
  return rows.length ? { job: rows[0] } : {};
}

/** Finished Telegram jobs that still need their notification. Claiming bumps notify_attempts first (at most 3 sends). */
export async function claimNotifications(db, limit) {
  const rows = (
    await db
      .prepare(
        `SELECT * FROM jobs WHERE notified_at IS NULL AND source = 'telegram' AND status IN ('succeeded', 'failed')
           AND notify_attempts < 3 ORDER BY updated_at LIMIT ?`,
      )
      .bind(limit)
      .all()
  ).results;
  const claimed = [];
  for (const row of rows) {
    const res = await db
      .prepare('UPDATE jobs SET notify_attempts = notify_attempts + 1 WHERE id = ? AND notified_at IS NULL AND notify_attempts = ?')
      .bind(row.id, row.notify_attempts)
      .run();
    if (res.meta.changes === 1) claimed.push(row);
  }
  return claimed;
}

export async function markNotified(db, id, now) {
  await db.prepare('UPDATE jobs SET notified_at = ? WHERE id = ? AND notified_at IS NULL').bind(now, id).run();
}

/** running -> failed right away, no retry. For errors a retry cannot fix (quota exhausted). */
export async function failJobFinal(db, id, errorCode, now) {
  const res = await db
    .prepare("UPDATE jobs SET status = 'failed', error_code = ?, lease_until = NULL, updated_at = ? WHERE id = ? AND status = 'running'")
    .bind(String(errorCode).slice(0, 64), now, id)
    .run();
  return res.meta.changes === 1;
}

export const recordAiCall = (db, now, jobId) => audit(db, now, 'worker', 'ai_call', jobId);

export async function countAiCallsSince(db, since) {
  return (await db.prepare("SELECT COUNT(*) AS n FROM audit WHERE action = 'ai_call' AND at >= ?").bind(since).first()).n;
}
