package io.github.dzhokhov.quotes.ui

/** Положение элемента списка в координатах области просмотра: индекс, верхний край, высота. */
data class ItemBounds(val index: Int, val offset: Int, val size: Int)

/** Чистая математика обмена при перетаскивании: цель меняется при пересечении середины соседа. */
object ReorderMath {
    /**
     * items — только строки валют (без «+ Добавить»). Нижний край перетаскиваемой ниже середины следующего → index + 1;
     * Верхний край выше середины предыдущего → index − 1; иначе без изменений.
     */
    fun targetIndex(draggedIndex: Int, draggedTop: Int, draggedSize: Int, items: List<ItemBounds>): Int {
        val draggedBottom = draggedTop + draggedSize
        val next = items.firstOrNull { it.index == draggedIndex + 1 }
        if (next != null && draggedBottom > next.offset + next.size / 2) return draggedIndex + 1
        val prev = items.firstOrNull { it.index == draggedIndex - 1 }
        if (prev != null && draggedTop < prev.offset + prev.size / 2) return draggedIndex - 1
        return draggedIndex
    }
}
