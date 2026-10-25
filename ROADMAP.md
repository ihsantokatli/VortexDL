# VortexDL Development Roadmap

## Phase 1: Project Setup, Share Intent & WebView Media Sniffer (COMPLETED)
- [x] Configure Gradle files (`libs.versions.toml`, project and app `build.gradle.kts`) with Compose, Hilt, Room, Coroutines, and OkHttp.
- [x] Configure `AndroidManifest.xml` with Internet permissions, `ACTION_SEND` (`text/plain`) Share Intent filter, and `singleTask` launchMode.
- [x] Create `DetectedMedia` domain model (id, url, title, mimeType, extension, requestHeaders map, timestamp).
- [x] Implement `BrowserViewModel` holding UI state (currentUrl, pageTitle, isLoading, detectedMediaList).
- [x] Implement `VideoSnifferWebView` using `AndroidView` and `shouldInterceptRequest` to detect `.mp4`, `.m3u8`, `.mpd`, `.webm` links and headers.
- [x] Build `MainActivity` & `BrowserScreen`: Top URL/Search bar, WebView container, dynamic badge FloatingActionButton showing detected video count, and a `ModalBottomSheet` listing captured videos.

## Phase 2: Direct Download Engine (.mp4 / .webm) & Room Database
- [ ] Setup Room Database (`DownloadEntity`, `DownloadDao`) for download history and state tracking.
- [ ] Implement OkHttp-based `FileDownloader` supporting custom headers (`Referer`, `User-Agent`, `Cookie`) and pause/resume (`Range` header).
- [ ] Create `DownloadForegroundService` with persistent progress notification.
- [ ] Build `DownloadsScreen` to monitor active and completed downloads.

## Phase 3: HLS (.m3u8) Stream Downloader & Merger
- [ ] Implement `.m3u8` Master and Media playlist parser to extract resolution variants and `.ts` segment URLs.
- [ ] Integrate segment downloading and merging into a single `.mp4` container.
