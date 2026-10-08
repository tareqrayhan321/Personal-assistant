# Google Play checklist

Policies change; verify each item in Play Console's current help before submitting.

## Blocking requirements (already handled in code)

| Requirement | Status |
|---|---|
| Target API 36 for new apps/updates (since 2026-08-31) | `targetSdk = 36` (needs AGP 8.10 + Gradle 8.11.1, see RELEASE.md) |
| In-app reporting of offensive AI output, without leaving the app | "Report" button under every assistant reply, sent to `/v1/report` |
| Privacy policy link in Play Console **and** inside the app | Settings → "Privacy policy" (`PRIVACY_POLICY_URL`); you still enter it in Play Console |
| No provider secret in the APK | Release builds use the proxy token only |

## Things only you can do in Play Console

### 1. Exact alarms (restricted permission)
The manifest declares `SCHEDULE_EXACT_ALARM`. Play only allows exact-alarm permissions for apps whose
**core function** needs precisely timed alarms (alarm clock / calendar style), and requires a declaration form.
This app's core function is an AI assistant, so approval is not guaranteed. Pick one:

- **Declare it.** Justification: "Users create time-based task reminders (for example 'remind me at 18:00 to call a client'); a reminder that arrives minutes late defeats its purpose. The app asks the user to grant the permission and keeps working with inexact alarms if they decline."
- **Remove it** (safest for review): delete the permission from the manifest. The code already falls back to inexact alarms when exact ones are not allowed; reminders may then arrive a few minutes late, and the Tasks-screen banner and the assistant's warning should be reworded or removed.

### 2. Data safety form (what the app actually does)

| Data | Collected | Why | Notes |
|---|---|---|---|
| Messages (chat prompts, conversation history sent as context) | Yes | App functionality (AI answers) | Sent over HTTPS to your proxy, which forwards to the AI provider. Also stored on the device. |
| Files/docs (text of documents added to the knowledge base) | Yes | App functionality | Chunks are sent to the embeddings API and, when relevant, included in prompts. |
| Task titles/notes | Yes, when the assistant lists tasks | App functionality | Task Mode sends tool results (the task list) to the AI provider. |
| Reports (reported reply + note) | Yes, only when the user sends one | Safety / moderation | Sent to your report endpoint. |
| Device or advertising IDs, location, contacts, analytics | No | | The proxy host sees IP addresses (rate limiting). |

- Data is encrypted in transit (HTTPS only; cleartext is allowed only to `10.0.2.2`/`localhost` for development).
- Not sold, no ads, no analytics SDKs.
- Whether the AI provider counts as "sharing" or as a service provider processing on your behalf depends on Google's current definitions and your provider's terms; answer the form accordingly and keep the privacy policy consistent.
- Deletion: local data is removed with "clear chat", removing documents, or uninstalling. Server side nothing is stored except reports; give a contact email for deletion requests.

### 3. Other forms
- **Content rating questionnaire:** mention that users can chat with a generative AI.
- **App access:** none needed (no login).
- **Ads:** none.
- **Permissions you will see flagged:** `POST_NOTIFICATIONS` (reminders), `RECEIVE_BOOT_COMPLETED` (re-arm reminders), `INTERNET`.

## Privacy policy must say (minimum)
1. The app sends your messages, task content shown to the assistant, and knowledge-base text excerpts to an AI service through the developer's server to produce answers.
2. What the server keeps (nothing except user reports and temporary operational logs) and for how long.
3. Reports: what is sent, who reads it.
4. Everything else (chat history, tasks, documents, settings) stays on the device; how to delete it.
5. Contact email.
