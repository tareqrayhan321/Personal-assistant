import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { execFile } from 'node:child_process';

function mock(handler) {
  return new Promise((resolve) => {
    const server = http.createServer(async (req, res) => {
      let body = '';
      for await (const c of req) body += c;
      handler(JSON.parse(body), res, req);
    });
    server.listen(0, '127.0.0.1', () => resolve(server));
  });
}
const runScript = (port) =>
  new Promise((resolve) =>
    execFile(process.execPath, ['scripts/check-workers-ai.mjs', '@cf/test/model'], { env: { ...process.env, CF_AI_BASE: `http://127.0.0.1:${port}` } }, (err, stdout) => resolve({ code: err?.code ?? 0, stdout })),
  );
const json = (res, obj) => { res.writeHead(200, { 'content-type': 'application/json' }); res.end(JSON.stringify(obj)); };

test('script reports PASS for a model that chats, calls tools and streams', async () => {
  const server = await mock((body, res) => {
    if (body.stream) { res.writeHead(200, { 'content-type': 'text/event-stream' }); res.end('data: {"choices":[]}\n\ndata: [DONE]\n\n'); return; }
    if (body.tools) return json(res, { choices: [{ message: { tool_calls: [{ function: { name: 'create_task', arguments: '{"title":"buy milk"}' } }] } }] });
    json(res, { choices: [{ message: { content: 'ok' } }] });
  });
  const out = await runScript(server.address().port);
  server.close();
  assert.equal(out.code, 0);
  assert.match(out.stdout, /3\/3 checks passed/);
  assert.match(out.stdout, /can drive Task Mode/);
});

test('script FAILS tool calling when the model only answers in text, and says not to use it for Task Mode', async () => {
  const server = await mock((body, res) => {
    if (body.stream) { res.writeHead(200); res.end('data: [DONE]\n\n'); return; }
    json(res, { choices: [{ message: { content: 'Sure, I added it.' } }] });
  });
  const out = await runScript(server.address().port);
  server.close();
  assert.equal(out.code, 1);
  assert.match(out.stdout, /FAIL  tool calling/);
  assert.match(out.stdout, /do NOT use this model for Task Mode/);
});

test('script reports an exhausted allowance clearly', async () => {
  const server = await mock((_b, res) => { res.writeHead(429); res.end('{}'); });
  const out = await runScript(server.address().port);
  server.close();
  assert.equal(out.code, 1);
  assert.match(out.stdout, /HTTP 429 \(daily free allowance used up\?\)/);
});
