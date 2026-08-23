package com.dzhokhov.currencyrates.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dzhokhov.currencyrates.R
import com.dzhokhov.currencyrates.core.CurrencyRegistry
import com.dzhokhov.currencyrates.core.DisplayRules
import com.dzhokhov.currencyrates.core.Formatters
import com.dzhokhov.currencyrates.core.Kind
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun rememberFormatters(): Formatters {
    val locale: Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return remember(locale) { Formatters(locale) }
}

@Composable
fun App(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    val picker = state.picker
    if (picker != null) {
        PickerScreen(
            picker = picker,
            names = vm.names,
            onQuery = { vm.onPickerQuery(it) },
            onSelect = { vm.onPickerSelect(it) },
            onClose = { vm.closePicker() },
        )
    } else {
        MainScreen(state, vm)
    }
}

@Composable
fun MainScreen(state: UiState, vm: MainViewModel) {
    val fmt = rememberFormatters()
    val listState = rememberLazyListState()
    val mode = state.mode
    val editing = mode.kind == ModeKind.EDITING

    // «Назад» вне Idle: закрыть ввод, вернуть карточку, отменить перетаскивание (IME на главном экране нет).
    BackHandler(enabled = mode.kind != ModeKind.IDLE) { vm.onBack() }

    // Базовая строка остаётся видимой над панелью: после кадра, в котором панель измерена.
    LaunchedEffect(editing, state.base) {
        if (!editing) return@LaunchedEffect
        withFrameNanos { }
        val idx = state.rows.indexOfFirst { it.isBase }
        if (idx < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo.any { it.index == idx && it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset }
        if (!visible) listState.animateScrollToItem(idx)
    }

    // Прокрутка пальцем (не программная): возвращает сдвинутую карточку.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) vm.onScrollStarted() }
    }

    // Перетаскивание: пиксели — здесь, режим Dragging — в ViewModel; выход из Dragging по любой причине сбрасывает пиксели.
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val drag = remember(listState) {
        DragReorderState(
            listState = listState,
            scope = scope,
            edgePx = with(density) { GestureDefaults.EDGE_DP.dp.toPx() },
            stepPx = with(density) { GestureDefaults.SCROLL_STEP_DP.dp.toPx() },
            onMove = { code, toIndex -> vm.onDragMove(code, toIndex) },
        )
    }
    LaunchedEffect(mode.kind) { if (mode.kind != ModeKind.DRAGGING) drag.end() }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        bottomBar = { if (editing) Keypad(fmt.decimalSeparator) { vm.onKey(it) } },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                // Пустое место: дочерние clickable поглощают свои касания, сюда доходят только касания мимо.
                .pointerInput(Unit) { detectTapGestures { vm.onOutsideTap() } },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                UpdatedLine(state.updated, fmt, onTap = { vm.onUpdatedTap() })
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.rows, key = { it.code }) { row ->
                        val dragged = drag.draggedKey == row.code
                        CurrencyRow(
                            row = row,
                            field = if (row.isBase) state.field else null,
                            fmt = fmt,
                            mode = mode,
                            vm = vm,
                            drag = drag,
                            modifier = if (dragged) {
                                Modifier
                                    .zIndex(1f)
                                    .graphicsLayer {
                                        translationY = drag.translationY(row.code)
                                        shadowElevation = 6.dp.toPx()
                                    }
                            } else {
                                Modifier.animateItem()
                            },
                        )
                    }
                    item(key = ADD_ROW_KEY) {
                        Text(
                            text = stringResource(R.string.add_currency),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.openPicker() }
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UpdatedLine(updated: UpdatedUi, fmt: Formatters, onTap: () -> Unit) {
    val info = updated.info
    val sep = stringResource(R.string.separator)
    val parts = ArrayList<String>()
    parts.add(stringResource(R.string.rates_for, info.setDate?.let { fmt.date(it) } ?: stringResource(R.string.no_amount)))
    parts.add(info.loadedAt?.let { stringResource(R.string.loaded_at, fmt.dateTime(it)) } ?: stringResource(R.string.embedded_set))
    if (info.noNetwork) parts.add(stringResource(R.string.no_network))
    if (info.updateFailed) parts.add(stringResource(R.string.update_failed))
    val text = (if (info.stale) stringResource(R.string.stale_prefix) + " " else "") + parts.joinToString(sep)
    val color = if (info.stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val hint = stringResource(R.string.tap_to_refresh)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .semantics { contentDescription = hint }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f))
        if (updated.refreshing) {
            val refreshing = stringResource(R.string.refreshing)
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(modifier = Modifier.size(14.dp).semantics { contentDescription = refreshing }, strokeWidth = 2.dp)
        }
    }
}

/**
 * Карточка валюты: сзади у правого края область корзины (п. 2), спереди содержимое со смещением offsetX.
 * Свайп — draggable по горизонтали; вертикальная прокрутка и свайп разделяются осью первого превышения touchSlop.
 * Возврат к нулю после отпускания выполняет onDragStopped во всех ветках без Revealed(code); сдвинутой бывает
 * Ровно одна карточка — каждая наблюдает режим экрана.
 */
@Composable
fun CurrencyRow(
    row: RowUi,
    field: FieldUi?,
    fmt: Formatters,
    mode: ModeUi,
    vm: MainViewModel,
    drag: DragReorderState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val revealPx = with(density) { GestureDefaults.REVEAL_DP.dp.toPx() }
    val rightMaxPx = with(density) { GestureDefaults.RIGHT_MAX_DP.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val isRevealed = mode.kind == ModeKind.REVEALED && mode.code == row.code
    LaunchedEffect(isRevealed) { offsetX.animateTo(if (isRevealed) -revealPx else 0f) }
    var grant by remember { mutableStateOf(SwipeGrant.NONE) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (row.isBase) MaterialTheme.colorScheme.primary else Color.Transparent
    val trashDescription = if (row.canRemove) stringResource(R.string.delete_row, row.code) else stringResource(R.string.delete_unavailable)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        // Корзина: красная при canRemove, серая и неактивная при двух строках.
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            Box(
                modifier = Modifier
                    .width(GestureDefaults.REVEAL_DP.dp)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(if (row.canRemove) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { vm.onTrashTap(row.code) }
                    .semantics { contentDescription = trashDescription },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = if (row.canRemove) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.background, shape)
                .border(2.dp, borderColor, shape)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (grant != SwipeGrant.NONE) {
                            val max = if (grant == SwipeGrant.FULL) rightMaxPx else 0f
                            val target = (offsetX.value + delta).coerceIn(-revealPx, max)
                            scope.launch { offsetX.snapTo(target) }
                        }
                    },
                    onDragStarted = { grant = vm.onSwipeStart(row.code) },
                    onDragStopped = {
                        val g = grant
                        grant = SwipeGrant.NONE
                        if (g != SwipeGrant.NONE) {
                            val revealed = vm.onSwipeEnd(row.code, with(density) { offsetX.value.toDp().value }, g)
                            if (!revealed) offsetX.animateTo(0f)
                        }
                    },
                )
                .clickable { vm.onRowTap(row.code) }
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        ) {
            FlagOrBadge(row.code, row.kind)
            Spacer(Modifier.width(12.dp))
            Text(text = row.code, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 230.dp)) {
                if (row.isBase && field != null) {
                    AmountField(field, fmt, onTapWhenIdle = { vm.onRowTap(row.code) })
                    val result = field.result
                    if (result != null) {
                        val resultText = stringResource(R.string.result_line, fmt.number(result, DisplayRules.amountScale(result, row.kind)))
                        Text(text = resultText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                } else {
                    val amountText = row.amount?.let { fmt.number(it, DisplayRules.amountScale(it, row.kind)) }
                        ?: stringResource(R.string.no_amount)
                    Text(text = amountText, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                    val rateText = row.rateLine?.let {
                        stringResource(R.string.rate_line, it.unitCode, fmt.number(it.value, it.scale), it.valueCode)
                    } ?: stringResource(R.string.rate_unavailable)
                    Text(text = rateText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            DragHandle(row.code, drag, vm)
        }
    }
}

/**
 * Ручка «⋮»: короткое касание ничего не делает (собственный clickable поглощает его); удержание (системный
 * longPressTimeout) начинает перетаскивание, если вентиль разрешил. Защита drag.active: если onHandleHold
 * Вернул false или «Назад» отменил перетаскивание, дальнейшие onDrag/onDragEnd этого жеста ничего не делают.
 */
@Composable
fun DragHandle(code: String, drag: DragReorderState, vm: MainViewModel) {
    val description = stringResource(R.string.drag_handle, code)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .pointerInput(code) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { if (vm.onHandleHold(code)) drag.start(code) },
                    onDrag = { change, amount ->
                        if (drag.active) {
                            change.consume()
                            drag.move(amount.y)
                        }
                    },
                    onDragEnd = {
                        if (drag.active) {
                            drag.end()
                            vm.onDragEnd()
                        }
                    },
                    onDragCancel = {
                        if (drag.active) {
                            drag.end()
                            vm.onDragEnd()
                        }
                    },
                )
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "⋮", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Поле суммы: Text без фокуса и IME (В1). Всегда показывает конец выражения — контейнер прокручивается к концу
 * При каждом изменении ширины текста; пальцем не прокручивается. В Editing касания внутри поля ничего не меняют;
 * В Idle касание поля — касание базовой строки.
 */
@Composable
fun AmountField(field: FieldUi, fmt: Formatters, onTapWhenIdle: () -> Unit) {
    val display = if (field.zero) stringResource(R.string.zero_hint) else fmt.expression(field.text)
    val color = if (field.zero) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.maxValue }.collect { scrollState.scrollTo(it) }
    }
    val description = stringResource(R.string.amount_field)
    Box(
        modifier = Modifier
            .widthIn(min = 120.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (!field.editing) onTapWhenIdle() }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(Modifier.horizontalScroll(scrollState, enabled = false)) {
            Text(
                text = display,
                style = MaterialTheme.typography.headlineSmall,
                color = color,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** Флаг-эмодзи (по региону из реестра), значок металла или биткоина; без глифа — код в кружке. */
@Composable
fun FlagOrBadge(code: String, kind: Kind) {
    val size = 40.dp
    when (kind) {
        Kind.METAL -> {
            val gold = code == "XAU"
            val bg = if (gold) Color(0xFFE0B340) else Color(0xFFB8BCC4)
            Box(Modifier.size(size).background(bg, CircleShape), contentAlignment = Alignment.Center) {
                Text(text = if (gold) "Au" else "Ag", style = MaterialTheme.typography.titleMedium, color = Color(0xFF3A2E00))
            }
        }
        Kind.CRYPTO -> {
            val symbol = "₿"
            if (Glyphs.has(symbol)) {
                Box(Modifier.size(size).background(Color(0xFFF7931A), CircleShape), contentAlignment = Alignment.Center) {
                    Text(text = symbol, style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
            } else CodeBadge(code, size)
        }
        Kind.FIAT -> {
            val flag = CurrencyRegistry.flagRegion(code)?.let { Glyphs.flag(it) }
            if (flag != null && Glyphs.has(flag)) {
                Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                    Text(text = flag, style = MaterialTheme.typography.headlineMedium)
                }
            } else CodeBadge(code, size)
        }
    }
}

@Composable
private fun CodeBadge(code: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** Проверка глифов с кэшем: Paint.hasGlyph (API 23+). */
object Glyphs {
    private val cache = HashMap<String, Boolean>()
    private val paint = android.graphics.Paint()

    fun has(text: String): Boolean = synchronized(cache) { cache.getOrPut(text) { paint.hasGlyph(text) } }

    fun flag(region: String): String {
        val sb = StringBuilder()
        for (c in region) sb.appendCodePoint(0x1F1E6 + (c - 'A'))
        return sb.toString()
    }
}
