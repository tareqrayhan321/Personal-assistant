// Checks, against YOUR Cloudflare account, whether a Workers AI model can be used by the app's Task Mode:
// plain chat, tool calling (the agent needs `tool_calls`) and streaming (the app streams answers).
// It sends a few tiny requests, so it uses a few neurons of the free daily allowance. Nothing is written anywhere.
//
//   PowerShell:  $env:CF_ACCOUNT_ID="..."; $env:CF_API_TOKEN="..."; node scripts/check-workers-ai.mjs "@cf/meta/llama-3.3-70b-instruct-fp8-fast"
//   (CF_AI_BASE overrides the endpoint, used by the tests.)

const account = process.env.CF_ACCOUNT_ID;
const token = process.env.CF_API_TOKEN;
const model = process.argv[2] || '@cf/meta/llama-3.1-8b-instruct';
const base = process.env.CF_AI_BASE || `https://api.cloudflare.com/client/v4/accounts/${account}/ai/v1`;

if (!process.env.CF_AI_BASE && (!account || !token)) {
  console.error('Set CF_ACCOUNT_ID and CF_API_TOKEN (an API token with the "Workers AI" permission).');
  process.exit(2);
}

const tools = [{
  type: 'function',
  function: {
    name: 'create_task',
    description: 'Create a task in the user\'s task list',
    parameters: { type: 'object', properties: { title: { type: 'string' } }, required: ['title'] },
  },
}];

async function post(body) {
  return fetch(`${base}/chat/completions`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', authorization: `Bearer ${token ?? 'test'}` },
    body: JSON.stringify({ model, max_tokens: 128, ...body }),
  });
}

const results = [];
const record = (name, ok, note = '') => { results.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${note ? '  - ' + note : ''}`); };

async function check(name, fn) {
  try {
    const [ok, note] = await fn();
    record(name, ok, note);
  } catch (e) {
    record(name, false, String(e.message).replaceAll(token ?? '\u0000', '***').slice(0, 120));
  }
}

console.log(`Model: ${model}`);

await check('plain chat', async () => {
  const r = await post({ messages: [{ role: 'user', content: 'Reply with the single word: ok' }] });
  if (!r.ok) return [false, `HTTP ${r.status}${r.status === 429 ? ' (daily free allowance used up?)' : ''}`];
  const j = await r.json();
  return [typeof j.choices?.[0]?.message?.content === 'string' && j.choices[0].message.content.length > 0, ''];
});

await check('tool calling (tool_calls in the answer)', async () => {
  const r = await post({
    tools,
    messages: [{ role: 'user', content: 'Add a task called "buy milk" to my list.' }],
  });
  if (!r.ok) return [false, `HTTP ${r.status}`];
  const call = (await r.json()).choices?.[0]?.message?.tool_calls?.[0];
  if (!call) return [false, 'the model answered in text instead of calling the tool'];
  let args;
  try { args = JSON.parse(call.function?.arguments ?? '{}'); } catch { return [false, 'arguments are not valid JSON']; }
  return [call.function?.name === 'create_task' && typeof args.title === 'string', `called ${call.function?.name}`];
});

await check('streaming (server-sent events)', async () => {
  const r = await post({ stream: true, messages: [{ role: 'user', content: 'Count from 1 to 3.' }] });
  if (!r.ok) return [false, `HTTP ${r.status}`];
  const text = await r.text();
  return [/^data: /m.test(text) && text.includes('[DONE]'), ''];
});

const passed = results.filter(Boolean).length;
console.log(`\n${passed}/${results.length} checks passed.`);
console.log(results[1] ? 'Tool calling works: this model can drive Task Mode.' : 'No tool calling: do NOT use this model for Task Mode; chat-only use is still fine.');
process.exit(results.every(Boolean) ? 0 : 1);
