package com.dzhokhov.currencyrates.core.expr

import com.dzhokhov.currencyrates.core.DisplayRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Золотые значения приёмки для ядра выражений. Внутренний текст: точка и + - * /. */
class ExpressionTest {
    private val fiat = Limits(maxFrac = 2)
    private val crypto = Limits(maxFrac = 8)

    private fun key(c: Char): Key = when (c) {
        '=' -> Key.Equals
        '<' -> Key.Backspace
        else -> KeyMap.fromChar(c) ?: error("no key for $c")
    }

    /** Набор клавиш из строки: цифры, '.', '+ - * /', '=' — равно, '<' — ⌫. */
    private fun Expression.type(keys: String, limits: Limits = fiat): Expression =
        keys.fold(this) { e, c -> e.apply(key(c), limits) }

    private fun typed(keys: String, limits: Limits = fiat): Expression = Expression.EMPTY.type(keys, limits)

    private fun value(e: Expression): String? = e.live?.let { DisplayRules.round(it, 2).toPlainString() }

    @Test fun untouchedDigitReplaces() {
        val e = Expression.untouched("9.95")
        assertEquals("9.95", e.text)
        assertTrue(e.untouched)
        assertFalse(e.touchedSinceOpen)
        val one = e.apply(Key.Digit('1'), fiat)
        assertEquals("1", one.text)
        assertFalse(one.untouched)
        assertTrue(one.touchedSinceOpen)
    }

    @Test fun untouchedOperatorContinues() {
        val e = Expression.untouched("9.95").apply(Key.Op(Operator.MUL), fiat)
        assertEquals("9.95*", e.text)
        assertEquals("9.95", value(e))
        val r = e.type("3")
        assertEquals("9.95*3", r.text)
        assertEquals("29.85", value(r))
    }

    @Test fun untouchedBackspaceErasesLastChar() {
        assertEquals("9.9", Expression.untouched("9.95").apply(Key.Backspace, fiat).text)
        // После первого ⌫ число редактируется как набранное
        val e = Expression.untouched("9.95").type("<<")
        assertEquals("9.", e.text)
        assertEquals("9.00", value(e))
        // ⌫ на «-5» → пусто (ноль)
        val neg = Expression.untouched("-5").apply(Key.Backspace, fiat)
        assertTrue(neg.isEmpty)
        assertNull(neg.live)
        assertTrue(neg.touchedSinceOpen)
    }

    @Test fun untouchedSeparatorStartsZero() {
        assertEquals("0.", Expression.untouched("9.95").apply(Key.Separator, fiat).text)
    }

    @Test fun equalsOnUntouchedNumberIsNoop() {
        val e = Expression.untouched("9.95")
        assertSame(e, e.apply(Key.Equals, fiat))
        assertFalse(e.apply(Key.Equals, fiat).touchedSinceOpen)
    }

    @Test fun zeroIsEmptyExpression() {
        assertTrue(Expression.untouched("0").isEmpty)
        assertTrue(Expression.untouched("").isEmpty)
        // Знак в пустом поле не принимается
        val e = Expression.untouched("0")
        assertSame(e, e.apply(Key.Op(Operator.ADD), fiat))
        assertSame(e, e.apply(Key.Backspace, fiat))
        assertEquals("5", e.apply(Key.Digit('5'), fiat).text)
    }

    @Test fun priorityAndGoldenValues() {
        assertEquals("175.00", value(typed("100+25*3")))
        assertEquals("100.00", value(typed("100+")))
        assertEquals("125.00", value(typed("100+25*")))
        assertEquals("14.00", value(typed("2+3*4")))
        assertEquals("80.00", value(typed("100-10-10")))
        assertEquals("33.33", value(typed("100/3")))
        assertEquals("33.33333333", DisplayRules.round(typed("100/3", crypto).live!!, 8).toPlainString())
        val neg = typed("100-150")
        assertEquals("-50.00", value(neg))
        // Обычный приоритет: «100−150×2» = −200; «×2» от результата «=» (нетронутое −50) = −100
        assertEquals("-200.00", value(neg.type("*2")))
        assertEquals("-100.00", value(neg.type("=*2")))
        assertEquals("-100", neg.type("=*2=").text)
        assertEquals("2.00", value(typed("8/2/2")))
        assertEquals("1.00", value(typed("7-2*3")))
    }

    @Test fun divisionByZeroKeepsLastComputable() {
        val e = typed("100/0")
        assertEquals("100/0", e.text)
        assertEquals("100.00", value(e))
        assertFalse(e.isComputable)
        // «=» не сворачивает
        assertSame(e, e.apply(Key.Equals, fiat))
        // «100÷0+5» — последнее вычислимое по-прежнему 100
        assertEquals("100.00", value(e.type("+5")))
        // «100÷0+» → «=» отбрасывает знак, но не сворачивает
        val trailing = e.type("+").apply(Key.Equals, fiat)
        assertEquals("100/0", trailing.text)
        assertFalse(trailing.untouched)
        // ⌫ ⌫ → «100»
        val fixed = e.type("<<")
        assertEquals("100", fixed.text)
        assertTrue(fixed.isComputable)
        assertEquals("100.00", value(fixed))
    }

    @Test fun operatorCorrections() {
        assertEquals("100*", typed("100+*").text)
        assertEquals("100+", typed("100.+").text)
        assertEquals("1.", typed("1..").text)
        assertTrue(typed("+").isEmpty)
        assertTrue(typed("*").isEmpty)
        assertEquals("0.5", typed(".5").text)
        assertEquals("100+0.5", typed("100+.5").text)
        // Один и тот же знак повторно — без изменений
        val plus = typed("100+")
        assertSame(plus, plus.apply(Key.Op(Operator.ADD), fiat))
    }

    @Test fun limits() {
        val twelve = typed("123456789012")
        assertEquals(12, twelve.text.length)
        assertSame(twelve, twelve.apply(Key.Digit('3'), fiat))
        assertEquals("123456789012.", twelve.apply(Key.Separator, fiat).text)
        val frac = typed("1.23")
        assertSame(frac, frac.apply(Key.Digit('4'), fiat))
        val frac8 = typed("0.12345678", crypto)
        assertSame(frac8, frac8.apply(Key.Digit('9'), crypto))
        assertEquals("0.123456789".length - 1, frac8.text.length)
        // 32 символа во всём выражении
        val long = typed("123456789012+123456789012+123456")
        assertEquals(32, long.text.length)
        assertSame(long, long.apply(Key.Digit('7'), fiat))
        assertSame(long, long.apply(Key.Op(Operator.ADD), fiat))
        assertSame(long, long.apply(Key.Separator, fiat))
        // ⌫ действует и на пределе
        assertEquals(31, long.apply(Key.Backspace, fiat).text.length)
    }

    @Test fun equalsFoldsToUntouchedResult() {
        val r = typed("100+25*3=")
        assertEquals("175", r.text)
        assertTrue(r.untouched)
        assertTrue(r.touchedSinceOpen)
        assertFalse(r.hasOp)
        // Результат нетронут: знак продолжает, цифра заменяет
        assertEquals("350", r.type("*2=").text)
        assertEquals("7", r.type("7").text)
        // «=» при «100+» → «100»
        assertEquals("100", typed("100+=").text)
        // Результат округляется до точности ввода базы и без хвостовых нулей
        assertEquals("29.85", typed("9.95*3=").text)
        assertEquals("33.33", typed("100/3=").text)
        assertEquals("33.33333333", typed("100/3=", crypto).text)
        assertEquals("-50", typed("100-150=").text)
        // Нулевой результат — пустое выражение (ноль)
        val zero = typed("5-5=")
        assertTrue(zero.isEmpty)
        assertTrue(zero.untouched)
        assertNull(zero.live)
    }

    @Test fun backspaceToEmptyIsZero() {
        val e = typed("12<<")
        assertTrue(e.isEmpty)
        assertNull(e.live)
        assertTrue(e.isComputable)
        assertSame(e, e.apply(Key.Backspace, fiat))
        // ⌫ снимает знак целиком
        assertEquals("100", typed("100+<").text)
        assertEquals("100+", typed("100+2<").text)
    }

    @Test fun enterIsHandledOutside() {
        val e = typed("100+25")
        assertSame(e, e.apply(Key.Enter, fiat))
    }

    @Test fun fieldTextGolden() {
        assertEquals("100", DisplayRules.fieldText(BigDecimal("100.00")))
        assertEquals("175", DisplayRules.fieldText(BigDecimal("175")))
        assertEquals("9.95", DisplayRules.fieldText(BigDecimal("9.95")))
        assertEquals("0", DisplayRules.fieldText(BigDecimal.ZERO))
        assertEquals("0", DisplayRules.fieldText(BigDecimal("0.00")))
        assertEquals("0", DisplayRules.fieldText(null))
        assertEquals("0.0001292", DisplayRules.fieldText(BigDecimal("0.00012920")))
        assertEquals("-50", DisplayRules.fieldText(BigDecimal("-50.00")))
        assertEquals("1000", DisplayRules.fieldText(BigDecimal("1000")))
    }

    @Test fun evaluateRejectsMalformed() {
        assertNull(ExpressionEval.evaluate(emptyList()))
        assertNull(ExpressionEval.evaluate(listOf(Token.Num("1"), Token.Op(Operator.ADD))))
        assertNull(ExpressionEval.evaluate(listOf(Token.Op(Operator.ADD), Token.Num("1"))))
        assertEquals(0, BigDecimal("3").compareTo(ExpressionEval.evaluate(listOf(Token.Num("1."), Token.Op(Operator.ADD), Token.Num("2")))))
        assertNull(ExpressionEval.lastComputable(emptyList()))
    }
}
