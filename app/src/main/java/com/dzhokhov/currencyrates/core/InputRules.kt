package com.dzhokhov.currencyrates.core

import java.math.BigDecimal

/**
 * Фильтр ввода суммы. Внутренний текст — нормализованная точка; принимаются «.» и «,».
 * Только цифры и один разделитель; до 12 цифр до разделителя; после — 2 (FIAT) или 8 (METAL/CRYPTO).
 */
object InputRules {
    fun normalize(raw: String): String = raw.replace(',', '.')

    fun isValid(text: String, kind: Kind): Boolean {
        val maxFraction = CurrencyRegistry.inputFractionDigits(kind)
        val dot = text.indexOf('.')
        if (dot >= 0 && text.indexOf('.', dot + 1) >= 0) return false
        val intPart = if (dot >= 0) text.substring(0, dot) else text
        val fraction = if (dot >= 0) text.substring(dot + 1) else ""
        if (!intPart.all { it in '0'..'9' } || !fraction.all { it in '0'..'9' }) return false
        if (intPart.length > CurrencyRegistry.MAX_INTEGER_DIGITS) return false
        if (fraction.length > maxFraction) return false
        return true
    }

    /** Возвращает принятый нормализованный текст или прежний, если новый не проходит фильтр. */
    fun accept(previous: String, proposed: String, kind: Kind): String {
        val normalized = normalize(proposed)
        return if (isValid(normalized, kind)) normalized else previous
    }

    /** Текст → точное значение; пустой текст или одинокий разделитель = ноль (null). */
    fun toAmount(text: String): BigDecimal? {
        val trimmed = text.trimEnd('.')
        if (trimmed.isEmpty()) return null
        return BigDecimal(trimmed)
    }
}
