# Currency Rates

An offline-first currency converter for Android with a built-in calculator. Tap any row to make it the base currency, type an amount — every other row is recalculated instantly. Rates come from free public sources; there is no backend, no account and no analytics.

[![Build](https://github.com/dzhokhov/currency-rates-android/actions/workflows/build.yml/badge.svg)](https://github.com/dzhokhov/currency-rates-android/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/dzhokhov/currency-rates-android?sort=semver)](https://github.com/dzhokhov/currency-rates-android/releases/latest)
[![License](https://img.shields.io/github/license/dzhokhov/currency-rates-android)](LICENSE)
[![Min API](https://img.shields.io/badge/API-26%2B-blue)](https://developer.android.com/tools/releases/platforms)

[Русская версия](README.ru.md)

| Rate list | Calculator | Swipe to delete | Localised |
|---|---|---|---|
| ![Rate list](docs/screenshots/01-main.png) | ![Calculator](docs/screenshots/02-calculator.png) | ![Swipe to delete](docs/screenshots/03-swipe-delete.png) | ![Russian interface](docs/screenshots/04-russian.png) |

## Features

- **Instant start.** The list is drawn from the cached (or bundled) rate set before any network call. Fresh rates arrive in the background.
- **Any row is the input.** Tap a row and it becomes the base currency. The previous amount stays visible and is replaced by the first digit you type.
- **Calculator keypad.** The app has its own keypad with `+ − × ÷` and `=`. Expressions such as `100+25×3` are evaluated with the usual operator precedence while every other row follows the running value. `.` and `,` are the same decimal separator.
- **Your own list.** Add any of ~165 fiat currencies plus gold (XAU), silver (XAG) and bitcoin (BTC); search by code or name. Reorder by long-pressing the handle and dragging. Swipe a row left to reveal a delete button. Composition, order, base currency and amount survive restarts.
- **Honest freshness.** The header shows the date of the rate set at the source and the moment it was downloaded, plus a plain marker when the app is offline, when an update failed, or when the data may be stale.
- **Works offline.** Without a network the app shows the last saved set and keeps converting. A bundled starter set makes the very first launch useful too.
- **Per-currency sources.** Every currency is mapped to the source that serves it. Swiping a row right refreshes exactly that source.
- **English and Russian**, following the device language. Numbers and dates are formatted per locale.

## Rates and data sources

All rates are daily reference rates. The app never asks for an API key and never sends anything but the rate requests themselves.

| Source | Used for | Licence / terms |
|---|---|---|
| [Frankfurter](https://frankfurter.dev) (`api.frankfurter.dev`) | Fiat currencies, gold (XAU), silver (XAG) | Service is MIT-licensed and free to use; the underlying reference rates come from central banks |
| [currency-api](https://github.com/fawazahmed0/exchange-api) (`latest.currency-api.pages.dev`, jsDelivr fallback) | Bitcoin (BTC) | CC0 1.0 |

Rates are reference values, not the price of an actual exchange. Nothing here is financial advice — do not use the app as the sole basis for a transaction.

## Install

Download the APK from the [latest release](https://github.com/dzhokhov/currency-rates-android/releases/latest) and open it on the device, or install it over ADB:

```bash
adb install -r currency-rates-<version>.apk
```

- Android 8.0 (API 26) or newer.
- The only system permission is internet access.
- **Version 0.4.0 does not install over 0.3.0.** The application id changed from `com.dzhokhov.currencyrates` to `io.github.dzhokhov.quotes`, so Android treats it as a different app: uninstall the old one first. Your currency list is lost once, this time only.
- Verify the download against the `.sha256` file published next to the APK:

```bash
shasum -a 256 -c currency-rates-<version>.apk.sha256
```

### Is this build really mine?

The checksum proves the file arrived intact. To prove *who built it*, check the signing certificate:

```bash
apksigner verify --print-certs currency-rates-<version>.apk
```

Expected fingerprint of the signing certificate:

```
Signer #1 certificate SHA-256 digest: ad3322fd16cbaebd6b9b182184786be2ab8d6120a1da80c50941428c78ec83ad
```

**Two channels, two signatures.** Builds published here are signed with the project key above. When the app appears on Google Play, that build will be signed by Google (Play App Signing re-signs the upload), so **its certificate fingerprint will be different** — it will be published next to this one once available. A consequence worth knowing before you choose a channel: builds from different channels do not update each other and cannot be installed side by side, so switching means uninstalling and losing the saved list once.

## Build from source

Requirements: JDK 17, Android SDK with platform 36 and build-tools 36.0.0. The Gradle wrapper pins Gradle 8.11.1.

```bash
git clone https://github.com/dzhokhov/currency-rates-android.git
cd currency-rates-android
./gradlew test lint assembleDebug
```

The debug APK appears in `app/build/outputs/apk/debug/`. `assembleRelease` additionally needs a signing key: create `keystore.properties` in the project root with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`, and keep both that file and the keystore out of version control.

## How it works

One Gradle module, Kotlin and Jetpack Compose, no dependency injection framework and no third-party networking or serialisation libraries:

- `core` — currency registry, conversion through an internal USD base on `BigDecimal`, display rules, expression parser and evaluator, freshness rules, a minimal JSON parser that keeps numeric literals exact.
- `sources` — HTTP client on `HttpURLConnection`, one adapter per rate source, and a repository that decides what to refresh and when.
- `storage` — atomic JSON files in the app's private directory: user state, refresh state, one raw response per source.
- `ui` — a single screen with an explicit mode gate (idle, editing, revealed, dragging), the calculator keypad and the currency picker.

More detail: [docs/architecture.md](docs/architecture.md). Data source specifics: [docs/data-sources.md](docs/data-sources.md).

## Privacy

No accounts, no telemetry, no advertising, no third-party SDKs. The app talks to the two rate endpoints listed above and to nothing else; everything it remembers stays in its own private storage on the device.

## Roadmap

- Intraday ("live") rates as a second layer on top of the daily set.
- An "about the rates" sheet showing the source, layer and timestamp per currency.
- A language switch inside the app, independent of the device language.

## Contributing

Issues and pull requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md). Security reports: [SECURITY.md](SECURITY.md).

## License

[Apache License 2.0](LICENSE) © 2026 Dmitry Zhokhov
