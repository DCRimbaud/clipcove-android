# Changelog

All notable changes to ClipCove are documented here.

## [1.3.1] - 2026-10-26

### Added

- Added a Gitee mirror download route with the same SHA-256, package, version, and signing-certificate verification.
- Added ABI-aware APK selection for ARM64 phones and x86_64 emulators.

### Changed

- Update route probing now measures real transfer throughput instead of choosing only by first-byte latency.
- Added a 39 MB ABI-specific R8 test-release artifact to reduce update download time.
- Version name set to `1.3.1` (`versionCode` 24).

## [1.3.0] - 2026-10-26

### Added

- Full normalized metadata extraction for Douyin and Xiaohongshu, including descriptions, author handles and avatars, hashtags, locations, play/recommend counters, soundtrack authors, durations, covers, and share URLs.
- A compact metadata panel and expandable full captions in parse results.
- Explicit image actions: per-image original download, batch download, Live Photo download, soundtrack merge, cover download, and copy-source-link.
- A launcher icon that matches the Home brand mark across legacy, adaptive, and monochrome Android icon formats.
- Complete English and Simplified Chinese interface resources with an in-app language picker for System, English, and Simplified Chinese.

### Changed

- Music rows now show soundtrack author and duration.
- Video candidates retain declared width, height, and size when the platform exposes them.
- Version name set to `1.3.0` (`versionCode` 23).

## [1.2.1] - 2026-10-26

### Fixed

- Update checks and APK downloads now fall back through dedicated GitHub API proxies and multiple release-asset mirrors when direct GitHub access is unavailable.
- Update routes are probed concurrently with short timeouts, so a reachable but stalled direct connection cannot block the whole download.

### Changed

- Added verified fallback routes including `gh.llkk.cc`, `gh.ddlc.top`, `gh.monlor.com`, and `gh.idayer.com`.
- Update progress now shows when ClipCove is selecting a route instead of waiting at 0%.
- Version name set to `1.2.1` (`versionCode` 22).

## [1.2.0] - 2026-10-26

### Added

- A restrained Compose motion system with ambient surfaces, page transitions, press feedback, animated loading and empty states, download progress, and update progress.
- Clearer visual hierarchy for page headers, section dividers, icon tiles, cards, and primary actions.

### Fixed

- Douyin image-note downloads now use the signed JPEG URL returned by the detail API instead of a synthesized original URL that returned HTTP 403.
- Image notes now download as watermark-free JPEG files with the correct extension.

### Security

- Hardened LAN transfer with a 256-bit random token, constant-time comparison, bounded workers, response-header validation, and no-store response headers.
- Enforced HTTPS for supported media URLs and improved update download validation.

### Changed

- Version name set to `1.2.0` (`versionCode` 21).
- The built-in changelog now shows only the current release highlights.

## [1.2.0-rc.2] - 2026-10-26

### Added

- Automatic fallback through public GitHub download accelerators after direct downloads fail.
- Setting to disable accelerator routes and enforce GitHub-only downloads.
- Route-selection tests covering direct-only mode and non-GitHub URLs.

### Security

- Accelerated downloads still require the configured SHA-256, package, version, and signing-certificate checks.

### Changed

- Version name set to `1.2.0-rc.2` (`versionCode` 20).

## [1.2.0-rc.1] - 2026-10-26

### Added

- Search and status filters for download tasks.
- Bulk cleanup for completed download tasks with confirmation.
- Favorites search by title, platform ID, or source URL.
- Copy source links directly from favorite cards.

### Fixed

- Open and share completed downloads directly from app-private storage without requiring a public copy first.

### Changed

- Version name set to `1.2.0-rc.1` (`versionCode` 19) as a release candidate for update-chain testing.

## [1.1.2] - 2026-10-26

### Added

- In-app update download with resumable transfer, automatic retries, progress, and storage checks.
- SHA-256, package name, version code, and signing-certificate verification before installation.
- Automatic launch of Android's package installer with a GitHub fallback for manual installation.

### Changed

- Update downloads request unknown-app-source permission before consuming mobile data.
- Version bumped to `1.1.2` (`versionCode` 18).

### Security

- Downloaded updates must match the installed application's signing certificate.
- Releases without an APK checksum asset are rejected for in-app installation.

## [1.1.1] - 2026-10-26

### Added

- One-tap batch downloads for multi-image galleries.
- Optional automatic parsing when a supported link is shared into ClipCove.
- Backward-compatible platform metadata for favorites and backups.

### Changed

- Legacy Xiaohongshu favorites and raw 24-character note IDs are recognized as Xiaohongshu content.
- Version bumped to `1.1.1` (`versionCode` 17).

## [1.1.0] - 2026-10-26

### Added

- Source-aware video candidates with resolution, size, bitrate, source kind, and watermark-risk metadata.
- In-app quality selection that defaults to the original Xiaohongshu stream and warns before downloading a transcoded fallback.
- Versioned backup restore with support for legacy flat-array backups.
- Clear-all local data confirmation.
- Bounded, sanitized parser diagnostics with export support.
- Manual GitHub Releases update check, including prerelease support.
- Configurable release signing through an ignored `keystore.properties` file.
- Architecture, quality, release, and privacy documentation.

### Changed

- Favorites now retain the original share URL so Xiaohongshu entries can be re-resolved reliably.
- History and favorites remain backward compatible with previously stored data.
- The built-in changelog is now a concise user-facing summary.
- Version bumped to `1.1.0` (`versionCode` 16).

### Security

- Diagnostics redact tokens, cookies, phone numbers, email addresses, and IPv4 addresses before storage and export.

## [1.0.2] - 2026-10-26

### Fixed

- Prefer Xiaohongshu `video.consumer.originVideoKey` as the download source to avoid platform-watermarked transcoded streams.
- Fall back to H.264/H.265 streams only when the original file is unavailable.
- Verify the downloaded file against the original media hash.

### Changed

- Rebranded the application and repository as ClipCove.
- Changed the Android application ID to `io.github.dcrimbaud.clipcove`.
- Translated GitHub-facing documentation to English.

## [1.0.1] - 2026-10-25

### Fixed

- Correctly use the final URL from `xhslink.cn` redirects when selecting a note.
- Support Xiaohongshu mobile SSR payloads under `noteData.data.noteData`.
- Support `mediaV2` snake_case video stream fields.
- Reject video notes that do not contain a playable stream.

## [1.0.0] - 2026-10-25

### Added

- New dark media-tool design system and floating navigation.
- Branded home screen, structured parse panel, result cards, and gallery grid.
- Redesigned download, favorites, and settings screens.
- Platform-aware result metadata, local favorites, download actions, and empty states.
- Android 9 and lower backup export support.

### Changed

- Video previews are paused by default.
- The home screen was split into page orchestration and reusable components.

## [0.9.0] - 2026-10-25

### Changed

- Introduced `core:model` with `MediaInfo`, `ParseResult`, and `ParseFailure`.
- Removed raw platform JSON from ViewModel and UI layers.
- Reworked Xiaohongshu fetching through Chaquopy.
- Added structured error handling, unit tests, GitHub Actions, and network smoke tests.
- Added legacy Android storage permissions and FileProvider support.

## [0.8.0] - 2026-08-13

- Added resumable downloads, pause/resume/cancel, retry backoff, speed display, foreground notifications, and task persistence.
- Improved download filenames and recovered task handling.

## Earlier Versions

- Added Xiaohongshu support and multi-source rendering.
- Added author profile batch downloads, WiFi transfer, gallery viewing, and fullscreen video.
- Added share intents, clipboard parsing, history, favorites, and download management.
- Added the initial standalone Douyin parser built with Chaquopy.

For the complete user-facing release notes, see the application's built-in changelog.
