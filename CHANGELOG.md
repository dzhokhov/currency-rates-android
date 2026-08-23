# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and version numbers follow the `MAJOR.MINOR.PATCH` convention of [Semantic Versioning](https://semver.org/spec/v2.0.0.html) as far as it applies to an application rather than a published API.

## [Unreleased]

## [0.4.0] - 2026-08-23

### Changed
- The application id is now `io.github.dzhokhov.quotes` (was `com.dzhokhov.currencyrates`), chosen so it stays accurate as the app grows beyond currencies. **This build does not install over 0.3.0** — Android treats a different id as a different app, so the old one has to be uninstalled and the saved list is lost once.
- Built against Android 16: `compileSdk` and `targetSdk` 36, Android Gradle Plugin 8.9.3, Gradle 8.11.1, Kotlin 2.1.21, Compose BOM 2025.05.01. Google Play requires apps submitted after 31 August 2026 to target Android 16.
- Minimum supported version is unchanged: Android 8.0 (API 26).

### Known issues
- With the system font size set to its largest, the bottom row of the calculator keypad is clipped in landscape. The keys still work. Present in 0.3.0 as well, so this is not a regression; a fix is planned.
- The known issue from 0.3.0 about the first Enter from an external keyboard still applies.


## [0.3.0] - 2026-08-23

### Added
- Drag to reorder: long-press the handle on the right of a row and drag it up or down.
- Swipe a row left to reveal a delete button; tapping it removes the currency. Removing the base currency moves the base to the first remaining row and keeps the equivalent amount.
- Swipe a row right to refresh the source that serves that currency.
- Calculator keypad inside the app: digits, decimal separator, backspace, `+ − × ÷`, `=` and enter. Expressions are evaluated with the usual operator precedence and every other row follows the running value.
- `.` and `,` are accepted interchangeably as the decimal separator.

### Changed
- Tapping a row no longer clears the amount: the previous value stays visible and is replaced by the first digit typed.
- While the input is open, a tap outside the field only closes the input and does nothing else.
- The row menu was removed; the `⋮` glyph is now only the drag handle.

### Fixed
- Changes to the list could be lost when leaving the app with the back button and reopening it while the process was still alive: a removed currency came back and the order, base currency and amount were restored to an older state.

### Known issues
- With an external physical keyboard, the first Enter after touching the screen may be swallowed by the platform — press it again. The in-app keypad is unaffected.
- The flag of a row swiped left is clipped at the screen edge.

## [0.2.0] - 2026-08-23

### Added
- Rate list with a tap-to-select base currency and live recalculation of every other row.
- Header showing the date of the rate set at the source and the moment it was downloaded, with markers for offline, failed update and possibly stale data; tapping it refreshes.
- Currency picker with search by code and name over ~165 fiat currencies plus gold, silver and bitcoin.
- Bundled starter rate set, on-device cache and background refresh, so the screen never waits for the network.
- English and Russian interface following the device language.

[Unreleased]: https://github.com/dzhokhov/currency-rates-android/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/dzhokhov/currency-rates-android/releases/tag/v0.4.0
[0.3.0]: https://github.com/dzhokhov/currency-rates-android/releases/tag/v0.3.0
[0.2.0]: https://github.com/dzhokhov/currency-rates-android/releases/tag/v0.2.0
