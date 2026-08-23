package com.dzhokhov.currencyrates.core

import com.dzhokhov.currencyrates.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ListOpsAndStateTest {
    private val rates = TestFiles.embeddedRates()
    private val start = UserState.START

    @Test fun startState() {
        assertEquals(listOf("USD", "EUR", "RSD", "RUB", "CNY", "AED", "JPY", "TRY", "BAM"), start.rows)
        assertEquals("RSD", start.base)
        assertEquals(BigDecimal("1000"), start.amount)
        // 0.3.0: текст «как набран» не хранится — первая запись от START уже без него
        assertFalse(start.amountTyped)
        assertNull(start.amountText)
        assertNull(start.validate())
    }

    @Test fun moveToIndex() {
        assertEquals(listOf("USD", "EUR", "RSD", "RUB", "CNY", "AED", "BAM", "JPY", "TRY"), ListOps.move(start, "BAM", 6).rows)
        assertEquals(listOf("BAM", "USD", "EUR", "RSD", "RUB", "CNY", "AED", "JPY", "TRY"), ListOps.move(start, "BAM", 0).rows)
        assertEquals(listOf("EUR", "RSD", "RUB", "CNY", "AED", "JPY", "TRY", "BAM", "USD"), ListOps.move(start, "USD", 8).rows)
        // Клиппинг за границы
        assertEquals(ListOps.move(start, "BAM", 0).rows, ListOps.move(start, "BAM", -5).rows)
        assertEquals(ListOps.move(start, "USD", 8).rows, ListOps.move(start, "USD", 99).rows)
        // Тот же индекс и неизвестный код — без изменений
        assertEquals(start, ListOps.move(start, "BAM", 8))
        assertEquals(start, ListOps.move(start, "ZZZ", 0))
        // Перемещение базовой не меняет базу и сумму
        val moved = ListOps.move(start, "RSD", 0)
        assertEquals("RSD", moved.base)
        assertEquals(start.amount, moved.amount)
        assertFalse(moved.amountTyped)
        assertNull(moved.validate())
    }

    @Test fun moveUpDownWithLimits() {
        assertFalse(ListOps.canMoveUp(start, "USD"))
        assertTrue(ListOps.canMoveUp(start, "EUR"))
        assertFalse(ListOps.canMoveDown(start, "BAM"))
        assertTrue(ListOps.canMoveDown(start, "TRY"))
        assertEquals(start, ListOps.moveUp(start, "USD"))
        assertEquals(start, ListOps.moveDown(start, "BAM"))
        val up = ListOps.moveUp(start, "BAM")
        assertEquals(listOf("USD", "EUR", "RSD", "RUB", "CNY", "AED", "JPY", "BAM", "TRY"), up.rows)
        val down = ListOps.moveDown(start, "USD")
        assertEquals(listOf("EUR", "USD", "RSD", "RUB", "CNY", "AED", "JPY", "TRY", "BAM"), down.rows)
        assertEquals(start, ListOps.moveUp(start, "ZZZ"))
    }

    @Test fun removeNonBaseKeepsBaseAndAmount() {
        val s = ListOps.remove(start, "AED", rates)
        assertEquals(listOf("USD", "EUR", "RSD", "RUB", "CNY", "JPY", "TRY", "BAM"), s.rows)
        assertEquals("RSD", s.base)
        assertEquals(BigDecimal("1000"), s.amount)
        assertFalse(s.amountTyped)
        assertNull(s.amountText)
    }

    @Test fun removeBaseMovesToFirstWithExactEquivalent() {
        val s = ListOps.remove(start, "RSD", rates)
        assertEquals("USD", s.base)
        assertFalse(s.amountTyped)
        assertNull(s.amountText)
        // 1000 RSD = 9.959167… USD — точное значение, остальные строки не сдвигаются
        assertEquals(0, Converter.convert(BigDecimal("1000"), "RSD", "USD", rates)!!.compareTo(s.amount))
        val eurBefore = Converter.convert(BigDecimal("1000"), "RSD", "EUR", rates)!!
        val eurAfter = Converter.convert(s.amount!!, "USD", "EUR", rates)!!
        assertEquals("8.52", DisplayRules.round(eurAfter, 2).toPlainString())
        assertEquals(DisplayRules.round(eurBefore, 2), DisplayRules.round(eurAfter, 2))
    }

    @Test fun removeUnavailableAtTwoRows() {
        val two = UserState(listOf("USD", "EUR"), "USD", BigDecimal.ONE, true, "1")
        assertFalse(ListOps.canRemove(two))
        assertEquals(two, ListOps.remove(two, "EUR", rates))
        assertEquals(two, ListOps.remove(two, "USD", rates))
    }

    @Test fun addAppendsAndIgnoresDuplicates() {
        val s = ListOps.add(start, "XAU")
        assertEquals(start.rows + "XAU", s.rows)
        assertEquals(s, ListOps.add(s, "XAU"))
        assertTrue(rates.hasRate("XAU"))
    }

    @Test fun rowWithoutRateNeverBecomesBase() {
        // Кэш currency-api потерян: BTC без курса
        val noBtc = ResolvedRates(rates.sets.filterKeys { it == CurrencyRegistry.FRANKFURTER })
        assertFalse(noBtc.hasRate("BTC"))
        assertNull(Converter.convert(BigDecimal.TEN, "RSD", "BTC", noBtc))
        assertNull(DisplayRules.rateLine("RSD", "BTC", noBtc))
        val withBtc = ListOps.add(start, "BTC")
        // База без курса переходит на первую строку с курсом с переносом эквивалента
        val baseBtc = withBtc.copy(base = "BTC", amount = BigDecimal("0.001"), amountTyped = true, amountText = "0.001")
        val fixed = ListOps.ensureBaseHasRate(baseBtc, noBtc)
        assertEquals("USD", fixed.base)
        assertFalse(fixed.amountTyped)
        // Эквивалент перенести невозможно (у BTC нет курса) — сумма остаётся числом, исключений нет
        assertEquals(BigDecimal("0.001"), fixed.amount)
        // С курсом у базы состояние не меняется
        assertEquals(start, ListOps.ensureBaseHasRate(start, noBtc))
        // Ни у одной строки нет курса — база остаётся
        val none = ResolvedRates.EMPTY
        assertEquals(start, ListOps.ensureBaseHasRate(start, none))
        // Удаление базовой при потерянном курсе переносит базу на первую строку с курсом
        val removed = ListOps.remove(baseBtc.copy(rows = listOf("BTC", "XAU", "USD")), "BTC", noBtc)
        assertEquals("XAU", removed.base)
        // Удаление небазовой строки базу не трогает даже без курса у базы
        assertEquals("BTC", ListOps.remove(baseBtc.copy(rows = listOf("BTC", "XAU", "USD")), "XAU", noBtc).base)
    }

    @Test fun validateInvariants() {
        assertEquals("rows<2", start.copy(rows = listOf("USD"), base = "USD").validate())
        assertEquals("base_not_in_rows", start.copy(base = "XAU").validate())
        assertEquals("duplicate_row", start.copy(rows = start.rows + "USD").validate())
        assertEquals("bad_code=usd1", start.copy(rows = listOf("usd1", "EUR"), base = "EUR").validate())
        assertEquals("bad_code=US", start.copy(rows = listOf("US", "EUR"), base = "EUR").validate())
        // Файлы 0.2.0 с текстом «как набран» читаются по тем же инвариантам
        val typed = start.copy(amountTyped = true, amountText = "1000")
        assertNull(typed.validate())
        assertEquals("amountText_missing", typed.copy(amountText = null).validate())
        assertEquals("amountText_without_typed", typed.copy(amountTyped = false).validate())
        assertEquals("amountText_invalid", typed.copy(amountText = "1,000").validate())
        assertNull(start.copy(amount = null).validate())
        assertNull(typed.copy(amount = BigDecimal("7"), amountText = "007.").validate())
        // Код новее встроенного набора известен реестру — список не обнуляется
        assertNull(start.copy(rows = start.rows + "ZZZ").validate())
    }
}
