package io.github.dzhokhov.quotes.core

import io.github.dzhokhov.quotes.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FreshnessTest {
    private val zone = ZoneId.of("Europe/Belgrade")
    private val now: Instant = Instant.parse("2026-08-23T07:14:00Z")
    private val start = CurrencyRegistry.startRows

    @Test fun embeddedSetHasNoLoadedAtAndEarliestDate() {
        val f = Freshness.compute(start, TestFiles.embeddedRates(), RefreshState.EMPTY, now, zone)
        assertTrue(f.embedded)
        assertNull(f.loadedAt)
        assertEquals(LocalDate.of(2026, 8, 22), f.setDate)
        assertFalse(f.stale)
        assertFalse(f.noNetwork)
        assertFalse(f.updateFailed)
        // С XAU дата набора становится 21-м (самая ранняя задействованная строка)
        val withGold = Freshness.compute(start + "XAU", TestFiles.embeddedRates(), RefreshState.EMPTY, now, zone)
        assertEquals(LocalDate.of(2026, 8, 21), withGold.setDate)
    }

    @Test fun loadedSetsAndStaleThresholds() {
        val fetched = now.minus(Duration.ofHours(47))
        val rates = TestFiles.embeddedRates(fetched)
        val f = Freshness.compute(start, rates, RefreshState.EMPTY, now, zone)
        assertFalse(f.embedded)
        assertEquals(fetched, f.loadedAt)
        assertFalse(f.stale)
        val later = Freshness.compute(start, rates, RefreshState.EMPTY, now.plus(Duration.ofHours(2)), zone)
        assertTrue("48 h after load", later.stale)
        // Дата набора старше 5 календарных дней
        // Дата набора 2026-08-22; загрузка свежая (час назад), чтобы действовал только порог 5 дней
        val day5 = TestFiles.embeddedRates(Instant.parse("2026-08-27T10:00:00Z"))
        assertFalse(Freshness.compute(start, day5, RefreshState.EMPTY, Instant.parse("2026-08-27T11:00:00Z"), zone).stale)
        val day6 = TestFiles.embeddedRates(Instant.parse("2026-08-28T10:00:00Z"))
        assertTrue(Freshness.compute(start, day6, RefreshState.EMPTY, Instant.parse("2026-08-28T11:00:00Z"), zone).stale)
    }

    @Test fun loadedAtIsMinimumAndEmbeddedIfAnyInvolvedSetIsEmbedded() {
        val f = TestFiles.embeddedRates(now.minus(Duration.ofHours(1))).sets.getValue(CurrencyRegistry.FRANKFURTER)
        val cEmbedded = TestFiles.embeddedRates().sets.getValue(CurrencyRegistry.CURRENCY_API)
        val mixed = ResolvedRates(mapOf(CurrencyRegistry.FRANKFURTER to f, CurrencyRegistry.CURRENCY_API to cEmbedded))
        assertFalse(Freshness.compute(start, mixed, RefreshState.EMPTY, now, zone).embedded)
        assertTrue(Freshness.compute(start + "BTC", mixed, RefreshState.EMPTY, now, zone).embedded)
        val cOld = cEmbedded.copy(fetchedAt = now.minus(Duration.ofHours(30)), origin = Origin.CACHED)
        val both = ResolvedRates(mapOf(CurrencyRegistry.FRANKFURTER to f, CurrencyRegistry.CURRENCY_API to cOld))
        assertEquals(now.minus(Duration.ofHours(30)), Freshness.compute(start + "BTC", both, RefreshState.EMPTY, now, zone).loadedAt)
    }

    @Test fun noNetworkAndUpdateFailedOnlyFromInvolvedSources() {
        val rates = TestFiles.embeddedRates()
        fun with(perSource: Map<String, SourceResult>, rows: List<String> = start) =
            Freshness.compute(rows, rates, RefreshState(now, Outcome.PARTIAL, null, perSource), now, zone)

        val fr = CurrencyRegistry.FRANKFURTER
        val ca = CurrencyRegistry.CURRENCY_API
        assertTrue(with(mapOf(fr to SourceResult.NO_NETWORK)).noNetwork)
        assertFalse(with(mapOf(fr to SourceResult.NO_NETWORK)).updateFailed)
        assertTrue(with(mapOf(fr to SourceResult.FAILED)).updateFailed)
        // Пример критика: partial из-за currency-api при списке без BTC — пометки нет
        val partial = with(mapOf(fr to SourceResult.OK, ca to SourceResult.FAILED))
        assertFalse(partial.updateFailed)
        assertFalse(partial.noNetwork)
        // Тот же результат с BTC в списке — «не удалось обновить»
        assertTrue(with(mapOf(fr to SourceResult.OK, ca to SourceResult.FAILED), start + "BTC").updateFailed)
        // Одна пометка по приоритету — «нет сети» у любого задействованного источника важнее «не удалось»
        val mix = with(mapOf(fr to SourceResult.FAILED, ca to SourceResult.NO_NETWORK), start + "BTC")
        assertTrue(mix.noNetwork)
        assertFalse(mix.updateFailed)
        // Тот же perSource без BTC в списке: результат незадействованного currency-api не показывается
        val mixNoBtc = with(mapOf(fr to SourceResult.FAILED, ca to SourceResult.NO_NETWORK))
        assertFalse(mixNoBtc.noNetwork)
        assertTrue(mixNoBtc.updateFailed)
        // Слитая карта после свайпа: frankfurter ok, currency-api без сети — «нет сети» только при BTC
        assertTrue(with(mapOf(fr to SourceResult.OK, ca to SourceResult.NO_NETWORK), start + "BTC").noNetwork)
        assertFalse(with(mapOf(fr to SourceResult.OK, ca to SourceResult.NO_NETWORK)).noNetwork)
        // Задействованный источник не представлен в perSource — пометок нет
        assertFalse(with(mapOf(ca to SourceResult.NO_NETWORK)).noNetwork)
        assertFalse(with(emptyMap()).noNetwork)
        // Нет сети не меняет даты
        assertEquals(LocalDate.of(2026, 8, 22), with(mapOf(fr to SourceResult.NO_NETWORK)).setDate)
    }

    @Test fun involvedSources() {
        assertEquals(setOf(CurrencyRegistry.FRANKFURTER), Freshness.involvedSources(start))
        assertEquals(setOf(CurrencyRegistry.FRANKFURTER, CurrencyRegistry.CURRENCY_API), Freshness.involvedSources(start + "BTC"))
    }
}
