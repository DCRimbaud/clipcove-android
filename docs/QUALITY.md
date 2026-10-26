# Quality Gate

## Required Automated Checks

Run before every pull request and release:

```powershell
.\gradlew.bat lintDebug testDebugUnitTest assembleDebug
python tools\check_comments.py
```

A parser change should include a minimal sanitized fixture. A persistence change should include backward-compatibility coverage. A UI change should include a screenshot with tokens and personal data removed.

## Review Checklist

- The change has one clear user-facing purpose.
- Raw platform JSON and HTTP details remain in data or app adapters.
- New state is typed; errors are not represented as unstructured `null` or toast-only failures.
- New local storage is versioned or backward compatible.
- User-visible destructive actions require confirmation.
- External URLs are limited to expected HTTPS hosts.
- Logs and exported diagnostics are sanitized.
- Accessibility labels exist for non-decorative icons.
- Unit tests and lint pass.
- First-party comments pass the SPDX, English-only, and public-contract checker.
- New dependencies have a compatible license and are reflected in third-party notices when required.

## Third-Party Code Policy

Public specifications, official API documentation, and general architecture patterns are valid references. Source code may be adapted only when all of the following are true:

1. The license is compatible with GPL-3.0 distribution.
2. Required copyright and license notices are retained.
3. The copied scope is minimal and has a clear maintenance owner.
4. A test or fixture verifies the adapted behavior.

Do not copy code from projects with non-commercial, unknown, or incompatible licenses. A useful idea is not permission to copy the implementation.

## Manual Smoke Matrix

Test at least once per release on a clean install:

- Douyin video with audio
- Douyin image note and Live Photo
- Xiaohongshu video with an original stream
- Xiaohongshu video with only a transcoded stream
- Favorites reopen using a source URL
- Versioned backup export and restore
- Legacy flat-array backup restore
- Clear-all confirmation
- Diagnostic export with a simulated failure
- GitHub update check while offline and online
- In-app update permission prompt, checksum validation, signature validation, download resume, and installer launch
- Download pause, resume, retry, cancel, pause all, resume all, and retry all failed
- Android 7.0 behavior for public Downloads export

## Severity Guide

- P0: credential leak, arbitrary file access, corrupting restore, or non-installable release.
- P1: core parse/download path broken, silent data loss, or signing/update failure.
- P2: recoverable workflow defect or missing important validation.
- P3: polish, wording, or low-impact maintainability issue.