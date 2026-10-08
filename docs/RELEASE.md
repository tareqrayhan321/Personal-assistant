# Release guide

## 1. Deploy the proxy (keeps the provider key off the phone)

One command (needs Node 22 and a free Cloudflare account; `npx wrangler login` once):

```
cd server
UPSTREAM_API_KEY=sk-... REPORT_WEBHOOK_URL=https://hooks... ./deploy.sh
```

It runs the tests, generates a client token, stores the secrets, deploys, and prints the two `local.properties` lines. Manual equivalent:

```
npx wrangler secret put UPSTREAM_API_KEY
npx wrangler secret put CLIENT_TOKENS        # comma-separate to rotate
npx wrangler secret put REPORT_WEBHOOK_URL   # optional
npx wrangler deploy
```

What the proxy enforces: client token on every call, a model allow-list, an output-token cap,
a request-size cap, and only three routes (chat, embeddings, report). Add Cloudflare's rate-limiting
binding (see `wrangler.toml`) before launch.

**Honest limit:** the client token ships inside the APK, so a determined person can extract it. It stops
casual abuse and can be rotated, but it is not strong authentication. If abuse appears, add Play Integrity
attestation to the proxy and keep per-IP limits and a monthly spend cap at the provider.

## 2. Publish the privacy policy

Fill the `REPLACE_*` placeholders in `site/privacy/index.html` (date, your name, contact email, AI provider name), push to `main`, and enable *Settings → Pages → Source: GitHub Actions* in the repository. The workflow refuses to publish while any placeholder is left. The URL is `https://<user>.github.io/<repo>/privacy/`; use it for `PRIVACY_POLICY_URL` and in Play Console.

## 3. `local.properties` for the release build

```
PROXY_BASE_URL=https://personalmentor-proxy.<you>.workers.dev/
PROXY_CLIENT_TOKEN=<one of CLIENT_TOKENS>
PRIVACY_POLICY_URL=https://<your-site>/privacy
RELEASE_STORE_FILE=../release.keystore
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

The release build ignores `LLM_API_KEY` / `LLM_BASE_URL` (debug-only) and fails if the proxy values or the
privacy-policy URL are missing. `-PciBuild=true` skips that check (CI shrink test only).

## 4. Build and test the bundle

```
gradle :app:bundleRelease        # app/build/outputs/bundle/release/app-release.aab
```

Install a release build (`gradle :app:assembleRelease`, then adb install) and run this smoke test before upload,
because R8 problems show up at runtime, not at build time:

- [ ] Task Mode: "remind me tomorrow 9am to call Sam" creates the task, reminder fires, Snooze and Done work
- [ ] "remind me daily at 8 to take vitamins" repeats the next day; Done only dismisses it
- [ ] Mentor Mode: streaming answer appears token by token
- [ ] Knowledge base: add a .txt/.md file, indexing reaches Ready, a question about it cites `[1]` and shows Sources
- [ ] Settings: change model, Save, next message uses it; invalid URL disables Save
- [ ] Report: tap Report on an answer, Send; the report reaches the webhook / worker logs
- [ ] Reboot the phone: pending reminders still fire
- [ ] Revoke "Alarms & reminders": the Tasks banner appears and reminders still arrive (inexact)

## 5. Store listing and Play Console

Copy text from `docs/STORE_LISTING.md`; follow `docs/PLAY_STORE.md` for the exact-alarm declaration and the Data safety form.

## 6. Versioning

Bump `versionCode` (must increase on every upload) and `versionName` in `app/build.gradle.kts`.

## 7. Toolchain note

`compileSdk`/`targetSdk` are 36 (Google Play requires API 36 for new apps and updates since 2026-08-31),
which needs AGP 8.10.x and Gradle 8.11.1 (already set). Do a full Android Studio sync and test on an Android 16
device or emulator, since the build was not run in the environment that produced these changes.
