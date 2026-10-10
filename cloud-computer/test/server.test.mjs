import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { createServer } = require('../server.js');

const TOKEN = 'test-token-0123456789';
const workdir = fs.mkdtempSync(path.join(os.tmpdir(), 'cc-work-'));
const outside = fs.mkdtempSync(path.join(os.tmpdir(), 'cc-outside-'));
fs.writeFileSync(path.join(outside, 'secret.txt'), 'secret');
fs.symlinkSync(outside, path.join(workdir, 'link'));

const server = createServer({ token: TOKEN, workdir, maxOutput: 50 });
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const base = `http://127.0.0.1:${server.address().port}`;

async function call(route, body, token = TOKEN) {
  const res = await fetch(base + route, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return { status: res.status, json: await res.json() };
}

test.after(() => server.close());

test('rejects a missing or wrong token', async () => {
  assert.equal((await call('/health', undefined, 'nope')).status, 401);
  assert.equal((await call('/health', undefined, '')).status, 401);
});

test('refuses to start with a short token', () => {
  assert.throws(() => createServer({ token: 'short', workdir }));
});

test('health works with the token', async () => {
  const r = await call('/health');
  assert.equal(r.status, 200);
  assert.equal(r.json.ok, true);
});

test('exec runs in the workspace and reports the exit code', async () => {
  const r = await call('/exec', { command: 'echo hello && pwd; exit 3' });
  assert.equal(r.status, 200);
  assert.match(r.json.stdout, /hello/);
  assert.equal(r.json.exit_code, 3);
  assert.equal(fs.realpathSync(r.json.stdout.trim().split('\n')[1]), fs.realpathSync(workdir));
});

test('exec output is capped', async () => {
  const r = await call('/exec', { command: 'yes x | head -c 5000' });
  assert.equal(r.json.truncated, true);
  assert.ok(r.json.stdout.length <= 50);
});

test('exec times out and kills the command', async () => {
  const started = Date.now();
  const r = await call('/exec', { command: 'sleep 20', timeout_sec: 1 });
  assert.equal(r.json.timed_out, true);
  assert.ok(Date.now() - started < 8000);
});

test('write, read and list files', async () => {
  assert.equal((await call('/write_file', { path: 'notes/a.txt', content: 'hi there' })).json.ok, true);
  assert.equal((await call('/read_file', { path: 'notes/a.txt' })).json.content, 'hi there');
  const list = await call('/list_files', { path: 'notes' });
  assert.deepEqual(list.json.entries, [{ name: 'a.txt', type: 'file' }]);
});

test('paths outside the workspace are rejected', async () => {
  assert.equal((await call('/read_file', { path: '../../etc/passwd' })).status, 400);
  assert.equal((await call('/read_file', { path: '/etc/passwd' })).status, 400);
  assert.equal((await call('/write_file', { path: '../escape.txt', content: 'x' })).status, 400);
});

test('symlinks that leave the workspace are rejected', async () => {
  assert.equal((await call('/read_file', { path: 'link/secret.txt' })).status, 400);
  assert.equal((await call('/write_file', { path: 'link/new.txt', content: 'x' })).status, 400);
  assert.equal(fs.existsSync(path.join(outside, 'new.txt')), false);
});

test('bad requests get clear errors', async () => {
  assert.equal((await call('/exec', {})).status, 400);
  assert.equal((await call('/nothing', {})).status, 404);
});
