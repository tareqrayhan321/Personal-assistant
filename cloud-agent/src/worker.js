// PersonalMentor Cloud Agent (Cloudflare Worker + D1). Event-driven only: requests, a webhook and a cron.
// No background loop. Private routes need the owner token; the Telegram webhook needs Telegram's secret token.
//
//   GET  /health
//   POST /v1/jobs               create (Idempotency-Key header required)
//   GET  /v1/jobs               list (?status=&limit=)
//   GET  /v1/jobs/:id           status
//   POST /v1/jobs/:id/cancel
//   POST /telegram/webhook      Telegram updates (owner-only commands: /new /jobs /failed /job /cancel)

import * as jobs from './jobs.js';
import { runCommand, replyTo, notifyFinished, parseCommand } from './telegram.js';
import { runPending } from './executor.js';

const HEADERS = { 'content-type': 'application/json', 'cache-control': 'no-store' };
const MAX_BODY = 16 * 1024;
const MAX_WEBHOOK_BODY = 64 * 1024;
const ID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const KEY_RE = /^[A-Za-z0-9_-]{8,64}$/;
const MIN_OWNER_TOKEN = 32;
const MIN_WEBHOOK_SECRET = 16;

/** After a job is created, try to run it right away (in the background of this request). The cron is the safety net. */
function kick(env, deps) {
  if (typeof deps.waitUntil !== 'function') return;
  deps.waitUntil(runPending(env, Date.now(), { max: 1 }).catch((e) => console.error('run failed', e?.name ?? 'Error')));
}

const json = (status, body, extra = {}) => new Response(JSON.stringify(body), { status, headers: { ...HEADERS, ...extra } });
const fail = (status, code, message, extra) => json(status, { error: { code, message } }, extra);
const flag = (v, dflt) => (v === undefined || v === '' ? dflt : String(v).toLowerCase() === 'true');

async function digest(s) {
  return new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s)));
}

/** Constant-time comparison (compares SHA-256 digests, so length does not leak either). */
export async function safeEqual(a, b) {
  const [x, y] = await Promise.all([digest(a), digest(b)]);
  let diff = 0;
  for (let i = 0; i < x.length; i++) diff |= x[i] ^ y[i];
  return diff === 0;
}

function bearer(request) {
  const m = /^Bearer\s+(.+)$/i.exec(request.headers.get('authorization') ?? '');
  return m ? m[1].trim() : '';
}

async function readLimited(request, max) {
  const declared = parseInt(request.headers.get('content-length') ?? '0', 10);
  if (Number.isFinite(declared) && declared > max) return null;
  const buf = await request.arrayBuffer();
  return buf.byteLength > max ? null : new TextDecoder().decode(buf);
}

async function readJson(request, max) {
  const raw = await readLimited(request, max);
  if (raw === null) return { response: fail(413, 'too_large', 'Request too large') };
  try {
    const body = JSON.parse(raw);
    if (body === null || typeof body !== 'object' || Array.isArray(body)) throw new Error('shape');
    return { body };
  } catch {
    return { response: fail(400, 'invalid_json', 'Body must be a JSON object') };
  }
}

async function jobsRoute(request, env, url, now, deps) {
  const parts = url.pathname.split('/').filter(Boolean); // ['v1','jobs',id?,'cancel'?]
  const id = parts[2];
  const action = parts[3];
  if (parts.length > 4 || (action !== undefined && action !== 'cancel')) return fail(404, 'not_found', 'Not found');
  if (id !== undefined && !ID_RE.test(id)) return fail(400, 'invalid_id', 'Invalid job id');

  // POST /v1/jobs
  if (id === undefined && request.method === 'POST') {
    const key = request.headers.get('idempotency-key') ?? '';
    if (!KEY_RE.test(key)) return fail(400, 'invalid_idempotency_key', 'Idempotency-Key must be 8-64 chars (A-Z a-z 0-9 _ -)');
    const parsed = await readJson(request, MAX_BODY);
    if (parsed.response) return parsed.response;
    const input = jobs.validateJobInput(parsed.body);
    if (input.error) return fail(400, 'invalid_input', input.error);
    // The source is always decided here; nothing about identity or approvals is read from the client.
    const out = await jobs.createJob(env.DB, { ...input, key, source: 'app' }, now);
    if (out.conflict) return fail(409, 'idempotency_conflict', 'Idempotency-Key was already used with different content');
    if (out.created) kick(env, deps);
    return json(out.created ? 201 : 200, { job: jobs.publicJob(out.job), duplicate: !out.created });
  }

  // GET /v1/jobs
  if (id === undefined && request.method === 'GET') {
    const status = url.searchParams.get('status');
    if (status !== null && !jobs.STATUSES.includes(status)) return fail(400, 'invalid_status', 'Unknown status');
    const rawLimit = url.searchParams.get('limit');
    const limit = rawLimit === null ? 20 : Number(rawLimit);
    if (!Number.isInteger(limit) || limit < 1 || limit > 50) return fail(400, 'invalid_limit', 'limit must be 1-50');
    const rows = await jobs.listJobs(env.DB, { status: status ?? undefined, limit });
    return json(200, { jobs: rows.map((r) => jobs.publicJob(r, false)) });
  }

  // GET /v1/jobs/:id
  if (id !== undefined && action === undefined && request.method === 'GET') {
    const row = await jobs.getJob(env.DB, id);
    return row ? json(200, { job: jobs.publicJob(row) }) : fail(404, 'not_found', 'Job not found');
  }

  // POST /v1/jobs/:id/cancel
  if (id !== undefined && action === 'cancel' && request.method === 'POST') {
    const row = await jobs.getJob(env.DB, id);
    if (!row) return fail(404, 'not_found', 'Job not found');
    if (!(await jobs.cancelJob(env.DB, id, now))) return fail(409, 'not_cancellable', `Job is already ${row.status}`);
    return json(200, { job: jobs.publicJob(await jobs.getJob(env.DB, id)) });
  }

  return fail(405, 'method_not_allowed', 'Method not allowed');
}

async function telegramRoute(request, env, now, deps) {
  if (request.method !== 'POST') return fail(405, 'method_not_allowed', 'Method not allowed', { allow: 'POST' });
  if (!flag(env.TELEGRAM_ENABLED, false)) return fail(503, 'telegram_disabled', 'Telegram integration is disabled');
  const secret = env.TELEGRAM_WEBHOOK_SECRET;
  if (typeof secret !== 'string' || secret.length < MIN_WEBHOOK_SECRET) return fail(503, 'not_configured', 'Webhook secret is not configured');
  const given = request.headers.get('x-telegram-bot-api-secret-token') ?? '';
  if (!(await safeEqual(given, secret))) return fail(401, 'unauthorized', 'Unauthorized');

  const parsed = await readJson(request, MAX_WEBHOOK_BODY);
  if (parsed.response) return parsed.response;
  const update = parsed.body;
  if (!Number.isSafeInteger(update.update_id)) return fail(400, 'invalid_update', 'update_id is required');

  // Owner only, private chats only. Everyone else gets a silent 200 so Telegram does not retry and nothing leaks.
  const owner = String(env.OWNER_TELEGRAM_ID ?? '');
  const msg = update.message;
  const isOwner =
    /^\d+$/.test(owner) && msg && msg.chat?.type === 'private' && String(msg.from?.id) === owner && String(msg.chat?.id) === owner;
  if (!isOwner) return json(200, { ok: true });

  const eventId = String(update.update_id);
  if (!(await jobs.recordEvent(env.DB, 'telegram', eventId, now))) return json(200, { ok: true, duplicate: true });
  if (typeof msg.text !== 'string' || msg.text.length > 4096) return json(200, { ok: true });

  let reply;
  try {
    reply = await runCommand(env, update, now);
    if (parseCommand(msg.text)?.name === 'new') kick(env, deps); // only a new job can need running
  } catch (e) {
    await jobs.deleteEvent(env.DB, 'telegram', eventId).catch(() => {}); // let Telegram's retry run it again
    throw e;
  }
  return json(200, replyTo(msg.chat.id, reply)); // answered inside the webhook response
}

export async function handle(request, env, now = Date.now(), deps = {}) {
  const url = new URL(request.url);
  if (url.pathname === '/health') {
    return request.method === 'GET' ? json(200, { ok: true }) : fail(405, 'method_not_allowed', 'Method not allowed', { allow: 'GET' });
  }
  if (url.pathname === '/telegram/webhook') return telegramRoute(request, env, now, deps);
  if (url.pathname !== '/v1/jobs' && !url.pathname.startsWith('/v1/jobs/')) return fail(404, 'not_found', 'Not found');

  if (!flag(env.CLOUD_ENABLED, true)) return fail(503, 'cloud_disabled', 'Cloud agent is disabled');

  if (env.RATE_LIMITER) {
    const key = request.headers.get('cf-connecting-ip') ?? 'global';
    const { success } = await env.RATE_LIMITER.limit({ key });
    if (!success) return fail(429, 'rate_limited', 'Too many requests', { 'retry-after': '60' });
  }

  const ownerToken = env.OWNER_TOKEN;
  if (typeof ownerToken !== 'string' || ownerToken.length < MIN_OWNER_TOKEN) return fail(503, 'not_configured', 'Owner token is not configured');
  const given = bearer(request);
  if (!given || !(await safeEqual(given, ownerToken))) return fail(401, 'unauthorized', 'Unauthorized');

  return jobsRoute(request, env, url, now, deps);
}

function isQuotaError(e) {
  return /exceeded|quota|limit/i.test(String(e?.message ?? ''));
}

export default {
  async fetch(request, env, ctx) {
    try {
      return await handle(request, env, Date.now(), { waitUntil: ctx?.waitUntil?.bind(ctx) });
    } catch (e) {
      console.error('unhandled', e?.name ?? 'Error'); // name only: messages may contain request data
      if (isQuotaError(e)) return fail(503, 'quota_exceeded', 'Free-tier quota reached; try again after 00:00 UTC', { 'retry-after': '3600' });
      return fail(500, 'internal', 'Internal error');
    }
  },

  async scheduled(_event, env, ctx) {
    if (!flag(env.CLOUD_ENABLED, true)) return;
    const now = Date.now();
    const safe = (p) => p.catch((e) => console.error('cron failed', e?.name ?? 'Error'));
    ctx.waitUntil(
      Promise.all([
        safe(jobs.requeueExpired(env.DB, now).then(() => runPending(env, now))),
        safe(notifyFinished(env, now)),
      ]),
    );
  },
};
