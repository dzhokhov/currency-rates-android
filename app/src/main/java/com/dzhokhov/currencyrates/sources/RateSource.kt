package com.dzhokhov.currencyrates.sources

import com.dzhokhov.currencyrates.core.CurrencyRegistry
import com.dzhokhov.currencyrates.core.RateRow
import com.dzhokhov.currencyrates.core.SourceSpec
import com.dzhokhov.currencyrates.core.json.JsonValue
import com.dzhokhov.currencyrates.core.json.MiniJson
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Ответ не того формата (разбор JSON прошёл, но содержимое негодное). */
class SourceFormatException(message: String) : Exception(message)

data class ParsedSet(val rows: Map<String, RateRow>, val skipped: Int) {
    val dates: Set<LocalDate> get() = rows.values.map { it.date }.toSet()
}

/** Адаптер источника: адреса и разбор тела ответа в строки USD → код. */
interface RateSource {
    val spec: SourceSpec

    /** Бросает JsonParseException или SourceFormatException; ноль годных строк — ошибка. */
    fun parse(body: String): ParsedSet
}

internal fun positiveRate(v: JsonValue?): BigDecimal? {
    val n = (v as? JsonValue.JNumber) ?: return null
    val d = n.toBigDecimal()
    return if (d.signum() > 0) d else null
}

internal fun isoDate(v: JsonValue?): LocalDate? {
    val s = (v as? JsonValue.JString)?.value ?: return null
    return try {
        LocalDate.parse(s)
    } catch (e: DateTimeParseException) {
        null
    }
}

/** Frankfurter v2: массив {date, base:"USD", quote, rate}; даты свои у каждой строки. */
object FrankfurterV2Source : RateSource {
    override val spec: SourceSpec = CurrencyRegistry.source(CurrencyRegistry.FRANKFURTER)

    override fun parse(body: String): ParsedSet {
        val root = MiniJson.parse(body) as? JsonValue.JArray ?: throw SourceFormatException("expected array")
        val rows = LinkedHashMap<String, RateRow>()
        var skipped = 0
        for (item in root.items) {
            val obj = item as? JsonValue.JObject
            val base = (obj?.get("base") as? JsonValue.JString)?.value
            val quote = (obj?.get("quote") as? JsonValue.JString)?.value?.uppercase()
            val rate = positiveRate(obj?.get("rate"))
            val date = isoDate(obj?.get("date"))
            if (obj == null || base != "USD" || quote == null || !CurrencyRegistry.isValidCode(quote) || rate == null || date == null) {
                skipped++
                continue
            }
            rows[quote] = RateRow(rate, date)
        }
        if (rows.isEmpty()) throw SourceFormatException("no valid rows (skipped=$skipped)")
        return ParsedSet(rows, skipped)
    }
}

/** currency-api: {date, usd:{код_в_нижнем_регистре: число}}; одна дата всем строкам. */
object CurrencyApiSource : RateSource {
    override val spec: SourceSpec = CurrencyRegistry.source(CurrencyRegistry.CURRENCY_API)

    override fun parse(body: String): ParsedSet {
        val root = MiniJson.parse(body) as? JsonValue.JObject ?: throw SourceFormatException("expected object")
        val date = isoDate(root["date"]) ?: throw SourceFormatException("missing or invalid date")
        val usd = root["usd"] as? JsonValue.JObject ?: throw SourceFormatException("missing usd object")
        val rows = LinkedHashMap<String, RateRow>()
        var skipped = 0
        for ((key, value) in usd.fields) {
            val code = key.uppercase()
            val rate = positiveRate(value)
            if (!CurrencyRegistry.isValidCode(code) || rate == null) {
                skipped++
                continue
            }
            rows[code] = RateRow(rate, date)
        }
        if (rows.isEmpty()) throw SourceFormatException("no valid rows (skipped=$skipped)")
        return ParsedSet(rows, skipped)
    }
}

object RateSources {
    val all: List<RateSource> = listOf(FrankfurterV2Source, CurrencyApiSource)
    fun byId(id: String): RateSource = all.first { it.spec.id == id }
}
