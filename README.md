# ClipCove

[![Android CI](https://github.com/DCRimbaud/clipcove-android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/DCRimbaud/clipcove-android/actions/workflows/android-ci.yml)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)
[![Latest release](https://img.shields.io/github/v/release/DCRimbaud/clipcove-android?include_prereleases)](https://github.com/DCRimbaud/clipcove-android/releases)

ClipCove is a local-first Android media downloader for **Douyin** and **Xiaohongshu**. Paste a share link and save public videos, image galleries, audio, and Live Photos directly to your device. There is no backend service: history, favorites, settings, and download tasks stay on the phone.

> ClipCove is not affiliated with Douyin, ByteDance, or Xiaohongshu. Only download content you have the right to save, and follow local law and platform terms.

## Features

- Douyin and Xiaohongshu share text, short links, full links, and raw IDs
- Video, original gallery image, audio, and Live Photo downloads
- Xiaohongshu original-file priority via `originVideoKey` to avoid platform-watermarked streams
- Resumable downloads with pause, continue, retry, and foreground notifications
- Local favorites, history, clipboard suggestions, and JSON backup
- Live Photo and audio merging with FFmpeg
- Built-in WiFi transfer server for moving files to a computer
- Dark Jetpack Compose UI with Android 7.0+ support (minSdk 24)

## Tech Stack

- Kotlin, Jetpack Compose, Material 3
- ViewModel, StateFlow, Koin
- OkHttp, Coil, Media3
- Chaquopy with Python 3.12
- ffmpeg-kit
- Multi-module Android Gradle build

## Architecture

```text
app/                   Application shell, navigation, DI, platform adapters
core/
  model/               Typed media models and parse results
  designsystem/        Theme and shared Compose components
  common/              Shared utilities
data/
  douyin/              Douyin parser port and JSON mapper
  xhs/                 Xiaohongshu parser port and SSR mapper
  download/            Download task state machine
  history/             Local history
  favorites/           Local favorites
  merger/              FFmpeg merge port
  transfer/            WiFi transfer server
  settings/            Settings
feature/
  home/                Home, parse result, history
  downloads/           Download manager
  favorites/           Favorites
  settings/            Settings
```

Dependencies flow from `feature` to `core:model` and data-layer ports. Concrete platform implementations are assembled in `app`. Platform JSON is isolated in data mappers; UI code consumes typed `MediaInfo` and `ParseFailure` values.

## Build Requirements

- JDK 17
- Android SDK 34
- Python 3.12 with `pip`
- Windows, macOS, or Linux

Chaquopy reads `PYTHON_EXE` when set. Otherwise it invokes `python` from `PATH`.

### Windows

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:PYTHON_EXE = "C:\path\to\python.exe"

# Create local.properties once and adjust the SDK path.
Set-Content local.properties 'sdk.dir=C\:\\Android'

.\gradlew.bat lintDebug testDebugUnitTest assembleDebug
```

### macOS / Linux

```bash
export JAVA_HOME=/path/to/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"
export PYTHON_EXE=python3
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew lintDebug testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Test

```powershell
.\gradlew.bat lintDebug testDebugUnitTest assembleDebug
python tools\smoke_network.py
```

`tools/smoke_network.py` performs optional live-network smoke tests. Platform rate limits, network policies, or expired share tokens can make it fail, so it is not part of normal CI.

## Installation

```bash
adb install -r app-debug.apk
```

Package ID: `io.github.dcrimbaud.clipcove`.

Versions before `1.0.2` used a different package ID. Uninstall the old test build before installing ClipCove.

## Limitations

- Douyin and Xiaohongshu APIs, anti-bot checks, and SSR data structures change frequently.
- Xiaohongshu `xsec_token` values expire, so old share links must be copied again.
- Anonymous parsing may hit rate limits or verification challenges.
- Current GitHub release artifacts use debug signing. Configure your own release keystore before production distribution.
- ClipCove does not provide account login, bulk scraping, proxy pools, or commercial collection features.

## Security and Compliance

- Never publish cookies, `xsec_token` values, phone numbers, LAN addresses, or private keys in issues or screenshots.
- Report vulnerabilities privately as described in [SECURITY.md](SECURITY.md).
- ClipCove is intended for personal study and interoperability research. Users are responsible for copyright, platform terms, and local law.

## Contributing

Bug reports, compatibility fixes, and tests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.

## License

ClipCove is released under the **GNU General Public License v3.0**. See [LICENSE](LICENSE). Third-party attribution is documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
