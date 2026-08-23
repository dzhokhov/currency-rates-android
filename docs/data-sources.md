# Rate sources

The app uses free, key-free, public endpoints only. A source that requires an API key is deliberately out of scope: the key would have to ship inside the APK and every installation would share one quota.

## Sources in use

### Frankfurter — fiat currencies, gold, silver

- Endpoint: `https://api.frankfurter.dev/v2/rates?base=USD`
- Currency list: `https://api.frankfurter.dev/v2/currencies`
- Coverage: ~165 currencies including RUB, RSD, BAM, plus XAU and XAG. No bitcoin.
- Cadence: daily. Each row in the response carries its own date; the app shows the earliest date among the rows it actually uses.
- Shape: a JSON array of `{date, base, quote, rate}`.
- Terms: the service is MIT-licensed and free to use, including commercially; no API key and no attribution requirement. The rates themselves are central bank reference rates and carry the terms of those banks.

### currency-api — bitcoin

- Primary: `https://latest.currency-api.pages.dev/v1/currencies/usd.json`
- Fallback: `https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json`
- Coverage: 341 codes (fiat, metals, crypto). Used here only for BTC.
- Cadence: daily.
- Shape: `{date, usd: {code: rate}}` with lowercase codes.
- Terms: [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) — public domain dedication, no attribution required.

The primary and fallback order is not arbitrary: the jsDelivr copy is served from a CDN cache that has been observed to lag the Pages copy by up to half a day, so the fresher address is tried first.

## Why these and not others

| Considered | Why it is not used |
|---|---|
| Open Exchange Rates, Fixer, currencyapi.com, currencyfreaks, exchangerate.host | API key required; 100–1500 requests per month shared across every installation |
| ExchangeRate-API (open endpoint) | Works without a key, but requires a visible attribution link; kept as a possible fallback rather than a default |
| moneyconvert | Terms of use do not clearly permit redistribution through an app |
| Wise, Revolut | No public rate API |
| National Bank of Serbia (official) | The public page blocks automated requests and the web service requires registration |

## Request policy

Requests happen only on events the user causes or would expect: cold start, returning to the app after an hour, tapping the header, swiping a row right, and adding a currency whose source has not been loaded yet. There is no polling, no timer and no work while the app is closed. Each attempt is at most two requests per source, and after a failed attempt automatic refreshes are suppressed for fifteen minutes — a manual tap always goes through.

## Bundled starter set

`app/src/main/assets/rates/` contains one captured response per source plus the Frankfurter currency list. They make the first launch useful before any network call and are read by exactly the same parser as live responses. Their dates are shown honestly as a bundled set until the first successful download.

Updating them before a release is a matter of refetching the same URLs, but note that several unit tests assert against the exact numbers in those files — refresh the fixtures and the expectations together.

## Adding a source

1. Implement an adapter in `sources` that turns the response into a rate set normalised to a USD base, rejecting a response that does not cover the requested currencies.
2. Register the currency-to-source mapping in `core/CurrencyRegistry`.
3. Add a parser test against a captured response in `app/src/test/resources` or `assets`.
4. Record the licence and terms of the source in this file — a source whose terms are unclear does not go in.
