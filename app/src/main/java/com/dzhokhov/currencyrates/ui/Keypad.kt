package com.dzhokhov.currencyrates.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dzhokhov.currencyrates.R
import com.dzhokhov.currencyrates.core.Formatters
import com.dzhokhov.currencyrates.core.expr.Key
import com.dzhokhov.currencyrates.core.expr.Operator

/**
 * Собственная клавиатура-калькулятор: 7 8 9 ÷ / 4 5 6 × / 1 2 3 − / 0 [разделитель языка] = + / ⌫ [ввод × 3].
 * Контейнер поглощает касания зазоров, чтобы они не считались касанием «мимо клавиатуры». Других клавиш нет.
 */
@Composable
fun Keypad(separator: Char, onKey: (Key) -> Unit) {
    val compact = LocalConfiguration.current.screenHeightDp < 500
    val keyHeight = if (compact) 36.dp else 48.dp
    val description = stringResource(R.string.keypad)
    val digitBg = MaterialTheme.colorScheme.surfaceVariant
    val opBg = MaterialTheme.colorScheme.secondaryContainer
    val enterBg = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { contentDescription = description }
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Digit('7', keyHeight, digitBg, onKey); Digit('8', keyHeight, digitBg, onKey); Digit('9', keyHeight, digitBg, onKey)
            Op(Operator.DIV, "÷", stringResource(R.string.key_divide), keyHeight, opBg, onKey)
        }
        Row(Modifier.fillMaxWidth()) {
            Digit('4', keyHeight, digitBg, onKey); Digit('5', keyHeight, digitBg, onKey); Digit('6', keyHeight, digitBg, onKey)
            Op(Operator.MUL, "×", stringResource(R.string.key_times), keyHeight, opBg, onKey)
        }
        Row(Modifier.fillMaxWidth()) {
            Digit('1', keyHeight, digitBg, onKey); Digit('2', keyHeight, digitBg, onKey); Digit('3', keyHeight, digitBg, onKey)
            Op(Operator.SUB, "−", stringResource(R.string.key_minus), keyHeight, opBg, onKey)
        }
        Row(Modifier.fillMaxWidth()) {
            Digit('0', keyHeight, digitBg, onKey)
            KeyBox(separator.toString(), stringResource(R.string.key_separator), keyHeight, digitBg, onKey = { onKey(Key.Separator) })
            KeyBox("=", stringResource(R.string.key_equals), keyHeight, opBg, onKey = { onKey(Key.Equals) })
            Op(Operator.ADD, "+", stringResource(R.string.key_plus), keyHeight, opBg, onKey)
        }
        Row(Modifier.fillMaxWidth()) {
            KeyBox("⌫", stringResource(R.string.key_backspace), keyHeight, opBg, onKey = { onKey(Key.Backspace) })
            KeyBox(
                stringResource(R.string.key_enter), stringResource(R.string.key_enter), keyHeight, enterBg,
                weight = 3f, textColor = MaterialTheme.colorScheme.onPrimary, onKey = { onKey(Key.Enter) },
            )
        }
    }
}

@Composable
private fun RowScope.Digit(c: Char, height: Dp, bg: Color, onKey: (Key) -> Unit) {
    KeyBox(c.toString(), c.toString(), height, bg, onKey = { onKey(Key.Digit(c)) })
}

@Composable
private fun RowScope.Op(op: Operator, label: String, description: String, height: Dp, bg: Color, onKey: (Key) -> Unit) {
    KeyBox(label, description, height, bg, onKey = { onKey(Key.Op(op)) })
}

@Composable
private fun RowScope.KeyBox(
    label: String,
    description: String,
    height: Dp,
    bg: Color,
    weight: Float = 1f,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onKey: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .height(height)
            .padding(2.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .clickable(onClick = onKey)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.titleLarge, color = textColor)
    }
}
