# Release And Signing

## Versioning

ClipCove uses semantic versions:

- Patch: parser, UI, and bug fixes.
- Minor: backward-compatible product features.
- Major: incompatible data or architecture changes.

Update `versionMajor`, `versionMinor`, `versionPatch`, and `appVersionCode` in `app/build.gradle.kts`. The version code must always increase.

## Release Signing

Official APKs must not use the Android debug key.

1. Generate a private keystore outside version control:

```powershell
keytool -genkeypair -v -keystore C:\secure\clipcove-release.jks `
  -alias clipcove -keyalg RSA -keysize 4096 -validity 10000
```

2. Copy `keystore.properties.example` to `keystore.properties` in the repository root.
3. Set an absolute `storeFile` path and the matching passwords and alias.
4. Keep the keystore and `keystore.properties` backed up in a secure secret store.

The Gradle build creates a release signing config only when all four properties are present. If the file is absent, debug builds still work, but `assembleRelease` produces an unsigned artifact and must not be published.

Never commit a keystore, password, token, or exported signing material. See `.gitignore`.

## Build

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:PYTHON_EXE = "C:\path\to\python.exe"
.\gradlew.bat lintDebug testDebugUnitTest assembleRelease
```

Expected artifact:

```text
app/build/outputs/apk/release/app-release.apk
```

Verify the signature:

```powershell
& "$env:ANDROID_HOME\build-tools\34.0.0\apksigner.bat" verify --verbose --print-certs `
  app\build\outputs\apk\release\app-release.apk
```

Generate a SHA-256 checksum:

```powershell
Get-FileHash app\build\outputs\apk\release\app-release.apk -Algorithm SHA256
```

Install on a temporary emulator or test device before publishing:

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
```

## Publish

1. Confirm `CHANGELOG.md` uses the release version and date.
2. Commit all source and documentation changes.
3. Tag the exact commit: `git tag -a v1.1.0 -m "ClipCove v1.1.0"`.
4. Push the branch and tag.
5. Create a GitHub prerelease or release with both the APK and `.sha256` checksum assets. In-app updates reject releases without these assets.
6. Wait for Android CI to pass on the released commit.
7. Download the published APK and verify its signature and checksum once more.

## Rollback

Do not move a published tag. Publish a new patch version with the fix. If an artifact is unsafe, mark the release as a prerelease or remove the artifact and publish a security notice with the affected version range.
