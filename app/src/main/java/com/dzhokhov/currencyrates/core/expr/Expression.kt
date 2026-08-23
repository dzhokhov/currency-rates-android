package com.dzhokhov.currencyrates.core.expr

import com.dzhokhov.currencyrates.core.DisplayRules
import java.math.BigDecimal

/**
 * Выражение в поле ввода. untouched — «нетронутое значение» (число при касании строки или результат после «=»):
 * Цифра и разделитель заменяют его целиком, знак продолжает, ⌫ стирает последний символ.
 * touchedSinceOpen — было ли хотя бы одно принятое нажатие с открытия ввода (правило закрытия).
 * Все отказы молчаливые: apply возвращает прежний объект.
 */
data class Expression(val tokens: List<Token>, val untouched: Boolean, val touchedSinceOpen: Boolean) {
    /** Нормализованный текст: точка и знаки + - * /. Пустой — ноль. */
    val text: String
        get() = tokens.joinToString("") {
            when (it) {
                is Token.Num -> it.text
                is Token.Op -> it.op.symbol.toString()
            }
        }

    val isEmpty: Boolean get() = tokens.isEmpty()
    val hasOp: Boolean get() = tokens.any { it is Token.Op }

    /** Живое значение; null — ноль. */
    val live: BigDecimal? get() = ExpressionEval.lastComputable(tokens)

    /** Вычислимо ли выражение целиком без завершающего знака (пустое — ноль, вычислимо); false при ÷0. */
    val isComputable: Boolean get() = tokens.isEmpty() || ExpressionEval.evaluate(withoutTrailingOp(tokens)) != null

    fun apply(key: Key, limits: Limits): Expression {
        if (key is Key.Enter) return this
        if (key is Key.Equals) return applyEquals(limits)
        val next = (if (untouched) applyUntouched(key) else applyTouched(key, limits)) ?: return this
        if (length(next) > limits.maxChars) return this
        return Expression(next, untouched = false, touchedSinceOpen = true)
    }

    /** «=»: без знака — ничего; завершающий знак отбрасывается; ÷0 — сворачивания нет; иначе результат — нетронутое значение. */
    private fun applyEquals(limits: Limits): Expression {
        if (!hasOp) return this
        val stripped = withoutTrailingOp(tokens)
        val value = ExpressionEval.evaluate(stripped)
            ?: return if (stripped.size == tokens.size) this else Expression(stripped, untouched = false, touchedSinceOpen = true)
        val text = DisplayRules.fieldText(DisplayRules.round(value, limits.maxFrac))
        return Expression(resultTokens(text), untouched = true, touchedSinceOpen = true)
    }

    private fun applyUntouched(key: Key): List<Token>? {
        val num = tokens.singleOrNull() as? Token.Num
        return when (key) {
            is Key.Digit -> listOf(Token.Num(key.c.toString()))
            Key.Separator -> listOf(Token.Num("0."))
            is Key.Op -> if (num == null) null else listOf(num, Token.Op(key.op))
            Key.Backspace -> if (num == null) null else erase(num)
            Key.Equals, Key.Enter -> null
        }
    }

    private fun applyTouched(key: Key, limits: Limits): List<Token>? {
        val last = tokens.lastOrNull()
        return when (key) {
            is Key.Digit -> {
                if (last is Token.Num) {
                    val text = last.text + key.c
                    if (!numberWithinLimits(text, limits)) null else tokens.dropLast(1) + Token.Num(text)
                } else tokens + Token.Num(key.c.toString())
            }
            Key.Separator -> {
                if (last is Token.Num) {
                    if ('.' in last.text) null else tokens.dropLast(1) + Token.Num(last.text + ".")
                } else tokens + Token.Num("0.")
            }
            is Key.Op -> when (last) {
                null -> null
                is Token.Op -> if (last.op == key.op) null else tokens.dropLast(1) + Token.Op(key.op)
                is Token.Num -> tokens.dropLast(1) + Token.Num(last.text.trimEnd('.')) + Token.Op(key.op)
            }
            Key.Backspace -> when (last) {
                null -> null
                is Token.Op -> tokens.dropLast(1)
                is Token.Num -> tokens.dropLast(1) + erase(last)
            }
            Key.Equals, Key.Enter -> null
        }
    }

    private fun erase(num: Token.Num): List<Token> {
        val t = num.text.dropLast(1)
        return if (t.isEmpty() || t == "-" || t == "-.") emptyList() else listOf(Token.Num(t))
    }

    companion object {
        val EMPTY = Expression(emptyList(), untouched = true, touchedSinceOpen = false)

        /** Нетронутое значение из текста поля; «0» и пустой текст — ноль (пустое выражение). */
        fun untouched(fieldText: String): Expression =
            Expression(resultTokens(fieldText), untouched = true, touchedSinceOpen = false)

        private fun resultTokens(fieldText: String): List<Token> =
            if (fieldText.isEmpty() || fieldText == "0") emptyList() else listOf(Token.Num(fieldText))

        fun withoutTrailingOp(tokens: List<Token>): List<Token> =
            if (tokens.lastOrNull() is Token.Op) tokens.dropLast(1) else tokens

        private fun length(tokens: List<Token>): Int = tokens.sumOf {
            when (it) {
                is Token.Num -> it.text.length
                is Token.Op -> 1
            }
        }

        /** До maxInt цифр до разделителя и до maxFrac после; знак только ведущий «-» (у результата). */
        fun numberWithinLimits(text: String, limits: Limits): Boolean {
            val unsigned = text.removePrefix("-")
            val dot = unsigned.indexOf('.')
            val intDigits = if (dot >= 0) dot else unsigned.length
            val fracDigits = if (dot >= 0) unsigned.length - dot - 1 else 0
            return intDigits <= limits.maxInt && fracDigits <= limits.maxFrac
        }
    }
}
