import test from 'node:test';
import assert from 'node:assert/strict';
import * as jobs from '../src/jobs.js';
import worker from '../src/worker.js';
import { makeDb, makeEnv } from './helpers.mjs';

const make = (db, key = 'key-00000001') =>
  jobs.createJob(db, { type: 'note', payload: { text: 'hi' }, key, source: 'app' }, 1000);

test('claim: pending -> running with lease and attempt count; nothing left to claim afterwards', async () => {
  const db = makeDb();
  const { job } = await make(db);
  const claimed = await jobs.claimNext(db, 2000, 60_000);
  assert.equal(claimed.id, job.id);
  assert.equal(claimed.status, 'running');
  assert.equal(claimed.attempts, 1);
  assert.equal(claimed.lease_until, 62_000);
  assert.equal(await jobs.claimNext(db, 2000, 60_000), null);
});

test('a job cannot be claimed twice (no double execution)', async () => {
  const db = makeDb();
  await make(db);
  const results = await Promise.all([jobs.claimNext(db, 2000, 1000), jobs.claimNext(db, 2000, 1000)]);
  assert.equal(results.filter(Boolean).length, 1);
});

test('complete: running -> succeeded only; cannot complete a pending or cancelled job', async () => {
  const db = makeDb();
  const { job } = await make(db);
  assert.equal(await jobs.completeJob(db, job.id, 'done', 3000), false); // still pending
  await jobs.claimNext(db, 2000, 1000);
  assert.equal(await jobs.completeJob(db, job.id, 'done', 3000), true);
  assert.equal((await jobs.getJob(db, job.id)).status, 'succeeded');
  assert.equal(await jobs.completeJob(db, job.id, 'again', 4000), false);
  assert.equal(await jobs.cancelJob(db, job.id, 5000), false);
});

test('fail: retries until max_attempts, then failed', async () => {
  const db = makeDb();
  const { job } = await make(db);
  for (let attempt = 1; attempt <= 3; attempt++) {
    const c = await jobs.claimNext(db, 2000 + attempt, 1000);
    assert.equal(c.attempts, attempt);
    assert.equal(await jobs.failJob(db, job.id, 'ai_error', 3000), true);
    const row = await jobs.getJob(db, job.id);
    assert.equal(row.status, attempt < 3 ? 'pending' : 'failed');
  }
  assert.equal(await jobs.claimNext(db, 9000, 1000), null);
  assert.equal((await jobs.getJob(db, job.id)).error_code, 'ai_error');
});

test('cancelled jobs are never claimed', async () => {
  const db = makeDb();
  const { job } = await make(db);
  assert.equal(await jobs.cancelJob(db, job.id, 1500), true);
  assert.equal(await jobs.claimNext(db, 2000, 1000), null);
});

test('requeueExpired: expired lease goes back to pending, or fails once attempts are used up', async () => {
  const db = makeDb();
  const { job } = await make(db);
  await jobs.claimNext(db, 2000, 1000); // lease until 3000
  assert.equal(await jobs.requeueExpired(db, 2500), 0); // still leased
  assert.equal(await jobs.requeueExpired(db, 3500), 1);
  assert.equal((await jobs.getJob(db, job.id)).status, 'pending');
  db.raw.prepare("UPDATE jobs SET status='running', attempts=3, lease_until=10 WHERE id=?").run(job.id);
  assert.equal(await jobs.requeueExpired(db, 3500), 1);
  const row = await jobs.getJob(db, job.id);
  assert.equal(row.status, 'failed');
  assert.equal(row.error_code, 'lease_expired');
});

test('database constraints reject bad status, type and duplicate keys', async () => {
  const db = makeDb();
  const { job } = await make(db);
  assert.throws(() => db.raw.prepare("UPDATE jobs SET status='weird' WHERE id=?").run(job.id));
  assert.throws(() => db.raw.prepare("UPDATE jobs SET type='shell' WHERE id=?").run(job.id));
  assert.throws(() =>
    db.raw
      .prepare("INSERT INTO jobs (id,type,status,payload,idempotency_key,source,created_at,updated_at) VALUES ('x','note','pending','{}',?, 'app',1,1)")
      .run('key-00000001'),
  );
});

test('cron handler recovers expired leases and respects CLOUD_ENABLED', async () => {
  const env = makeEnv();
  const { job } = await make(env.DB);
  env.DB.raw.prepare("UPDATE jobs SET status='running', attempts=1, lease_until=1 WHERE id=?").run(job.id);
  const pending = [];
  await worker.scheduled({}, env, { waitUntil: (p) => pending.push(p) });
  await Promise.all(pending);
  assert.equal((await jobs.getJob(env.DB, job.id)).status, 'pending');

  const off = makeEnv({ CLOUD_ENABLED: 'false' });
  const { job: j2 } = await make(off.DB);
  off.DB.raw.prepare("UPDATE jobs SET status='running', attempts=1, lease_until=1 WHERE id=?").run(j2.id);
  await worker.scheduled({}, off, { waitUntil: (p) => pending.push(p) });
  assert.equal((await jobs.getJob(off.DB, j2.id)).status, 'running');
});
