# VortexDL - AI Agent Coding Guidelines (AGENTS.md)

## Mandatory Workflow for the Agent
1. **Read Context First:** Before writing any code, read `PROGRESS.md`, `ARCHITECTURE.md`, `DECISIONS.md`, and `ROADMAP.md`.
2. **Package Name:** Always use `com.vortexdl.app` as the root package.
3. **Update Progress:** After completing any file creation or bug fix, update the checkboxes and status inside `PROGRESS.md`.
4. **No Placeholders:** Never leave `// TODO` or incomplete functions. Write compilable, production-ready Kotlin code.

## Tech Stack
- **Language:** Kotlin (2.0+)
- **UI Framework:** Jetpack Compose (Material 3)
- **Architecture:** MVVM + Clean Architecture (`data`, `domain`, `presentation`, `service`, `di`)
- **DI:** Dagger Hilt
- **Concurrency:** Coroutines & StateFlow
- **Database:** Room
- **Networking:** OkHttp3

## Critical Technical Rules
- **WebView Thread Safety:** Never access `WebView` methods (`view.title`, `view.url`) inside `shouldInterceptRequest`.
- **Media Deduplication:** Prevent duplicate media URLs from being added to `detectedMediaList` in `BrowserViewModel`.
- **Header Preservation:** Always capture and pass `User-Agent`, `Referer`, `Origin`, and `Cookie` headers from `WebResourceRequest`.