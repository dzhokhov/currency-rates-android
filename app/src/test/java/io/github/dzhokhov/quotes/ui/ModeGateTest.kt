package io.github.dzhokhov.quotes.ui

import io.github.dzhokhov.quotes.core.expr.Expression
import org.junit.Assert.assertEquals
import org.junit.Test

/** Таблица вентиля целиком: каждая клетка — проверка. */
class ModeGateTest {
    private val editing = ScreenMode.Editing(Expression.EMPTY)
    private val revealed = ScreenMode.Revealed("AED")
    private val dragging = ScreenMode.Dragging("BAM")

    private val same = "AED"
    private val other = "JPY"

    private fun all(code: String) = listOf(
        Intent.RowTap(code), Intent.HandleHold(code), Intent.SwipeStart(code), Intent.SwipeEnd(code), Intent.TrashTap(code),
        Intent.OutsideTap, Intent.Back, Intent.Scroll, Intent.UpdatedTap, Intent.AddTap, Intent.Key, Intent.DragMove, Intent.DragEnd,
    )

    private fun check(mode: ScreenMode, expected: Map<Intent, Action>) {
        for ((intent, action) in expected) assertEquals("$mode / $intent", action, ModeGate.resolve(mode, intent))
    }

    @Test fun idle() {
        val expected = all(same).associateWith {
            when (it) {
                is Intent.RowTap, is Intent.HandleHold, is Intent.SwipeStart, is Intent.SwipeEnd, Intent.UpdatedTap, Intent.AddTap -> Action.PROCEED
                else -> Action.IGNORE
            }
        }
        assertEquals(13, expected.size)
        check(ScreenMode.Idle, expected)
    }

    @Test fun editing() {
        val expected = all(same).associateWith {
            when (it) {
                Intent.Key -> Action.PROCEED
                is Intent.RowTap, is Intent.HandleHold, is Intent.SwipeStart, Intent.OutsideTap, Intent.Back, Intent.UpdatedTap, Intent.AddTap -> Action.CLOSE_INPUT
                else -> Action.IGNORE
            }
        }
        assertEquals(13, expected.size)
        check(editing, expected)
    }

    @Test fun revealedSameCard() {
        val expected = all(same).associateWith {
            when (it) {
                is Intent.TrashTap -> Action.PROCEED
                is Intent.RowTap, is Intent.HandleHold, is Intent.SwipeStart, Intent.OutsideTap, Intent.Back, Intent.Scroll, Intent.UpdatedTap, Intent.AddTap -> Action.DISMISS_REVEAL
                else -> Action.IGNORE
            }
        }
        assertEquals(13, expected.size)
        check(revealed, expected)
    }

    @Test fun revealedOtherCard() {
        // Свайп влево по другой карточке — SwitchReveal; корзина другой карточки — только возврат
        assertEquals(Action.SWITCH_REVEAL, ModeGate.resolve(revealed, Intent.SwipeStart(other)))
        assertEquals(Action.DISMISS_REVEAL, ModeGate.resolve(revealed, Intent.TrashTap(other)))
        assertEquals(Action.DISMISS_REVEAL, ModeGate.resolve(revealed, Intent.RowTap(other)))
        assertEquals(Action.DISMISS_REVEAL, ModeGate.resolve(revealed, Intent.HandleHold(other)))
        assertEquals(Action.IGNORE, ModeGate.resolve(revealed, Intent.SwipeEnd(other)))
    }

    @Test fun dragging() {
        val expected = all(same).associateWith {
            when (it) {
                Intent.DragMove, Intent.DragEnd -> Action.PROCEED
                Intent.Back -> Action.CANCEL_DRAG
                else -> Action.IGNORE
            }
        }
        assertEquals(13, expected.size)
        check(dragging, expected)
        assertEquals(Action.IGNORE, ModeGate.resolve(dragging, Intent.RowTap(other)))
        assertEquals(Action.IGNORE, ModeGate.resolve(dragging, Intent.SwipeStart("BAM")))
    }
}
