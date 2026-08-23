package com.dzhokhov.currencyrates.core

/** Операции списка с продуктовыми ограничениями: вверх/вниз, удалить (минимум две строки), добавить в конец. */
object ListOps {
    const val MIN_ROWS = 2

    fun canMoveUp(s: UserState, code: String): Boolean = s.rows.indexOf(code) > 0
    fun canMoveDown(s: UserState, code: String): Boolean = s.rows.indexOf(code).let { it >= 0 && it < s.rows.size - 1 }
    fun canRemove(s: UserState): Boolean = s.rows.size > MIN_ROWS

    fun moveUp(s: UserState, code: String): UserState {
        if (!canMoveUp(s, code)) return s
        val i = s.rows.indexOf(code)
        val rows = s.rows.toMutableList()
        rows[i] = rows[i - 1].also { rows[i - 1] = rows[i] }
        return s.copy(rows = rows)
    }

    fun moveDown(s: UserState, code: String): UserState {
        if (!canMoveDown(s, code)) return s
        val i = s.rows.indexOf(code)
        val rows = s.rows.toMutableList()
        rows[i] = rows[i + 1].also { rows[i + 1] = rows[i] }
        return s.copy(rows = rows)
    }

    /** Перемещение строки на индекс (клиппинг в [0, lastIndex]); база и сумма не меняются. */
    fun move(s: UserState, code: String, toIndex: Int): UserState {
        val from = s.rows.indexOf(code)
        if (from < 0) return s
        val to = toIndex.coerceIn(0, s.rows.lastIndex)
        if (to == from) return s
        val rows = s.rows.toMutableList()
        rows.removeAt(from)
        rows.add(to, code)
        return s.copy(rows = rows)
    }

    /** Удаление; при удалении базовой база — первая оставшаяся с курсом, сумма — точный эквивалент. */
    fun remove(s: UserState, code: String, rates: ResolvedRates): UserState {
        if (!canRemove(s) || code !in s.rows) return s
        val rows = s.rows - code
        if (code != s.base) return s.copy(rows = rows)
        val newBase = rows.firstOrNull { rates.hasRate(it) } ?: rows.first()
        return rebase(s.copy(rows = rows), newBase, rates)
    }

    fun add(s: UserState, code: String): UserState =
        if (code in s.rows) s else s.copy(rows = s.rows + code)

    /** Перенос базы с точным эквивалентом прежней суммы; поле показывает округлённое значение (amountTyped = false). */
    fun rebase(s: UserState, newBase: String, rates: ResolvedRates): UserState {
        if (newBase == s.base || newBase !in s.rows) return s
        val converted = s.amount?.let { Converter.convert(it, s.base, newBase, rates) ?: it }
        return s.copy(base = newBase, amount = converted, amountTyped = false, amountText = null)
    }

    /** Если база потеряла курс, база переходит на первую строку с курсом. Без курсов у всех — база остаётся. */
    fun ensureBaseHasRate(s: UserState, rates: ResolvedRates): UserState {
        if (rates.hasRate(s.base)) return s
        val candidate = s.rows.firstOrNull { rates.hasRate(it) } ?: return s
        return rebase(s, candidate, rates)
    }
}
