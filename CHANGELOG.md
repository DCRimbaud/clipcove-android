# Changelog

All notable changes to ClipCove are documented here.

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
