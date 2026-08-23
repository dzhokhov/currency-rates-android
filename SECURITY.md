# Security Policy

## Supported versions

Only the latest release receives fixes. Older APKs are not patched — install the newest release instead.

| Version | Supported |
|---|---|
| 0.3.x | yes |
| < 0.3 | no |

## Reporting a vulnerability

Please report privately through GitHub's [private vulnerability reporting](https://github.com/dzhokhov/currency-rates-android/security/advisories/new) rather than in a public issue. Include what you found, how to reproduce it, the app version and the Android version. You can expect a first reply within a week; a fix ships in the next release, and the advisory is published once it is available.

## Threat model of this app

Worth knowing before you report:

- The app has no backend, no account, no user data collection and one permission (internet access). It reads two public rate endpoints over HTTPS and writes only to its own private storage.
- Rate data is treated as untrusted input: responses are parsed by a bundled parser with size and depth limits, and a set that does not cover the visible list is rejected rather than partially applied.
- Release APKs are signed with the maintainer's key. Builds you make yourself are signed with your own key and cannot replace a release build.
- A stale or wrong rate shown as fresh counts as a security-relevant defect for this project: the header must always tell the truth about where the numbers came from and when.

Things that are out of scope: attacks that require an unlocked device with an attacker already having physical access or root, and the availability of the third-party rate sources themselves.
