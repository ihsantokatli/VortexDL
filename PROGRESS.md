# VortexDL - Live Progress Tracker (PROGRESS.md)

> **AGENT INSTRUCTION:** Read this file before starting any task, and ALWAYS update it after completing a task or fixing a bug.

## Current Status
- **Active Phase:** Phase 3 (HLS & YouTube Stream Sniffing & Quality Selection) - COMPLETED
- **Package Name:** `com.vortexdl.app`
- **Build Status:** Verified (66 unit tests passing, `assembleDebug` APK build successful)

## Completed Components
- [x] Workspace initialized (`AGENTS.md`, `ROADMAP.md`, `DECISIONS.md`, `FEATURE_BACKLOG.md`, `.gitignore`)
- [x] Gradle setup (`settings.gradle.kts`, `build.gradle.kts`, `libs.versions.toml`)
- [x] `AndroidManifest.xml` (Permissions, Share Intent, Hilt App registration, Foreground Service, FileProvider)
- [x] `VortexDLApp.kt` (`@HiltAndroidApp`)
- [x] `DetectedMedia.kt` (Domain model with headers map)
- [x] `BrowserViewModel.kt` (StateFlow for URL, loading state, media detection, and download trigger)
- [x] `VideoSnifferWebView.kt` (Background thread-safe `shouldInterceptRequest`)
- [x] `BrowserScreen.kt` & `MainActivity.kt` (URL bar, WebView, FAB badge, BottomSheet, Bottom Navigation)
- [x] Room Database (`DownloadEntity.kt`, `DownloadDao.kt`, `DownloadDatabase.kt`, `HeaderMapConverter.kt`, `DatabaseModule.kt`)
- [x] Domain Models & Repository (`DownloadStatus.kt`, `DownloadItem.kt`, `DownloadRepository.kt`, `DownloadRepositoryImpl.kt`)
- [x] OkHttp Download Engine (`FileDownloader.kt` with HTTP Range 206 resume, header replay, speed calculation, sanitized file names)
- [x] Foreground Service (`DownloadForegroundService.kt` with persistent progress notification, dataSync, Pause/Cancel actions)
- [x] Downloads UI (`DownloadsUiState.kt`, `DownloadsViewModel.kt`, `DownloadsScreen.kt`)
- [x] Bottom Sheet Download Action & Main Navigation Bar between Browser and Downloads
- [x] Browser WebView Loading Fix (Sanitized User-Agent avoiding Google bot/consent block, enabled HTML5 database & window open permissions, HTTP 200 dummy adblock responses, user CA trust anchors, and comprehensive SSL/HTTP error handlers)
- [x] Blank-Browser Bug Fix (Blank page / stuck progress on launch): the WebView is no longer destroyed by a `DisposableEffect` keyed on the view instance. `VideoSnifferWebView` now creates the view in `AndroidView(factory)` and releases it only through `AndroidView.onRelease` (`stopLoading` + `removeView` + `destroy`), while `LifecycleEventEffect` handles `onResume`/`onPause` non-destructively. The command collector is keyed on the view state and no longer influences view destruction.
- [x] Phase 3: HLS (.m3u8) Stream Downloader & Merger:
  - `HlsModels.kt`: Domain models for Master and Media playlists (`HlsVariant`, `HlsSegment`, `HlsMasterPlaylist`, `HlsMediaPlaylist`, `HlsPlaylist`).
  - `HlsPlaylistParser.kt`: RFC-compliant M3U8 parser with Master variant sorting, `#EXTINF` duration/title extraction, `#EXT-X-BYTERANGE` parsing, and robust relative URL resolution.
  - `HlsDownloader.kt`: OkHttp-based concurrent segment fetching (3 concurrent workers via `Semaphore`), per-segment retry logic, instantaneous resume from partial segment caches, progress & speed tracking, and sequential MPEG-TS stream merger into `.mp4`.
  - `DownloadForegroundService.kt` & `DownloadRepositoryImpl.kt`: Automatic routing of `.m3u8` streams to `HlsDownloader`, temporary segment cache cleanup on cancel/delete, and output file naming as `.mp4`.
  - Unit test suite: `HlsPlaylistParserTest.kt` (5 tests) & `HlsDownloaderTest.kt` (3 tests) covering variant sorting, relative URLs, byte ranges, stream concatenation, and missing segment detection.
- [x] Video Resolution Selection & Display Flow:
  - `ResolutionHelper.kt`: Maps RFC 8216 dimensions (e.g. `1920x1080` → `1080p`), extracts quality tags from URLs/titles for direct media, and formats bitrates (`5.0 Mbps`).
  - `VideoQualityOption.kt`: Model representing available stream variants with resolution, description, and bandwidth.
  - Quality Selection Modal (`QualitySelectionSheet.kt`): Interactive Material 3 BottomSheet allowing the user to select their desired video quality (1080p, 720p, 480p, 360p, 4K) when downloading multi-variant HLS streams.
  - Room Database Migration (`MIGRATION_1_2`): Added `resolution` column to `DownloadEntity` and `DownloadItem`.
  - UI Enhancements: Resolution badges in `DetectedMediaSheet`, format & resolution indicators in `DownloadsScreen` cards (`[MP4 · 1080p]`), and quality tags in foreground service notifications and file names.
- [x] YouTube & Extension-less Media Sniffing:
  - `MediaUrlDetector.kt`: Added Google Video (`googlevideo.com/videoplayback`) detection with itag mapping (18 → 360p muxed, 22 → 720p muxed, 137 → 1080p, 140 → Audio M4A), playback chunk canonicalization, and generic query-based MIME detection (`mime=video/mp4`).
  - `VortexDLWebViewClient.kt`: Added non-blocking JavaScript stream probe on YouTube watch pages (`ytInitialPlayerResponse.streamingData.formats`) in `onPageFinished` and `doUpdateVisitedHistory`.
- [x] YouTube 0.03KB False-Positive Elimination & HLS Safari Stream Extraction:
  - `MediaUrlDetector.kt`: Strict filtering of `/videoplayback` requests. Eliminated 30-byte (0.03KB) telemetry pings on homepage/search/feed by validating watch page context (`isYouTubeWatchUrl`), content-length (`clen >= 100_000L`), duration (`dur >= 3.0s`), and format itags. Excluded generic titles ("YouTube", "Home", "videoplayback"). Preserved `range` parameter whenever signed in `sparams` to prevent HTTP 403 Forbidden.
  - `VortexDLWebViewClient.kt`: Domain-scoped User-Agent spoofing (`IPHONE_USER_AGENT` exclusively for `youtube.com` and `youtu.be`), forcing YouTube to deliver native Apple HTML5 HLS (`.m3u8`) master playlists. Extended JavaScript probe to extract `hlsManifestUrl` and DOM `<video>` src with delayed fallback execution.
  - Unit test suite expanded to 66 unit tests passing (`MediaUrlDetectorTest` covering telemetry ping rejection, watch page stream detection, `sparams` range preservation, and HLS manifest detection).

## Next Immediate Steps
1. Select items from `FEATURE_BACKLOG.md` (e.g., In-App ExoPlayer/Media3 player for downloaded videos, Wi-Fi only mode, or multi-threaded chunked direct download).
2. Add end-to-end user settings preferences (DataStore) for download directory selection and concurrent download limits.

## Known Issues / Blockers
- None. Build and all 66 unit tests pass cleanly.
