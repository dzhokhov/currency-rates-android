package io.github.dzhokhov.quotes.ui

import io.github.dzhokhov.quotes.core.expr.Expression

/** Режим экрана (живёт в ViewModel, переживает поворот). Не более одного из Editing/Revealed/Dragging. */
sealed class ScreenMode {
    object Idle : ScreenMode()
    data class Editing(val expr: Expression) : ScreenMode()
    data class Revealed(val code: String) : ScreenMode()
    data class Dragging(val code: String) : ScreenMode()
}

/** Намерение экрана: что хочет сделать обработчик до сверки с вентилем. */
sealed class Intent {
    data class RowTap(val code: String) : Intent()
    data class HandleHold(val code: String) : Intent()
    data class SwipeStart(val code: String) : Intent()
    data class SwipeEnd(val code: String) : Intent()
    data class TrashTap(val code: String) : Intent()
    object OutsideTap : Intent()
    object Back : Intent()
    object Scroll : Intent()
    object UpdatedTap : Intent()
    object AddTap : Intent()
    object Key : Intent()
    object DragMove : Intent()
    object DragEnd : Intent()
}

enum class Action { PROCEED, CLOSE_INPUT, DISMISS_REVEAL, SWITCH_REVEAL, CANCEL_DRAG, IGNORE }

/**
 * Вентиль режимов: в Editing первый жест только закрывает ввод, в Revealed —
 * Только возвращает карточку (исключение — свайп по другой карточке: SwitchReveal), в Dragging всё игнорируется,
 * Кроме движения, отпускания и «Назад». Чистая функция без android.*.
 */
object ModeGate {
    fun resolve(mode: ScreenMode, intent: Intent): Action = when (mode) {
        ScreenMode.Idle -> when (intent) {
            is Intent.RowTap, is Intent.HandleHold, is Intent.SwipeStart, is Intent.SwipeEnd,
            Intent.UpdatedTap, Intent.AddTap -> Action.PROCEED
            else -> Action.IGNORE
        }
        is ScreenMode.Editing -> when (intent) {
            Intent.Key -> Action.PROCEED
            is Intent.RowTap, is Intent.HandleHold, is Intent.SwipeStart, Intent.OutsideTap, Intent.Back,
            Intent.UpdatedTap, Intent.AddTap -> Action.CLOSE_INPUT
            else -> Action.IGNORE
        }
        is ScreenMode.Revealed -> when (intent) {
            is Intent.TrashTap -> if (intent.code == mode.code) Action.PROCEED else Action.DISMISS_REVEAL
            is Intent.SwipeStart -> if (intent.code == mode.code) Action.DISMISS_REVEAL else Action.SWITCH_REVEAL
            is Intent.RowTap, is Intent.HandleHold, Intent.OutsideTap, Intent.Back, Intent.Scroll,
            Intent.UpdatedTap, Intent.AddTap -> Action.DISMISS_REVEAL
            else -> Action.IGNORE
        }
        is ScreenMode.Dragging -> when (intent) {
            Intent.DragMove, Intent.DragEnd -> Action.PROCEED
            Intent.Back -> Action.CANCEL_DRAG
            else -> Action.IGNORE
        }
    }
}
