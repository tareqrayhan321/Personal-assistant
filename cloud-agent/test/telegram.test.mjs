import test from 'node:test';
import assert from 'node:assert/strict';
import { handle } from '../src/worker.js';
import { makeEnv, count, WEBHOOK_SECRET, OWNER_ID } from './helpers.mjs';

const update = (over = {}) => ({
  update_id: 1001,
  message: { message_id: 1, text: '/jobs', from: { id: Number(OWNER_ID) }, chat: { id: Number(OWNER_ID), type: 'private' } },
  ...over,
});
const post = (env, body, secret = WEBHOOK_SECRET) =>
  handle(
    new Request('https://agent.test/telegram/webhook', {
      method: 'POST',
      headers: { 'content-type': 'application/json', ...(secret === null ? {} : { 'x-telegram-bot-api-secret-token': secret }) },
      body: typeof body === 'string' ? body : JSON.stringify(body),
    }),
    env,
  );

test('missing or wrong secret token is 401 and nothing is recorded', async () => {
  const env = makeEnv();
  for (const secret of [null, '', 'wrong', WEBHOOK_SECRET + 'x']) assert.equal((await post(env, update(), secret)).status, 401);
  assert.equal(count(env, 'events'), 0);
});

test('GET is 405; disabled switch is 503; unconfigured or short secret fails closed', async () => {
  const env = makeEnv();
  assert.equal((await handle(new Request('https://agent.test/telegram/webhook'), env)).status, 405);
  assert.equal((await post(makeEnv({ TELEGRAM_ENABLED: 'false' }), update())).status, 503);
  assert.equal((await post(makeEnv({ TELEGRAM_ENABLED: undefined }), update())).status, 503); // off by default
  assert.equal((await post(makeEnv({ TELEGRAM_WEBHOOK_SECRET: undefined }), update(), 'x')).status, 503);
  assert.equal((await post(makeEnv({ TELEGRAM_WEBHOOK_SECRET: 'short' }), update(), 'short')).status, 503);
});

test('owner message is recorded once; a replayed update_id is a duplicate', async () => {
  const env = makeEnv();
  const first = await post(env, update());
  assert.equal(first.status, 200);
  assert.equal((await first.json()).duplicate, undefined);
  const again = await post(env, update());
  assert.equal(again.status, 200);
  assert.equal((await again.json()).duplicate, true);
  assert.equal(count(env, 'events'), 1);
  assert.equal((await post(env, update({ update_id: 1002 }))).status, 200);
  assert.equal(count(env, 'events'), 2);
});

test('other users, groups and channels get a silent 200 and leave no trace', async () => {
  const env = makeEnv();
  const stranger = update({ message: { from: { id: 999 }, chat: { id: 999, type: 'private' }, text: '/jobs' } });
  const group = update({ update_id: 2, message: { from: { id: Number(OWNER_ID) }, chat: { id: -100123, type: 'supergroup' }, text: '/jobs' } });
  const ownerInOthersChat = update({ update_id: 3, message: { from: { id: Number(OWNER_ID) }, chat: { id: 999, type: 'private' }, text: 'x' } });
  const noMessage = update({ update_id: 4, message: undefined, channel_post: { chat: { id: 5, type: 'channel' } } });
  for (const u of [stranger, group, ownerInOthersChat, noMessage]) {
    const r = await post(env, u);
    assert.equal(r.status, 200);
    assert.deepEqual(await r.json(), { ok: true });
  }
  assert.equal(count(env, 'events'), 0);
  assert.equal(count(env, 'jobs'), 0);
});

test('owner id not configured: everyone is rejected', async () => {
  for (const OWNER_TELEGRAM_ID of [undefined, '', 'abc', '0x1']) {
    const env = makeEnv({ OWNER_TELEGRAM_ID });
    await post(env, update());
    assert.equal(count(env, 'events'), 0);
  }
});

test('invalid bodies: bad JSON 400, missing update_id 400, oversized 413', async () => {
  const env = makeEnv();
  assert.equal((await post(env, '{bad')).status, 400);
  assert.equal((await post(env, { message: {} })).status, 400);
  assert.equal((await post(env, { update_id: 'abc' })).status, 400);
  assert.equal((await post(env, { update_id: 1, pad: 'x'.repeat(70000) })).status, 413);
});
