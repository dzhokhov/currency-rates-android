package com.dzhokhov.currencyrates.sources

import com.dzhokhov.currencyrates.core.CurrencyRegistry
import com.dzhokhov.currencyrates.core.Freshness
import com.dzhokhov.currencyrates.core.Origin
import com.dzhokhov.currencyrates.core.Outcome
import com.dzhokhov.currencyrates.core.RateSet
import com.dzhokhov.currencyrates.core.RefreshState
import com.dzhokhov.currencyrates.core.ResolvedRates
import com.dzhokhov.currencyrates.core.SourceResult
import com.dzhokhov.currencyrates.core.json.JsonParseException
import com.dzhokhov.currencyrates.log.RatesLog
import com.dzhokhov.currencyrates.storage.RateSetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** COLD/FOREGROUND/MANUAL — полные попытки; SWIPE/ADD_SOURCE — частичные (один источник), от действия пользователя. */
enum class Trigger { COLD, FOREGROUND, MANUAL, SWIPE, ADD_SOURCE }

data class Decision(val run: Boolean, val reason: String)

/** Нужно ли обновить источник после добавления его валюты. */
enum class AddSourceDecision { NONE, RUN, DEFER }

/**
 * Когда запускать обновление: вручную, свайпом и при добавлении валюты — всегда (кроме идущего обновления);
 * Автоматически — пропуск, если последняя неудачная попытка была меньше 15 минут назад; возврат на передний план —
 * Дополнительно требует > 1 ч с последней полностью успешной загрузки. Частичные попытки пороги не читают.
 */
object RefreshPolicy {
    val RETRY_AFTER_FAILURE: Duration = Duration.ofMinutes(15)
    val FOREGROUND_MIN_AGE: Duration = Duration.ofHours(1)

    fun decide(trigger: Trigger, refresh: RefreshState, now: Instant, inProgress: Boolean): Decision {
        if (inProgress) return Decision(false, "in_progress")
        if (trigger == Trigger.MANUAL || trigger == Trigger.SWIPE || trigger == Trigger.ADD_SOURCE) {
            return Decision(true, trigger.name.lowercase())
        }
        val lastAttempt = refresh.lastAttemptAt
        val outcome = refresh.lastAttemptOutcome
        if (outcome != null && outcome != Outcome.OK && lastAttempt != null) {
            val since = Duration.between(lastAttempt, now)
            if (since < RETRY_AFTER_FAILURE) return Decision(false, "failed_${since.toMinutes()}m_ago<15m")
        }
        if (trigger == Trigger.FOREGROUND) {
            val ok = refresh.lastFullSuccessAt
            if (ok != null && Duration.between(ok, now) <= FOREGROUND_MIN_AGE) {
                return Decision(false, "success_${Duration.between(ok, now).toMinutes()}m_ago<=1h")
            }
        }
        return Decision(true, trigger.name.lowercase())
    }

    /** Набор источника добавленной валюты никогда не загружался → обновить сейчас; репозиторий занят → слот. */
    fun afterAdd(code: String, rates: ResolvedRates, inProgress: Boolean): AddSourceDecision {
        val source = CurrencyRegistry.sourceFor(code)
        if (rates.sets[source]?.fetchedAt != null) return AddSourceDecision.NONE
        return if (inProgress) AddSourceDecision.DEFER else AddSourceDecision.RUN
    }
}

data class SourceFetch(val result: SourceResult, val set: RateSet?, val detail: String)

data class RefreshResult(val sets: Map<String, RateSet>, val refresh: RefreshState, val polled: Set<String>)

/**
 * Обновление наборов: опрашиваются только источники, которым реестр назначает код текущего списка,
 * При only — их пересечение с only; на источник не более двух попыток (основной адрес,
 * Затем запасной или тот же) с паузой; при HTTP 200 с ошибкой разбора или покрытия повтора нет;
 * Единовременность — повторный запуск игнорируется. perSource сливается по источникам; поля 15-минутного
 * Правила (lastAttemptAt, lastAttemptOutcome, lastFullSuccessAt) пишут только полные попытки;
 * Пустой объём — без запросов и без изменения состояния (refresh_skip).
 */
class RatesRepository(
    private val sources: List<RateSource>,
    private val store: RateSetStore,
    private val http: HttpClient,
    private val log: RatesLog,
    private val clock: Clock,
    private val retryDelayMs: Long = RETRY_DELAY_MS,
) {
    private val running = AtomicBoolean(false)
    val inProgress: Boolean get() = running.get()

    /** null — обновление уже идёт; polled пуст — пустой объём частичной попытки (состояние не изменено). */
    suspend fun refresh(rows: List<String>, previous: RefreshState, reason: String, only: Set<String>? = null): RefreshResult? {
        if (!running.compareAndSet(false, true)) return null
        try {
            val involved = Freshness.involvedSources(rows)
            val polled = sources.filter { it.spec.id in involved && (only == null || it.spec.id in only) }
            val skipped = sources.map { it.spec.id } - polled.map { it.spec.id }.toSet()
            val scope = if (only == null) "full" else "partial"
            if (polled.isEmpty()) {
                log.event(
                    "refresh_skip", "RatesRepository", "refresh", belief(emptyList()),
                    "reason=$reason scope=${only?.sorted()} involved=${involved.sorted()} skip=empty_scope",
                    "none",
                )
                return RefreshResult(emptyMap(), previous, emptySet())
            }
            val results = coroutineScope {
                polled.map { source -> async(Dispatchers.IO) { source.spec.id to fetchSource(source, rows) } }.awaitAll()
            }.toMap()
            val now = clock.instant()
            val values = results.values.map { it.result }
            val outcome = when {
                values.isNotEmpty() && values.all { it == SourceResult.OK } -> Outcome.OK
                values.any { it == SourceResult.OK } -> Outcome.PARTIAL
                values.isNotEmpty() && values.all { it == SourceResult.NO_NETWORK } -> Outcome.NO_NETWORK
                else -> Outcome.FAILED
            }
            val merged = previous.perSource + results.mapValues { it.value.result }
            val refresh = if (only == null) {
                RefreshState(
                    lastAttemptAt = now,
                    lastAttemptOutcome = outcome,
                    lastFullSuccessAt = if (outcome == Outcome.OK) now else previous.lastFullSuccessAt,
                    perSource = merged,
                )
            } else {
                previous.copy(perSource = merged)
            }
            log.event(
                "refresh_done", "RatesRepository", "refresh", belief(polled.map { it.spec.id }),
                "reason=$reason scope=$scope outcome=${outcome.name.lowercase()} perSource=${results.map { "${it.key}=${it.value.result.name.lowercase()}(${it.value.detail})" }} notPolled=$skipped",
                "recompute",
            )
            val sets = results.filterValues { it.set != null }.mapValues { it.value.set!! }
            return RefreshResult(sets, refresh, polled.map { it.spec.id }.toSet())
        } finally {
            running.set(false)
        }
    }

    private suspend fun fetchSource(source: RateSource, rows: List<String>): SourceFetch {
        val id = source.spec.id
        val urls = if (source.spec.urls.size >= 2) source.spec.urls.take(MAX_ATTEMPTS) else List(MAX_ATTEMPTS) { source.spec.urls[0] }
        var allNoNetwork = true
        var lastDetail = ""
        for ((attempt, url) in urls.withIndex()) {
            if (attempt > 0) delay(retryDelayMs)
            val t0 = System.nanoTime()
            val res = http.get(url)
            val ms = (System.nanoTime() - t0) / 1_000_000
            val last = attempt == urls.size - 1
            val next = if (last) "give_up" else if (url == urls[attempt + 1]) "retry_same_url" else "next_url"
            when (res) {
                is HttpResult.Error -> {
                    val noNet = isNoNetwork(res.exception)
                    if (!noNet) allNoNetwork = false
                    lastDetail = "${res.exception.javaClass.simpleName}"
                    log.event("fetch", "RatesRepository", "fetchSource", belief(listOf(id)), "url=$url error=${res.exception.javaClass.simpleName} noNetwork=$noNet ms=$ms attempt=${attempt + 1}", next)
                }
                is HttpResult.Response -> {
                    allNoNetwork = false
                    log.event("fetch", "RatesRepository", "fetchSource", belief(listOf(id)), "url=$url http=${res.code} bytes=${res.bytes} ms=$ms attempt=${attempt + 1}", if (res.code == 200) "parse" else next)
                    if (res.code != 200) {
                        lastDetail = "http=${res.code}"
                        continue
                    }
                    return accept(source, url, res.body, rows)
                }
            }
        }
        return SourceFetch(if (allNoNetwork) SourceResult.NO_NETWORK else SourceResult.FAILED, null, lastDetail)
    }

    /** HTTP 200: разбор → проверка покрытия → сохранение. Повтора при ошибке нет (ответ тот же). */
    private fun accept(source: RateSource, url: String, body: String, rows: List<String>): SourceFetch {
        val id = source.spec.id
        val parsed = try {
            source.parse(body)
        } catch (e: JsonParseException) {
            log.event("parse", "RatesRepository", "accept", belief(listOf(id)), "error=json offset=${e.offset} message=${e.message}", "keep_previous_set")
            return SourceFetch(SourceResult.FAILED, null, "parse")
        } catch (e: SourceFormatException) {
            log.event("parse", "RatesRepository", "accept", belief(listOf(id)), "error=format message=${e.message}", "keep_previous_set")
            return SourceFetch(SourceResult.FAILED, null, "format")
        }
        log.event("parse", "RatesRepository", "accept", belief(listOf(id)), "rows=${parsed.rows.size} skipped=${parsed.skipped} dates=${parsed.dates.sorted()}", "validate")
        val needed = rows.filter { CurrencyRegistry.sourceFor(it) == id && it != CurrencyRegistry.USD }
        val missing = needed.filter { it !in parsed.rows }
        if (missing.isNotEmpty()) {
            log.event("validate", "RatesRepository", "accept", belief(listOf(id)), "needed=${needed.size} missing=$missing", "keep_previous_set")
            return SourceFetch(SourceResult.FAILED, null, "missing=$missing")
        }
        log.event("validate", "RatesRepository", "accept", belief(listOf(id)), "needed=${needed.size} missing=[]", "store")
        val now = clock.instant()
        store.save(id, now, url, body)
        return SourceFetch(SourceResult.OK, RateSet(id, parsed.rows, now, Origin.CACHED), "ok")
    }

    private fun belief(ids: List<String>) = "${ids.joinToString(",")};USD;daily"

    companion object {
        const val MAX_ATTEMPTS = 2
        const val RETRY_DELAY_MS = 1000L
    }
}
