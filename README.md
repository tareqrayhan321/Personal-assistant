# Personal Mentor — AI Assistant & Mentor for Android

A native Android app that combines two things in one chat interface:

- **Task Mode** — a personal agent that manages to-dos, sets reminders, and (in later iterations) acts on your behalf.
- **Mentor Mode** — a specialized mentor that answers complex questions using a large knowledge base via **RAG** (Retrieval-Augmented Generation).

> **Status:** v0.4.0 (in progress). Streaming LLM chat, a tool-calling task agent, reminders with snooze, an exact-alarm permission flow, and an on-device RAG pipeline for Mentor Mode (knowledge-base screen, citations) are in.

## Tech Stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material Design 3 (Material You dynamic color) |
| Architecture | MVVM + Clean Architecture (presentation / domain / data) |
| DI | Dagger Hilt |
| Local storage | Room (chat history, offline tasks) |
| Networking | Retrofit + OkHttp + kotlinx.serialization |
| Async | Kotlin Coroutines + Flow |
| Build | Gradle Kotlin DSL + Version Catalog (`gradle/libs.versions.toml`) |

Min SDK 26 · Target/Compile SDK 35 · JDK 17

## Architecture

```
presentation  (Compose UI, ViewModels)
      │  depends on
      ▼
domain        (models, repository interfaces, use cases, AssistantResponder)   ← pure Kotlin, no Android deps on logic
      ▲
      │  implemented by
data          (Room, Retrofit, repository implementations)
```

The key extension point is `domain/assistant/AssistantResponder`. Today it is bound to a fake; the next iterations will bind:

- an **LLM-backed responder** with tool calling for Task Mode, and
- a **RAG pipeline** for Mentor Mode: embed query → vector DB similarity search → build grounded prompt → LLM.

## Project Structure

```
PersonalMentor/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/values/{strings,themes}.xml
│       └── java/com/personalmentor/app/
│           ├── PersonalMentorApp.kt          # @HiltAndroidApp
│           ├── MainActivity.kt               # entry point, edge-to-edge, theme, NavHost
│           ├── di/                           # Database, Network, Repository modules
│           ├── data/
│           │   ├── local/                    # Room: entities, DAOs, database, mappers
│           │   ├── remote/                   # Retrofit LlmApi, DTOs, auth interceptor
│           │   └── repository/               # Repository impls + FakeAssistantResponder
│           ├── domain/
│           │   ├── model/                    # AssistantMode, ChatMessage, TodoTask
│           │   ├── repository/               # Repository contracts
│           │   ├── assistant/                # AssistantResponder (LLM/RAG seam)
│           │   └── usecase/                  # Observe/Send/Clear chat
│           └── presentation/
│               ├── MainViewModel.kt          # Task/Mentor mode + chat state
│               ├── navigation/AppNavHost.kt
│               ├── chat/ChatScreen.kt
│               ├── tasks/TasksScreen.kt      # placeholder
│               └── theme/Theme.kt
├── gradle/libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── local.properties.example
```

## Getting Started

**Requirements:** Android Studio Ladybug (2024.2.1) or newer, JDK 17 (bundled with Android Studio).

1. **Clone**
   ```bash
   git clone https://github.com/<your-username>/PersonalMentor.git
   ```
2. **Open** the folder in Android Studio and let Gradle sync.
   If the Gradle wrapper JAR is missing, run `gradle wrapper --gradle-version 8.10.2` once, or use *File → Sync Project with Gradle Files*.
3. **(Optional) Configure API access.** Copy `local.properties.example` into `local.properties` and set:
   ```properties
   LLM_BASE_URL=https://api.openai.com/
   LLM_API_KEY=your-key-here
   LLM_MODEL=gpt-4o-mini
   ```
   Any OpenAI-compatible API works (OpenAI, OpenRouter, a local Ollama/LM Studio server via `LLM_BASE_URL`). The model must support tool calling for Task Mode.
   `local.properties` is git-ignored. These values are exposed through `BuildConfig`.
4. **Run** the `app` configuration on an emulator or device (API 26+).

Without a key or base URL the app runs an offline demo responder (it streams a canned reply and cannot act on tasks).

## Settings

Gear icon in the chat top bar. Values override `local.properties`; *Reset to defaults* returns to them. With no key and the default OpenAI URL the app stays in offline demo mode. The key is kept in app-private storage (backups are off); for a public release move it behind your own server (see roadmap).

## Mentor Mode / RAG

- **Ingestion:** in Mentor Mode, open the list icon in the top bar, then add plain-text files (txt, md, csv, json; up to 2 MB each). Text is split into ~900-character overlapping chunks (paragraph/sentence aware, including the Bengali danda), embedded through the OpenAI-compatible `/v1/embeddings` endpoint (`EMBEDDING_MODEL`, default `text-embedding-3-small`), and stored in Room. Indexing runs in the background and shows progress.
- **Search:** the question is embedded and compared against all stored vectors (exact cosine similarity, paged scan). The top 5 passages above a minimum score go into the prompt; the model cites them as `[1]`, `[2]`, and the reply ends with a *Sources* list of only the cited passages.
- **Vector store choice:** on-device Room + brute-force search keeps the app offline-capable for storage and needs no extra backend. It is comfortable up to a few tens of thousands of chunks; beyond that, swap `KnowledgeRepositoryImpl.search` for a server-side store (Qdrant / pgvector) behind the same `KnowledgeRepository` interface.
- **Limits:** PDF/DOCX are not supported yet. If you change `EMBEDDING_MODEL`, re-add the documents (vectors from another model are skipped during search).

## Reminders

**Repeating:** ask the assistant in Task Mode (e.g. "remind me daily at 8 to take vitamins"). The task's reminder always holds the next occurrence and keeps its local time of day, also across daylight-saving changes. Missed occurrences (device off) are skipped, not replayed. On the notification, *Snooze* delays only that occurrence; *Done* dismisses it. Ticking the task in the Tasks screen ends the series.

- Reminders use `AlarmManager`. On Android 12+ exact alarms need user approval: when a reminder is pending and access is off, the Tasks screen shows an **Allow** banner that opens the system settings, and pending alarms are upgraded to exact as soon as access is granted.
- The notification has **Snooze 10 min**, **Snooze 1 h** and **Done** buttons. In Task Mode you can also say e.g. "snooze the gym reminder by 20 minutes".
- Alarms are re-armed after a reboot and every time the app opens.

## Security Note

Keys in `BuildConfig` can be extracted from a shipped APK, so `LLM_API_KEY` is **debug-only**. Release builds talk to your own proxy (`server/`) with a revocable client token; see `docs/RELEASE.md`.

## Roadmap

- [x] LLM integration (SSE streaming)
- [x] Task agent: tool calling (create / list / complete / delete tasks, set reminders) + AlarmManager reminders, boot re-arm, notification permission
- [x] Task list UI backed by `TaskRepository`
- [x] RAG pipeline: ingestion, chunking, embeddings, on-device vector search (Room), cited sources in answers
- [x] Mentor knowledge-base management (add / remove / index status)
- [x] Settings screen: provider presets, base URL, API key, chat and embedding model (applied immediately, stored on-device)
- [x] Unit tests (task tools, SSE parsing, ViewModel, RAG, reminders, proxy) and CI (GitHub Actions: tests, debug build, release shrink check)
- [x] Compose UI tests for the chat screen (`app/src/androidTest`, run on an emulator in CI); other screens still untested
- [x] Release prep: proxy server, no provider key in the APK, adaptive launcher icon, ProGuard rules, in-app reporting of AI responses, privacy-policy link, API 36 (see `docs/RELEASE.md`, `docs/PLAY_STORE.md`)
- [ ] Play Console steps that only you can do (exact-alarm declaration, data-safety form, privacy policy page)
- [x] Exact-alarm permission flow (Android 12+/14+) and snooze (notification buttons + `snooze_task` tool)
- [x] Repeating reminders (daily / weekly)

## License

Choose a license (e.g. MIT) and add a `LICENSE` file.
