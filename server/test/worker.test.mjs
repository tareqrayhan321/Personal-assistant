import test from 'node:test';
import assert from 'node:assert/strict';
import { handle, safeEqual } from '../worker.js';

const env = (extra = {}) => ({
  CLIENT_TOKENS: 'tok-new, tok-old',
  UPSTREAM_API_KEY: 'sk-secret',
  ALLOWED_MODELS: 'gpt-4o-mini',
  ALLOWED_EMBEDDING_MODELS: 'text-embedding-3-small',
  ...extra,
});

const req = (path, body, { token = 'tok-new', method = 'POST', headers = {} } = {}) =>
  new Request(`https://proxy.test${path}`, {
    method,
    headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}), ...headers },
    body: method === 'GET' ? undefined : typeof body === 'string' ? body : JSON.stringify(body),
  });

function mockFetch(responder) {
  const calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url: String(url), init, body: init?.body ? JSON.parse(init.body) : undefined });
    return responder(String(url), init);
  };
  return calls;
}

const chatBody = { model: 'gpt-4o-mini', messages: [{ role: 'user', content: 'hi' }], stream: true };

test('safeEqual', () => {
  assert.equal(safeEqual('abc', 'abc'), true);
  assert.equal(safeEqual('abc', 'abd'), false);
  assert.equal(safeEqual('abc', 'abcd'), false);
});

test('unknown route is 404, wrong method is 405', async () => {
  assert.equal((await handle(req('/nope', {}), env())).status, 404);
  const r = await handle(req('/v1/chat/completions', null, { method: 'GET' }), env());
  assert.equal(r.status, 405);
});

test('missing or wrong token is 401 and never reaches upstream', async () => {
  const calls = mockFetch(() => new Response('{}'));
  assert.equal((await handle(req('/v1/chat/completions', chatBody, { token: null }), env())).status, 401);
  assert.equal((await handle(req('/v1/chat/completions', chatBody, { token: 'bad' }), env())).status, 401);
  assert.equal(calls.length, 0);
});

test('both tokens work during rotation', async () => {
  mockFetch(() => new Response('data: [DONE]\n\n', { headers: { 'content-type': 'text/event-stream' } }));
  assert.equal((await handle(req('/v1/chat/completions', chatBody, { token: 'tok-new' }), env())).status, 200);
  assert.equal((await handle(req('/v1/chat/completions', chatBody, { token: 'tok-old' }), env())).status, 200);
});

test('chat: forwards with the upstream key, hides the client token, sanitizes the payload', async () => {
  const calls = mockFetch(() => new Response('data: {"x":1}\n\ndata: [DONE]\n\n', { headers: { 'content-type': 'text/event-stream' } }));
  const r = await handle(
    req('/v1/chat/completions', { ...chatBody, n: 8, user: 'x', max_tokens: 99999, temperature: 9, logit_bias: { 1: 1 } }),
    env(),
  );
  assert.equal(r.status, 200);
  assert.equal(r.headers.get('content-type'), 'text/event-stream');
  assert.equal(await r.text(), 'data: {"x":1}\n\ndata: [DONE]\n\n');
  const [c] = calls;
  assert.equal(c.url, 'https://api.openai.com/v1/chat/completions');
  assert.equal(c.init.headers.authorization, 'Bearer sk-secret');
  assert.deepEqual(Object.keys(c.body).sort(), ['max_tokens', 'messages', 'model', 'stream', 'temperature']);
  assert.equal(c.body.max_tokens, 1500);
  assert.equal(c.body.temperature, 1.5);
});

test('chat: tools are passed through, default token cap applies', async () => {
  const calls = mockFetch(() => new Response('{}'));
  await handle(req('/v1/chat/completions', { ...chatBody, tools: [{ type: 'function', function: { name: 'f' } }] }), env({ MAX_OUTPUT_TOKENS: '300' }));
  assert.equal(calls[0].body.tools.length, 1);
  assert.equal(calls[0].body.max_tokens, 300);
});

test('chat: disallowed model and missing messages are 400', async () => {
  const calls = mockFetch(() => new Response('{}'));
  assert.equal((await handle(req('/v1/chat/completions', { ...chatBody, model: 'gpt-5-pro' }), env())).status, 400);
  assert.equal((await handle(req('/v1/chat/completions', { model: 'gpt-4o-mini', messages: [] }), env())).status, 400);
  assert.equal(calls.length, 0);
});

test('upstream errors keep their status', async () => {
  mockFetch(() => new Response('{"error":{"message":"rate"}}', { status: 429, headers: { 'content-type': 'application/json' } }));
  assert.equal((await handle(req('/v1/chat/completions', chatBody), env())).status, 429);
});

test('unreachable upstream is 502', async () => {
  mockFetch(() => { throw new Error('down'); });
  assert.equal((await handle(req('/v1/chat/completions', chatBody), env())).status, 502);
});

test('oversized and malformed bodies', async () => {
  mockFetch(() => new Response('{}'));
  const big = { ...chatBody, messages: [{ role: 'user', content: 'x'.repeat(2000) }] };
  assert.equal((await handle(req('/v1/chat/completions', big), env({ MAX_BODY_BYTES: '1000' }))).status, 413);
  assert.equal((await handle(req('/v1/chat/completions', '{not json'), env())).status, 400);
  assert.equal((await handle(req('/v1/chat/completions', '[1]'), env())).status, 400);
});

test('embeddings: validates input and model, forwards', async () => {
  const calls = mockFetch(() => new Response('{"data":[]}', { headers: { 'content-type': 'application/json' } }));
  const ok = await handle(req('/v1/embeddings', { model: 'text-embedding-3-small', input: ['a', 'b'], dimensions: 8 }), env());
  assert.equal(ok.status, 200);
  assert.deepEqual(calls[0].body, { model: 'text-embedding-3-small', input: ['a', 'b'] });
  assert.equal((await handle(req('/v1/embeddings', { model: 'other', input: ['a'] }), env())).status, 400);
  assert.equal((await handle(req('/v1/embeddings', { model: 'text-embedding-3-small', input: [] }), env())).status, 400);
  assert.equal((await handle(req('/v1/embeddings', { model: 'text-embedding-3-small', input: [''] }), env())).status, 400);
  assert.equal((await handle(req('/v1/embeddings', { model: 'text-embedding-3-small', input: Array(65).fill('a') }), env())).status, 400);
});

test('report: delivered to the webhook', async () => {
  const calls = mockFetch(() => new Response('ok'));
  const r = await handle(
    req('/v1/report', { reason: 'offensive', note: 'bad', response: 'text', model: 'm', app_version: '1.0' }),
    env({ REPORT_WEBHOOK_URL: 'https://hooks.test/x' }),
  );
  assert.equal(r.status, 204);
  assert.equal(calls[0].url, 'https://hooks.test/x');
  assert.equal(calls[0].body.report.reason, 'offensive');
  assert.equal(calls[0].init.headers.authorization, undefined);
});

test('report: logged when no webhook, validated otherwise', async () => {
  const logs = [];
  const orig = console.log;
  console.log = (m) => logs.push(m);
  try {
    assert.equal((await handle(req('/v1/report', { reason: 'other', response: 'r' }), env())).status, 204);
  } finally {
    console.log = orig;
  }
  assert.equal(JSON.parse(logs[0]).type, 'report');
  assert.equal((await handle(req('/v1/report', { reason: 'spam', response: 'r' }), env())).status, 400);
  assert.equal((await handle(req('/v1/report', { reason: 'other' }), env())).status, 400);
});

test('report: failing webhook is 502', async () => {
  mockFetch(() => new Response('no', { status: 500 }));
  const r = await handle(req('/v1/report', { reason: 'other', response: 'r' }), env({ REPORT_WEBHOOK_URL: 'https://hooks.test/x' }));
  assert.equal(r.status, 502);
});

test('rate limiter binding is honoured', async () => {
  mockFetch(() => new Response('{}'));
  const limited = env({ RATE_LIMITER: { limit: async () => ({ success: false }) } });
  const r = await handle(req('/v1/chat/completions', chatBody), limited);
  assert.equal(r.status, 429);
  assert.equal(r.headers.get('retry-after'), '60');
});
