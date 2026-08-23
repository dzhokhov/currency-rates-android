package io.github.dzhokhov.quotes.core

import io.github.dzhokhov.quotes.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CurrencyNamesPickerTest {
    private val names = CurrencyNames.parse(TestFiles.currenciesBody)
    private val en = Locale.US
    private val ru = Locale("ru", "RU")
    private val rates = TestFiles.embeddedRates()

    @Test fun englishNamesFromEmbeddedList() {
        assertEquals("Gold (Troy Ounce)", names.english("XAU"))
        assertEquals("Silver (Troy Ounce)", names.english("XAG"))
        assertEquals("Serbian Dinar", names.english("RSD"))
        assertEquals("Bitcoin", names.english("BTC"))
        assertEquals("ZZZ", names.english("ZZZ"))
        assertEquals("Serbian Dinar", names.display("RSD", en))
        assertNull(names.localized("RSD", en))
    }

    @Test fun russianNamesFromDeviceWhenAvailable() {
        val usd = names.localized("USD", ru)
        assertTrue("device gives a russian name for USD: $usd", usd != null && !usd.equals("USD", ignoreCase = true))
        assertNotEquals(names.english("USD"), names.display("USD", ru))
        assertNull(names.localized("BTC", ru))
        assertEquals("Bitcoin", names.display("BTC", ru))
        assertEquals("ZZZ", names.display("ZZZ", ru))
    }

    @Test fun searchByCodeAndNames() {
        assertTrue(names.matches("XAU", "gold", en))
        assertTrue(names.matches("XAU", "XAU", en))
        assertTrue(names.matches("XAU", "xau", en))
        assertTrue(names.matches("BTC", "btc", en))
        assertTrue(names.matches("BTC", "Bitcoin", en))
        assertTrue(names.matches("RSD", "dinar", en))
        assertTrue(names.matches("RSD", "rsd", en))
        assertTrue(names.matches("RUB", "rub", en))
        assertTrue(names.matches("RUB", "", en))
        assertFalse(names.matches("RUB", "gold", en))
        assertTrue(names.matches("USD", "долл", ru))
    }

    @Test fun pickerListCompositionAndFilter() {
        val (group, fiat) = PickerList.build(rates, UserState.START.rows)
        assertEquals(listOf("XAU", "XAG", "BTC"), group.map { it.code })
        assertTrue(group.none { it.present })
        assertEquals(fiat.map { it.code }, fiat.map { it.code }.sorted())
        assertTrue(fiat.all { it.kind == Kind.FIAT })
        assertTrue(fiat.none { it.code in setOf("XAU", "XAG", "XPD", "XPT", "BTC") })
        assertTrue(fiat.any { it.code == "XDR" })
        assertTrue(fiat.first { it.code == "RUB" }.present)
        assertFalse(fiat.first { it.code == "CHF" }.present)
        assertEquals(165 - 4, fiat.size) // 165 кодов Frankfurter минус XAU, XAG, XPD, XPT; USD остаётся
    }

    @Test fun pickerFilterFindsGoldAndRub() {
        val (group, fiat) = PickerList.build(rates, UserState.START.rows)
        assertEquals(listOf("XAU"), PickerList.filter(group, "gold", names, en).map { it.code })
        assertEquals(listOf("BTC"), PickerList.filter(group, "btc", names, en).map { it.code })
        // «rub» по подстроке названия находит и Aruban Florin (AWG), и Belarusian Ruble (BYN); RUB приглушён как присутствующий
        val rub = PickerList.filter(fiat, "rub", names, en)
        assertEquals(listOf("AWG", "BYN", "RUB"), rub.map { it.code })
        assertTrue(rub.first { it.code == "RUB" }.present)
        assertTrue(rub.filter { it.code != "RUB" }.none { it.present })
        assertTrue(PickerList.filter(fiat, "dinar", names, en).map { it.code }.containsAll(listOf("RSD", "BHD", "DZD")))
        // Без курса в наборе — нет в списке выбора
        val onlyFr = ResolvedRates(rates.sets.filterKeys { it == CurrencyRegistry.FRANKFURTER })
        assertEquals(listOf("XAU", "XAG"), PickerList.build(onlyFr, emptyList()).first.map { it.code })
    }
}
