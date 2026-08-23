package io.github.dzhokhov.quotes.sources

import io.github.dzhokhov.quotes.TestFiles
import io.github.dzhokhov.quotes.core.CurrencyRegistry
import io.github.dzhokhov.quotes.core.Freshness
import io.github.dzhokhov.quotes.core.Origin
import io.github.dzhokhov.quotes.core.Outcome
import io.github.dzhokhov.quotes.core.RefreshState
import io.github.dzhokhov.quotes.core.ResolvedRates
import io.github.dzhokhov.quotes.core.SourceResult
import io.github.dzhokhov.quotes.core.UserState
import io.github.dzhokhov.quotes.log.ListRatesLog
import io.github.dzhokhov.quotes.storage.DirectoryAssetReader
import io.github.dzhokhov.quotes.storage.EmbeddedAssets
import io.github.dzhokhov.quotes.storage.JsonFiles
import io.github.dzhokhov.quotes.storage.RateSetStore
import io.github.dzhokhov.quotes.storage.StateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class FakeHttp : HttpClient {
    val calls = ArrayList<String>()
    val handlers = LinkedHashMap<String, ArrayDeque<() -> HttpResult>>()

    fun on(url: String, vararg results: () -> HttpResult) {
        handlers.getOrPut(url) { ArrayDeque() }.addAll(results)
    }

    override fun get(url: String): HttpResult {
        synchronized(calls) { calls.add(url) }
        val q = handlers[url] ?: return HttpResult.Error(IOException("unexpected url $url"))
        val h = if (q.size > 1) q.removeFirst() else q.first()
        return h()
    }
}

class RatesRepositoryTest {
    private val frUrl = CurrencyRegistry.source(CurrencyRegistry.FRANKFURTER).urls[0]
    private val caUrl = CurrencyRegistry.source(CurrencyRegistry.CURRENCY_API).urls[0]
    private val caFallback = CurrencyRegistry.source(CurrencyRegistry.CURRENCY_API).urls[1]
    private val now: Instant = Instant.parse("2026-08-23T07:14:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val start = UserState.START.rows
    private val withBtc = start + "BTC"

    private lateinit var dir: File
    private lateinit var log: ListRatesLog
    private lateinit var files: JsonFiles
    private lateinit var store: RateSetStore
    private lateinit var http: FakeHttp
    private lateinit var repo: RatesRepository

    private fun ok(body: String): () -> HttpResult = { HttpResult.Response(200, body, body.length) }
    private fun http500(): () -> HttpResult = { HttpResult.Response(500, "oops", 4) }
    private fun err(e: Exception): () -> HttpResult = { HttpResult.Error(e) }

    @Before fun setUp() {
        dir = Files.createTempDirectory("rates-repo").toFile()
        log = ListRatesLog()
        files = JsonFiles(dir, log)
        val assetsDir = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }
        store = RateSetStore(files, EmbeddedAssets(DirectoryAssetReader(assetsDir)), log)
        http = FakeHttp()
        repo = RatesRepository(RateSources.all, store, http, log, clock, retryDelayMs = 10)
    }

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    @Test fun successSavesBothSetsWhenBtcInList() = runBlocking {
        http.on(frUrl, ok(TestFiles.frankfurterBody))
        http.on(caUrl, ok(TestFiles.currencyApiBody))
        val r = repo.refresh(withBtc, RefreshState.EMPTY, "cold")!!
        assertEquals(setOf(frUrl, caUrl), http.calls.toSet())
        assertEquals(2, http.calls.size)
        assertEquals(Outcome.OK, r.refresh.lastAttemptOutcome)
        assertEquals(now, r.refresh.lastAttemptAt)
        assertEquals(now, r.refresh.lastFullSuccessAt)
        assertEquals(mapOf(CurrencyRegistry.FRANKFURTER to SourceResult.OK, CurrencyRegistry.CURRENCY_API to SourceResult.OK), r.refresh.perSource)
        assertEquals(2, r.sets.size)
        assertEquals(Origin.CACHED, r.sets.getValue(CurrencyRegistry.FRANKFURTER).origin)
        assertEquals(now, r.sets.getValue(CurrencyRegistry.CURRENCY_API).fetchedAt)
        assertTrue(File(dir, "rates/frankfurter-v2.json").exists())
        assertTrue(File(dir, "rates/currency-api.json").exists())
        // Кэш читается обратно как CACHED, курс в наборе равен курсу ответа
        val reloaded = store.load(CurrencyRegistry.FRANKFURTER)!!
        assertEquals(Origin.CACHED, reloaded.origin)
        assertEquals(now, reloaded.fetchedAt)
        assertEquals(java.math.BigDecimal("100.41"), ResolvedRates(mapOf(CurrencyRegistry.FRANKFURTER to reloaded)).rate("RSD"))
        val rates = ResolvedRates(r.sets)
        val info = Freshness.compute(withBtc, rates, r.refresh, now, ZoneId.of("UTC"))
        assertEquals(now, info.loadedAt)
        assertFalse(info.embedded)
        assertTrue(log.lines.all { it.contains("belief_state=") })
        assertEquals(listOf("fetch", "parse", "validate", "store"), log.steps().filter { it != "refresh_done" }.distinct().let { s -> listOf("fetch", "parse", "validate", "store").filter { it in s } })
        assertEquals("refresh_done", log.steps().last())
    }

    @Test fun listWithoutBtcPollsOnlyFrankfurter() = runBlocking {
        http.on(frUrl, ok(TestFiles.frankfurterBody))
        val r = repo.refresh(start, RefreshState.EMPTY, "cold")!!
        assertEquals(listOf(frUrl), http.calls)
        assertEquals(setOf(CurrencyRegistry.FRANKFURTER), r.polled)
        assertEquals(setOf(CurrencyRegistry.FRANKFURTER), r.refresh.perSource.keys)
        assertEquals(Outcome.OK, r.refresh.lastAttemptOutcome)
        assertFalse(File(dir, "rates/currency-api.json").exists())
        assertTrue(log.lines.last().contains("notPolled=[currency-api]"))
        // После добавления BTC следующее событие опрашивает currency-api
        http.on(caUrl, ok(TestFiles.currencyApiBody))
        val r2 = repo.refresh(withBtc, r.refresh, "manual")!!
        assertTrue(caUrl in http.calls)
        assertEquals(setOf(CurrencyRegistry.FRANKFURTER, CurrencyRegistry.CURRENCY_API), r2.polled)
    }

    @Test fun noNetworkGivesNoNetworkAfterTwoAttemptsAndNoFile() = runBlocking {
        http.on(frUrl, err(UnknownHostException("api.frankfurter.dev")))
        val r = repo.refresh(start, RefreshState.EMPTY, "cold")!!
        assertEquals(listOf(frUrl, frUrl), http.calls)
        assertEquals(Outcome.NO_NETWORK, r.refresh.lastAttemptOutcome)
        assertEquals(SourceResult.NO_NETWORK, r.refresh.perSource[CurrencyRegistry.FRANKFURTER])
        assertNull(r.refresh.lastFullSuccessAt)
        assertTrue(r.sets.isEmpty())
        assertFalse(File(dir, "rates/frankfurter-v2.json").exists())
        val fetches = log.lines.filter { it.contains("step_id=fetch") }
        assertEquals(2, fetches.size)
        assertTrue(fetches[0].contains("next_action=retry_same_url"))
        assertTrue(fetches[1].contains("next_action=give_up"))
        assertTrue(log.lines.last().contains("outcome=no_network"))
        // Встроенный набор остаётся действующим
        assertEquals(Origin.EMBEDDED, store.load(CurrencyRegistry.FRANKFURTER)!!.origin)
    }

    @Test fun fallbackAddressOfCurrencyApiIsUsed() = runBlocking {
        http.on(frUrl, ok(TestFiles.frankfurterBody))
        http.on(caUrl, err(ConnectException("refused")))
        http.on(caFallback, ok(TestFiles.currencyApiBody))
        val r = repo.refresh(withBtc, RefreshState.EMPTY, "cold")!!
        assertEquals(Outcome.OK, r.refresh.lastAttemptOutcome)
        assertEquals(listOf(caUrl, caFallback), http.calls.filter { it != frUrl })
        val envelope = File(dir, "rates/currency-api.json").readText()
        assertTrue(envelope.contains("\"url\":\"$caFallback\""))
        val fetch = log.lines.first { it.contains("step_id=fetch") && it.contains(caUrl) }
        assertTrue(fetch.contains("next_action=next_url"))
    }

    @Test fun http500ThenOkRetriesSameAddress() = runBlocking {
        http.on(frUrl, http500(), ok(TestFiles.frankfurterBody))
        val r = repo.refresh(start, RefreshState.EMPTY, "cold")!!
        assertEquals(2, http.calls.size)
        assertEquals(Outcome.OK, r.refresh.lastAttemptOutcome)
    }

    @Test fun parseErrorOnHttp200HasNoRetry() = runBlocking {
        http.on(frUrl, ok("[{\"date\":\"2026-08-22\""))
        val r = repo.refresh(start, RefreshState.EMPTY, "cold")!!
        assertEquals(1, http.calls.size)
        assertEquals(Outcome.FAILED, r.refresh.lastAttemptOutcome)
        assertEquals(SourceResult.FAILED, r.refresh.perSource[CurrencyRegistry.FRANKFURTER])
        assertTrue(log.lines.any { it.contains("step_id=parse") && it.contains("error=json offset=") })
        assertFalse(File(dir, "rates/frankfurter-v2.json").exists())
    }

    @Test fun missingCoverageRejectsSetKeepingPrevious() = runBlocking {
        http.on(frUrl, ok(TestFiles.sample("frankfurter-v2-rates-usd-subset.json")))
        val r = repo.refresh(start, RefreshState.EMPTY, "cold")!!
        assertEquals(1, http.calls.size)
        assertEquals(Outcome.FAILED, r.refresh.lastAttemptOutcome)
        assertTrue(log.lines.any { it.contains("step_id=validate") && it.contains("missing=[CNY, AED, JPY, TRY]") })
        assertFalse(File(dir, "rates/frankfurter-v2.json").exists())
        // Подмножество покрывает список [USD, EUR, RSD] — принимается
        http.calls.clear()
        val r2 = repo.refresh(listOf("USD", "EUR", "RSD"), r.refresh, "manual")!!
        assertEquals(Outcome.OK, r2.refresh.lastAttemptOutcome)
        assertTrue(File(dir, "rates/frankfurter-v2.json").exists())
    }

    @Test fun partialOutcomeKeepsPreviousFullSuccess() = runBlocking {
        val earlier = now.minus(Duration.ofHours(5))
        val previous = RefreshState(earlier, Outcome.OK, earlier, mapOf(CurrencyRegistry.FRANKFURTER to SourceResult.OK))
        http.on(frUrl, ok(TestFiles.frankfurterBody))
        http.on(caUrl, http500())
        http.on(caFallback, http500())
        val r = repo.refresh(withBtc, previous, "cold")!!
        assertEquals(Outcome.PARTIAL, r.refresh.lastAttemptOutcome)
        assertEquals(earlier, r.refresh.lastFullSuccessAt)
        assertEquals(SourceResult.FAILED, r.refresh.perSource[CurrencyRegistry.CURRENCY_API])
        assertEquals(SourceResult.OK, r.refresh.perSource[CurrencyRegistry.FRANKFURTER])
        assertEquals(setOf(CurrencyRegistry.FRANKFURTER), r.sets.keys)
    }

    @Test fun mixedFailedAndNoNetworkIsFailedAndTimeoutIsFailed() = runBlocking {
        http.on(frUrl, http500())
        http.on(caUrl, err(UnknownHostException("x")))
        http.on(caFallback, err(UnknownHostException("y")))
        val r = repo.refresh(withBtc, RefreshState.EMPTY, "cold")!!
        assertEquals(Outcome.FAILED, r.refresh.lastAttemptOutcome)
        assertEquals(SourceResult.FAILED, r.refresh.perSource[CurrencyRegistry.FRANKFURTER])
        assertEquals(SourceResult.NO_NETWORK, r.refresh.perSource[CurrencyRegistry.CURRENCY_API])
        http.calls.clear()
        http.handlers.clear()
        http.on(frUrl, err(SocketTimeoutException("read timed out")))
        val r2 = repo.refresh(start, RefreshState.EMPTY, "cold")!!
        assertEquals(Outcome.FAILED, r2.refresh.lastAttemptOutcome)
        assertEquals(2, http.calls.size)
    }

    @Test fun concurrentRefreshIsIgnored() = runBlocking {
        http.on(frUrl, { Thread.sleep(300); HttpResult.Response(200, TestFiles.frankfurterBody, 1) })
        val first = async(Dispatchers.IO) { repo.refresh(start, RefreshState.EMPTY, "cold") }
        delay(80)
        assertTrue(repo.inProgress)
        val second = repo.refresh(start, RefreshState.EMPTY, "manual")
        assertNull(second)
        assertNotNull(first.await())
        assertFalse(repo.inProgress)
        assertEquals(1, http.calls.size)
    }

    @Test fun perSourceSurvivesRestartThroughStateStore() = runBlocking {
        http.on(frUrl, ok(TestFiles.frankfurterBody))
        http.on(caUrl, http500())
        http.on(caFallback, http500())
        val r = repo.refresh(withBtc, RefreshState.EMPTY, "cold")!!
        val stateStore = StateStore(files, CoroutineScope(SupervisorJob() + Dispatchers.IO), log)
        stateStore.writeRefresh(r.refresh)
        val reread = stateStore.readRefresh()
        assertEquals(r.refresh, reread)
        // Список без BTC: partial из-за currency-api не даёт «не удалось обновить»
        val rates = ResolvedRates(r.sets)
        assertFalse(Freshness.compute(start, rates, reread, now, ZoneId.of("UTC")).updateFailed)
        assertTrue(Freshness.compute(withBtc, rates, reread, now, ZoneId.of("UTC")).updateFailed)
    }

    @Test fun policyFifteenMinutesAndOneHour() {
        val failed = RefreshState(now.minus(Duration.ofMinutes(14)), Outcome.FAILED, null, emptyMap())
        assertFalse(RefreshPolicy.decide(Trigger.COLD, failed, now, false).run)
        assertFalse(RefreshPolicy.decide(Trigger.FOREGROUND, failed, now, false).run)
        assertTrue(RefreshPolicy.decide(Trigger.MANUAL, failed, now, false).run)
        val failed16 = failed.copy(lastAttemptAt = now.minus(Duration.ofMinutes(16)))
        assertTrue(RefreshPolicy.decide(Trigger.COLD, failed16, now, false).run)
        assertTrue(RefreshPolicy.decide(Trigger.FOREGROUND, failed16, now, false).run)
        val noNet = failed.copy(lastAttemptOutcome = Outcome.NO_NETWORK)
        assertFalse(RefreshPolicy.decide(Trigger.COLD, noNet, now, false).run)
        // Холодный старт после успеха — всегда; возврат — только спустя > 1 ч
        val recent = RefreshState(now.minus(Duration.ofMinutes(30)), Outcome.OK, now.minus(Duration.ofMinutes(30)), emptyMap())
        assertTrue(RefreshPolicy.decide(Trigger.COLD, recent, now, false).run)
        assertFalse(RefreshPolicy.decide(Trigger.FOREGROUND, recent, now, false).run)
        val old = recent.copy(lastFullSuccessAt = now.minus(Duration.ofMinutes(61)), lastAttemptAt = now.minus(Duration.ofMinutes(61)))
        assertTrue(RefreshPolicy.decide(Trigger.FOREGROUND, old, now, false).run)
        assertTrue(RefreshPolicy.decide(Trigger.COLD, RefreshState.EMPTY, now, false).run)
        assertTrue(RefreshPolicy.decide(Trigger.FOREGROUND, RefreshState.EMPTY, now, false).run)
        // Идущее обновление блокирует даже ручной запуск
        assertFalse(RefreshPolicy.decide(Trigger.MANUAL, RefreshState.EMPTY, now, true).run)
    }

    @Test fun onlyCurrencyApiIsPolledAndPerSourceMerged() = runBlocking {
        val earlier = now.minus(Duration.ofHours(2))
        val previous = RefreshState(earlier, Outcome.OK, earlier, mapOf(CurrencyRegistry.FRANKFURTER to SourceResult.OK))
        http.on(caUrl, ok(TestFiles.currencyApiBody))
        val r = repo.refresh(withBtc, previous, "swipe", only = setOf(CurrencyRegistry.CURRENCY_API))!!
        assertEquals(listOf(caUrl), http.calls)
        assertEquals(setOf(CurrencyRegistry.CURRENCY_API), r.polled)
        assertEquals(setOf(CurrencyRegistry.CURRENCY_API), r.sets.keys)
        // Слияние: прежний frankfurter-v2=ok остаётся
        assertEquals(mapOf(CurrencyRegistry.FRANKFURTER to SourceResult.OK, CurrencyRegistry.CURRENCY_API to SourceResult.OK), r.refresh.perSource)
        // Частичная попытка не пишет поля 15-минутного правила
        assertEquals(earlier, r.refresh.lastAttemptAt)
        assertEquals(Outcome.OK, r.refresh.lastAttemptOutcome)
        assertEquals(earlier, r.refresh.lastFullSuccessAt)
        val done = log.lines.last()
        assertTrue(done, done.contains("step_id=refresh_done") && done.contains("scope=partial") && done.contains("notPolled=[frankfurter-v2]"))
        assertFalse(File(dir, "rates/frankfurter-v2.json").exists())
        assertTrue(File(dir, "rates/currency-api.json").exists())
    }

    @Test fun partialNoNetworkDoesNotSuppressColdRefresh() = runBlocking {
        // Полная попытка OK в T0; свайп по BTC без сети в T0+1 мин → COLD в T0+2 мин всё равно запускается
        http.on(frUrl, ok(TestFiles.frankfurterBody))
        http.on(caUrl, ok(TestFiles.currencyApiBody))
        val full = repo.refresh(withBtc, RefreshState.EMPTY, "cold")!!.refresh
        assertEquals(now, full.lastAttemptAt)
        http.handlers.clear()
        http.calls.clear()
        http.on(caUrl, err(UnknownHostException("x")))
        http.on(caFallback, err(UnknownHostException("y")))
        val r = repo.refresh(withBtc, full, "swipe", only = setOf(CurrencyRegistry.CURRENCY_API))!!
        assertEquals(listOf(caUrl, caFallback), http.calls)
        assertEquals(SourceResult.NO_NETWORK, r.refresh.perSource[CurrencyRegistry.CURRENCY_API])
        assertEquals(SourceResult.OK, r.refresh.perSource[CurrencyRegistry.FRANKFURTER])
        assertEquals(Outcome.OK, r.refresh.lastAttemptOutcome)
        assertEquals(now, r.refresh.lastAttemptAt)
        assertEquals(now, r.refresh.lastFullSuccessAt)
        val d = RefreshPolicy.decide(Trigger.COLD, r.refresh, now.plus(Duration.ofMinutes(2)), false)
        assertTrue(d.run)
        assertEquals("cold", d.reason)
        // Строка «обновлено»: «нет сети» по задействованному currency-api; после удаления BTC пометка исчезает
        val rates = TestFiles.embeddedRates(now)
        assertTrue(Freshness.compute(withBtc, rates, r.refresh, now, ZoneId.of("UTC")).noNetwork)
        assertFalse(Freshness.compute(start, rates, r.refresh, now, ZoneId.of("UTC")).noNetwork)
    }

    @Test fun partialSuccessDoesNotLiftFailureSuppression() = runBlocking {
        // Полная попытка FAILED в T0; свайп по USD успешен в T0+1 мин → COLD в T0+2 мин пропущен
        http.on(frUrl, http500())
        val full = repo.refresh(start, RefreshState.EMPTY, "cold")!!.refresh
        assertEquals(Outcome.FAILED, full.lastAttemptOutcome)
        http.handlers.clear()
        http.calls.clear()
        http.on(frUrl, ok(TestFiles.frankfurterBody))
        val r = repo.refresh(start, full, "swipe", only = setOf(CurrencyRegistry.FRANKFURTER))!!
        assertEquals(listOf(frUrl), http.calls)
        assertEquals(SourceResult.OK, r.refresh.perSource[CurrencyRegistry.FRANKFURTER])
        assertEquals(Outcome.FAILED, r.refresh.lastAttemptOutcome)
        assertNull(r.refresh.lastFullSuccessAt)
        assertEquals(now, r.refresh.lastAttemptAt)
        val d = RefreshPolicy.decide(Trigger.COLD, r.refresh, now.plus(Duration.ofMinutes(2)), false)
        assertFalse(d.run)
        assertEquals("failed_2m_ago<15m", d.reason)
        // Набор при этом обновлён и пометки «не удалось обновить» больше нет
        assertEquals(setOf(CurrencyRegistry.FRANKFURTER), r.sets.keys)
        assertFalse(Freshness.compute(start, ResolvedRates(r.sets), r.refresh, now, ZoneId.of("UTC")).updateFailed)
    }

    @Test fun emptyScopeMakesNoRequestAndKeepsState() = runBlocking {
        // only={currency-api} при списке без BTC
        val earlier = now.minus(Duration.ofMinutes(5))
        val previous = RefreshState(earlier, Outcome.OK, earlier, mapOf(CurrencyRegistry.FRANKFURTER to SourceResult.OK))
        val r = repo.refresh(start, previous, "add_source", only = setOf(CurrencyRegistry.CURRENCY_API))!!
        assertTrue(http.calls.isEmpty())
        assertEquals(previous, r.refresh)
        assertTrue(r.polled.isEmpty())
        assertTrue(r.sets.isEmpty())
        assertFalse(repo.inProgress)
        val skip = log.lines.last()
        assertTrue(skip, skip.contains("step_id=refresh_skip") && skip.contains("skip=empty_scope") && skip.contains("scope=[currency-api]"))
    }

    @Test fun singleSourceNoNetworkMarksOnlyThatSource() = runBlocking {
        val previous = RefreshState(now, Outcome.OK, now, mapOf(CurrencyRegistry.FRANKFURTER to SourceResult.OK, CurrencyRegistry.CURRENCY_API to SourceResult.OK))
        http.on(frUrl, err(UnknownHostException("x")))
        val r = repo.refresh(withBtc, previous, "swipe", only = setOf(CurrencyRegistry.FRANKFURTER))!!
        assertEquals(listOf(frUrl, frUrl), http.calls)
        assertEquals(SourceResult.NO_NETWORK, r.refresh.perSource[CurrencyRegistry.FRANKFURTER])
        assertEquals(SourceResult.OK, r.refresh.perSource[CurrencyRegistry.CURRENCY_API])
        assertEquals(Outcome.OK, r.refresh.lastAttemptOutcome)
    }

    @Test fun swipeAndAddSourceTriggersAlwaysRunUnlessInProgress() {
        val failed = RefreshState(now.minus(Duration.ofMinutes(1)), Outcome.FAILED, null, emptyMap())
        assertTrue(RefreshPolicy.decide(Trigger.SWIPE, failed, now, false).run)
        assertTrue(RefreshPolicy.decide(Trigger.ADD_SOURCE, failed, now, false).run)
        assertEquals("swipe", RefreshPolicy.decide(Trigger.SWIPE, failed, now, false).reason)
        assertFalse(RefreshPolicy.decide(Trigger.SWIPE, failed, now, true).run)
        assertFalse(RefreshPolicy.decide(Trigger.ADD_SOURCE, RefreshState.EMPTY, now, true).run)
        val recent = RefreshState(now, Outcome.OK, now, emptyMap())
        assertTrue(RefreshPolicy.decide(Trigger.SWIPE, recent, now, false).run)
    }

    @Test fun afterAddRefreshesOnlyNeverLoadedSource() {
        val embedded = TestFiles.embeddedRates()
        assertEquals(AddSourceDecision.RUN, RefreshPolicy.afterAdd("BTC", embedded, false))
        assertEquals(AddSourceDecision.DEFER, RefreshPolicy.afterAdd("BTC", embedded, true))
        assertEquals(AddSourceDecision.RUN, RefreshPolicy.afterAdd("XAU", embedded, false))
        val loaded = TestFiles.embeddedRates(now)
        assertEquals(AddSourceDecision.NONE, RefreshPolicy.afterAdd("BTC", loaded, false))
        assertEquals(AddSourceDecision.NONE, RefreshPolicy.afterAdd("BTC", loaded, true))
        // Набора источника нет вовсе (кэш потерян) — тоже обновить
        val none = ResolvedRates(loaded.sets.filterKeys { it == CurrencyRegistry.FRANKFURTER })
        assertEquals(AddSourceDecision.RUN, RefreshPolicy.afterAdd("BTC", none, false))
    }

    @Test fun httpClientRejectsNonHttpsAndClassifiesErrors() {
        val client = HttpUrlConnectionClient("CurrencyRates/test")
        val r = client.get("http://example.invalid/x")
        assertTrue(r is HttpResult.Error)
        assertFalse(isNoNetwork((r as HttpResult.Error).exception))
        assertTrue(isNoNetwork(UnknownHostException("x")))
        assertTrue(isNoNetwork(ConnectException("x")))
        assertTrue(isNoNetwork(java.net.NoRouteToHostException("x")))
        assertFalse(isNoNetwork(SocketTimeoutException("x")))
        assertFalse(isNoNetwork(IOException("x")))
    }
}
