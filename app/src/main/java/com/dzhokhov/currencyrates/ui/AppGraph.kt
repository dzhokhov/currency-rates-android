package com.dzhokhov.currencyrates.ui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.dzhokhov.currencyrates.core.CurrencyNames
import com.dzhokhov.currencyrates.core.CurrencyRegistry
import com.dzhokhov.currencyrates.core.ListOps
import com.dzhokhov.currencyrates.core.RateSet
import com.dzhokhov.currencyrates.core.ResolvedRates
import com.dzhokhov.currencyrates.log.AndroidRatesLog
import com.dzhokhov.currencyrates.log.RatesLog
import com.dzhokhov.currencyrates.sources.HttpClient
import com.dzhokhov.currencyrates.sources.HttpUrlConnectionClient
import com.dzhokhov.currencyrates.sources.RateSources
import com.dzhokhov.currencyrates.sources.RatesRepository
import com.dzhokhov.currencyrates.storage.AndroidAssetReader
import com.dzhokhov.currencyrates.storage.EmbeddedAssets
import com.dzhokhov.currencyrates.storage.JsonFiles
import com.dzhokhov.currencyrates.storage.RateSetStore
import com.dzhokhov.currencyrates.storage.StateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import java.util.UUID

/**
 * Ручная сборка графа объектов. Создаётся при первом обращении в MainActivity.onCreate и синхронно
 * Читает первое состояние (state.json и действующие наборы: кэш или assets) до setContent —
 * Экран никогда не показывается без данных. Запись состояния живёт в области приложения.
 *
 * Граф — синглтон процесса, а MainViewModel создаётся на каждую активность; поэтому первое состояние не хранится
 * Снимком (Д-1): состояние пользователя и обновления — в StateStore (currentState/currentRefresh), действующие
 * Наборы — в rates, который MainViewModel обновляет после каждой загрузки.
 */
class AppGraph private constructor(context: Context) {
    val runId: String = UUID.randomUUID().toString().take(8)
    val log: RatesLog = AndroidRatesLog(runId)
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val embedded = EmbeddedAssets(AndroidAssetReader(context.assets))
    val files = JsonFiles(context.filesDir, log)
    val stateStore = StateStore(files, scope, log)
    val rateSetStore = RateSetStore(files, embedded, log)
    val clock: Clock = Clock.systemUTC()
    val versionName: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
    val http: HttpClient = HttpUrlConnectionClient("CurrencyRates/$versionName (Android ${Build.VERSION.SDK_INT})")
    val repository = RatesRepository(RateSources.all, rateSetStore, http, log, clock)

    /** Список валют для экрана выбора читается лениво при первом открытии выбора. */
    val names: CurrencyNames by lazy {
        try {
            CurrencyNames.parse(embedded.currenciesBody())
        } catch (e: Exception) {
            log.event("storage_fallback", "AppGraph", "names", "-", "file=assets/currencies/frankfurter-v2.json reason=${e.javaClass.simpleName}", "codes_only")
            CurrencyNames.EMPTY
        }
    }

    /** Действующие наборы процесса: прочитаны из файлов один раз при старте, далее обновляются MainViewModel.applyRefresh. */
    @Volatile
    var rates: ResolvedRates

    init {
        val t0 = SystemClock.elapsedRealtime()
        val sets = LinkedHashMap<String, RateSet>()
        val origins = ArrayList<String>()
        for (spec in CurrencyRegistry.sources) {
            val set = rateSetStore.load(spec.id)
            if (set != null) {
                sets[spec.id] = set
                origins.add("${spec.id}=${set.origin.name.lowercase()}(rows=${set.rows.size},dates=${set.rows.values.map { it.date }.toSet().sorted()})")
            } else {
                origins.add("${spec.id}=missing")
            }
        }
        rates = ResolvedRates(sets)
        val user = ListOps.ensureBaseHasRate(stateStore.currentState(), rates)
        val refresh = stateStore.currentRefresh()
        val ms = SystemClock.elapsedRealtime() - t0
        log.event(
            step = "app_start",
            module = "AppGraph",
            function = "init",
            belief = "${sets.keys.joinToString(",")};${user.base};daily",
            observed = "${origins.joinToString(" ")} rows=${user.rows.size} lastAttempt=${refresh.lastAttemptOutcome} ms=$ms",
            next = "first_frame",
        )
    }

    companion object {
        @Volatile
        private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context.applicationContext).also { instance = it }
            }
    }
}
