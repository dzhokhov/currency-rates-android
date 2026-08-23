package io.github.dzhokhov.quotes.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val ADD_ROW_KEY = "+add"

/**
 * Локальное состояние перетаскивания: ключ перетаскиваемой строки, её положение на момент
 * Удержания и накопленное смещение пальца. Смещение на экране = initialOffset + fingerDelta − текущее положение
 * Элемента — формула самокорректируется после любого обмена и прокрутки. Обмен — при пересечении середины соседа
 * (ReorderMath); автопрокрутка — пока карточка ближе edgePx к краю области списка (шаг stepPx за кадр).
 * Режим Dragging живёт в ViewModel; здесь только пиксели.
 */
class DragReorderState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val edgePx: Float,
    private val stepPx: Float,
    private val onMove: (String, Int) -> Unit,
) {
    var draggedKey by mutableStateOf<String?>(null)
        private set
    private var initialOffset = 0
    private var draggedSize = 0
    private var fingerDelta by mutableFloatStateOf(0f)
    private var autoScroll: Job? = null
    private var scrollDirection = 0f

    val active: Boolean get() = draggedKey != null

    fun start(key: String) {
        val item = rows().firstOrNull { it.key == key } ?: return
        draggedKey = key
        initialOffset = item.offset
        draggedSize = item.size
        fingerDelta = 0f
    }

    fun move(dy: Float) {
        if (!active) return
        fingerDelta += dy
        checkSwap()
        updateAutoScroll()
    }

    fun end() {
        draggedKey = null
        fingerDelta = 0f
        autoScroll?.cancel()
        autoScroll = null
    }

    /** Смещение перетаскиваемой карточки относительно её текущего места в списке; 0 для остальных. */
    fun translationY(key: String): Float {
        if (key != draggedKey) return 0f
        val current = rows().firstOrNull { it.key == key }?.offset ?: return 0f
        return visualTop() - current
    }

    private fun visualTop(): Float = initialOffset + fingerDelta

    private fun rows(): List<LazyListItemInfo> = listState.layoutInfo.visibleItemsInfo.filter { it.key != ADD_ROW_KEY }

    private fun checkSwap() {
        val key = draggedKey ?: return
        val items = rows()
        val dragged = items.firstOrNull { it.key == key } ?: return
        val target = ReorderMath.targetIndex(
            dragged.index,
            visualTop().roundToInt(),
            dragged.size,
            items.map { ItemBounds(it.index, it.offset, it.size) },
        )
        if (target != dragged.index) onMove(key, target)
    }

    private fun updateAutoScroll() {
        if (edgePx <= 0f) return
        val info = listState.layoutInfo
        val top = visualTop()
        val bottom = top + draggedSize
        scrollDirection = when {
            top < info.viewportStartOffset + edgePx -> -1f
            bottom > info.viewportEndOffset - edgePx -> 1f
            else -> 0f
        }
        if (scrollDirection == 0f) {
            autoScroll?.cancel()
            autoScroll = null
            return
        }
        if (autoScroll?.isActive == true) return
        autoScroll = scope.launch {
            while (isActive && active && scrollDirection != 0f) {
                withFrameNanos { }
                val consumed = listState.scrollBy(scrollDirection * stepPx)
                if (consumed == 0f) break
                checkSwap()
            }
        }
    }
}
