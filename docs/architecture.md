# Architecture

A single Gradle module (`:app`), Kotlin and Jetpack Compose. No dependency injection framework, no networking library, no serialisation library, no database. Everything below the UI is plain Kotlin and runs on the JVM, which is why most of the logic is covered by ordinary unit tests.

## Packages

```
com.dzhokhov.currencyrates
├── core          currency registry, conversion, display rules, expressions, freshness, JSON
│   ├── expr      expression model, evaluator, key mapping
│   └── json      minimal JSON reader/writer
├── sources       HTTP client, one adapter per rate source, refresh repository
├── storage       atomic JSON files in the app's private directory
├── ui            single screen, mode gate, keypad, currency picker, view model
└── log           one-line structured logging under the tag "Rates"
```

Data flows in one direction: bundled assets and stored files → repository → resolved rates → view model state → composables. User input travels back through the view model into the state store.

## Conversion

Every rate set is normalised to an internal USD base. A conversion between two currencies is one division of `BigDecimal` values with `DECIMAL128` context; rounding happens only when a number is rendered, so a round trip (`A → B → A`) returns the original amount.

Display rules are deliberate rather than emergent:

- Amounts: two decimals for fiat; for gold, silver and bitcoin at least four significant digits, between two and eight decimals.
- The per-row rate line reads `1 <base> = X <row>`; when `X` would fall below `0.001` the line is inverted to `1 <row> = Y <base>` so the number stays readable.
- Rate precision: two decimals from 100 up, four decimals between 1 and 100, four significant digits below 1 (never more than eight decimals).
- A value that is not zero but rounds to zero in its own format falls back to four significant digits instead of showing `0`.

## Expressions

The keypad feeds a small expression model: a sequence of numbers and operators, with limits (12 digits before the separator, 2 or 8 after depending on the base currency, 32 characters overall). The evaluator applies the usual precedence — `×` and `÷` before `+` and `−`, left to right within a precedence level — on `BigDecimal`.

While typing, other rows follow the *last computable value*: the longest prefix of the expression that evaluates, ignoring a trailing operator. Division by zero simply has no value: the rows keep their previous numbers and `=` does nothing until the zero is edited away. Closing the input by tapping outside or pressing back collapses the expression to that last computable value, rounded to the input precision of the base row.

## Rate sources and refreshing

`CurrencyRegistry` maps every currency to the source that serves it, so the repository only ever queries the sources actually present in the visible list. A refresh happens on explicit events — cold start, returning after an hour, tapping the header, swiping a row right, adding a currency whose source has never loaded — never on a timer and never in the background while the app is closed.

Each attempt makes at most two requests per source (primary address, then the fallback where one exists). A response is rejected outright if it does not cover the visible list, so a partial set never half-replaces a good one. Attempt outcomes are recorded per source; only full attempts participate in the rule that suppresses automatic retries for fifteen minutes after a failure.

Freshness shown in the header is derived, not stored as prose: the date is the earliest date among the sets actually in use, the "loaded" moment is the earliest successful download among them, and markers (`no network`, `could not update`, `may be stale`) come from the recorded per-source outcomes. Thresholds: 48 hours since the last successful download, or a set older than five days.

## Storage

Three kinds of file in `filesDir`, all JSON with a `schemaVersion`:

- `state.json` — the user's list, order, base currency and amount.
- `refresh.json` — last attempt, its outcome, per-source outcomes, last full success.
- `rates/<source>.json` — the raw response body plus the moment it was fetched, so the same parser reads bundled assets, cache and network.

Writes go through a single serialised writer: temporary file, `fsync`, atomic rename. Changes to the list, order and base currency are written immediately; the amount is coalesced over 300 ms and flushed when the app is paused. A corrupt or unreadable file is ignored in favour of defaults rather than crashing the launch.

The store also keeps the current state in memory, so a view model created for a new activity — for example after leaving with the back button and coming back while the process is still alive — starts from what was last written, not from a snapshot taken at process start.

## The screen

One screen with an explicit mode gate, which is a pure function tested on the JVM:

| Mode | What a tap, swipe or drag does |
|---|---|
| `Idle` | tap selects a base currency; swipes and the drag handle work normally |
| `Editing` | anything outside the field and the keypad only closes the input |
| `Revealed` (a row swiped left) | any touch returns the row; swiping another row left moves the reveal there in one gesture |
| `Dragging` | input is closed; back cancels the drag and restores the original order |

The keypad belongs to the app, not to the system: the amount is a plain text node with no focus and no IME, which is also why the software keyboard never appears by itself on the main screen. The currency picker, where free-text search makes sense, still uses the system keyboard.

## Logging

One line per event under the tag `Rates`, carrying the step, the module, what the app believed at that moment, the observed result and the next action. Amounts entered by the user are never logged.

## Build and release

`assembleDebug` needs nothing but the SDK. `assembleRelease` requires a `keystore.properties` with the signing key and stops with an explicit message when it is missing, so a release can never be produced unsigned by accident. Releases are built locally and attached to a GitHub release together with a SHA-256 checksum; the signing key is not stored in this repository or in CI.
