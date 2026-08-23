package io.github.dzhokhov.quotes.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class FreshnessInfo(
    val setDate: LocalDate?,
    val loadedAt: Instant?,
    val embedded: Boolean,
    val noNetwork: Boolean,
    val updateFailed: Boolean,
    val stale: Boolean,
)

/** Правила строки «обновлено»: дата набора, момент загрузки, пометки. Часы передаются снаружи. */
object Freshness {
    val STALE_AFTER_LOAD: Duration = Duration.ofHours(48)
    const val STALE_AFTER_SET_DAYS = 5L

    /** Источники, которым реестр назначает хотя бы один код списка. */
    fun involvedSources(rows: List<String>): Set<String> = rows.map { CurrencyRegistry.sourceFor(it) }.toSet()

    fun compute(rows: List<String>, rates: ResolvedRates, refresh: RefreshState, now: Instant, zone: ZoneId): FreshnessInfo {
        val setDate = rows.filter { it != CurrencyRegistry.USD }.mapNotNull { rates.row(it)?.date }.minOrNull()
        val involved = involvedSources(rows)
        val embedded = involved.any { rates.sets[it]?.fetchedAt == null }
        val loadedAt = if (embedded) null else involved.mapNotNull { rates.sets[it]?.fetchedAt }.minOrNull()
        val today = now.atZone(zone).toLocalDate()
        val stale = (loadedAt != null && Duration.between(loadedAt, now) > STALE_AFTER_LOAD) ||
            (setDate != null && ChronoUnit.DAYS.between(setDate, today) > STALE_AFTER_SET_DAYS)
        // Одна пометка по приоритету из слитой карты, только по задействованным источникам.
        val noNetwork = involved.any { refresh.perSource[it] == SourceResult.NO_NETWORK }
        val updateFailed = !noNetwork && involved.any { refresh.perSource[it] == SourceResult.FAILED }
        return FreshnessInfo(setDate, loadedAt, embedded, noNetwork, updateFailed, stale)
    }
}
