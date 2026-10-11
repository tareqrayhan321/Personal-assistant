import test from 'node:test';
import assert from 'node:assert/strict';
import worker, { handle } from '../src/worker.js';
import { makeEnv, req, count, OWNER_TOKEN } from './helpers.mjs';

const KEY = 'key-0001-abcdef';
const good = { type: 'ai_prompt', payload: { prompt: 'Summarise my day' } };
const create = (env, over = {}) => handle(req('/v1/jobs', { method: 'POST', key: KEY, body: good, ...over }), env);

test('health is public and GET only', async () => {
  const env = makeEnv();
  assert.equal((await handle(req('/health', { token: null }), env)).status, 200);
  assert.equal((await handle(req('/health', { method: 'POST', token: null }), env)).status, 405);
});

test('unknown route is 404', async () => {
  assert.equal((await handle(req('/nope'), makeEnv())).status, 404);
});

test('private routes reject missing, wrong and malformed tokens', async () => {
  const env = makeEnv();
  for (const token of [null, 'wrong', OWNER_TOKEN + 'x', '']) {
    const r = await handle(req('/v1/jobs', { token }), env);
    assert.equal(r.status, 401);
  }
  const basic = new Request('https://agent.test/v1/jobs', { headers: { authorization: `Basic ${OWNER_TOKEN}` } });
  assert.equal((await handle(basic, env)).status, 401);
  assert.equal(count(env, 'jobs'), 0);
});

test('fails closed when the owner token is missing or too short', async () => {
  for (const OWNER_TOKEN of [undefined, '', 'short']) {
    const env = makeEnv({ OWNER_TOKEN });
    assert.equal((await handle(req('/v1/jobs', { token: 'short' }), env)).status, 503);
  }
});

test('CLOUD_ENABLED=false switches the job API off', async () => {
  const env = makeEnv({ CLOUD_ENABLED: 'false' });
  const r = await handle(req('/v1/jobs'), env);
  assert.equal(r.status, 503);
  assert.equal((await r.json()).error.code, 'cloud_disabled');
});

test('create returns 201 pending job', async () => {
  const env = makeEnv();
  const r = await create(env);
  assert.equal(r.status, 201);
  const { job, duplicate } = await r.json();
  assert.equal(job.status, 'pending');
  assert.equal(job.attempts, 0);
  assert.deepEqual(job.payload, good.payload);
  assert.equal(duplicate, false);
});

test('same Idempotency-Key + same content returns the same job and creates no second row', async () => {
  const env = makeEnv();
  const a = await (await create(env)).json();
  const second = await create(env);
  assert.equal(second.status, 200);
  const b = await second.json();
  assert.equal(b.job.id, a.job.id);
  assert.equal(b.duplicate, true);
  assert.equal(count(env, 'jobs'), 1);
});

test('same key with different content is 409', async () => {
  const env = makeEnv();
  await create(env);
  const r = await create(env, { body: { type: 'ai_prompt', payload: { prompt: 'Something else' } } });
  assert.equal(r.status, 409);
  assert.equal(count(env, 'jobs'), 1);
});

test('invalid inputs are rejected with 400 and nothing is stored', async () => {
  const env = makeEnv();
  const bad = [
    { type: 'shell', payload: { command: 'rm -rf /' } },
    { type: 'ai_prompt' },
    { type: 'ai_prompt', payload: [] },
    { type: 'ai_prompt', payload: { prompt: '' } },
    { type: 'ai_prompt', payload: { prompt: '   ' } },
    { type: 'ai_prompt', payload: { prompt: 'x'.repeat(2001) } },
    { type: 'ai_prompt', payload: { prompt: 5 } },
    { type: 'note', payload: { text: 'x'.repeat(501) } },
    { type: 'ai_prompt', payload: { prompt: 'hi', model: 'big' } },
  ];
  for (const body of bad) assert.equal((await create(env, { body })).status, 400, JSON.stringify(body).slice(0, 60));
  assert.equal(count(env, 'jobs'), 0);
});

test('client-supplied owner ids, approval flags and sources are rejected, never trusted', async () => {
  const env = makeEnv();
  for (const extra of [{ owner_id: '1' }, { approved: true }, { source: 'telegram' }, { status: 'succeeded' }]) {
    const r = await create(env, { body: { ...good, ...extra } });
    assert.equal(r.status, 400);
  }
  assert.equal(count(env, 'jobs'), 0);
});

test('missing or malformed Idempotency-Key is 400', async () => {
  const env = makeEnv();
  for (const key of [undefined, 'short', 'has space in it', 'x'.repeat(65)]) {
    const r = await handle(req('/v1/jobs', { method: 'POST', key, body: good }), env);
    assert.equal(r.status, 400);
  }
});

test('bad JSON is 400, oversized body is 413', async () => {
  const env = makeEnv();
  assert.equal((await create(env, { body: '{nope' })).status, 400);
  assert.equal((await create(env, { body: '[1,2]' })).status, 400);
  const big = JSON.stringify({ type: 'note', payload: { text: 'x'.repeat(20000) } });
  assert.equal((await create(env, { body: big })).status, 413);
  const lying = req('/v1/jobs', { method: 'POST', key: KEY, body: good, headers: { 'content-length': '999999' } });
  assert.equal((await handle(lying, env)).status, 413);
});

test('get job: found, unknown id 404, malformed id 400', async () => {
  const env = makeEnv();
  const { job } = await (await create(env)).json();
  const ok = await handle(req(`/v1/jobs/${job.id}`), env);
  assert.equal(ok.status, 200);
  assert.equal((await ok.json()).job.id, job.id);
  assert.equal((await handle(req('/v1/jobs/11111111-1111-1111-1111-111111111111'), env)).status, 404);
  assert.equal((await handle(req('/v1/jobs/not-a-uuid'), env)).status, 400);
  assert.equal((await handle(req(`/v1/jobs/${job.id}`, { token: 'bad' }), env)).status, 401);
});

test('list validates filters and limits, and hides payloads', async () => {
  const env = makeEnv();
  await create(env);
  const r = await handle(req('/v1/jobs?status=pending&limit=5'), env);
  assert.equal(r.status, 200);
  const { jobs } = await r.json();
  assert.equal(jobs.length, 1);
  assert.equal('payload' in jobs[0], false);
  assert.equal((await handle(req('/v1/jobs?status=hacked'), env)).status, 400);
  for (const limit of ['0', '51', 'abc', '1.5']) assert.equal((await handle(req(`/v1/jobs?limit=${limit}`), env)).status, 400);
});

test('cancel: pending ok, then 409; finished job cannot be cancelled', async () => {
  const env = makeEnv();
  const { job } = await (await create(env)).json();
  const c = await handle(req(`/v1/jobs/${job.id}/cancel`, { method: 'POST' }), env);
  assert.equal(c.status, 200);
  assert.equal((await c.json()).job.status, 'cancelled');
  assert.equal((await handle(req(`/v1/jobs/${job.id}/cancel`, { method: 'POST' }), env)).status, 409);
  assert.equal((await handle(req(`/v1/jobs/${job.id}/cancel`, { method: 'GET' }), env)).status, 405);
});

test('rate limiter binding is honoured, and runs before auth', async () => {
  const env = makeEnv({ RATE_LIMITER: { limit: async () => ({ success: false }) } });
  const r = await handle(req('/v1/jobs', { token: 'wrong' }), env);
  assert.equal(r.status, 429);
  assert.equal(r.headers.get('retry-after'), '60');
});

test('audit records ids and actions only, never content', async () => {
  const env = makeEnv();
  await create(env);
  const rows = env.DB.raw.prepare('SELECT * FROM audit').all();
  assert.equal(rows.length, 1);
  assert.equal(rows[0].action, 'job_created');
  assert.ok(!JSON.stringify(rows).includes('Summarise'));
});

test('unexpected errors give a generic 500 without internals; quota errors give 503', async () => {
  const quiet = console.error;
  console.error = () => {};
  try {
    const boom = makeEnv({ DB: { prepare: () => { throw new Error('secret SQL detail'); } } });
    const r = await worker.fetch(req('/v1/jobs'), boom);
    assert.equal(r.status, 500);
    assert.ok(!(await r.text()).includes('secret'));
    const quota = makeEnv({ DB: { prepare: () => { throw new Error('D1 DB reached daily limit exceeded'); } } });
    const q = await worker.fetch(req('/v1/jobs'), quota);
    assert.equal(q.status, 503);
    assert.equal((await q.json()).error.code, 'quota_exceeded');
  } finally {
    console.error = quiet;
  }
});
