import { DatabaseSync } from 'node:sqlite';
import { readFileSync, readdirSync } from 'node:fs';

/** A D1-shaped wrapper over node:sqlite, so tests run the real migrations and real SQL. */
export function makeDb() {
  const db = new DatabaseSync(':memory:');
  for (const f of readdirSync(new URL('../migrations/', import.meta.url)).sort()) {
    db.exec(readFileSync(new URL(`../migrations/${f}`, import.meta.url), 'utf8'));
  }
  return {
    raw: db,
    prepare(sql) {
      const stmt = db.prepare(sql);
      return {
        bind: (...args) => ({
          first: async () => stmt.get(...args) ?? null,
          all: async () => ({ results: stmt.all(...args) }),
          run: async () => ({ meta: { changes: Number(stmt.run(...args).changes) } }),
        }),
      };
    },
  };
}

export const OWNER_TOKEN = 'o'.repeat(40);
export const WEBHOOK_SECRET = 'webhook_secret_1234';
export const OWNER_ID = '424242';

export function makeEnv(extra = {}) {
  return {
    DB: makeDb(),
    OWNER_TOKEN,
    TELEGRAM_ENABLED: 'true',
    TELEGRAM_WEBHOOK_SECRET: WEBHOOK_SECRET,
    OWNER_TELEGRAM_ID: OWNER_ID,
    ...extra,
  };
}

export function req(path, { method = 'GET', token = OWNER_TOKEN, key, body, headers = {} } = {}) {
  const h = { ...headers };
  if (token) h.authorization = `Bearer ${token}`;
  if (key) h['idempotency-key'] = key;
  const init = { method, headers: h };
  if (body !== undefined) {
    init.body = typeof body === 'string' ? body : JSON.stringify(body);
    h['content-type'] = 'application/json';
  }
  return new Request(`https://agent.test${path}`, init);
}

export const count = (env, table) => env.DB.raw.prepare(`SELECT COUNT(*) AS n FROM ${table}`).get().n;
