package com.dzhokhov.currencyrates.core.expr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Символ важнее кода; коды без символа — по таблице; BACK и прочее → null. */
class KeyMapTest {
    private fun map(keyCode: Int, char: Char = 0.toChar()) = KeyMap.fromKeyEvent(keyCode, char.code)

    @Test fun symbolOverridesKeyCode() {
        // Shift+8 на US-раскладке: KEYCODE_8 с символом '*'
        assertEquals(Key.Op(Operator.MUL), map(KeyMap.KEYCODE_0 + 8, '*'))
        // Shift+=: KEYCODE_EQUALS с символом '+'
        assertEquals(Key.Op(Operator.ADD), map(KeyMap.KEYCODE_EQUALS, '+'))
        // Без Shift: KEYCODE_8 с символом '8'
        assertEquals(Key.Digit('8'), map(KeyMap.KEYCODE_0 + 8, '8'))
        assertEquals(Key.Equals, map(KeyMap.KEYCODE_EQUALS, '='))
        assertEquals(Key.Op(Operator.SUB), map(KeyMap.KEYCODE_MINUS, '-'))
    }

    @Test fun separators() {
        assertEquals(Key.Separator, map(KeyMap.KEYCODE_PERIOD, '.'))
        assertEquals(Key.Separator, map(KeyMap.KEYCODE_COMMA, ','))
        assertEquals(Key.Separator, map(KeyMap.KEYCODE_PERIOD))
        assertEquals(Key.Separator, map(KeyMap.KEYCODE_COMMA))
        assertEquals(Key.Separator, map(KeyMap.KEYCODE_NUMPAD_DOT))
        assertEquals(Key.Separator, map(KeyMap.KEYCODE_NUMPAD_COMMA))
    }

    @Test fun operatorsByCode() {
        assertEquals(Key.Op(Operator.MUL), map(KeyMap.KEYCODE_STAR))
        assertEquals(Key.Op(Operator.MUL), map(KeyMap.KEYCODE_NUMPAD_MULTIPLY))
        assertEquals(Key.Op(Operator.DIV), map(KeyMap.KEYCODE_SLASH))
        assertEquals(Key.Op(Operator.DIV), map(KeyMap.KEYCODE_NUMPAD_DIVIDE))
        assertEquals(Key.Op(Operator.ADD), map(KeyMap.KEYCODE_PLUS))
        assertEquals(Key.Op(Operator.ADD), map(KeyMap.KEYCODE_NUMPAD_ADD))
        assertEquals(Key.Op(Operator.SUB), map(KeyMap.KEYCODE_NUMPAD_SUBTRACT))
        assertEquals(Key.Equals, map(KeyMap.KEYCODE_NUMPAD_EQUALS))
    }

    @Test fun digitsByCode() {
        for (d in 0..9) {
            assertEquals(Key.Digit('0' + d), map(KeyMap.KEYCODE_0 + d))
            assertEquals(Key.Digit('0' + d), map(KeyMap.KEYCODE_NUMPAD_0 + d))
        }
    }

    @Test fun enterBackspaceBack() {
        assertEquals(Key.Enter, map(KeyMap.KEYCODE_ENTER))
        assertEquals(Key.Enter, map(KeyMap.KEYCODE_NUMPAD_ENTER))
        assertEquals(Key.Enter, map(KeyMap.KEYCODE_ENTER, '\n'))
        assertEquals(Key.Backspace, map(KeyMap.KEYCODE_DEL))
        assertNull(map(KeyMap.KEYCODE_BACK))
        assertNull(map(29, 'a')) // KEYCODE_A
        assertNull(map(62, ' ')) // KEYCODE_SPACE
    }
}
