# VortexDL - System Architecture & Data Flow (ARCHITECTURE.md)

## 1. Core Data Flow (Sniffing to Downloading)
```text
[External App Share Intent] OR [In-App URL Bar]
       │
       ▼
[MainActivity] ──(Extracts URL via Regex)──► [BrowserViewModel]
                                                    │
                                                    ▼
[VideoSnifferWebView] ◄──(Loads Page)────── [BrowserUiState]
       │
       ├──(shouldInterceptRequest on IO/Background Thread)
       ├──(Inspects URL extension: .mp4, .m3u8, .mpd, .webm & MIME types)
       ├──(Captures Request Headers: User-Agent, Referer, Cookie, Origin)
       │
       ▼
[BrowserViewModel.onMediaDetected(DetectedMedia)] (Thread-safe StateFlow update)
       │
       ▼
[DetectedMediaBottomSheet] ──(User clicks Download)──► [DownloadForegroundService]