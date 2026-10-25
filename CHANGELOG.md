# Changelog

All notable changes to ClipCove are documented here.

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
