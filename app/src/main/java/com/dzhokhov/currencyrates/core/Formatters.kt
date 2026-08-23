package com.dzhokhov.currencyrates.core

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Форматирование по языку устройства: числа с группировкой (BigDecimal без double), даты в коротком формате. */
class Formatters(val locale: Locale, private val zone: ZoneId = ZoneId.systemDefault()) {
    companion object {
        const val MINUS = '\u2212'
        const val TIMES = '\u00D7'
        const val DIVIDE = '\u00F7'
    }

    val decimalSeparator: Char = DecimalFormatSymbols.getInstance(locale).decimalSeparator

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

    /** Число с фиксированным числом знаков, группировкой разрядов и HALF_UP. */
    fun number(value: BigDecimal, scale: Int): String {
        val f = NumberFormat.getNumberInstance(locale) as DecimalFormat
        f.minimumFractionDigits = scale
        f.maximumFractionDigits = scale
        f.roundingMode = DisplayRules.ROUNDING
        f.isGroupingUsed = true
        return f.format(value)
    }

    /** Значение для поля ввода: без группировки, с разделителем языка, округлено до scale. */
    fun plain(value: BigDecimal, scale: Int): String =
        DisplayRules.round(value, scale).toPlainString().replace('.', decimalSeparator)

    /** Набранный текст (нормализованная точка) → текст с разделителем языка. */
    fun inputText(normalized: String): String = normalized.replace('.', decimalSeparator)

    /** Выражение (точка, + - * /) → показ: разделитель языка и знаки + − × ÷. */
    fun expression(normalized: String): String {
        val sb = StringBuilder(normalized.length)
        for (c in normalized) {
            sb.append(
                when (c) {
                    '.' -> decimalSeparator
                    '-' -> MINUS
                    '*' -> TIMES
                    '/' -> DIVIDE
                    else -> c
                },
            )
        }
        return sb.toString()
    }

    fun date(d: LocalDate): String = dateFormatter.format(d)

    fun dateTime(i: Instant): String {
        val zdt = i.atZone(zone)
        return dateFormatter.format(zdt) + " " + timeFormatter.format(zdt)
    }
}
