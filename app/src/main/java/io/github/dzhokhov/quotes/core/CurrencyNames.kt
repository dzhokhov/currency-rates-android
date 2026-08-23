package io.github.dzhokhov.quotes.core

import io.github.dzhokhov.quotes.core.json.JsonValue
import io.github.dzhokhov.quotes.core.json.MiniJson
import java.util.Currency
import java.util.Locale

/**
 * Имена валют: английские из встроенного списка Frankfurter (BTC — константа реестра);
 * Для русского интерфейса — имя от java.util.Currency устройства, если оно не равно коду; иначе английское.
 */
class CurrencyNames(private val english: Map<String, String>) {
    fun english(code: String): String = english[code] ?: if (code == "BTC") CurrencyRegistry.BTC_NAME else code

    fun localized(code: String, locale: Locale): String? {
        if (locale.language != "ru") return null
        return try {
            val name = Currency.getInstance(code).getDisplayName(locale)
            if (name.isNullOrBlank() || name.equals(code, ignoreCase = true)) null else name
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun display(code: String, locale: Locale): String = localized(code, locale) ?: english(code)

    /** Поиск по подстроке без учёта регистра: код, английское и локализованное имя. */
    fun matches(code: String, query: String, locale: Locale): Boolean {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return true
        if (code.lowercase(Locale.ROOT).contains(q)) return true
        if (english(code).lowercase(Locale.ROOT).contains(q)) return true
        val local = localized(code, locale) ?: return false
        return local.lowercase(locale).contains(q)
    }

    companion object {
        val EMPTY = CurrencyNames(emptyMap())

        /** Разбор assets/currencies/frankfurter-v2.json: массив {iso_code, name, …}; лишние ключи пропускаются. */
        fun parse(body: String): CurrencyNames {
            val arr = MiniJson.parse(body) as? JsonValue.JArray ?: return EMPTY
            val map = LinkedHashMap<String, String>()
            for (item in arr.items) {
                val obj = item as? JsonValue.JObject ?: continue
                val code = (obj["iso_code"] as? JsonValue.JString)?.value?.uppercase() ?: continue
                val name = (obj["name"] as? JsonValue.JString)?.value ?: continue
                if (CurrencyRegistry.isValidCode(code)) map[code] = name
            }
            return CurrencyNames(map)
        }
    }
}

/** Элемент списка выбора: present — уже в списке пользователя (приглушён, не выбирается). */
data class PickerEntry(val code: String, val kind: Kind, val present: Boolean)

/** Состав списка выбора: группа XAU, XAG, BTC (только с курсом) + все фиатные с курсом в наборе их источника, по коду. */
object PickerList {
    fun build(rates: ResolvedRates, rows: List<String>): Pair<List<PickerEntry>, List<PickerEntry>> {
        val present = rows.toSet()
        val group = CurrencyRegistry.pickerGroup.filter { rates.hasRate(it) }.map { PickerEntry(it, CurrencyRegistry.kind(it), it in present) }
        val fiat = rates.codesWithRate(CurrencyRegistry.FRANKFURTER)
            .filter { CurrencyRegistry.isValidCode(it) && CurrencyRegistry.kind(it) == Kind.FIAT && rates.hasRate(it) }
            .sorted()
            .map { PickerEntry(it, Kind.FIAT, it in present) }
        return group to fiat
    }

    fun filter(entries: List<PickerEntry>, query: String, names: CurrencyNames, locale: Locale): List<PickerEntry> =
        entries.filter { names.matches(it.code, query, locale) }
}
