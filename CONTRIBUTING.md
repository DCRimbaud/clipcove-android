# Contributing to ClipCove

Thanks for helping improve ClipCove. Search existing issues before starting work.

## Development Workflow

1. Fork the repository and create a branch from `main`.
2. Keep changes focused and avoid unrelated refactors in the same pull request.
3. Add a minimal, sanitized fixture or unit test for parser fixes.
4. Run the local quality gate:

```powershell
.\gradlew.bat lintDebug testDebugUnitTest assembleDebug
```

5. For live platform validation, run:

```powershell
python tools\smoke_network.py
```

## Code Guidelines

- Keep platform JSON inside data mappers. UI code should use `MediaInfo` and `ParseResult`.
- Add new platforms through a dedicated data module and parser port.
- Preserve structured error types and log actionable diagnostic details.
- Never commit cookies, tokens, private keys, personal share links, or copyrighted media.
- Do not commit `local.properties`, build outputs, logs, or APKs.

## Bug Reports

Include the app version, Android version, device model, platform, and link type. Remove `xsec_token`, cookies, phone numbers, IP addresses, and other personal data from logs and screenshots.

## Pull Requests

Explain the problem, approach, and verification evidence. UI changes should include sanitized screenshots. By contributing, you agree that your changes are licensed under GPL-3.0.
