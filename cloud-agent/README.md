# Cloud Agent (Phases 2-3: foundation + Telegram)

Cloudflare Worker + D1 for cloud jobs. Event-driven only (requests, a webhook, a cron). There is no always-running process.
Kept separate from `server/` (the AI proxy) on purpose: the proxy token ships in the APK, the owner token here never does.

## Endpoints
| Method | Path | Auth |
|---|---|---|
| GET | `/health` | none |
| POST | `/v1/jobs` (needs `Idempotency-Key`) | owner token |
| GET | `/v1/jobs`, `/v1/jobs/:id` | owner token |
| POST | `/v1/jobs/:id/cancel` | owner token |
| POST | `/telegram/webhook` | Telegram secret header, then owner-only |

Job types: `ai_prompt`, `note`. Nothing else is accepted: no shell, no file, no GitHub jobs. Those stay on the phone behind `ApprovalGate`.
Status flow: `pending -> running -> succeeded | failed | cancelled`; a failed attempt goes back to `pending` until 3 attempts are used, then `failed`.

## Free-tier limits (checked 2026-10-11; recheck before relying on them)
| Service | Free limit | When exceeded |
|---|---|---|
| Workers | 100,000 requests/day, 5 cron triggers per account | requests fail |
| D1 | 5M rows read/day, 100,000 rows written/day, 5 GB | queries fail (HTTP 503 `quota_exceeded` from this Worker) |
| Workers AI (Phase 5) | 10,000 Neurons/day, shared by all models | calls fail |

Daily limits reset at 00:00 UTC. Charges only start if you upgrade to Workers Paid yourself. Never do that for this project. Each created job costs 2 row writes, so roughly 50,000 jobs/day fit.

## Run the tests (Windows, no Cloudflare account needed)
1. Install Node 22 LTS from nodejs.org.
2. In a terminal: `cd cloud-agent` then `npm test`.

## Deploy (free)
```
cd cloud-agent
npx wrangler login
npx wrangler d1 create personalmentor-cloud
```
Copy the printed `database_id` into `wrangler.toml`. Then:
```
npx wrangler d1 migrations apply personalmentor-cloud --remote
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
npx wrangler secret put OWNER_TOKEN
npx wrangler deploy
```
Paste the generated hex string when `secret put` asks. Keep a copy in your password manager. Do not commit it.

Check: `curl https://personalmentor-cloud-agent.<your-subdomain>.workers.dev/health` returns `{"ok":true}`.

## Switches
`CLOUD_ENABLED` and `TELEGRAM_ENABLED` in `wrangler.toml` `[vars]`. Set to `"false"`, then `npx wrangler deploy`. Telegram is off by default.

## Rollback
`npx wrangler rollback` restores the previous Worker version. To remove everything: `npx wrangler delete` and `npx wrangler d1 delete personalmentor-cloud`. The Android app is unchanged in this phase.

## Telegram (optional, off by default)
Owner-only: the update must come from a **private chat** whose user id and chat id both equal `OWNER_TELEGRAM_ID`. Everyone else (other users, groups, channels) gets a silent `200` and nothing is stored.

| Command | Does |
|---|---|
| `/new <text>` | creates an `ai_prompt` job (max 20 unfinished jobs at once) |
| `/jobs`, `/failed` | last 10 jobs / last 5 failed |
| `/job <id>` | details and result; the first 6+ characters of the id are enough |
| `/cancel <id>` | cancels a pending or running job |

Plain text and unknown commands do nothing (there is no way to run shell or files from Telegram). Replies travel inside the webhook response. When a job submitted from Telegram finishes (succeeded or failed), the 15-minute cron sends the owner one message: at most 5 per run and 3 tries per job. Delivery can therefore be up to ~15 minutes late, and a message may be missed if all 3 tries fail. Jobs created from the app are not echoed to Telegram.

### Setup (Windows PowerShell)
1. In Telegram, talk to **@BotFather**, `/newbot`, keep the bot token.
2. Find your numeric id: send any message to your new bot, then open `https://api.telegram.org/bot<TOKEN>/getUpdates` in a browser and read `"from":{"id":...}`. Do this before step 4.
3. Secrets (each command asks you to paste the value):
```
npx wrangler secret put TELEGRAM_BOT_TOKEN
npx wrangler secret put OWNER_TELEGRAM_ID
npx wrangler secret put TELEGRAM_WEBHOOK_SECRET
```
For the webhook secret use 16+ letters/digits/`_`/`-`, e.g. the output of `node -e "console.log(require('crypto').randomBytes(24).toString('hex'))"`.
4. In `wrangler.toml` set `TELEGRAM_ENABLED = "true"`, then `npx wrangler d1 migrations apply personalmentor-cloud --remote` and `npx wrangler deploy`.
5. Register the webhook (replace the three values; keep the command out of shared screenshots):
```
Invoke-RestMethod -Method Post -Uri "https://api.telegram.org/bot<TOKEN>/setWebhook" -Body @{ url = "https://personalmentor-cloud-agent.<subdomain>.workers.dev/telegram/webhook"; secret_token = "<WEBHOOK_SECRET>"; allowed_updates = '["message"]' }
```
6. Send `/help` to the bot.

To switch Telegram off: set `TELEGRAM_ENABLED = "false"` and deploy, or call `deleteWebhook`. Telegram's Bot API has no fee that I could find in the documentation I read; I did not verify its current terms page.

## Not covered yet
Job execution and AI (Phase 5; until then jobs stay `pending`), the Android client (Phase 4).
