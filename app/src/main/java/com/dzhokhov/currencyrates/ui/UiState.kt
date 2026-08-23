package com.dzhokhov.currencyrates.ui

import com.dzhokhov.currencyrates.core.FreshnessInfo
import com.dzhokhov.currencyrates.core.Kind
import com.dzhokhov.currencyrates.core.PickerEntry
import com.dzhokhov.currencyrates.core.RateLine
import java.math.BigDecimal

enum class ModeKind { IDLE, EDITING, REVEALED, DRAGGING }

/** Режим экрана для композаблов: вид и код карточки (сдвинутой или перетаскиваемой). */
data class ModeUi(val kind: ModeKind, val code: String?) {
    companion object {
        val IDLE = ModeUi(ModeKind.IDLE, null)
    }
}

/** Строка списка: сумма null — «—» (нет курса); rateLine null у базовой или без курса; canRemove — не при двух строках. */
data class RowUi(
    val code: String,
    val kind: Kind,
    val isBase: Boolean,
    val hasRate: Boolean,
    val amount: BigDecimal?,
    val rateLine: RateLine?,
    val canRemove: Boolean,
)

/**
 * Поле базовой строки: text — нормализованное выражение (точка, + - * /) или текст результата; пустой — ноль;
 * result — живое значение, пока в выражении есть знак действия (показывается «= результат» на месте строки курса).
 */
data class FieldUi(
    val kind: Kind,
    val editing: Boolean,
    val text: String,
    val result: BigDecimal?,
) {
    val zero: Boolean get() = text.isEmpty()
}

data class UpdatedUi(val info: FreshnessInfo, val refreshing: Boolean)

/** Экран выбора: запрос поиска и полный состав (фильтрация по языку — в композабле). */
data class PickerUi(val query: String, val group: List<PickerEntry>, val fiat: List<PickerEntry>)

data class UiState(
    val rows: List<RowUi>,
    val base: String,
    val field: FieldUi,
    val updated: UpdatedUi,
    val picker: PickerUi?,
    val mode: ModeUi,
)
