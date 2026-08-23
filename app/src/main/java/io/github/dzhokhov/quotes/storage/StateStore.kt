package io.github.dzhokhov.quotes.storage

import io.github.dzhokhov.quotes.core.Outcome
import io.github.dzhokhov.quotes.core.RefreshState
import io.github.dzhokhov.quotes.core.SourceResult
import io.github.dzhokhov.quotes.core.UserState
import io.github.dzhokhov.quotes.core.json.JsonValue
import io.github.dzhokhov.quotes.log.RatesLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.time.Instant

/**
 * Единственный писатель state.json и refresh.json: последовательная очередь (Mutex) в области приложения.
 * Список, порядок и база — немедленно (update возвращается после записи); сумма — с объединением 300 мс;
 * flushNow — принудительная запись отложенной суммы.
 *
 * Живое состояние процесса (Д-1): последнее состояние, переданное писателю (update/scheduleAmount/writeRefresh),
 * Включая ещё не записанную сумму, доступно через currentState/currentRefresh — источник правды для каждой новой
 * MainViewModel в живом процессе (активность завершена «Назад», процесс жив). До первой записи — содержимое файла.
 */
class StateStore(
    private val files: JsonFiles,
    private val scope: CoroutineScope,
    private val log: RatesLog,
    private val amountDelayMs: Long = AMOUNT_DELAY_MS,
) {
    private val mutex = Mutex()
    private val lock = Any()
    private var pending: UserState? = null
    private var pendingJob: Job? = null

    /** Последнее состояние, переданное писателю; null — ещё не передавалось и не читалось. */
    @Volatile private var latest: UserState? = null
    @Volatile private var latestRefresh: RefreshState? = null

    fun readState(): UserState {
        val obj = files.read(STATE_FILE) ?: return UserState.START
        val state = decodeState(obj)
        if (state == null) {
            log.event("storage_fallback", "StateStore", "readState", "-", "file=$STATE_FILE reason=invalid_state violation=decode", "use_start_state")
            return UserState.START
        }
        val violation = state.validate()
        if (violation != null) {
            log.event("storage_fallback", "StateStore", "readState", "-", "file=$STATE_FILE reason=invalid_state violation=$violation", "use_start_state")
            return UserState.START
        }
        return state
    }

    fun readRefresh(): RefreshState {
        val obj = files.read(REFRESH_FILE) ?: return RefreshState.EMPTY
        return decodeRefresh(obj) ?: run {
            log.event("storage_fallback", "StateStore", "readRefresh", "-", "file=$REFRESH_FILE reason=invalid_state", "use_empty_refresh")
            RefreshState.EMPTY
        }
    }

    /**
     * Текущее состояние процесса: последнее переданное update/scheduleAmount (в том числе ещё не записанная сумма);
     * При первом обращении — чтение файла. Именно отсюда берёт состояние новая MainViewModel (Д-1).
     */
    fun currentState(): UserState = latest ?: synchronized(lock) { latest ?: readState().also { latest = it } }

    /** Текущее состояние обновления процесса: последнее переданное writeRefresh; при первом обращении — файл. */
    fun currentRefresh(): RefreshState =
        latestRefresh ?: synchronized(lock) { latestRefresh ?: readRefresh().also { latestRefresh = it } }

    /** Немедленная запись (список, порядок, база); возвращается после завершения записи. */
    suspend fun update(state: UserState, reason: String) {
        synchronized(lock) {
            latest = state
            pendingJob?.cancel()
            pendingJob = null
            pending = null
        }
        enqueue { files.write(STATE_FILE, encodeState(state), reason) }.join()
    }

    /** Объединённая запись суммы: каждая новая цифра сдвигает окно. */
    fun scheduleAmount(state: UserState) {
        synchronized(lock) {
            latest = state
            pending = state
            pendingJob?.cancel()
            pendingJob = scope.launch {
                delay(amountDelayMs)
                writePending("amount")
            }
        }
    }

    /** Принудительная запись отложенной суммы (onPause/onStop). */
    suspend fun flushNow() {
        synchronized(lock) { pendingJob?.cancel(); pendingJob = null }
        enqueue { writePending("flush") }.join()
    }

    suspend fun writeRefresh(refresh: RefreshState) {
        synchronized(lock) { latestRefresh = refresh }
        enqueue { files.write(REFRESH_FILE, encodeRefresh(refresh), "refresh") }.join()
    }

    private fun writePending(reason: String) {
        val state = synchronized(lock) { pending.also { pending = null } } ?: return
        files.write(STATE_FILE, encodeState(state), reason)
    }

    private fun enqueue(block: () -> Unit): Job = scope.launch {
        mutex.withLock { block() }
    }

    companion object {
        const val STATE_FILE = "state.json"
        const val REFRESH_FILE = "refresh.json"
        const val AMOUNT_DELAY_MS = 300L

        fun encodeState(s: UserState): JsonValue.JObject = JsonValue.JObject(
            linkedMapOf(
                "schemaVersion" to JsonValue.JNumber(JsonFiles.SCHEMA_VERSION.toString()),
                "rows" to JsonValue.JArray(s.rows.map { JsonValue.JString(it) }),
                "base" to JsonValue.JString(s.base),
                "amount" to (s.amount?.let { JsonValue.JString(it.toPlainString()) } ?: JsonValue.JNull),
                "amountTyped" to JsonValue.JBool(s.amountTyped),
                "amountText" to (s.amountText?.let { JsonValue.JString(it) } ?: JsonValue.JNull),
            ),
        )

        fun decodeState(o: JsonValue.JObject): UserState? {
            val rows = (o["rows"] as? JsonValue.JArray)?.items?.map { (it as? JsonValue.JString)?.value ?: return null } ?: return null
            val base = (o["base"] as? JsonValue.JString)?.value ?: return null
            val amount = when (val a = o["amount"]) {
                is JsonValue.JString -> try { BigDecimal(a.value) } catch (e: NumberFormatException) { return null }
                JsonValue.JNull, null -> null
                else -> return null
            }
            val typed = (o["amountTyped"] as? JsonValue.JBool)?.value ?: return null
            val text = when (val t = o["amountText"]) {
                is JsonValue.JString -> t.value
                JsonValue.JNull, null -> null
                else -> return null
            }
            return UserState(rows, base, amount, typed, text)
        }

        fun encodeRefresh(r: RefreshState): JsonValue.JObject = JsonValue.JObject(
            linkedMapOf(
                "schemaVersion" to JsonValue.JNumber(JsonFiles.SCHEMA_VERSION.toString()),
                "lastAttemptAt" to (r.lastAttemptAt?.let { JsonValue.JNumber(it.toEpochMilli().toString()) } ?: JsonValue.JNull),
                "lastAttemptOutcome" to (r.lastAttemptOutcome?.let { JsonValue.JString(it.name.lowercase()) } ?: JsonValue.JNull),
                "lastFullSuccessAt" to (r.lastFullSuccessAt?.let { JsonValue.JNumber(it.toEpochMilli().toString()) } ?: JsonValue.JNull),
                "perSource" to JsonValue.JObject(r.perSource.mapValues { JsonValue.JString(it.value.name.lowercase()) }),
            ),
        )

        fun decodeRefresh(o: JsonValue.JObject): RefreshState? {
            fun instant(v: JsonValue?): Instant? = when (v) {
                is JsonValue.JNumber -> Instant.ofEpochMilli(v.toBigDecimal().toLong())
                else -> null
            }
            val outcome = (o["lastAttemptOutcome"] as? JsonValue.JString)?.value?.let { name ->
                Outcome.values().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
            }
            val perSource = (o["perSource"] as? JsonValue.JObject)?.fields?.mapValues { (_, v) ->
                val name = (v as? JsonValue.JString)?.value ?: return null
                SourceResult.values().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
            } ?: emptyMap()
            return RefreshState(instant(o["lastAttemptAt"]), outcome, instant(o["lastFullSuccessAt"]), perSource)
        }
    }
}
