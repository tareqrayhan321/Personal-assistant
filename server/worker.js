// Minimal AI proxy for PersonalMentor (Cloudflare Worker, also runs on any runtime with the fetch API).
// The provider key lives here as a secret; the app only holds a revocable client token.
//
//   POST /v1/chat/completions   chat (SSE streaming passes straight through)
//   POST /v1/embeddings         embeddings for the knowledge base
//   POST /v1/report             "Report this response" from the app (Google Play AI-content policy)

const JSON_TYPE = { 'content-type': 'application/json' };

const int = (v, d) => (Number.isFinite(parseInt(v, 10)) ? parseInt(v, 10) : d);
const csv = (v, d = []) => (v ? String(v).split(',').map((s) => s.trim()).filter(Boolean) : d);

function error(status, message, extra = {}) {
  return new Response(JSON.stringify({ error: { message } }), { status, headers: { ...JSON_TYPE, ...extra } });
}

/** Constant-time string comparison (length is not secret here, content is). */
export function safeEqual(a, b) {
  const x = new TextEncoder().encode(a);
  const y = new TextEncoder().encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}

function bearer(request) {
  const m = /^Bearer\s+(.+)$/i.exec(request.headers.get('authorization') ?? '');
  return m ? m[1].trim() : null;
}

async function readLimited(request, maxBytes) {
  const declared = int(request.headers.get('content-length'), 0);
  if (declared > maxBytes) return null;
  const buf = await request.arrayBuffer();
  return buf.byteLength > maxBytes ? null : new TextDecoder().decode(buf);
}

async function forward(path, payload, env) {
  let upstream;
  try {
    upstream = await fetch(`${(env.UPSTREAM_BASE || 'https://api.openai.com').replace(/\/+$/, '')}${path}`, {
      method: 'POST',
      headers: { ...JSON_TYPE, authorization: `Bearer ${env.UPSTREAM_API_KEY}` },
      body: JSON.stringify(payload),
    });
  } catch {
    return error(502, 'Upstream unreachable');
  }
  const headers = new Headers();
  headers.set('content-type', upstream.headers.get('content-type') ?? 'application/json');
  headers.set('cache-control', 'no-store');
  headers.set('x-accel-buffering', 'no');
  return new Response(upstream.body, { status: upstream.status, headers });
}

async function chat(body, env) {
  const allowed = csv(env.ALLOWED_MODELS, ['gpt-4o-mini']);
  if (typeof body.model !== 'string' || !allowed.includes(body.model)) return error(400, 'Model not allowed');
  if (!Array.isArray(body.messages) || body.messages.length === 0) return error(400, 'messages is required');

  const cap = int(env.MAX_OUTPUT_TOKENS, 1500);
  const requested = Number.isFinite(body.max_tokens) ? body.max_tokens : cap;
  const payload = {
    model: body.model,
    messages: body.messages,
    stream: body.stream !== false,
    temperature: Math.min(Math.max(Number.isFinite(body.temperature) ? body.temperature : 0.7, 0), 1.5),
    max_tokens: Math.max(1, Math.min(requested, cap)),
  };
  if (Array.isArray(body.tools) && body.tools.length > 0) payload.tools = body.tools;
  if (body.tool_choice !== undefined) payload.tool_choice = body.tool_choice;
  return forward('/v1/chat/completions', payload, env);
}

async function embeddings(body, env) {
  const allowed = csv(env.ALLOWED_EMBEDDING_MODELS, ['text-embedding-3-small']);
  if (typeof body.model !== 'string' || !allowed.includes(body.model)) return error(400, 'Model not allowed');
  const input = typeof body.input === 'string' ? [body.input] : body.input;
  const ok =
    Array.isArray(input) &&
    input.length > 0 &&
    input.length <= int(env.MAX_EMBEDDING_INPUTS, 64) &&
    input.every((s) => typeof s === 'string' && s.length > 0 && s.length <= int(env.MAX_EMBEDDING_CHARS, 8000));
  if (!ok) return error(400, 'input must be 1-64 non-empty strings');
  return forward('/v1/embeddings', { model: body.model, input }, env);
}

const REASONS = ['offensive', 'inaccurate', 'other'];
const str = (v, max) => (typeof v === 'string' ? v.slice(0, max) : '');

async function report(body, env) {
  const record = {
    reason: REASONS.includes(body.reason) ? body.reason : '',
    note: str(body.note, 500),
    response: str(body.response, 8000),
    mode: str(body.mode, 16),
    model: str(body.model, 100),
    app_version: str(body.app_version, 32),
    received_at: new Date().toISOString(),
  };
  if (!record.reason || !record.response) return error(400, 'reason and response are required');

  if (env.REPORT_WEBHOOK_URL) {
    const text = `Report (${record.reason}) model=${record.model} v${record.app_version}\n${record.note}\n---\n${record.response}`;
    let r;
    try {
      r = await fetch(env.REPORT_WEBHOOK_URL, {
        method: 'POST',
        headers: JSON_TYPE,
        body: JSON.stringify({ text: text.slice(0, 1900), content: text.slice(0, 1900), report: record }),
      });
    } catch {
      return error(502, 'Report delivery failed');
    }
    if (!r.ok) return error(502, 'Report delivery failed');
  } else {
    console.log(JSON.stringify({ type: 'report', ...record }));
  }
  return new Response(null, { status: 204 });
}

const ROUTES = {
  '/v1/chat/completions': chat,
  '/v1/embeddings': embeddings,
  '/v1/report': report,
};

export async function handle(request, env) {
  const route = ROUTES[new URL(request.url).pathname];
  if (!route) return error(404, 'Not found');
  if (request.method !== 'POST') return error(405, 'Method not allowed', { allow: 'POST' });

  const token = bearer(request);
  if (!token || !csv(env.CLIENT_TOKENS).some((t) => safeEqual(t, token))) return error(401, 'Unauthorized');

  if (env.RATE_LIMITER) {
    const key = request.headers.get('cf-connecting-ip') ?? token;
    const { success } = await env.RATE_LIMITER.limit({ key });
    if (!success) return error(429, 'Too many requests', { 'retry-after': '60' });
  }

  const raw = await readLimited(request, int(env.MAX_BODY_BYTES, 512 * 1024));
  if (raw === null) return error(413, 'Request too large');
  let body;
  try {
    body = JSON.parse(raw);
  } catch {
    return error(400, 'Invalid JSON');
  }
  if (body === null || typeof body !== 'object' || Array.isArray(body)) return error(400, 'Invalid JSON');
  return route(body, env);
}

export default {
  async fetch(request, env) {
    try {
      return await handle(request, env);
    } catch (e) {
      console.error(e);
      return error(500, 'Internal error');
    }
  },
};
