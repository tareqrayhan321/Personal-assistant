// Runs pending jobs in short, bounded steps. Called from the cron and once after a job is created.
// Nothing here loops forever: at most `max` jobs per call, one AI call per job, a lease on every running job.

import * as jobs from './jobs.js';
import { runAi, AiError } from './ai.js';
import { notifyFinished } from './telegram.js';

const LEASE_MS = 60_000;
const DAY_MS = 86_400_000;

const flag = (v, dflt) => (v === undefined || v === '' ? dflt : String(v).toLowerCase() === 'true');
const intVar = (v, dflt) => (Number.isInteger(parseInt(v, 10)) && parseInt(v, 10) > 0 ? parseInt(v, 10) : dflt);

export const aiEnabled = (env) => flag(env.AI_ENABLED, false);

/**
 * @returns {{ran: number, stopped?: string}}
 * Stops early when AI is off, the daily call cap is reached, or the provider reports an exhausted quota.
 */
export async function runPending(env, now = Date.now(), { max } = {}) {
  if (!flag(env.CLOUD_ENABLED, true) || !aiEnabled(env)) return { ran: 0, stopped: 'disabled' };
  const started = Date.now();
  const clock = () => now + (Date.now() - started); // `now` plus real elapsed time, so tests can inject a clock
  const perRun = max ?? intVar(env.AI_MAX_JOBS_PER_RUN, 3);
  const dailyCap = intVar(env.AI_DAILY_CALL_CAP, 100);
  const dayStart = now - (now % DAY_MS); // quotas reset at 00:00 UTC
  let ran = 0;
  let stopped;

  for (let i = 0; i < perRun; i++) {
    if ((await jobs.countAiCallsSince(env.DB, dayStart)) >= dailyCap) {
      stopped = 'daily_cap';
      break;
    }
    const job = await jobs.claimNext(env.DB, clock(), LEASE_MS);
    if (!job) break;
    ran += 1;

    if (job.type === 'note') {
      await jobs.completeJob(env.DB, job.id, 'noted', clock());
      continue;
    }
    await jobs.recordAiCall(env.DB, clock(), job.id); // counted before the call, so a crash cannot hide spending
    try {
      const prompt = JSON.parse(job.payload).prompt;
      const text = await runAi(env, prompt);
      await jobs.completeJob(env.DB, job.id, text, clock());
    } catch (e) {
      const kind = e instanceof AiError ? e.kind : 'provider';
      if (kind === 'quota') {
        await jobs.failJobFinal(env.DB, job.id, 'quota_exceeded', clock());
        stopped = 'quota';
        break;
      }
      await jobs.failJob(env.DB, job.id, kind === 'timeout' ? 'ai_timeout' : kind === 'empty' ? 'ai_empty' : 'ai_error', clock());
    }
  }
  if (ran > 0) await notifyFinished(env, clock()).catch(() => {}); // tell the owner right away instead of at the next cron
  return stopped ? { ran, stopped } : { ran };
}
