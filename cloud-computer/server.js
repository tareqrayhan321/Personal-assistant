'use strict';
/**
 * Cloud Computer for Personal Mentor: a small HTTP API (no dependencies) that runs shell commands and reads/writes
 * files inside one workspace directory, on a machine YOU own. Every request needs `Authorization: Bearer <TOKEN>`.
 *
 * It runs arbitrary commands by design: put it on a disposable VM or in a container, run it as a non-root user,
 * and serve it over HTTPS (reverse proxy or tunnel). See README.md.
 */
const http = require('node:http');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');

const MAX_BODY_BYTES = 1024 * 1024;
const MAX_READ_BYTES = 200 * 1024;
const MIN_TOKEN_LENGTH = 16;

function sha256(text) {
  return crypto.createHash('sha256').update(text).digest();
}

function createServer(config) {
  const token = String(config.token || '');
  if (token.length < MIN_TOKEN_LENGTH) throw new Error(`TOKEN must be at least ${MIN_TOKEN_LENGTH} characters`);
  const tokenDigest = sha256(token);
  const name = config.name || 'cloud-computer';
  const maxOutput = config.maxOutput || 20000;
  const defaultTimeoutSec = config.defaultTimeoutSec || 120;
  const maxTimeoutSec = config.maxTimeoutSec || 600;
  const maxConcurrent = config.maxConcurrent || 2;

  fs.mkdirSync(config.workdir, { recursive: true });
  const workdir = fs.realpathSync(config.workdir);
  let running = 0;

  const inside = (full) => full === workdir || full.startsWith(workdir + path.sep);

  /** Resolves a user path inside the workspace; also rejects symlinks that lead outside of it. */
  function safePath(rel) {
    if (typeof rel !== 'string') throw new HttpError(400, 'path must be a string');
    const full = path.resolve(workdir, rel || '.');
    if (!inside(full)) throw new HttpError(400, 'path is outside the workspace');
    let probe = full;
    while (!fs.existsSync(probe)) probe = path.dirname(probe);
    if (!inside(fs.realpathSync(probe))) throw new HttpError(400, 'path is outside the workspace');
    return full;
  }

  function authorized(req) {
    const header = req.headers.authorization || '';
    const given = header.startsWith('Bearer ') ? header.slice(7) : '';
    return crypto.timingSafeEqual(sha256(given), tokenDigest);
  }

  function exec(command, timeoutSec) {
    return new Promise((resolve) => {
      const child = spawn('bash', ['-c', command], {
        cwd: workdir,
        env: { ...process.env, HOME: workdir },
        detached: true,
        stdio: ['ignore', 'pipe', 'pipe'],
      });
      let stdout = '';
      let stderr = '';
      let truncated = false;
      let timedOut = false;
      const collect = (current, chunk) => {
        const next = current + chunk.toString('utf8');
        if (next.length > maxOutput) {
          truncated = true;
          return next.slice(0, maxOutput);
        }
        return next;
      };
      child.stdout.on('data', (c) => { stdout = collect(stdout, c); });
      child.stderr.on('data', (c) => { stderr = collect(stderr, c); });
      const timer = setTimeout(() => {
        timedOut = true;
        try { process.kill(-child.pid, 'SIGKILL'); } catch { /* already gone */ }
      }, timeoutSec * 1000);
      child.on('error', (e) => {
        clearTimeout(timer);
        resolve({ exit_code: -1, stdout, stderr: stderr + String(e.message), timed_out: false, truncated });
      });
      child.on('close', (code) => {
        clearTimeout(timer);
        resolve({ exit_code: code === null ? -1 : code, stdout, stderr, timed_out: timedOut, truncated });
      });
    });
  }

  async function handle(method, route, body) {
    if (route === '/health' && method === 'GET') return { ok: true, name };
    if (method !== 'POST') throw new HttpError(404, 'not found');

    if (route === '/exec') {
      const command = typeof body.command === 'string' ? body.command.trim() : '';
      if (!command) throw new HttpError(400, 'command is required');
      const asked = Number(body.timeout_sec);
      const timeoutSec = Math.min(maxTimeoutSec, Math.max(1, Number.isFinite(asked) && asked > 0 ? asked : defaultTimeoutSec));
      if (running >= maxConcurrent) throw new HttpError(429, 'too many commands running');
      running += 1;
      try {
        return { ok: true, ...(await exec(command, timeoutSec)) };
      } finally {
        running -= 1;
      }
    }
    if (route === '/read_file') {
      const full = safePath(body.path);
      if (!fs.existsSync(full) || !fs.statSync(full).isFile()) throw new HttpError(404, 'file not found');
      const size = fs.statSync(full).size;
      const fd = fs.openSync(full, 'r');
      try {
        const buffer = Buffer.alloc(Math.min(size, MAX_READ_BYTES));
        fs.readSync(fd, buffer, 0, buffer.length, 0);
        return { ok: true, content: buffer.toString('utf8'), truncated: size > MAX_READ_BYTES };
      } finally {
        fs.closeSync(fd);
      }
    }
    if (route === '/write_file') {
      if (typeof body.content !== 'string') throw new HttpError(400, 'content must be a string');
      const full = safePath(body.path);
      if (full === workdir) throw new HttpError(400, 'path must name a file');
      fs.mkdirSync(path.dirname(full), { recursive: true });
      if (!inside(fs.realpathSync(path.dirname(full)))) throw new HttpError(400, 'path is outside the workspace');
      fs.writeFileSync(full, body.content);
      return { ok: true, bytes: Buffer.byteLength(body.content) };
    }
    if (route === '/list_files') {
      const full = safePath(body.path);
      if (!fs.existsSync(full) || !fs.statSync(full).isDirectory()) throw new HttpError(404, 'directory not found');
      const entries = fs.readdirSync(full, { withFileTypes: true }).slice(0, 500).map((e) => ({
        name: e.name,
        type: e.isDirectory() ? 'dir' : 'file',
      }));
      return { ok: true, entries };
    }
    throw new HttpError(404, 'not found');
  }

  const server = http.createServer((req, res) => {
    const send = (code, payload) => {
      const text = JSON.stringify(payload);
      res.writeHead(code, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(text) });
      res.end(text);
    };
    if (!authorized(req)) return send(401, { ok: false, error: 'unauthorized' });

    const chunks = [];
    let size = 0;
    let tooLarge = false;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) { tooLarge = true; return; }
      chunks.push(chunk);
    });
    req.on('end', async () => {
      try {
        if (tooLarge) throw new HttpError(413, 'request body too large');
        let body = {};
        if (chunks.length) {
          try { body = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { throw new HttpError(400, 'invalid JSON'); }
          if (body === null || typeof body !== 'object' || Array.isArray(body)) throw new HttpError(400, 'body must be a JSON object');
        }
        const route = new URL(req.url, 'http://localhost').pathname;
        send(200, await handle(req.method, route, body));
      } catch (e) {
        if (e instanceof HttpError) return send(e.status, { ok: false, error: e.message });
        send(500, { ok: false, error: 'internal error' });
      }
    });
  });
  return server;
}

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

module.exports = { createServer };

if (require.main === module) {
  const env = process.env;
  let server;
  try {
    server = createServer({
      token: env.TOKEN,
      name: env.NAME,
      workdir: env.WORKDIR || path.join(__dirname, 'workspace'),
      maxOutput: env.MAX_OUTPUT ? Number(env.MAX_OUTPUT) : undefined,
    });
  } catch (e) {
    console.error(`Cannot start: ${e.message}. Example: TOKEN=$(openssl rand -hex 24) node server.js`);
    process.exit(1);
  }
  const port = Number(env.PORT) || 8787;
  const host = env.HOST || '127.0.0.1';
  server.listen(port, host, () => console.log(`Cloud computer listening on http://${host}:${port}`));
}
