package com.dzhokhov.currencyrates.core

import java.math.BigDecimal

enum class Kind { FIAT, METAL, CRYPTO }

/** Описание источника: идентификатор, адреса (основной и запасной), слой. Набор всегда с базой USD. */
data class SourceSpec(val id: String, val urls: List<String>) {
    val base: String get() = "USD"
    val layer: String get() = "daily"
}

/** Статическая таблица «валюта → вид, источник, точность, флаг». Экрана нет. */
object CurrencyRegistry {
    const val FRANKFURTER = "frankfurter-v2"
    const val CURRENCY_API = "currency-api"
    const val USD = "USD"

    val sources: List<SourceSpec> = listOf(
        SourceSpec(FRANKFURTER, listOf("https://api.frankfurter.dev/v2/rates?base=USD")),
        SourceSpec(
            CURRENCY_API,
            listOf(
                "https://latest.currency-api.pages.dev/v1/currencies/usd.json",
                "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json",
            ),
        ),
    )

    fun source(id: String): SourceSpec = sources.first { it.id == id }

    /** Группа «Золото, серебро, биткоин» в списке выбора — ровно в этом порядке. */
    val pickerGroup: List<String> = listOf("XAU", "XAG", "BTC")

    val startRows: List<String> = listOf("USD", "EUR", "RSD", "RUB", "CNY", "AED", "JPY", "TRY", "BAM")
    const val START_BASE = "RSD"
    val startAmount: BigDecimal = BigDecimal("1000")

    const val BTC_NAME = "Bitcoin"

    fun kind(code: String): Kind = when (code) {
        "XAU", "XAG", "XPD", "XPT" -> Kind.METAL
        "BTC" -> Kind.CRYPTO
        else -> Kind.FIAT
    }

    /** Источник по валюте: BTC → currency-api, всё остальное → Frankfurter v2. */
    fun sourceFor(code: String): String = if (code == "BTC") CURRENCY_API else FRANKFURTER

    fun inputFractionDigits(kind: Kind): Int = if (kind == Kind.FIAT) 2 else 8

    const val MAX_INTEGER_DIGITS = 12

    /** Регион эмодзи-флага: EUR → EU; коды на X — без региона; металлы и крипта — без флага. */
    fun flagRegion(code: String): String? {
        if (kind(code) != Kind.FIAT) return null
        if (code == "EUR") return "EU"
        if (code.startsWith("X")) return null
        return code.substring(0, 2)
    }

    fun isValidCode(code: String): Boolean = code.length == 3 && code.all { it in 'A'..'Z' }
}
