# Privacy

ClipCove is local-first. The project does not operate a server, analytics service, advertising network, or user account system.

## Data Stored On The Device

- Parse history and favorites, including the source URL needed for re-resolution
- App settings and download task metadata
- Downloaded files in app-private storage
- Optional copies in the public Downloads collection
- Up to 50 sanitized parser diagnostic entries

The app does not collect contacts, precise location, advertising identifiers, or account credentials.

## Network Requests

Network requests are made only to support user actions:

- Douyin and Xiaohongshu page/media hosts when parsing or downloading
- Image and media CDNs when displaying previews
- GitHub Releases when the user manually taps **Check for updates**
- Local WiFi transfer only while the user has enabled it

As with any network request, platform servers and GitHub can observe the device IP address and request metadata. ClipCove does not send history, favorites, or diagnostics to those services.

## Diagnostics

Diagnostics record parser category, sanitized error text, timestamp, app version, Android version, and ABI. Before storage and export, ClipCove attempts to redact:

- `xsec_token`, share/access/refresh tokens, cookies, and authorization headers
- Phone numbers, email addresses, and IPv4 addresses
- Sensitive query parameters and bearer tokens

Diagnostics are bounded and can be cleared from Settings. Sanitization is best-effort; users should still inspect a report before sharing it publicly.

## Backups

Backups are created only when the user chooses **Export backup**. They contain history, favorites, titles, covers, timestamps, and source URLs. Treat a backup as private data and store it accordingly. Restore uses the Android document picker and does not require broad storage access.

Backups are not encrypted by ClipCove. Do not upload a backup to a public location.

## User Control

Settings provides actions to clear history, downloads, diagnostics, or all local app data. Clearing all data does not remove files already copied to the public Downloads collection.

## Contact

Report privacy defects through the repository's private security reporting flow. Do not post real share tokens, cookies, phone numbers, IP addresses, or private media in a public issue.
