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

## ADR 006: WebView Ownership and Lifetime
- **Status:** Accepted
- **Context:** The browser stayed blank on launch. A `DisposableEffect` keyed on the `WebView` instance called `stopLoading()`, `removeView()` and `destroy()` in `onDispose`, then reset the state that keyed the effect (`webView = null`). Any effect churn (recomposition, lifecycle-local or state updates) therefore destroyed the view while its `ViewFactoryHolder` stayed attached, leaving a blank container and a progress bar that never finished.
- **Decision:** The `AndroidView` node is the sole owner of the WebView lifetime:
  - Creation: `AndroidView(factory)` — configure, load the start URL, publish the instance to state used by the command collector.
  - Release: `AndroidView(onRelease)` — `stopLoading()`, detach from parent, `destroy()`, clear the state. This also covers renderer-process recovery (`key(generation)`).
  - Pause/resume: `LifecycleEventEffect(ON_PAUSE/ON_RESUME)` calls `onPause()`/`onResume()`; no effect is ever allowed to destroy or detach a live view.
  - Tab switching only toggles `View.VISIBLE`/`View.GONE` so page state and session survive navigation between Browser and Downloads.

## ADR 007: Domain-Scoped User-Agent & Telemetry Filtering for YouTube
- **Status:** Accepted
- **Context:** Modern YouTube serves encrypted MediaSource Extension (MSE) binary SABR streams to Android Chrome User-Agents, while emitting 30-byte (0.03KB) `/videoplayback` preconnect and telemetry pings on homepage/feed navigation. Stripping signed query parameters (`range`) also broke cryptographic CDN signatures (`sparams`), returning HTTP 403 Forbidden.
- **Decision:**
  - Spoof `IPHONE_USER_AGENT` exclusively when browsing YouTube domains (`*.youtube.com`, `youtu.be`), forcing YouTube to output standard Apple HTML5 HLS (`.m3u8`) playlists. All other sites retain standard Chrome Mobile User-Agent.
  - In `MediaUrlDetector`, strictly filter out `/videoplayback` pings on non-watch pages (`isYouTubeWatchUrl`), reject items with `clen < 100_000L` and `dur < 3.0s`, and preserve `range` when signed in `sparams`.
  - In `VortexDLWebViewClient`, probe for `hlsManifestUrl` and HTML5 `<video src>` on watch pages, feeding genuine HLS streams into our Phase 3 `HlsDownloader` and `QualitySelectionSheet`.
