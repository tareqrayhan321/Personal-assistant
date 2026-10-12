import test from 'node:test';
import assert from 'node:assert/strict';
import * as jobs from '../src/jobs.js';
import { runPending } from '../src/executor.js';
import { runAi, extractText } from '../src/ai.js';
import worker, { handle } from '../src/worker.js';
import { makeEnv, req, count, WEBHOOK_SECRET, OWNER_ID } from './helpers.mjs';

const aiEnv = (run, extra = {}) => makeEnv({ AI_ENABLED: 'true', AI_TIMEOUT_MS: '200', AI: { run }, TELEGRAM_BOT_TOKEN: 'T0KEN_1234567890_abcdefghij', ...extra });
const add = (env, key, source = 'app', type = 'ai_prompt') =>
  jobs.createJob(env.DB, { type, payload: type === 'note' ? { text: 'n' } : { prompt: 'hello' }, key, source }, 1).then((r) => r.job);
const row = (env, id) => env.DB.raw.prepare('SELECT * FROM jobs WHERE id=?').get(id);
const quiet = (fn) => async () => {
  const keep = console.error;
  console.error = () => {};
  try { await fn(); } finally { console.error = keep; }
};

test('extractText understands both Workers AI response shapes', () => {
  assert.equal(extractText({ response: ' hi ' }), 'hi');
  assert.equal(extractText({ choices: [{ message: { content: 'yo' } }] }), 'yo');
  assert.equal(extractText({}), '');
  assert.equal(extractText(null), '');
});

test('AI off (the default): nothing is claimed and the AI binding is never called', async () => {
  let calls = 0;
  const env = aiEnv(async () => { calls++; return { response: 'x' }; }, { AI_ENABLED: undefined });
  const j = await add(env, 'key-aaaaaaaa');
  assert.deepEqual(await runPending(env, 1000), { ran: 0, stopped: 'disabled' });
  assert.equal(calls, 0);
  assert.equal(row(env, j.id).status, 'pending');
});

test('success: job succeeds with the answer and one ai_call is audited', async () => {
  const seen = [];
  const env = aiEnv(async (model, input) => { seen.push({ model, input }); return { response: 'Hello there' }; });
  const j = await add(env, 'key-aaaaaaaa');
  assert.equal((await runPending(env, 1000)).ran, 1);
  const r = row(env, j.id);
  assert.equal(r.status, 'succeeded');
  assert.equal(r.result, 'Hello there');
  assert.equal(seen[0].model, '@cf/meta/llama-3.1-8b-instruct');
  assert.equal(seen[0].input.max_tokens, 512);
  assert.equal(env.DB.raw.prepare("SELECT COUNT(*) n FROM audit WHERE action='ai_call'").get().n, 1);
});

test('transient failures retry up to max_attempts, then fail with a short code (no provider text stored)', quiet(async () => {
  const env = aiEnv(async () => { throw new Error('internal provider detail 500'); });
  const j = await add(env, 'key-aaaaaaaa');
  for (let i = 0; i < 3; i++) assert.equal((await runPending(env, 1000 + i * 3_600_000)).ran, 1); // one attempt per run, an hour apart
  const r = row(env, j.id);
  assert.equal(r.status, 'failed');
  assert.equal(r.attempts, 3);
  assert.equal(r.error_code, 'ai_error');
  assert.ok(!JSON.stringify(r).includes('provider detail'));
}));

test('quota exhausted: fails at once without retries and stops the run; later jobs stay pending', async () => {
  let calls = 0;
  const env = aiEnv(async () => { calls++; throw new Error('4006: you have used up your daily free allocation of 10,000 neurons'); });
  const a = await add(env, 'key-aaaaaaaa');
  const b = await add(env, 'key-bbbbbbbb');
  assert.deepEqual(await runPending(env, 1000), { ran: 1, stopped: 'quota' });
  assert.equal(calls, 1);
  assert.equal(row(env, a.id).status, 'failed');
  assert.equal(row(env, a.id).error_code, 'quota_exceeded');
  assert.equal(row(env, a.id).attempts, 1);
  assert.equal(row(env, b.id).status, 'pending');
});

test('there is no paid fallback: only the AI binding is used, never fetch', async () => {
  const realFetch = globalThis.fetch;
  let fetched = 0;
  globalThis.fetch = async () => { fetched++; throw new Error('no network allowed'); };
  try {
    const env = aiEnv(async () => { throw new Error('quota exceeded'); });
    await add(env, 'key-aaaaaaaa');
    await runPending(env, 1000);
    assert.equal(fetched, 0);
  } finally {
    globalThis.fetch = realFetch;
  }
});

test('a failed job is not hammered again within the same run or before its backoff', quiet(async () => {
  let calls = 0;
  const env = aiEnv(async () => { calls++; throw new Error('boom'); }, { AI_MAX_JOBS_PER_RUN: '5' });
  await add(env, 'key-aaaaaaaa');
  assert.equal((await runPending(env, 1000)).ran, 1);
  assert.equal((await runPending(env, 1000 + 60_000)).ran, 0); // inside the backoff
  assert.equal(calls, 1);
}));

test('a hanging AI call times out as ai_timeout', quiet(async () => {
  const env = aiEnv(() => new Promise(() => {}), { AI_TIMEOUT_MS: '100' });
  const j = await add(env, 'key-aaaaaaaa');
  await runPending(env, 1000);
  const r = row(env, j.id);
  assert.equal(r.status, 'pending'); // will retry
  assert.equal(r.error_code, 'ai_timeout');
}));

test('an empty answer is ai_empty, not a blank success', quiet(async () => {
  const env = aiEnv(async () => ({ response: '   ' }));
  const j = await add(env, 'key-aaaaaaaa');
  await runPending(env, 1000);
  assert.equal(row(env, j.id).error_code, 'ai_empty');
  assert.notEqual(row(env, j.id).status, 'succeeded');
}));

test('daily call cap stops the run before the free allowance can be burnt', async () => {
  let calls = 0;
  const env = aiEnv(async () => { calls++; return { response: 'ok' }; }, { AI_DAILY_CALL_CAP: '2', AI_MAX_JOBS_PER_RUN: '5' });
  for (let i = 0; i < 4; i++) await add(env, `key-0000000${i}`);
  const out = await runPending(env, 5 * 86_400_000 + 1000);
  assert.deepEqual(out, { ran: 2, stopped: 'daily_cap' });
  assert.equal(calls, 2);
  // the next UTC day starts a fresh count
  assert.equal((await runPending(env, 6 * 86_400_000 + 1000)).ran, 2);
});

test('per-run limit and notes', async () => {
  const env = aiEnv(async () => ({ response: 'ok' }), { AI_MAX_JOBS_PER_RUN: '2' });
  for (let i = 0; i < 3; i++) await add(env, `key-0000000${i}`);
  assert.equal((await runPending(env, 1000)).ran, 2);
  const env2 = aiEnv(async () => { throw new Error('must not be called'); });
  const n = await add(env2, 'key-nnnnnnnn', 'app', 'note');
  await runPending(env2, 1000);
  assert.equal(row(env2, n.id).status, 'succeeded');
});

test('a job cancelled while the AI call is in flight stays cancelled', async () => {
  let env;
  env = aiEnv(async () => {
    env.DB.raw.prepare("UPDATE jobs SET status='cancelled' WHERE status='running'").run();
    return { response: 'late answer' };
  });
  const j = await add(env, 'key-aaaaaaaa');
  await runPending(env, 1000);
  assert.equal(row(env, j.id).status, 'cancelled');
  assert.equal(row(env, j.id).result, null);
});

test('creating a job runs it in the background; the request itself returns the pending job', async () => {
  const env = aiEnv(async () => ({ response: 'done fast' }));
  const pending = [];
  const r = await handle(req('/v1/jobs', { method: 'POST', key: 'key-create-01', body: { type: 'ai_prompt', payload: { prompt: 'hi' } } }), env, Date.now(), { waitUntil: (p) => pending.push(p) });
  assert.equal(r.status, 201);
  assert.equal((await r.json()).job.status, 'pending');
  await Promise.all(pending);
  assert.equal(env.DB.raw.prepare('SELECT status FROM jobs').get().status, 'succeeded');
  // a duplicate request does not schedule another run
  pending.length = 0;
  await handle(req('/v1/jobs', { method: 'POST', key: 'key-create-01', body: { type: 'ai_prompt', payload: { prompt: 'hi' } } }), env, Date.now(), { waitUntil: (p) => pending.push(p) });
  assert.equal(pending.length, 0);
});

test('telegram /new runs the job and the owner is notified straight away', async () => {
  const sent = [];
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (url, init) => { sent.push({ url, body: JSON.parse(init.body) }); return { ok: true, status: 200 }; };
  try {
    const env = aiEnv(async () => ({ response: 'the answer' }));
    const pending = [];
    const update = { update_id: 4242, message: { text: '/new what is 2+2', from: { id: Number(OWNER_ID) }, chat: { id: Number(OWNER_ID), type: 'private' } } };
    const r = await handle(new Request('https://agent.test/telegram/webhook', { method: 'POST', headers: { 'x-telegram-bot-api-secret-token': WEBHOOK_SECRET }, body: JSON.stringify(update) }), env, Date.now(), { waitUntil: (p) => pending.push(p) });
    assert.match((await r.json()).text, /^Created/);
    await Promise.all(pending);
    assert.equal(sent.length, 1);
    assert.equal(sent[0].body.chat_id, Number(OWNER_ID));
    assert.match(sent[0].body.text, /Job finished[\s\S]*the answer/);
  } finally {
    globalThis.fetch = realFetch;
  }
});

test('a /jobs command does not start any AI work', async () => {
  const env = aiEnv(async () => { throw new Error('must not run'); });
  await add(env, 'key-aaaaaaaa');
  const pending = [];
  const update = { update_id: 77, message: { text: '/jobs', from: { id: Number(OWNER_ID) }, chat: { id: Number(OWNER_ID), type: 'private' } } };
  await handle(new Request('https://agent.test/telegram/webhook', { method: 'POST', headers: { 'x-telegram-bot-api-secret-token': WEBHOOK_SECRET }, body: JSON.stringify(update) }), env, Date.now(), { waitUntil: (p) => pending.push(p) });
  assert.equal(pending.length, 0);
});

test('cron runs pending jobs and recovers expired leases first', async () => {
  const env = aiEnv(async () => ({ response: 'cron answer' }));
  const j = await add(env, 'key-aaaaaaaa');
  env.DB.raw.prepare("UPDATE jobs SET status='running', attempts=1, lease_until=1 WHERE id=?").run(j.id);
  const pending = [];
  await worker.scheduled({}, env, { waitUntil: (p) => pending.push(p) });
  await Promise.all(pending);
  assert.equal(row(env, j.id).status, 'succeeded');
});

test('runAi without a binding is a provider error, and CLOUD_ENABLED=false stops execution', async () => {
  await assert.rejects(runAi({}, 'x'), (e) => e.kind === 'provider');
  const env = aiEnv(async () => ({ response: 'x' }), { CLOUD_ENABLED: 'false' });
  await add(env, 'key-aaaaaaaa');
  assert.equal((await runPending(env, 1000)).stopped, 'disabled');
  assert.equal(count(env, 'jobs'), 1);
});
