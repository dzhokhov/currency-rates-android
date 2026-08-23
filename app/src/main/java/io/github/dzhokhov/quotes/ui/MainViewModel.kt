package io.github.dzhokhov.quotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.dzhokhov.quotes.core.Converter
import io.github.dzhokhov.quotes.core.CurrencyNames
import io.github.dzhokhov.quotes.core.CurrencyRegistry
import io.github.dzhokhov.quotes.core.DisplayRules
import io.github.dzhokhov.quotes.core.Freshness
import io.github.dzhokhov.quotes.core.ListOps
import io.github.dzhokhov.quotes.core.PickerList
import io.github.dzhokhov.quotes.core.RefreshState
import io.github.dzhokhov.quotes.core.ResolvedRates
import io.github.dzhokhov.quotes.core.UserState
import io.github.dzhokhov.quotes.core.expr.Expression
import io.github.dzhokhov.quotes.core.expr.Key
import io.github.dzhokhov.quotes.core.expr.Limits
import io.github.dzhokhov.quotes.sources.AddSourceDecision
import io.github.dzhokhov.quotes.sources.RefreshPolicy
import io.github.dzhokhov.quotes.sources.RefreshResult
import io.github.dzhokhov.quotes.sources.Trigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

/**
 * Состояние экрана: список, база, сумма, режим (Idle | Editing(expr) | Revealed | Dragging), состояние обновления.
 * Переживает поворот. Каждый обработчик экрана сначала спрашивает вентиль ModeGate.
 */
class MainViewModel(private val graph: AppGraph) : ViewModel() {
    // Источник правды — живое состояние процесса (StateStore.currentState/currentRefresh, AppGraph.rates), а не снимок
    // На момент старта процесса: новая ViewModel после завершения активности «Назад» видит все изменения (Д-1).
    // 0.3.0 пишет amountTyped=false, amountText=null во всех записях; файл 0.2.0 читается по amount.
    private var rates: ResolvedRates = graph.rates
    private var user: UserState = ListOps.ensureBaseHasRate(graph.stateStore.currentState(), rates).copy(amountTyped = false, amountText = null)
    private var refresh: RefreshState = graph.stateStore.currentRefresh()
    private var mode: ScreenMode = ScreenMode.Idle
    private var refreshing = false
    private var pickerQuery: String? = null

    private var foregroundSeen = false

    /** Источник, который нужно обновить по завершении идущего обновления. */
    private var pendingSourceRefresh: String? = null

    /** Порядок до удержания ручки и была ли запись списка во время перетаскивания. */
    private var dragStartRows: List<String>? = null
    private var persistedWhileDragging = false

    private val _state = MutableStateFlow(render())
    val state: StateFlow<UiState> = _state

    init {
        // Холодный старт: обновление после первого кадра, асинхронно.
        refresh(Trigger.COLD)
    }

    private fun inputDigits(): Int = CurrencyRegistry.inputFractionDigits(CurrencyRegistry.kind(user.base))
    private fun limits(): Limits = Limits(maxFrac = inputDigits())

    /** Текст поля в Idle: сумма, округлённая до точности ввода базы; ноль — пусто. */
    private fun idleFieldText(): String {
        val amount = user.amount ?: return ""
        if (amount.signum() == 0) return ""
        return DisplayRules.fieldText(DisplayRules.round(amount, inputDigits()))
    }

    private fun modeUi(): ModeUi = when (val m = mode) {
        ScreenMode.Idle -> ModeUi.IDLE
        is ScreenMode.Editing -> ModeUi(ModeKind.EDITING, null)
        is ScreenMode.Revealed -> ModeUi(ModeKind.REVEALED, m.code)
        is ScreenMode.Dragging -> ModeUi(ModeKind.DRAGGING, m.code)
    }

    private fun modeName(): String = modeUi().kind.name.lowercase()

    private fun render(): UiState {
        val base = user.base
        val amount = user.amount ?: BigDecimal.ZERO
        val canRemove = ListOps.canRemove(user)
        val rows = user.rows.map { code ->
            val isBase = code == base
            RowUi(
                code = code,
                kind = CurrencyRegistry.kind(code),
                isBase = isBase,
                hasRate = rates.hasRate(code),
                amount = if (isBase) null else Converter.convert(amount, base, code, rates),
                rateLine = if (isBase) null else DisplayRules.rateLine(base, code, rates),
                canRemove = canRemove,
            )
        }
        val editing = mode as? ScreenMode.Editing
        val field = FieldUi(
            kind = CurrencyRegistry.kind(base),
            editing = editing != null,
            text = editing?.expr?.text ?: idleFieldText(),
            result = editing?.expr?.takeIf { it.hasOp }?.let { it.live ?: BigDecimal.ZERO },
        )
        val info = Freshness.compute(user.rows, rates, refresh, Instant.now(), ZoneId.systemDefault())
        val picker = pickerQuery?.let { q ->
            val (group, fiat) = PickerList.build(rates, user.rows)
            PickerUi(q, group, fiat)
        }
        return UiState(rows, base, field, UpdatedUi(info, refreshing), picker, modeUi())
    }

    private fun publish() {
        _state.value = render()
    }

    /**
     * Сверка с вентилем: PROCEED → true (обработчик продолжает); иначе выполняется действие вентиля
     * (закрыть ввод / вернуть карточку / отменить перетаскивание) и возвращается false.
     */
    private fun pass(intent: Intent): Boolean = when (ModeGate.resolve(mode, intent)) {
        Action.PROCEED -> true
        Action.CLOSE_INPUT -> {
            closeInput(intent.javaClass.simpleName.lowercase())
            false
        }
        Action.DISMISS_REVEAL, Action.SWITCH_REVEAL -> {
            mode = ScreenMode.Idle
            publish()
            false
        }
        Action.CANCEL_DRAG -> {
            cancelDrag()
            false
        }
        Action.IGNORE -> false
    }

    /** Удержание ручки: в Idle — начало перетаскивания (true); иначе действие вентиля и false. */
    fun onHandleHold(code: String): Boolean {
        if (!pass(Intent.HandleHold(code))) return false
        if (code !in user.rows) return false
        mode = ScreenMode.Dragging(code)
        dragStartRows = user.rows
        persistedWhileDragging = false
        graph.log.event("recompute", "MainViewModel", "onHandleHold", belief(), "dragging=$code rows=${user.rows.size} mode=dragging", "await_drag")
        publish()
        return true
    }

    /** Обмен при пересечении середины соседа — только в памяти, без записи. */
    fun onDragMove(code: String, toIndex: Int) {
        if (!pass(Intent.DragMove)) return
        if ((mode as? ScreenMode.Dragging)?.code != code) return
        val next = ListOps.move(user, code, toIndex)
        if (next == user) return
        user = next
        publish()
    }

    /** Отпускание или отмена жеста (поворот, уход указателя): фиксация текущего порядка одной записью. */
    fun onDragEnd() {
        if (!pass(Intent.DragEnd)) return
        mode = ScreenMode.Idle
        dragStartRows = null
        graph.log.event("recompute", "MainViewModel", "onDragEnd", belief(), "rows=${user.rows.size} mode=idle", "persist")
        persistList("reorder")
        publish()
    }

    /**
     * «Назад» в Dragging: порядок до удержания, Idle, без записи; если за время перетаскивания список уже
     * Записывался (перенос базы после обновления), порядок восстанавливается с сохранением переноса и одной записью.
     */
    private fun cancelDrag() {
        val start = dragStartRows
        mode = ScreenMode.Idle
        dragStartRows = null
        if (start != null && start.toSet() == user.rows.toSet()) user = user.copy(rows = start)
        graph.log.event("recompute", "MainViewModel", "cancelDrag", belief(), "rows=${user.rows.size} rewrite=$persistedWhileDragging mode=idle", "render")
        if (persistedWhileDragging) persistList("cancel_drag")
        persistedWhileDragging = false
        publish()
    }

    /** Касание строки (вне ручки). В Idle: строка с курсом → база (точный эквивалент), ввод открыт с нетронутым числом. */
    fun onRowTap(code: String) {
        if (!pass(Intent.RowTap(code))) return
        if (!rates.hasRate(code)) return
        if (code != user.base) {
            user = ListOps.rebase(user, code, rates)
            persistList("base")
        }
        mode = ScreenMode.Editing(Expression.untouched(idleFieldText()))
        graph.log.event("recompute", "MainViewModel", "onRowTap", belief(), "base=$code rows=${user.rows.size} mode=editing", "await_input")
        publish()
    }

    /** Клавиша панели или её эквивалент с физической клавиатуры; только в Editing. */
    fun onKey(key: Key) {
        val m = mode as? ScreenMode.Editing ?: return
        if (!pass(Intent.Key)) return
        if (key is Key.Enter) {
            if (m.expr.isComputable) closeInput("enter")
            return
        }
        val next = m.expr.apply(key, limits())
        if (next === m.expr) return
        mode = ScreenMode.Editing(next)
        user = user.copy(amount = next.live?.let { DisplayRules.round(it, inputDigits()) })
        persistAmount()
        publish()
    }

    /** Правило закрытия: без нажатий — всё как было; иначе сумма = последнее вычислимое, округлённое. */
    private fun closeInput(reason: String) {
        val m = mode as? ScreenMode.Editing ?: return
        mode = ScreenMode.Idle
        if (m.expr.touchedSinceOpen) {
            user = user.copy(amount = m.expr.live?.let { DisplayRules.round(it, inputDigits()) })
            persistAmount()
        }
        graph.log.event("recompute", "MainViewModel", "closeInput", belief(), "reason=$reason touched=${m.expr.touchedSinceOpen} base=${user.base} rows=${user.rows.size} mode=idle", "render")
        publish()
    }

    /** Касание пустого места экрана: в Editing — закрыть ввод, в Revealed — вернуть карточку, иначе ничего. */
    fun onOutsideTap() {
        pass(Intent.OutsideTap)
    }

    /** «Назад» (BackHandler включён вне Idle): закрыть ввод, вернуть карточку или отменить перетаскивание. */
    fun onBack() {
        pass(Intent.Back)
    }

    /** Начало прокрутки списка пальцем: возвращает сдвинутую карточку; ввод не закрывает. */
    fun onScrollStarted() {
        pass(Intent.Scroll)
    }

    /**
     * Начало горизонтального свайпа: Idle → FULL; Revealed(другая) → предыдущая возвращается, новая идёт только влево
     * (SwitchReveal); Editing → только закрытие ввода; Revealed(эта же) → только возврат; Dragging → ничего.
     */
    fun onSwipeStart(code: String): SwipeGrant = when (ModeGate.resolve(mode, Intent.SwipeStart(code))) {
        Action.PROCEED -> SwipeGrant.FULL
        Action.SWITCH_REVEAL -> {
            mode = ScreenMode.Idle
            publish()
            SwipeGrant.LEFT_ONLY
        }
        Action.CLOSE_INPUT -> {
            closeInput("swipe")
            SwipeGrant.NONE
        }
        Action.DISMISS_REVEAL -> {
            mode = ScreenMode.Idle
            publish()
            SwipeGrant.NONE
        }
        Action.CANCEL_DRAG, Action.IGNORE -> SwipeGrant.NONE
    }

    /**
     * Отпускание после свайпа (offsetDp — смещение карточки, влево отрицательное). Возвращает true, если карточка
     * Зафиксирована сдвинутой (Revealed); иначе экран сам возвращает её к нулю. Сдвиг вправо за порог при FULL —
     * Обновление источника этой валюты.
     */
    fun onSwipeEnd(code: String, offsetDp: Float, grant: SwipeGrant): Boolean {
        if (!pass(Intent.SwipeEnd(code))) return false
        if (offsetDp <= -GestureDefaults.REVEAL_THRESHOLD_DP) {
            mode = ScreenMode.Revealed(code)
            graph.log.event("recompute", "MainViewModel", "onSwipeEnd", belief(), "revealed=$code base=${user.base} rows=${user.rows.size} mode=revealed", "await_trash_or_dismiss")
            publish()
            return true
        }
        if (grant == SwipeGrant.FULL && offsetDp >= GestureDefaults.RIGHT_THRESHOLD_DP) onSwipeRight(code)
        return false
    }

    /** Свайп вправо в покое: обновление только источника этой валюты; база и ввод не меняются. */
    private fun onSwipeRight(code: String) {
        refreshSource(CurrencyRegistry.sourceFor(code), Trigger.SWIPE)
    }

    /** Частичная попытка по одному источнику: без 15-минутного правила, единственная защита — единовременность. */
    private fun refreshSource(sourceId: String, trigger: Trigger) = refresh(trigger, only = setOf(sourceId))

    /** Корзина сдвинутой карточки: при двух строках ничего (карточка остаётся сдвинутой); иначе удаление с переносом базы. */
    fun onTrashTap(code: String) {
        if (!pass(Intent.TrashTap(code))) return
        if (!ListOps.canRemove(user)) return
        val next = ListOps.remove(user, code, rates)
        if (next == user) return
        user = next
        mode = ScreenMode.Idle
        graph.log.event("recompute", "MainViewModel", "onTrashTap", belief(), "removed=$code base=${user.base} rows=${user.rows.size} mode=idle", "persist")
        persistList("remove")
        publish()
    }

    private fun changeList(next: UserState) {
        if (next == user) return
        user = next
        persistList("list")
        publish()
    }

    val names: CurrencyNames get() = graph.names

    /** «+ Добавить валюту»: проходит вентиль (в Editing — только закрытие), поэтому picker != null ⇒ Idle. */
    fun openPicker() {
        if (!pass(Intent.AddTap)) return
        pickerQuery = ""
        publish()
    }

    fun closePicker() {
        if (pickerQuery == null) return
        pickerQuery = null
        publish()
    }

    fun onPickerQuery(query: String) {
        if (pickerQuery == null) return
        pickerQuery = query
        publish()
    }

    /** Касание доступной валюты: добавить в конец (сразу с эквивалентом) и закрыть выбор; — обновить незагруженный источник. */
    fun onPickerSelect(code: String) {
        if (code in user.rows || !rates.hasRate(code)) return
        pickerQuery = null
        changeList(ListOps.add(user, code))
        val source = CurrencyRegistry.sourceFor(code)
        when (RefreshPolicy.afterAdd(code, rates, refreshing || graph.repository.inProgress)) {
            AddSourceDecision.RUN -> refreshSource(source, Trigger.ADD_SOURCE)
            AddSourceDecision.DEFER -> pendingSourceRefresh = source
            AddSourceDecision.NONE -> Unit
        }
    }

    /** Касание строки «обновлено» — обновить сейчас (в Editing — только закрытие ввода). */
    fun onUpdatedTap() {
        if (pass(Intent.UpdatedTap)) refresh(Trigger.MANUAL)
    }

    /** onStart активности: первый вызов после создания — это холодный старт, он уже обработан. */
    fun onForeground() {
        if (!foregroundSeen) {
            foregroundSeen = true
            return
        }
        refresh(Trigger.FOREGROUND)
    }

    private fun refresh(trigger: Trigger, only: Set<String>? = null) {
        val decision = RefreshPolicy.decide(trigger, refresh, Instant.now(), refreshing || graph.repository.inProgress)
        graph.log.event(
            "refresh_decide", "MainViewModel", "refresh", belief(),
            "trigger=${trigger.name.lowercase()} scope=${only?.sorted()?.joinToString(",") ?: "all"} run=${decision.run} reason=${decision.reason} lastAttempt=${refresh.lastAttemptAt} lastOk=${refresh.lastFullSuccessAt}",
            if (decision.run) "fetch" else "skip",
        )
        if (!decision.run) return
        refreshing = true
        publish()
        val rows = user.rows
        val previous = refresh
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) { graph.repository.refresh(rows, previous, trigger.name.lowercase(), only) }
            } finally {
                refreshing = false
            }
            // Пустой объём частичной попытки: ни записи refresh.json, ни пересчёта
            if (result != null && result.polled.isNotEmpty()) applyRefresh(result)
            publish()
            val pending = pendingSourceRefresh
            if (pending != null) {
                pendingSourceRefresh = null
                refreshSource(pending, Trigger.ADD_SOURCE)
            }
        }
    }

    /** Новые наборы действуют сразу; в Editing строки пересчитываются от текущего значения, выражение не меняется. */
    private fun applyRefresh(result: RefreshResult) {
        if (result.sets.isNotEmpty()) {
            rates = ResolvedRates(rates.sets + result.sets)
            graph.rates = rates
            val fixed = ListOps.ensureBaseHasRate(user, rates)
            if (fixed != user) {
                user = fixed
                // Перенос базы закрывает ввод, как удаление базовой
                if (mode is ScreenMode.Editing) mode = ScreenMode.Idle
                persistList("base")
            }
        }
        refresh = result.refresh
        val snapshot = result.refresh
        graph.scope.launch { graph.stateStore.writeRefresh(snapshot) }
        graph.log.event("recompute", "MainViewModel", "applyRefresh", belief(), "sets=${result.sets.keys} outcome=${result.refresh.lastAttemptOutcome} base=${user.base} rows=${user.rows.size} mode=${modeName()}", "render")
    }

    /** Принудительная запись отложенной суммы (onPause/onStop) — в области приложения, переживает активность. */
    fun flushNow() {
        graph.scope.launch { graph.stateStore.flushNow() }
    }

    /** Список, порядок и база — немедленно; экран обновляется из памяти, не дожидаясь записи. */
    private fun persistList(reason: String) {
        if (mode is ScreenMode.Dragging) persistedWhileDragging = true
        val snapshot = user
        viewModelScope.launch { graph.stateStore.update(snapshot, reason) }
    }

    /** Сумма — с объединением 300 мс. */
    private fun persistAmount() {
        graph.stateStore.scheduleAmount(user)
    }

    private fun belief(): String = "${rates.sets.keys.joinToString(",")};${user.base};daily"

    class Factory(private val graph: AppGraph) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(graph) as T
    }
}
