# Contributing

Thanks for taking a look. This is a small, single-maintainer app, so the bar is simple: keep it fast, honest about where rates come from, and free of servers, accounts and tracking.

## Ways to help

- **Report a bug.** Open an issue with the device model, Android version, app version (see the release you installed) and what you expected to happen. A screenshot of the rate list including the header line helps a lot.
- **Suggest a feature.** Describe the situation you are in and what you would do with the feature, not only the control you want. Ideas that keep the app to a single conversion screen are the easiest to accept.
- **Send a pull request.** For anything larger than a fix, open an issue first so the approach can be agreed before you spend time on it.

## Out of scope

Pull requests adding any of the following will be declined, because they change what this app is:

- A backend, cloud storage, accounts, sign-in or sync.
- Rate sources that require an API key, a paid plan or per-installation quotas.
- Analytics, crash reporting, advertising or any third-party SDK that phones home.
- Screens beyond conversion: transfers, charts, rate history, notifications, widgets.

## Development

Requirements: JDK 17 and an Android SDK with platform 35 and build-tools 35.0.1. Gradle is pinned by the wrapper.

```bash
./gradlew test lint assembleDebug
```

Both must be green before a pull request: tests pass and lint reports no errors. `assembleRelease` needs a signing key and is only used for published releases.

### House rules for code

- Conversion arithmetic uses `BigDecimal` through an internal USD base, with rounding applied only when a value is displayed. Do not introduce `Double` on the money path.
- Rate data keeps the exact digits returned by the source; the bundled JSON parser preserves numeric literals for that reason.
- Anything the user changes — list, order, base currency, amount — must survive leaving the app and coming back, including the back button.
- The screen must be usable before any network call completes. Network work happens on explicit events only: cold start, return after an hour, tapping the header, swiping a row right, adding a currency of a source that has not loaded yet. No timers, no background services.
- New user-visible strings go into `app/src/main/res/values/strings.xml` and `values-ru/strings.xml`. No hard-coded text in composables.
- Add unit tests for logic in `core` and `sources`; those packages are plain Kotlin and run on the JVM.

### Commits and pull requests

- One topic per pull request, with a short description of what changed for the user and how you verified it.
- Reference the issue it closes.
- Keep the changelog entry in the pull request description; `CHANGELOG.md` is updated at release time.

## Code of Conduct

Participation is covered by the [Code of Conduct](CODE_OF_CONDUCT.md).

## Licence of contributions

By contributing you agree that your work is published under the [Apache License 2.0](LICENSE) of this project, including the patent grant in section 3. There is no contributor licence agreement to sign and no copyright assignment: you keep the copyright to what you write.
