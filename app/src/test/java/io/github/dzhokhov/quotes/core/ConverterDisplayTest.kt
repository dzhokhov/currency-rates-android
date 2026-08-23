package io.github.dzhokhov.quotes.core

import io.github.dzhokhov.quotes.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class ConverterDisplayTest {
    private val rates = TestFiles.embeddedRates()
    private val thousand = BigDecimal("1000")

    private fun amountText(amount: BigDecimal, base: String, row: String): String {
        val v = Converter.convert(amount, base, row, rates)!!
        return DisplayRules.round(v, DisplayRules.amountScale(v, CurrencyRegistry.kind(row))).toPlainString()
    }

    private fun rateText(base: String, row: String): String {
        val l = DisplayRules.rateLine(base, row, rates)!!
        return "1 ${l.unitCode} = ${DisplayRules.round(l.value, l.scale).toPlainString()} ${l.valueCode}"
    }

    @Test fun goldenAmountsBaseRsd1000() {
        assertEquals("9.96", amountText(thousand, "RSD", "USD"))
        assertEquals("8.52", amountText(thousand, "RSD", "EUR"))
        assertEquals("833.08", amountText(thousand, "RSD", "RUB"))
        assertEquals("16.67", amountText(thousand, "RSD", "BAM"))
        assertEquals("0.002191", amountText(thousand, "RSD", "XAU"))
        assertEquals("0.1516", amountText(thousand, "RSD", "XAG"))
        assertEquals("0.0001275", amountText(thousand, "RSD", "BTC"))
        assertEquals("1000.00", amountText(thousand, "RSD", "RSD"))
    }

    @Test fun goldenRateLinesBaseRsd() {
        assertEquals("1 RSD = 0.009959 USD", rateText("RSD", "USD"))
        assertEquals("1 RSD = 0.008521 EUR", rateText("RSD", "EUR"))
        assertEquals("1 RSD = 0.8331 RUB", rateText("RSD", "RUB"))
        assertEquals("1 XAU = 456409.09 RSD", rateText("RSD", "XAU"))
        assertEquals("1 XAG = 6597.24 RSD", rateText("RSD", "XAG"))
        assertEquals("1 BTC = 7844919.82 RSD", rateText("RSD", "BTC"))
        assertEquals("1 RSD = 1.5818 JPY", rateText("RSD", "JPY"))
    }

    @Test fun rateLinesOtherBases() {
        assertEquals("1 USD = 100.41 RSD", rateText("USD", "RSD"))
        assertEquals("1 USD = 0.8556 EUR", rateText("USD", "EUR"))
        assertEquals("1 USD = 26022.00 VND", rateText("USD", "VND"))
        assertEquals("1 XAU = 4545.45 USD", rateText("USD", "XAU"))
        assertEquals("1 USD = 0.01522 XAG", rateText("USD", "XAG"))
        assertEquals("1 EUR = 1.9558 BAM", rateText("EUR", "BAM"))
        // При базе VND строка USD переворачивается (X = 1/26022 < 0,001)
        assertEquals("1 USD = 26022.00 VND", rateText("VND", "USD"))
    }

    @Test fun acceptance3RoundTrip() {
        assertEquals("10041.00", amountText(BigDecimal("100"), "USD", "RSD"))
        assertEquals("100.00", amountText(BigDecimal("10041"), "RSD", "USD"))
        // Обратный путь без промежуточного округления
        val rsd = Converter.convert(BigDecimal("100"), "USD", "RSD", rates)!!
        val back = Converter.convert(rsd, "RSD", "USD", rates)!!
        assertEquals(0, back.compareTo(BigDecimal("100")))
    }

    @Test fun crossRateWithoutIntermediateRounding() {
        val x = Converter.crossRate("RSD", "USD", rates)!!
        assertEquals(0, x.compareTo(BigDecimal.ONE.divide(BigDecimal("100.41"), Converter.MC)))
        assertEquals(34, Converter.MC.precision)
        assertNull(Converter.convert(thousand, "RSD", "ZZZ", rates))
        assertNull(DisplayRules.rateLine("RSD", "ZZZ", rates))
        assertNull(Converter.convert(thousand, "ZZZ", "RSD", rates))
    }

    @Test fun rateScaleRules() {
        assertEquals(2, DisplayRules.rateScale(BigDecimal("100.46")))
        assertEquals(2, DisplayRules.rateScale(BigDecimal("7741025.15")))
        assertEquals(2, DisplayRules.rateScale(BigDecimal("100")))
        assertEquals(4, DisplayRules.rateScale(BigDecimal("99.9999")))
        assertEquals(4, DisplayRules.rateScale(BigDecimal("1.1678")))
        assertEquals(4, DisplayRules.rateScale(BigDecimal("1")))
        assertEquals(4, DisplayRules.rateScale(BigDecimal("0.8563")))
        assertEquals(4, DisplayRules.rateScale(BigDecimal("0.4783")))
        assertEquals(5, DisplayRules.rateScale(BigDecimal("0.03656")))
        assertEquals(6, DisplayRules.rateScale(BigDecimal("0.009954")))
        assertEquals(8, DisplayRules.rateScale(BigDecimal("0.0000012345")))
        assertEquals(2, DisplayRules.rateScale(BigDecimal.ZERO))
    }

    @Test fun amountScaleRules() {
        assertEquals(2, DisplayRules.amountScale(BigDecimal("1582.40"), Kind.FIAT))
        assertEquals(2, DisplayRules.amountScale(BigDecimal("0.005"), Kind.FIAT))
        // Ненулевое, округляющееся до нуля, — четыре значащих
        assertEquals(6, DisplayRules.amountScale(BigDecimal("0.001"), Kind.FIAT))
        assertEquals(7, DisplayRules.amountScale(BigDecimal("0.0001292"), Kind.CRYPTO))
        assertEquals(6, DisplayRules.amountScale(BigDecimal("0.002927"), Kind.METAL))
        assertEquals(2, DisplayRules.amountScale(BigDecimal("12.3456"), Kind.METAL))
        assertEquals(2, DisplayRules.amountScale(BigDecimal("1234.5678"), Kind.METAL))
        assertEquals(8, DisplayRules.amountScale(BigDecimal("0.000000012345"), Kind.CRYPTO))
        // И так ноль — «0,00»
        assertEquals(2, DisplayRules.amountScale(BigDecimal("0.000000001"), Kind.CRYPTO))
        assertEquals(2, DisplayRules.amountScale(BigDecimal("0.000000001"), Kind.FIAT))
        assertEquals(2, DisplayRules.amountScale(BigDecimal.ZERO, Kind.FIAT))
        assertEquals("0.00", DisplayRules.round(BigDecimal.ZERO, 2).toPlainString())
    }

    @Test fun zeroAmountGivesZeroRowsButRateLinesStay() {
        val v = Converter.convert(BigDecimal.ZERO, "RSD", "USD", rates)!!
        assertEquals("0.00", DisplayRules.round(v, DisplayRules.amountScale(v, Kind.FIAT)).toPlainString())
        assertEquals("1 RSD = 0.009959 USD", rateText("RSD", "USD"))
    }
}
