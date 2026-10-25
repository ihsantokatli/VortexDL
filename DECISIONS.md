# VortexDL - Architecture Decision Records (DECISIONS.md)

## ADR 001: Unified Project Naming & Root Package
- **Status:** Accepted
- **Context:** The application was initially drafted with "StreamCatch" and `com.streamcatch.app`. The project name is finalized as "VortexDL" with root package `com.vortexdl.app`.
- **Decision:** Align all modules, build scripts (`settings.gradle.kts`, `app/build.gradle.kts`), manifest, resources, and source directories to `com.vortexdl.app` and "VortexDL". Remove all leftover `streamcatch` directories and references.

## ADR 002: Clean Architecture & Package Hierarchy
- **Status:** Accepted
- **Context:** The application requires scalable separation of concerns across presentation, domain, and data layers.
- **Decision:** Use MVVM with Clean Architecture:
  - `domain`: Pure Kotlin entities (`DetectedMedia`) and repository interfaces (`MediaRepository`).
  - `data`: Network detection logic (`MediaUrlDetector`, `AdBlockList`) and repository implementations (`MediaRepositoryImpl`).
  - `presentation`: Jetpack Compose Material 3 screens, viewmodels, and custom WebView components.
  - `di`: Dagger Hilt dependency injection modules.

## ADR 003: Thread Safety for WebView Network Interception
- **Status:** Accepted
- **Context:** `WebViewClient.shouldInterceptRequest` is invoked on background/IO threads by Android WebView. Calling `WebView` APIs (such as `view.title` or `view.url`) or directly modifying UI state from this callback causes race conditions or crashes.
- **Decision:** Cache the current page URL, title, and cookies in volatile fields on main-thread callbacks (`onPageStarted`, `onPageFinished`). In `shouldInterceptRequest`, pass immutable snapshots to `BrowserWebViewCallbacks` / `MediaRepository`, which synchronizes state and emits through thread-safe `StateFlow`.

## ADR 004: Preservation of Video Request Headers
- **Status:** Accepted
- **Context:** Streaming CDNs (Cloudflare, Akamai, AWS CloudFront) validate `User-Agent`, `Referer`, `Origin`, and `Cookie` headers to guard against hotlinking and unauthorized downloads (HTTP 403 Forbidden).
- **Decision:** `MediaUrlDetector` extracts and retains all essential HTTP headers from the intercepted `WebResourceRequest`, falling back to the WebView's default User-Agent, page origin, and cookie jar where appropriate.

## ADR 005: Media Deduplication & Bounded Buffer
- **Status:** Accepted
- **Context:** Video players poll chunklists and fragments repeatedly, firing multiple requests for identical video URLs.
- **Decision:** Deduplicate detected media URLs in `MediaRepositoryImpl` (ignoring URL fragments) and limit in-memory tracking to the most recent items (bounded to 200 items) to prevent memory leaks.
