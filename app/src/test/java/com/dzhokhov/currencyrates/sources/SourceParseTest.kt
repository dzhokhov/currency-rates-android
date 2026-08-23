package com.dzhokhov.currencyrates.sources

import com.dzhokhov.currencyrates.TestFiles
import com.dzhokhov.currencyrates.core.CurrencyRegistry
import com.dzhokhov.currencyrates.core.json.JsonParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class SourceParseTest {

    @Test fun frankfurterEmbeddedSetParses() {
        val set = FrankfurterV2Source.parse(TestFiles.frankfurterBody)
        assertEquals(165, set.rows.size)
        assertEquals(0, set.skipped)
        for (code in CurrencyRegistry.startRows + listOf("XAU", "XAG", "VND")) {
            assertNotNull("missing $code", set.rows[code])
        }
        assertEquals(BigDecimal("100.41"), set.rows["RSD"]!!.rate)
        assertEquals(LocalDate.of(2026, 8, 22), set.rows["RSD"]!!.date)
        assertEquals(LocalDate.of(2026, 8, 21), set.rows["XAU"]!!.date)
        assertEquals(BigDecimal("1.0"), set.rows["USD"]!!.rate)
        assertEquals(setOf(LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 22)), set.dates)
    }

    @Test fun currencyApiEmbeddedSetParses() {
        val set = CurrencyApiSource.parse(TestFiles.currencyApiBody)
        assertTrue(set.rows.size > 280)
        assertTrue("non-3-letter keys skipped", set.skipped > 0)
        assertEquals(BigDecimal("0.000012799366"), set.rows["BTC"]!!.rate)
        assertEquals(BigDecimal("47998502.53177214"), set.rows["TRL"]!!.rate)
        assertEquals(LocalDate.of(2026, 8, 22), set.rows["BTC"]!!.date)
        assertEquals(setOf(LocalDate.of(2026, 8, 22)), set.dates)
        assertTrue(set.rows.keys.all { CurrencyRegistry.isValidCode(it) })
    }

    @Test fun embeddedSetsCoverPickerAndStartList() {
        val rates = TestFiles.embeddedRates()
        for (code in CurrencyRegistry.startRows + CurrencyRegistry.pickerGroup) {
            assertTrue("no rate for $code", rates.hasRate(code))
        }
        assertEquals(CurrencyRegistry.CURRENCY_API, CurrencyRegistry.sourceFor("BTC"))
        assertEquals(CurrencyRegistry.FRANKFURTER, CurrencyRegistry.sourceFor("XAU"))
        // Фиатные строки currency-api никогда не действуют: RSD берётся из Frankfurter
        assertEquals(BigDecimal("100.41"), rates.rate("RSD"))
    }

    @Test fun frankfurterSkipsBadRowsAndCountsThem() {
        val body = """[
            {"date":"2026-08-22","base":"USD","quote":"eur","rate":0.85559},
            {"date":"2026-08-22","base":"USD","quote":"ZZZ","rate":0},
            {"date":"2026-08-22","base":"USD","quote":"NEG","rate":-1},
            {"date":"2026-08-22","base":"USD","quote":"STR","rate":"1.2"},
            {"date":"2026-08-22","base":"USD","quote":"TOOLONG","rate":1.2},
            {"date":"2026-08-22","base":"EUR","quote":"RSD","rate":117.3},
            {"date":"not-a-date","base":"USD","quote":"RSD","rate":100.41},
            {"date":"2026-08-22","base":"USD","quote":"RSD","rate":100.41,"extra":{"nested":[1,2,3]},"flag":null},
            42, "str", null
        ]"""
        val set = FrankfurterV2Source.parse(body)
        assertEquals(setOf("EUR", "RSD"), set.rows.keys)
        assertEquals(9, set.skipped)
        assertEquals(BigDecimal("0.85559"), set.rows["EUR"]!!.rate)
    }

    @Test fun frankfurterZeroValidRowsIsError() {
        try {
            FrankfurterV2Source.parse("""[{"date":"2026-08-22","base":"USD","quote":"EUR","rate":0}]""")
            fail()
        } catch (e: SourceFormatException) {
            assertTrue(e.message!!.contains("no valid rows"))
        }
        try {
            FrankfurterV2Source.parse("""{"not":"array"}""")
            fail()
        } catch (e: SourceFormatException) {
        }
        try {
            FrankfurterV2Source.parse("[{")
            fail()
        } catch (e: JsonParseException) {
            assertEquals(2, e.offset)
        }
    }

    @Test fun currencyApiSkipsAndErrors() {
        val set = CurrencyApiSource.parse("""{"date":"2026-08-22","extra":true,"usd":{"1inch":1.5,"btc":0.00001,"eur":"x","zzz":0,"rsd":100.41,"toolong":2}}""")
        assertEquals(setOf("BTC", "RSD"), set.rows.keys)
        assertEquals(4, set.skipped)
        try {
            CurrencyApiSource.parse("""{"usd":{"btc":1}}""")
            fail("missing date")
        } catch (e: SourceFormatException) {
        }
        try {
            CurrencyApiSource.parse("""{"date":"2026-08-22"}""")
            fail("missing usd")
        } catch (e: SourceFormatException) {
        }
        try {
            CurrencyApiSource.parse("""{"date":"2026-08-22","usd":{"1inch":1}}""")
            fail("zero rows")
        } catch (e: SourceFormatException) {
        }
    }

    @Test fun registryFlagRegionsForAllSampleCodes() {
        val set = FrankfurterV2Source.parse(TestFiles.frankfurterBody)
        for (code in set.rows.keys) {
            val region = CurrencyRegistry.flagRegion(code)
            if (code.startsWith("X")) assertEquals("X-code $code has no region", null, region)
            else if (code == "EUR") assertEquals("EU", region)
            else assertEquals(code.substring(0, 2), region)
        }
        assertEquals(null, CurrencyRegistry.flagRegion("BTC"))
        assertEquals("US", CurrencyRegistry.flagRegion("USD"))
        assertEquals("BA", CurrencyRegistry.flagRegion("BAM"))
        assertEquals("RS", CurrencyRegistry.flagRegion("RSD"))
    }

    @Test fun registryPrimaryAndFallbackAddresses() {
        val ca = CurrencyRegistry.source(CurrencyRegistry.CURRENCY_API)
        assertEquals(2, ca.urls.size)
        assertTrue(ca.urls[0].startsWith("https://latest.currency-api.pages.dev/"))
        assertTrue(ca.urls[1].startsWith("https://cdn.jsdelivr.net/"))
        assertEquals(1, CurrencyRegistry.source(CurrencyRegistry.FRANKFURTER).urls.size)
        assertTrue(CurrencyRegistry.sources.all { s -> s.urls.all { it.startsWith("https://") } })
    }
}
