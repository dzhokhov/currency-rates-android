package com.dzhokhov.currencyrates.core.expr

/**
 * Физическая клавиатура → клавиша панели. Коды — значения android.view.KeyEvent,
 * Продублированы здесь, чтобы core не зависел от android.*. BACK и всё прочее → null (не перехватывается).
 */
object KeyMap {
    const val KEYCODE_BACK = 4
    const val KEYCODE_0 = 7
    const val KEYCODE_9 = 16
    const val KEYCODE_STAR = 17
    const val KEYCODE_COMMA = 55
    const val KEYCODE_PERIOD = 56
    const val KEYCODE_ENTER = 66
    const val KEYCODE_DEL = 67
    const val KEYCODE_MINUS = 69
    const val KEYCODE_EQUALS = 70
    const val KEYCODE_SLASH = 76
    const val KEYCODE_PLUS = 81
    const val KEYCODE_NUMPAD_0 = 144
    const val KEYCODE_NUMPAD_9 = 153
    const val KEYCODE_NUMPAD_DIVIDE = 154
    const val KEYCODE_NUMPAD_MULTIPLY = 155
    const val KEYCODE_NUMPAD_SUBTRACT = 156
    const val KEYCODE_NUMPAD_ADD = 157
    const val KEYCODE_NUMPAD_DOT = 158
    const val KEYCODE_NUMPAD_COMMA = 159
    const val KEYCODE_NUMPAD_ENTER = 160
    const val KEYCODE_NUMPAD_EQUALS = 161

    /** unicodeChar — символ события с учётом модификаторов (KeyEvent.getUnicodeChar); 0 — символа нет. */
    fun fromKeyEvent(keyCode: Int, unicodeChar: Int): Key? {
        fromChar(unicodeChar.toChar())?.let { return it }
        return when (keyCode) {
            in KEYCODE_0..KEYCODE_9 -> Key.Digit('0' + (keyCode - KEYCODE_0))
            in KEYCODE_NUMPAD_0..KEYCODE_NUMPAD_9 -> Key.Digit('0' + (keyCode - KEYCODE_NUMPAD_0))
            KEYCODE_PERIOD, KEYCODE_COMMA, KEYCODE_NUMPAD_DOT, KEYCODE_NUMPAD_COMMA -> Key.Separator
            KEYCODE_PLUS, KEYCODE_NUMPAD_ADD -> Key.Op(Operator.ADD)
            KEYCODE_MINUS, KEYCODE_NUMPAD_SUBTRACT -> Key.Op(Operator.SUB)
            KEYCODE_STAR, KEYCODE_NUMPAD_MULTIPLY -> Key.Op(Operator.MUL)
            KEYCODE_SLASH, KEYCODE_NUMPAD_DIVIDE -> Key.Op(Operator.DIV)
            KEYCODE_EQUALS, KEYCODE_NUMPAD_EQUALS -> Key.Equals
            KEYCODE_ENTER, KEYCODE_NUMPAD_ENTER -> Key.Enter
            KEYCODE_DEL -> Key.Backspace
            else -> null
        }
    }

    /** Символ из 0-9., + - * / = → клавиша; иначе null. */
    fun fromChar(c: Char): Key? = when (c) {
        in '0'..'9' -> Key.Digit(c)
        '.', ',' -> Key.Separator
        '+' -> Key.Op(Operator.ADD)
        '-' -> Key.Op(Operator.SUB)
        '*' -> Key.Op(Operator.MUL)
        '/' -> Key.Op(Operator.DIV)
        '=' -> Key.Equals
        else -> null
    }
}
