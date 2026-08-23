package com.dzhokhov.currencyrates.core.expr

/** Знак действия; symbol — внутренний символ нормализованного текста выражения. */
enum class Operator(val symbol: Char) { ADD('+'), SUB('-'), MUL('*'), DIV('/') }

/** Токен выражения: число (нормализованный текст с точкой, как набран) или знак действия. */
sealed class Token {
    data class Num(val text: String) : Token()
    data class Op(val op: Operator) : Token()
}

/** Клавиша панели-калькулятора или её эквивалент с физической клавиатуры. */
sealed class Key {
    data class Digit(val c: Char) : Key()
    object Separator : Key()
    data class Op(val op: Operator) : Key()
    object Backspace : Key()
    object Equals : Key()
    object Enter : Key()
}

/** Пределы набора: цифр до разделителя, после (2 — фиатная база, 8 — XAU/XAG/BTC), символов во всём выражении. */
data class Limits(val maxFrac: Int, val maxInt: Int = MAX_INT, val maxChars: Int = MAX_CHARS) {
    companion object {
        const val MAX_INT = 12
        const val MAX_CHARS = 32
    }
}
