---
title: Privacy Policy — Currency Rates
---

# Privacy Policy

**App:** Currency Rates (`io.github.dzhokhov.quotes`; builds before 0.4.0 used `com.dzhokhov.currencyrates`)
**Effective date:** 23 August 2026

## Short version

Currency Rates does not collect, store, transmit or share any personal data. There is no account, no analytics, no advertising and no third-party tracking library in the app. The only thing it sends over the network is a request for exchange rates, and those requests contain nothing about you.

## What the app sends

To show current rates the app makes plain HTTPS requests to two public services:

- `api.frankfurter.dev` — daily reference rates for currencies, gold and silver.
- `latest.currency-api.pages.dev`, with `cdn.jsdelivr.net` as a fallback — the daily rate for bitcoin.

These requests contain no identifiers, no account, no device information beyond what any HTTP client sends, and nothing you typed into the app. Amounts you enter never leave the device.

As with any request to any website, the operators of those services and their content delivery networks can see the IP address the request came from and may keep server logs. Their handling of that data is governed by their own terms — the app has no control over it and no agreement with them. If that matters to you, the app remains fully usable with no network at all: it keeps converting from the rates it has already stored.

## What stays on the device

The app saves, in its own private storage:

- your list of currencies and their order,
- the currency you last used as the base and the amount in it,
- the last rate sets it downloaded, with the date they carry and the moment they were fetched.

None of this is transmitted anywhere. It is removed when you uninstall the app. The app does not use cloud backup.

## Permissions

One permission: internet access (`android.permission.INTERNET`), used only for the rate requests described above. The app does not request location, contacts, storage, camera, microphone or any other permission.

## Children

The app is a general-purpose utility and is not directed at children. It collects no data from anyone, including children.

## Changes

If this policy ever changes, the new version will be published at this address and the effective date above will be updated. Material changes will also be noted in the app's release notes.

## Contact

Questions about this policy: open an issue at [github.com/dzhokhov/currency-rates-android/issues](https://github.com/dzhokhov/currency-rates-android/issues), or write to the developer email shown on the app's Google Play listing.

---

# Политика конфиденциальности

**Приложение:** «Курсы валют» (`io.github.dzhokhov.quotes`; в сборках до 0.4.0 — `com.dzhokhov.currencyrates`)
**Дата вступления в силу:** 23 августа 2026

## Коротко

Приложение не собирает, не хранит, не передаёт и не раскрывает персональные данные. В нём нет учётных записей, аналитики, рекламы и сторонних библиотек слежения. Единственное, что уходит в сеть, — запрос курсов валют, и в нём нет никаких сведений о вас.

## Что приложение отправляет

Чтобы показать актуальные курсы, приложение делает обычные HTTPS-запросы к двум публичным сервисам:

- `api.frankfurter.dev` — ежедневные справочные курсы валют, золота и серебра;
- `latest.currency-api.pages.dev`, запасной адрес `cdn.jsdelivr.net` — ежедневный курс биткоина.

В этих запросах нет идентификаторов, учётных записей, сведений об устройстве сверх того, что отправляет любой HTTP-клиент, и ничего из введённого вами. Введённые суммы никогда не покидают устройство.

Как и при обращении к любому сайту, операторы этих сервисов и их сети доставки содержимого видят IP-адрес, с которого пришёл запрос, и могут вести журналы. Обработка этих сведений подчиняется их собственным условиям — приложение на неё не влияет и договора с ними не имеет. Если это важно, приложением можно пользоваться вообще без сети: оно продолжает считать по уже сохранённым курсам.

## Что остаётся на устройстве

Приложение сохраняет в своём личном хранилище:

- список валют и его порядок;
- валюту, которая последней была основной, и сумму в ней;
- последние загруженные наборы курсов с датой набора и моментом загрузки.

Ничего из этого никуда не передаётся. Всё удаляется вместе с приложением. Облачное резервное копирование не используется.

## Разрешения

Одно разрешение — доступ в интернет (`android.permission.INTERNET`), только для описанных выше запросов курсов. Приложение не запрашивает местоположение, контакты, хранилище, камеру, микрофон и любые другие разрешения.

## Дети

Приложение — утилита общего назначения и не адресовано детям. Оно не собирает данные ни у кого, включая детей.

## Изменения

Если политика изменится, новая версия будет опубликована по этому адресу, а дата вступления в силу выше будет обновлена. О существенных изменениях будет сказано и в примечаниях к выпуску.

## Связь

Вопросы по политике: создайте обращение на [github.com/dzhokhov/currency-rates-android/issues](https://github.com/dzhokhov/currency-rates-android/issues) или напишите на адрес разработчика, указанный в карточке приложения в Google Play.
