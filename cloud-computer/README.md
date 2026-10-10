# Cloud Computer

A tiny server (Node 18+, no dependencies) that gives the Personal Mentor app's scheduled tasks a computer to work on:
run shell commands and read/write files inside one workspace directory.

**It runs arbitrary commands by design.** Anyone with the token controls this machine, so:

- Use a disposable VM or a container, never your personal computer or a machine with secrets on it.
- Run it as a non-root user (the Dockerfile does).
- The token must be long and random. The server refuses to start with fewer than 16 characters.
- Serve it over **HTTPS** only. The app rejects plain `http://` (except `localhost` / `10.0.2.2` for development).
  The server listens on `127.0.0.1` by default; put a reverse proxy (Caddy, nginx) or a tunnel (Cloudflare Tunnel,
  Tailscale Funnel) in front of it.

## Run

```bash
export TOKEN=$(openssl rand -hex 24)   # keep this; the app needs it
node server.js                          # http://127.0.0.1:8787, workspace ./workspace
```

Docker:

```bash
docker build -t cloud-computer .
docker run -d --restart unless-stopped -p 127.0.0.1:8787:8787 -e TOKEN=... -v cc-workspace:/workspace cloud-computer
```

Settings (environment variables): `TOKEN` (required), `PORT` (8787), `HOST` (127.0.0.1), `WORKDIR` (./workspace),
`NAME` (shown by `/health`), `MAX_OUTPUT` (characters kept per stream, default 20000).

## Use it in the app

Task Mode → New scheduled task → Advanced settings → **Cloud Computer** → New: a name, the HTTPS URL of the server and the
token. *Test connection* checks both. The task's agent then gets four tools: `computer_exec`, `computer_read_file`,
`computer_write_file`, `computer_list_files`.

Approvals: with *Skip confirmations* off, every command and file write needs your approval (the run waits 5 minutes, then the
action counts as declined), so a task that should run on its own needs *Skip confirmations* on, which is only sensible
against a sandbox like this one. Each command runs in a fresh `bash -c` (so `cd` and exported variables do not carry over; chain
steps with `&&`), has a timeout (default 120 s, at most 600 s), and its output is capped.

## API

All requests carry `Authorization: Bearer <TOKEN>`. Responses are JSON with `"ok": true|false` (and `"error"` when false).

| Route | Body | Result |
|---|---|---|
| `GET /health` | | `{ok, name}` |
| `POST /exec` | `{command, timeout_sec?}` | `{exit_code, stdout, stderr, timed_out, truncated}` |
| `POST /read_file` | `{path}` | `{content, truncated}` (first 200 KB) |
| `POST /write_file` | `{path, content}` | `{bytes}` |
| `POST /list_files` | `{path?}` | `{entries: [{name, type}]}` |

File paths are relative to the workspace; anything that resolves outside it (`..`, absolute paths, symlinks) is rejected.
Note that `/exec` itself is not confined to the workspace: it is a shell on that machine.

## Test

```bash
npm test
```
