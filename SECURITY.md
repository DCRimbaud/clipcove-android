# Security Policy

## Supported Versions

The current maintained line is `1.0.x`. If a vulnerability also affects older versions, include the affected versions in your report.

## Reporting a Vulnerability

Use the repository's private **Security → Report a vulnerability** flow. Do not open a public issue for an exploitable vulnerability.

Include:

- Affected versions and Android versions
- Reproduction steps
- Security impact
- Sanitized logs or a minimal proof of concept

Never include real cookies, `xsec_token` values, signing keys, phone numbers, IP addresses, or copyrighted media files.

## In Scope

- Token or credential leakage
- WiFi transfer authentication and path traversal issues
- Arbitrary file read, overwrite, or path injection
- Unsafe external intents or file sharing
- Sensitive information in logs or backups
- Known high-severity dependency vulnerabilities

Platform rate limiting, expired links, and API structure changes are compatibility bugs and should be reported through a normal issue.
