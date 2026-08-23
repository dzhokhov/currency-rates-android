package com.dzhokhov.currencyrates.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Обмен при пересечении середины соседа; строки равной высоты 72 px; «+ Добавить» в списке целей нет. */
class ReorderMathTest {
    private val size = 72
    private val items = (0 until 5).map { ItemBounds(index = it, offset = it * size, size = size) }

    @Test fun noChangeInsideOwnSlot() {
        assertEquals(2, ReorderMath.targetIndex(2, 2 * size, size, items))
        assertEquals(2, ReorderMath.targetIndex(2, 2 * size + 30, size, items))
        assertEquals(2, ReorderMath.targetIndex(2, 2 * size - 30, size, items))
    }

    @Test fun bottomEdgeBelowNextMiddleMovesDown() {
        // Середина следующего (index 3) = 3*72 + 36 = 252; нижний край = top + 72 > 252 → top > 180
        assertEquals(2, ReorderMath.targetIndex(2, 180, size, items))
        assertEquals(3, ReorderMath.targetIndex(2, 181, size, items))
    }

    @Test fun topEdgeAbovePrevMiddleMovesUp() {
        // Середина предыдущего (index 1) = 72 + 36 = 108; верхний край < 108
        assertEquals(2, ReorderMath.targetIndex(2, 108, size, items))
        assertEquals(1, ReorderMath.targetIndex(2, 107, size, items))
    }

    @Test fun edgesHaveNoNeighbour() {
        assertEquals(0, ReorderMath.targetIndex(0, -500, size, items))
        assertEquals(4, ReorderMath.targetIndex(4, 4 * size + 500, size, items))
        // «+add» отсутствует в items: последняя строка вниз не уходит
        assertEquals(4, ReorderMath.targetIndex(4, 4 * size + 40, size, items))
    }

    @Test fun onlyVisibleNeighboursCount() {
        val partial = items.filter { it.index != 3 }
        assertEquals(2, ReorderMath.targetIndex(2, 300, size, partial))
    }
}
