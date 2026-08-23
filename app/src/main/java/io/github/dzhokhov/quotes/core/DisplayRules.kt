package io.github.dzhokhov.quotes.core

import java.math.BigDecimal
import java.math.RoundingMode

/** Строка курса: «1 {unitCode} = {value} {valueCode}» с числом знаков scale. */
data class RateLine(val unitCode: String, val value: BigDecimal, val valueCode: String, val scale: Int)

/** Чистые правила показа над BigDecimal: знаки курса, знаки суммы, переворот. Округление — HALF_UP, один раз. */
object DisplayRules {
    val ROUNDING: RoundingMode = RoundingMode.HALF_UP
    private val HUNDRED = BigDecimal(100)
    private val FLIP_BELOW = BigDecimal("0.001")
    private const val MAX_SCALE = 8
    private const val SIGNIFICANT = 4

    /** floor(log10 |x|) для x ≠ 0. */
    private fun exponent(x: BigDecimal): Int = x.precision() - x.scale() - 1

    /** Масштаб, дающий 4 значащих цифры, не более 8 знаков. */
    private fun significantScale(x: BigDecimal): Int = minOf(MAX_SCALE, SIGNIFICANT - 1 - exponent(x))

    /** Курс X: ≥ 100 → 2; 1 ≤ X < 100 → 4; < 1 → 4 значащих, ≤ 8 знаков. */
    fun rateScale(x: BigDecimal): Int = when {
        x.signum() == 0 -> 2
        x >= HUNDRED -> 2
        x >= BigDecimal.ONE -> 4
        else -> maxOf(0, significantScale(x))
    }

    /** Сумма: FIAT — ровно 2; METAL/CRYPTO — max(2, 4 значащих), ≤ 8; ненулевое, округляющееся до нуля, — 4 значащих. */
    fun amountScale(value: BigDecimal, kind: Kind): Int {
        if (value.signum() == 0) return 2
        val sig = minOf(MAX_SCALE, maxOf(2, significantScale(value)))
        val scale = when (kind) {
            Kind.FIAT -> if (value.setScale(2, ROUNDING).signum() == 0) sig else 2
            Kind.METAL, Kind.CRYPTO -> sig
        }
        return if (value.setScale(scale, ROUNDING).signum() == 0) 2 else scale
    }

    fun round(value: BigDecimal, scale: Int): BigDecimal = value.setScale(scale, ROUNDING)

    /** Текст поля — значение без хвостовых нулей дробной части, с точкой; ноль и null — «0». */
    fun fieldText(value: BigDecimal?): String {
        if (value == null || value.signum() == 0) return "0"
        return value.stripTrailingZeros().toPlainString()
    }

    /** Строка курса под суммой; переворот при X < 0,001. null — у одной из валют нет курса. */
    fun rateLine(base: String, row: String, rates: ResolvedRates): RateLine? {
        val rBase = rates.rate(base) ?: return null
        val rRow = rates.rate(row) ?: return null
        val x = rRow.divide(rBase, Converter.MC)
        return if (x < FLIP_BELOW) {
            val y = rBase.divide(rRow, Converter.MC)
            RateLine(row, y, base, rateScale(y))
        } else {
            RateLine(base, x, row, rateScale(x))
        }
    }
}
