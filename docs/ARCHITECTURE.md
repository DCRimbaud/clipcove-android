# Architecture

ClipCove is a local-first Android application with a small, explicit module graph. The architecture favors typed contracts, replaceable platform adapters, and testable parsers over framework coupling.

## Principles

1. Keep platform payloads inside data mappers.
2. Keep Android framework details out of domain models.
3. Keep feature modules dependent on interfaces, not concrete adapters.
4. Treat network failures, rate limits, unavailable content, and parser drift as different states.
5. Persist only the minimum local data needed for user-visible features.
6. Never add bulk scraping, account automation, proxy pools, DRM bypass, or anti-bot evasion.

## Module Graph

```text
app
├── feature:home
├── feature:favorites
├── feature:downloads
└── feature:settings
        │
        ├── core:model
        ├── core:common
        ├── core:designsystem
        └── data:*
```

`app` is the composition root. It owns Android navigation, concrete repositories, foreground services, and Koin bindings. Feature modules contain Compose UI and ViewModels. Data modules implement parser, persistence, download, transfer, and update ports. `core:model` remains a small pure-Kotlin media contract.

## Parse Pipeline

```text
shared text or URL
  -> platform detection
  -> platform repository
  -> official page / resolver response
  -> platform mapper
  -> MediaInfo + typed video candidates
  -> HomeViewModel
  -> Compose result card and download queue
```

- Douyin mapping lives in `data:douyin`.
- Xiaohongshu SSR mapping lives in `data:xhs`.
- Python is used only where the Android network stack is insufficient.
- A successful result always exposes `MediaInfo`; raw JSON never reaches Compose.
- `VideoCandidate` records source kind, resolution, size, bitrate, and watermark risk.
- An original stream is preferred whenever the platform provides one.

## Data Ownership

- History and favorites are stored in app-private SharedPreferences.
- Favorites and history both retain the original share URL for re-resolution.
- Backups use a versioned JSON object and still accept legacy flat arrays.
- Diagnostics are bounded to 50 sanitized entries and never include raw share URLs.
- Download tasks are persisted app-private; completed media can be copied to the public Downloads collection.
- No analytics SDK, advertising SDK, account system, or ClipCove backend is present.

## Update Checks

`data:update` queries the public GitHub Releases API only after a manual user action. Release selection parses numeric semantic versions; prerelease tags are considered so users can still discover candidate builds explicitly published by the project.

## Adding A Platform

1. Add a repository port and typed mapper in a dedicated `data:*` module.
2. Reuse `MediaInfo`, `VideoCandidate`, and `ParseFailure`.
3. Add sanitized fixtures and JVM tests for successful and failing payloads.
4. Bind the adapter in `app`.
5. Document new permissions and privacy effects before merging.
