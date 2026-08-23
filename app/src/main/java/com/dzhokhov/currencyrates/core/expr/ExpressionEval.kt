package com.dzhokhov.currencyrates.core.expr

import java.math.BigDecimal
import java.math.MathContext

/** Вычислитель на BigDecimal без double: обычный приоритет (× ÷ раньше + −), внутри приоритета слева направо. */
object ExpressionEval {
    val MC: MathContext = MathContext.DECIMAL128

    /** Значение полного выражения Num (Op Num)*; null — невычислимо: пусто, завершающий знак или деление на ноль. */
    fun evaluate(tokens: List<Token>): BigDecimal? {
        if (tokens.isEmpty()) return null
        val first = tokens[0] as? Token.Num ?: return null
        var term = number(first) ?: return null
        var sum: BigDecimal? = null
        var pending: Operator? = null
        var i = 1
        while (i < tokens.size) {
            val op = (tokens[i] as? Token.Op)?.op ?: return null
            val num = (tokens.getOrNull(i + 1) as? Token.Num)?.let { number(it) } ?: return null
            when (op) {
                Operator.MUL -> term = term.multiply(num)
                Operator.DIV -> {
                    if (num.signum() == 0) return null
                    term = term.divide(num, MC)
                }
                Operator.ADD, Operator.SUB -> {
                    sum = fold(sum, pending, term)
                    pending = op
                    term = num
                }
            }
            i += 2
        }
        return fold(sum, pending, term)
    }

    /** Значение самого длинного префикса, который не оканчивается знаком и вычислим; null — ноль (пусто или ничего не вычислимо). */
    fun lastComputable(tokens: List<Token>): BigDecimal? {
        var n = tokens.size
        while (n > 0) {
            if (tokens[n - 1] is Token.Num) {
                evaluate(tokens.subList(0, n))?.let { return it }
            }
            n--
        }
        return null
    }

    private fun fold(sum: BigDecimal?, op: Operator?, term: BigDecimal): BigDecimal = when {
        sum == null || op == null -> term
        op == Operator.ADD -> sum.add(term)
        else -> sum.subtract(term)
    }

    /** Текст числа → значение; завершающая точка отбрасывается; «-» без цифр — невычислимо. */
    fun number(num: Token.Num): BigDecimal? {
        val t = num.text.trimEnd('.')
        if (t.isEmpty() || t == "-") return null
        return try {
            BigDecimal(t)
        } catch (e: NumberFormatException) {
            null
        }
    }
}
