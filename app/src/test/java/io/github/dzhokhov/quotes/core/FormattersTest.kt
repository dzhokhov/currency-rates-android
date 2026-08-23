package io.github.dzhokhov.quotes.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class FormattersTest {
    private val zone = ZoneId.of("Europe/Belgrade")
    private val ru = Formatters(Locale("ru", "RU"), zone)
    private val en = Formatters(Locale.US, zone)

    private fun norm(s: String) = s.replace(0x00A0.toChar(), ' ').replace(0x202F.toChar(), ' ').replace(0x2009.toChar(), ' ')

    @Test fun numbersRu() {
        assertEquals("1 582,40", norm(ru.number(BigDecimal("1582.4"), 2)))
        assertEquals("9,95", norm(ru.number(BigDecimal("9.95"), 2)))
        assertEquals("7 844 919,82", norm(ru.number(BigDecimal("7844919.818684"), 2)))
        assertEquals("0,0001275", norm(ru.number(BigDecimal("0.000127471"), 7)))
        assertEquals("0,00", norm(ru.number(BigDecimal.ZERO, 2)))
        assertEquals(',', ru.decimalSeparator)
        assertEquals("100,5", ru.inputText("100.5"))
        assertEquals("9,96", ru.plain(BigDecimal("9.959167"), 2))
    }

    @Test fun numbersEn() {
        assertEquals("1,582.40", en.number(BigDecimal("1582.4"), 2))
        assertEquals("9.95", en.number(BigDecimal("9.95"), 2))
        assertEquals("10,041.00", en.number(BigDecimal("10041"), 2))
        assertEquals('.', en.decimalSeparator)
        assertEquals("007.", en.inputText("007."))
        assertEquals("1000.00", en.plain(BigDecimal("1000"), 2))
    }

    @Test fun expressionDisplay() {
        assertEquals("100+25×3", en.expression("100+25*3"))
        assertEquals("100+25×3", ru.expression("100+25*3"))
        assertEquals("9,95", ru.expression("9.95"))
        assertEquals("9.95", en.expression("9.95"))
        assertEquals("100÷0", en.expression("100/0"))
        assertEquals("−50×2", en.expression("-50*2"))
        assertEquals("", en.expression(""))
    }

    @Test fun halfUpRoundingAtDisplayOnly() {
        assertEquals("0.13", en.number(BigDecimal("0.125"), 2))
        assertEquals("2.35", en.number(BigDecimal("2.345"), 2))
        // BigDecimal подаётся без перевода в double: 16 значащих цифр сохраняются
        assertEquals("47,998,502.53177214", en.number(BigDecimal("47998502.53177214"), 8))
    }

    @Test fun datesShortFormat() {
        val d = LocalDate.of(2026, 8, 22)
        assertEquals("22.08.2026", ru.date(d))
        assertEquals("8/22/26", en.date(d))
        val i = Instant.parse("2026-08-23T07:14:00Z") // 09:14 в Белграде (CEST)
        assertEquals("23.08.2026 09:14", norm(ru.dateTime(i)))
        assertEquals("8/23/26 9:14 AM", norm(en.dateTime(i)))
    }
}
