// Telegram owner commands and completion notifications. Replies to commands go back in the webhook response
// itself (no outbound call). Only the cron sends messages on its own, and only to the owner's private chat.
// Commands can read jobs, create `ai_prompt` jobs and cancel; there is no way to run anything else from here.

import * as jobs from './jobs.js';

const MAX_ACTIVE_JOBS = 20; // stops a runaway chat from burning the free D1/AI quota
const MAX_REPLY = 3500; // Telegram's limit is 4096
const HELP = [
  'Commands:',
  '/new <text>  create an AI job',
  '/jobs  last 10 jobs',
  '/failed  last 5 failed jobs',
  '/job <id>  details (first 6+ characters of the id are enough)',
  '/cancel <id>  cancel a pending or running job',
].join('\n');

const clip = (s, n) => (s.length > n ? s.slice(0, n - 1) + '…' : s);
const short = (id) => id.slice(0, 8);
const line = (j) => `${short(j.id)}  ${j.status}  ${j.type}${j.error_code ? '  (' + j.error_code + ')' : ''}`;

export function parseCommand(text) {
  if (typeof text !== 'string' || !text.startsWith('/')) return null;
  const [head, ...rest] = text.trim().split(/\s+/);
  const name = head.slice(1).split('@')[0].toLowerCase(); // "/jobs@MyBot" -> "jobs"
  return { name, args: rest.join(' ') };
}

function details(job) {
  const out = [`${short(job.id)}  ${job.status}  ${job.type}`, `attempts ${job.attempts}/${job.max_attempts}`];
  if (job.error_code) out.push(`error: ${job.error_code}`);
  if (job.result) out.push('', clip(job.result, 1500));
  return out.join('\n');
}

/** Returns the reply text for one owner message. Throws only on storage errors (the caller then asks Telegram to retry). */
export async function runCommand(env, update, now) {
  const cmd = parseCommand(update.message.text);
  if (!cmd) return 'Send a command. ' + HELP;
  const db = env.DB;

  switch (cmd.name) {
    case 'start':
    case 'help':
      return HELP;

    case 'jobs': {
      const rows = await jobs.listJobs(db, { limit: 10 });
      return rows.length ? rows.map(line).join('\n') : 'No jobs yet.';
    }

    case 'failed': {
      const rows = await jobs.listJobs(db, { status: 'failed', limit: 5 });
      return rows.length ? rows.map(line).join('\n') : 'No failed jobs.';
    }

    case 'job': {
      if (!cmd.args) return 'Usage: /job <id>';
      const found = await jobs.findJob(db, cmd.args.split(' ')[0]);
      if (found.ambiguous) return 'That prefix matches several jobs. Use more characters.';
      return found.job ? details(found.job) : 'Job not found.';
    }

    case 'cancel': {
      if (!cmd.args) return 'Usage: /cancel <id>';
      const found = await jobs.findJob(db, cmd.args.split(' ')[0]);
      if (found.ambiguous) return 'That prefix matches several jobs. Use more characters.';
      if (!found.job) return 'Job not found.';
      return (await jobs.cancelJob(db, found.job.id, now)) ? `Cancelled ${short(found.job.id)}.` : `Job is already ${found.job.status}.`;
    }

    case 'new': {
      const input = jobs.validateJobInput({ type: 'ai_prompt', payload: { prompt: cmd.args } });
      if (input.error) return 'Usage: /new <text> (1-2000 characters)';
      if ((await jobs.countActive(db)) >= MAX_ACTIVE_JOBS) return `Too many unfinished jobs (${MAX_ACTIVE_JOBS}). Wait for some to finish or /cancel them.`;
      // The key comes from the update id, so even a replayed update can never create a second job.
      const out = await jobs.createJob(db, { ...input, key: `tg-upd-${update.update_id}`, source: 'telegram' }, now);
      if (out.conflict) return 'Could not create the job.';
      return `${out.created ? 'Created' : 'Already created'} ${short(out.job.id)} (${out.job.status}).`;
    }

    default:
      return 'Unknown command. ' + HELP;
  }
}

export const replyTo = (chatId, text) => ({ method: 'sendMessage', chat_id: chatId, text: clip(text, MAX_REPLY) });

/**
 * Sends "job finished" messages for Telegram-submitted jobs. Bounded: at most `limit` jobs per run, 3 tries per job.
 * Needs TELEGRAM_BOT_TOKEN; without it (or with Telegram disabled) it does nothing. Never logs the URL or the text.
 */
export async function notifyFinished(env, now, { fetchImpl = fetch, limit = 5 } = {}) {
  const enabled = String(env.TELEGRAM_ENABLED ?? '').toLowerCase() === 'true';
  const token = env.TELEGRAM_BOT_TOKEN;
  const owner = String(env.OWNER_TELEGRAM_ID ?? '');
  if (!enabled || typeof token !== 'string' || token.length < 20 || !/^\d+$/.test(owner)) return { sent: 0 };

  let sent = 0;
  for (const job of await jobs.claimNotifications(env.DB, limit)) {
    const head = job.status === 'succeeded' ? 'Job finished' : 'Job failed';
    const body = job.status === 'succeeded' ? clip(job.result ?? '', 1500) : `error: ${job.error_code ?? 'unknown'}`;
    let res;
    try {
      res = await fetchImpl(`https://api.telegram.org/bot${token}/sendMessage`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ chat_id: Number(owner), text: clip(`${head} ${short(job.id)}\n${body}`, MAX_REPLY) }),
      });
    } catch {
      console.error('telegram notify: network error');
      continue;
    }
    if (res.ok) {
      await jobs.markNotified(env.DB, job.id, now);
      sent += 1;
    } else {
      console.error('telegram notify: status', res.status);
      if (res.status === 429) break; // rate limited: stop, the cron tries again later
    }
  }
  return { sent };
}
