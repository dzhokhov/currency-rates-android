package com.dzhokhov.currencyrates

import com.dzhokhov.currencyrates.core.CurrencyRegistry
import com.dzhokhov.currencyrates.core.Origin
import com.dzhokhov.currencyrates.core.RateSet
import com.dzhokhov.currencyrates.core.ResolvedRates
import com.dzhokhov.currencyrates.sources.CurrencyApiSource
import com.dzhokhov.currencyrates.sources.FrankfurterV2Source
import java.io.File
import java.time.Instant

/** Доступ к встроенным активам и записанным ответам источников из JVM-тестов (рабочий каталог — модуль app). */
object TestFiles {
    private fun find(vararg candidates: String): File =
        candidates.map { File(it) }.firstOrNull { it.exists() }
            ?: error("file not found: ${candidates.joinToString()} (cwd=${File(".").absolutePath})")

    fun asset(path: String): String = find("src/main/assets/$path", "app/src/main/assets/$path").readText()
    fun sample(name: String): String = find("src/test/resources/samples/$name", "app/src/test/resources/samples/$name").readText()

    val frankfurterBody: String by lazy { asset("rates/frankfurter-v2.json") }
    val currencyApiBody: String by lazy { asset("rates/currency-api.json") }
    val currenciesBody: String by lazy { asset("currencies/frankfurter-v2.json") }

    /** Встроенные наборы как ResolvedRates (fetchedAt = null). */
    fun embeddedRates(fetchedAt: Instant? = null): ResolvedRates {
        val f = FrankfurterV2Source.parse(frankfurterBody)
        val c = CurrencyApiSource.parse(currencyApiBody)
        val origin = if (fetchedAt == null) Origin.EMBEDDED else Origin.CACHED
        return ResolvedRates(
            mapOf(
                CurrencyRegistry.FRANKFURTER to RateSet(CurrencyRegistry.FRANKFURTER, f.rows, fetchedAt, origin),
                CurrencyRegistry.CURRENCY_API to RateSet(CurrencyRegistry.CURRENCY_API, c.rows, fetchedAt, origin),
            ),
        )
    }
}
