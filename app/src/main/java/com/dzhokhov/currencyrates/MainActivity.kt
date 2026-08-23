package com.dzhokhov.currencyrates

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import com.dzhokhov.currencyrates.core.expr.KeyMap
import com.dzhokhov.currencyrates.ui.App
import com.dzhokhov.currencyrates.ui.AppGraph
import com.dzhokhov.currencyrates.ui.MainViewModel
import com.dzhokhov.currencyrates.ui.ModeKind

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels { MainViewModel.Factory(AppGraph.get(applicationContext)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Первое состояние строится синхронно из файлов до setContent.
        AppGraph.get(applicationContext)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                App(vm)
            }
        }
    }

    /**
     * Физическая клавиатура: раньше иерархии представлений. На экране выбора — система (там текстовое поле);
     * Клавиши калькулятора в Editing переводятся в нажатия панели, в остальных режимах поглощаются без действия,
     * Чтобы Enter не стал кликом по сфокусированному элементу; «Назад» и прочее — super.
     * RestrictedApi: androidx.activity помечает своё переопределение Activity.dispatchKeyEvent как внутреннее, но это
     * Публичный метод платформы; маршрут раньше иерархии требует именно его, onKeyDown идёт после иерархии.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val state = vm.state.value
        if (state.picker != null) return super.dispatchKeyEvent(event)
        val key = KeyMap.fromKeyEvent(event.keyCode, event.unicodeChar) ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && state.mode.kind == ModeKind.EDITING) vm.onKey(key)
        return true
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onPause() {
        vm.flushNow()
        super.onPause()
    }

    override fun onStop() {
        vm.flushNow()
        super.onStop()
    }
}
