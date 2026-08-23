package com.dzhokhov.currencyrates.storage

import com.dzhokhov.currencyrates.core.CurrencyRegistry
import com.dzhokhov.currencyrates.core.Outcome
import com.dzhokhov.currencyrates.core.RefreshState
import com.dzhokhov.currencyrates.core.SourceResult
import com.dzhokhov.currencyrates.core.UserState
import com.dzhokhov.currencyrates.core.json.JsonValue
import com.dzhokhov.currencyrates.core.json.MiniJson
import com.dzhokhov.currencyrates.log.ListRatesLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.time.Instant

class StateStoreTest {
    private lateinit var dir: File
    private lateinit var log: ListRatesLog
    private lateinit var files: JsonFiles
    private lateinit var store: StateStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before fun setUp() {
        dir = Files.createTempDirectory("rates-state").toFile()
        log = ListRatesLog()
        files = JsonFiles(dir, log)
        store = StateStore(files, scope, log, amountDelayMs = 300)
    }

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    private fun stateFile() = File(dir, StateStore.STATE_FILE)

    @Test fun emptyStorageGivesStartState() {
        assertEquals(UserState.START, store.readState())
        assertEquals(RefreshState.EMPTY, store.readRefresh())
        assertTrue(log.lines.none { it.contains("storage_fallback") })
    }

    @Test fun listChangeIsWrittenBeforeUpdateReturns() = runBlocking {
        val s = UserState.START.copy(rows = UserState.START.rows + "XAU")
        store.update(s, "list")
        assertTrue(stateFile().exists())
        assertEquals(s, StateStore.decodeState(MiniJson.parse(stateFile().readText()) as JsonValue.JObject))
        assertEquals(s, store.readState())
        assertTrue(dir.listFiles()!!.none { it.name.contains(".tmp-") })
        assertTrue(log.lines.any { it.contains("step_id=store") && it.contains("file=state.json") && it.contains("reason=list") })
    }

    @Test fun roundTripOfAllFields() = runBlocking {
        val s1 = UserState(listOf("USD", "EUR"), "EUR", BigDecimal("7"), true, "007.")
        store.update(s1, "list")
        assertEquals(s1, store.readState())
        val s2 = UserState(listOf("USD", "EUR", "BTC"), "BTC", BigDecimal("9.959167413604222686983368190419281"), false, null)
        store.update(s2, "base")
        assertEquals(s2, store.readState())
        val s3 = s2.copy(amount = null, amountTyped = true, amountText = "")
        store.update(s3, "list")
        assertEquals(s3, store.readState())
    }

    @Test fun amountIsDebouncedAndFlushed() = runBlocking {
        store.update(UserState.START, "list")
        val typed = UserState.START.copy(amount = BigDecimal("10"))
        store.scheduleAmount(typed)
        assertEquals("not written immediately", UserState.START, store.readState())
        val deadline = System.currentTimeMillis() + 3000
        while (store.readState() != typed && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertEquals(typed, store.readState())
        // flushNow пишет отложенную сумму сразу
        val typed2 = typed.copy(amount = BigDecimal("100"))
        store.scheduleAmount(typed2)
        store.flushNow()
        assertEquals(typed2, store.readState())
        assertTrue(log.lines.any { it.contains("reason=amount") })
        assertTrue(log.lines.any { it.contains("reason=flush") })
    }

    @Test fun immediateUpdateSupersedesPendingAmount() = runBlocking {
        val typed = UserState.START.copy(amount = BigDecimal("5"))
        store.scheduleAmount(typed)
        val list = typed.copy(rows = typed.rows + "XAG")
        store.update(list, "list")
        Thread.sleep(600)
        assertEquals(list, store.readState())
    }

    @Test fun corruptedFileGivesStartAndLogsFallback() {
        stateFile().writeText("{\"schemaVersion\":1,\"rows\":[\"USD\",")
        assertEquals(UserState.START, store.readState())
        assertTrue(log.lines.any { it.contains("storage_fallback") && it.contains("json_parse offset=") })
        log.lines.clear()
        stateFile().writeText("{\"schemaVersion\":2,\"rows\":[\"USD\",\"EUR\"],\"base\":\"USD\",\"amount\":null,\"amountTyped\":false,\"amountText\":null}")
        assertEquals(UserState.START, store.readState())
        assertTrue(log.lines.any { it.contains("storage_fallback") && it.contains("reason=schema") })
        log.lines.clear()
        stateFile().writeText("[1,2]")
        assertEquals(UserState.START, store.readState())
        assertTrue(log.lines.any { it.contains("storage_fallback") })
    }

    @Test fun invariantViolationsGiveStartState() {
        fun write(rows: List<String>, base: String) {
            val json = "{\"schemaVersion\":1,\"rows\":[${rows.joinToString(",") { "\"$it\"" }}],\"base\":\"$base\",\"amount\":\"1\",\"amountTyped\":true,\"amountText\":\"1\"}"
            stateFile().writeText(json)
            log.lines.clear()
        }
        write(listOf("USD"), "USD")
        assertEquals(UserState.START, store.readState())
        assertTrue(log.lines.any { it.contains("invalid_state violation=rows<2") })
        write(listOf("USD", "EUR"), "RSD")
        assertEquals(UserState.START, store.readState())
        assertTrue(log.lines.any { it.contains("violation=base_not_in_rows") })
        write(listOf("USD", "EUR", "USD"), "USD")
        assertEquals(UserState.START, store.readState())
        assertTrue(log.lines.any { it.contains("violation=duplicate_row") })
        write(listOf("usd1", "EUR"), "EUR")
        assertEquals(UserState.START, store.readState())
        assertTrue(log.lines.any { it.contains("violation=bad_code=usd1") })
        // Неизвестный ключ и код новее встроенного набора допустимы
        stateFile().writeText("{\"schemaVersion\":1,\"future\":{\"x\":[1]},\"rows\":[\"USD\",\"ZZZ\"],\"base\":\"USD\",\"amount\":\"1\",\"amountTyped\":true,\"amountText\":\"1\"}")
        assertEquals(listOf("USD", "ZZZ"), store.readState().rows)
    }

    @Test fun orphanTempFilesAreRemovedAtStart() {
        File(dir, "state.json.tmp-abc").writeText("garbage")
        File(dir, "rates").mkdirs()
        File(dir, "rates/frankfurter-v2.json.tmp-123").writeText("garbage")
        val fresh = JsonFiles(dir, log)
        assertFalse(File(dir, "state.json.tmp-abc").exists())
        assertFalse(File(dir, "rates/frankfurter-v2.json.tmp-123").exists())
        assertNull(fresh.read("state.json"))
    }

    @Test fun parallelWritesAreSerializedAndFileStaysValid() = runBlocking {
        val jobs = (1..40).map { i ->
            async(Dispatchers.IO) {
                store.update(UserState.START.copy(amount = BigDecimal(i)), "list")
            }
        }
        jobs.awaitAll()
        val read = store.readState()
        assertEquals(UserState.START.rows, read.rows)
        assertTrue(read.amount!!.toInt() in 1..40)
        assertTrue(dir.listFiles()!!.none { it.name.contains(".tmp-") })
    }

    @Test fun fileFrom020WithTypedTextIsReadByAmount() {
        stateFile().writeText("{\"schemaVersion\":1,\"rows\":[\"USD\",\"EUR\"],\"base\":\"EUR\",\"amount\":\"7\",\"amountTyped\":true,\"amountText\":\"007.\"}")
        val s = store.readState()
        assertEquals(BigDecimal("7"), s.amount)
        assertEquals("EUR", s.base)
        assertTrue(s.amountTyped)
        assertEquals("007.", s.amountText)
        assertTrue(log.lines.none { it.contains("storage_fallback") })
    }

    @Test fun firstWriteFromStartHasNoTypedText() = runBlocking {
        // persistList("reorder") до первого касания строки пишет amountTyped=false, amountText=null
        store.update(UserState.START, "reorder")
        val text = stateFile().readText()
        assertTrue(text, text.contains("\"amount\":\"1000\",\"amountTyped\":false,\"amountText\":null"))
        assertEquals(UserState.START, store.readState())
        assertNull(UserState.START.validate())
        assertTrue(log.lines.any { it.contains("reason=reorder") })
    }

    /**
     * Д-1 (0.3.0): активность завершена «Назад», процесс жив — новая MainViewModel создаётся от того же StateStore.
     * Она должна видеть изменённый список (а не снимок на момент старта процесса), и её первая запись не должна
     * Откатывать файл. Две «ViewModel» здесь — два обращения к currentState одного хранилища.
     */
    @Test fun secondViewModelInSameProcessSeesChangedListAndFirstWriteDoesNotRollBack() = runBlocking {
        // Старт процесса: первая ViewModel берёт состояние (из файла — пусто → START)
        val startSnapshot = store.currentState()
        assertEquals(UserState.START, startSnapshot)
        assertTrue("AED" in startSnapshot.rows)
        // Первая ViewModel: удаление AED, подъём BAM, сумма — как на эмуляторе
        val removed = startSnapshot.copy(rows = startSnapshot.rows - "AED")
        store.update(removed, "remove")
        val reordered = removed.copy(rows = listOf("BAM") + (removed.rows - "BAM"))
        store.update(reordered, "reorder")
        val typed = reordered.copy(amount = BigDecimal("250"))
        store.scheduleAmount(typed)
        store.flushNow()
        // Активность завершена «Назад», процесс жив: новая ViewModel того же графа
        val second = store.currentState()
        assertEquals(typed, second)
        assertFalse("удалённая валюта не должна вернуться", "AED" in second.rows)
        assertEquals("BAM", second.rows.first())
        assertEquals(BigDecimal("250"), second.amount)
        // Снимок на момент старта устарел — именно его раньше получала новая ViewModel
        assertTrue(startSnapshot != second)
        // Первая запись новой ViewModel (смена базы от её состояния) не откатывает файл
        val rebased = second.copy(base = "USD")
        store.update(rebased, "base")
        val onDisk = StateStore.decodeState(MiniJson.parse(stateFile().readText()) as JsonValue.JObject)
        assertEquals(rebased, onDisk)
        assertFalse("AED" in onDisk!!.rows)
        assertEquals("BAM", onDisk.rows.first())
        assertEquals(BigDecimal("250"), onDisk.amount)
        // Перезапуск процесса: новое хранилище читает то же из файла
        assertEquals(rebased, StateStore(files, scope, log, amountDelayMs = 300).currentState())
    }

    @Test fun currentStateIncludesPendingAmountBeforeFlush() = runBlocking {
        store.update(UserState.START, "list")
        val typed = UserState.START.copy(amount = BigDecimal("42"))
        store.scheduleAmount(typed)
        // Сумма ещё не на диске (окно 300 мс), но уже в живом состоянии процесса
        assertEquals(UserState.START, store.readState())
        assertEquals(typed, store.currentState())
        store.flushNow()
        assertEquals(typed, store.readState())
        assertEquals(typed, store.currentState())
    }

    @Test fun currentRefreshFollowsWriteRefreshAndFile() = runBlocking {
        assertEquals(RefreshState.EMPTY, store.currentRefresh())
        val r = RefreshState(Instant.ofEpochMilli(1756000000000), Outcome.OK, Instant.ofEpochMilli(1756000000000), mapOf(CurrencyRegistry.FRANKFURTER to SourceResult.OK))
        store.writeRefresh(r)
        assertEquals(r, store.currentRefresh())
        assertEquals(r, StateStore(files, scope, log, amountDelayMs = 300).currentRefresh())
    }

    @Test fun refreshRoundTripWithPerSource() = runBlocking {
        val r = RefreshState(
            lastAttemptAt = Instant.ofEpochMilli(1756000000000),
            lastAttemptOutcome = Outcome.PARTIAL,
            lastFullSuccessAt = null,
            perSource = mapOf(CurrencyRegistry.FRANKFURTER to SourceResult.OK, CurrencyRegistry.CURRENCY_API to SourceResult.NO_NETWORK),
        )
        store.writeRefresh(r)
        assertEquals(r, store.readRefresh())
        val text = File(dir, StateStore.REFRESH_FILE).readText()
        assertTrue(text.contains("\"perSource\":{\"frankfurter-v2\":\"ok\",\"currency-api\":\"no_network\"}"))
        File(dir, StateStore.REFRESH_FILE).writeText("{\"schemaVersion\":1,\"lastAttemptOutcome\":\"weird\"}")
        assertEquals(RefreshState.EMPTY, store.readRefresh())
    }
}
