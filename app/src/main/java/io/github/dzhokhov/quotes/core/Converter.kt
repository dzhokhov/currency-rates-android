package io.github.dzhokhov.quotes.core

import java.math.BigDecimal
import java.math.MathContext

/** Пересчёт через внутреннюю базу USD на BigDecimal: умножение точное, одно деление с 34 значащими. */
object Converter {
    val MC: MathContext = MathContext.DECIMAL128

    /** amount × r(to) ÷ r(from); null, если у одной из валют нет курса. */
    fun convert(amount: BigDecimal, from: String, to: String, rates: ResolvedRates): BigDecimal? {
        val rFrom = rates.rate(from) ?: return null
        val rTo = rates.rate(to) ?: return null
        return amount.multiply(rTo).divide(rFrom, MC)
    }

    /** Кросс-курс X(base → row) = r(row) ÷ r(base). */
    fun crossRate(base: String, row: String, rates: ResolvedRates): BigDecimal? {
        val rBase = rates.rate(base) ?: return null
        val rRow = rates.rate(row) ?: return null
        return rRow.divide(rBase, MC)
    }
}
