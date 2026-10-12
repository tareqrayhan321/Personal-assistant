# Cloud agent: architecture, limits, tests, rollback

## What was added
```
Android app (unchanged UI, Room, reminders, RAG, ApprovalGate)
  Settings -> "Cloud agent (optional)" -> CloudAgentScreen        [off by default; no request is ever sent while off]
        |  HTTPS + owner token (entered by you, stored only in app-private preferences)
        v
cloud-agent/  Cloudflare Worker + D1   (separate from server/ on purpose)
  /v1/jobs ...  /telegram/webhook  cron (every 15 min)  -> Workers AI binding (free allowance only)
        ^
Telegram (optional, owner-only private chat)
```
`server/` (AI proxy, token inside the APK) and `cloud-computer/` (shell/file server) are untouched.

## Division of work
| Work | Runs on |
|---|---|
| Chat, Mentor Mode, RAG, reminders, scheduled tasks, GitHub, Browser, Cloud Computer, every approval | the phone, as before |
| Short AI questions submitted from the app or Telegram | the Worker (`ai_prompt` jobs) |
| Anything destructive, financial, publishing, shell or file | **never** the cloud agent; only the phone behind `ApprovalGate` |

Cloud jobs have two types only (`ai_prompt`, `note`). The Worker rejects every other type and any client-supplied owner/approval/source field.

## Android changes (all additive)
New: `domain/cloud/*`, `domain/repository/Cloud*Repository.kt`, `data/cloud/*`, `data/settings/CloudSettingsRepositoryImpl.kt`, `data/repository/CloudJobRepositoryImpl.kt`, `presentation/cloud/*`, tests in `app/src/test/.../domain/cloud/`.
Edited: `di/RepositoryModule.kt` (3 binds), `presentation/navigation/AppNavHost.kt` (1 route), `presentation/settings/SettingsScreen.kt` (optional `onOpenCloud` parameter, defaults to null so existing callers/tests compile unchanged). No Room schema change, no package/application-id change, no new Gradle dependency.

The cloud HTTP client is its own OkHttp client (no redirects) so the owner token can never reach another host.
Connection states shown: Off, Not configured, Unchecked, Checking, Connected, Failed(reason). Quota, token, rate-limit, network and server-off errors each have their own plain message.

## Free-tier limits (checked 2026-10-11; recheck before relying on them)
Workers 100,000 requests/day and 5 cron triggers; D1 5M rows read, 100,000 rows written, 5 GB; Workers AI 10,000 neurons/day. Daily limits reset at 00:00 UTC. Exceeding them makes requests fail; charges only start if you upgrade to a paid plan yourself. Cloudflare's CPU limit for free Workers was reported inconsistently by the sources I read (10 ms vs 30 ms); AI waiting time is I/O, not CPU.
Telegram's Bot API has no fee that I found in the documentation I read; I did not verify Telegram's current terms page.

## Not possible on the free architecture (documented, not faked)
No always-on agent loop; no shell or files in the cloud; no job longer than one short AI call; notifications can arrive up to 15 minutes late if the immediate send fails; exact free CPU limit and per-model tool-calling support must be confirmed on your account (`cloud-agent/scripts/check-workers-ai.mjs`).

## What was actually run (this environment)
| Check | Result |
|---|---|
| `cloud-agent` tests (`npm test`) | 71 pass, 0 fail |
| `server` tests | 15 pass |
| `cloud-computer` tests | 10 pass |
| Both D1 migrations on wrangler's local D1 | applied |
| `wrangler deploy --dry-run` | OK |
| `wrangler dev --local` smoke test (health, auth, create, duplicate, Telegram secret/owner/replay) | OK |
| Android pure-Kotlin cloud domain code + 17 unit tests | compiled with kotlinc 2.0.21 and run through a small JUnit shim (not Gradle) |
| **Android Gradle build, Android unit tests, Compose UI, Hilt wiring, OkHttp/serialization client, ViewModel** | **NOT run: no Android SDK or Maven access here. CI (`ci.yml`) must be the first real check.** |
| Real Cloudflare deploy, real Telegram bot, real Workers AI call | NOT run (needs your accounts) |

## Integration test (on your machine)
1. `cd cloud-agent && npm test`, then follow `cloud-agent/README.md` to deploy; check `/health`.
2. App: Settings -> Cloud agent -> paste address and token -> Save -> Test connection (expect "Connected.").
3. Create a job (it stays `pending` while `AI_ENABLED` is false). Turn `AI_ENABLED` on, deploy, create another: it should become `succeeded` within seconds.
4. Telegram: set up as in the README, send `/new hello`, expect the reply and a "Job finished" message.
5. Wrong token in the app -> "rejected the owner token". Switch the Cloud agent off -> no requests; chat, reminders and knowledge still work. Airplane mode -> "Could not reach...", local features unaffected.
6. Run `node scripts/check-workers-ai.mjs <model>` before using a Workers AI model for Task Mode.

## Rollback
- Android: delete the new files listed above and revert the 3 edited files (or `git revert` the commit). Nothing else depends on them; Room is untouched.
- Cloud: `npx wrangler rollback`; full removal: `npx wrangler delete` and `npx wrangler d1 delete personalmentor-cloud`. Remove the webhook with Telegram's `deleteWebhook`.
- Quick off switch without redeploying the app: turn the Cloud agent off in the app, set `CLOUD_ENABLED`/`TELEGRAM_ENABLED`/`AI_ENABLED` to `"false"` and `wrangler deploy`.
