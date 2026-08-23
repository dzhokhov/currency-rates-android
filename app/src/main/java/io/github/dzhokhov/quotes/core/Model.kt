package io.github.dzhokhov.quotes.core

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class RateRow(val rate: BigDecimal, val date: LocalDate)

enum class Origin { EMBEDDED, CACHED }

/** Набор курсов одного источника: USD → код. fetchedAt == null — встроенный набор. */
data class RateSet(
    val sourceId: String,
    val rows: Map<String, RateRow>,
    val fetchedAt: Instant?,
    val origin: Origin,
) {
    val base: String get() = "USD"
    val layer: String get() = "daily"
}

/** Действующие наборы по источникам; какая строка действует для кода, решает только реестр. */
class ResolvedRates(val sets: Map<String, RateSet>) {
    fun row(code: String): RateRow? {
        val set = sets[CurrencyRegistry.sourceFor(code)] ?: return null
        set.rows[code]?.let { return it }
        if (code == CurrencyRegistry.USD) {
            val anyDate = set.rows.values.firstOrNull()?.date ?: return null
            return RateRow(BigDecimal.ONE, anyDate)
        }
        return null
    }

    fun rate(code: String): BigDecimal? = row(code)?.rate
    fun hasRate(code: String): Boolean = row(code) != null
    fun codesWithRate(sourceId: String): Set<String> = sets[sourceId]?.rows?.keys ?: emptySet()

    companion object {
        val EMPTY = ResolvedRates(emptyMap())
    }
}

/**
 * Состояние пользователя. amount == null — пустое поле (ноль).
 * amountText — текст суммы как набран (нормализованная точка), не null тогда и только тогда, когда amountTyped.
 */
data class UserState(
    val rows: List<String>,
    val base: String,
    val amount: BigDecimal?,
    val amountTyped: Boolean,
    val amountText: String?,
) {
    /** Возвращает нарушенный инвариант или null. */
    fun validate(): String? {
        if (rows.size < 2) return "rows<2"
        if (rows.toSet().size != rows.size) return "duplicate_row"
        rows.firstOrNull { !CurrencyRegistry.isValidCode(it) }?.let { return "bad_code=$it" }
        if (base !in rows) return "base_not_in_rows"
        if (amountTyped && amountText == null) return "amountText_missing"
        if (!amountTyped && amountText != null) return "amountText_without_typed"
        if (amountText != null && !InputRules.isValid(amountText, CurrencyRegistry.kind(base))) return "amountText_invalid"
        return null
    }

    companion object {
        val START = UserState(
            rows = CurrencyRegistry.startRows,
            base = CurrencyRegistry.START_BASE,
            amount = CurrencyRegistry.startAmount,
            // 0.3.0: текст «как набран» не хранится; поля схемы 1 остаются для совместимости.
            amountTyped = false,
            amountText = null,
        )
    }
}

enum class Outcome { OK, PARTIAL, FAILED, NO_NETWORK }

enum class SourceResult { OK, FAILED, NO_NETWORK }

/** Состояние обновления; perSource — результаты по источникам последней попытки (только опрошенные). */
data class RefreshState(
    val lastAttemptAt: Instant?,
    val lastAttemptOutcome: Outcome?,
    val lastFullSuccessAt: Instant?,
    val perSource: Map<String, SourceResult>,
) {
    companion object {
        val EMPTY = RefreshState(null, null, null, emptyMap())
    }
}
