package io.github.dzhokhov.quotes.log

import android.util.Log

/** Журнал наблюдаемости: одна строка на событие, тег Rates. Сумм и набранного текста в журнале нет. */
interface RatesLog {
    fun event(step: String, module: String, function: String, belief: String, observed: String, next: String)
}

fun RatesLog.line(runId: String, step: String, module: String, function: String, belief: String, observed: String, next: String): String =
    "run_id=$runId step_id=$step file_or_module=$module function_id=$function belief_state=$belief observed_result=$observed next_action=$next"

class AndroidRatesLog(private val runId: String) : RatesLog {
    override fun event(step: String, module: String, function: String, belief: String, observed: String, next: String) {
        Log.i("Rates", line(runId, step, module, function, belief, observed, next))
    }
}

/** Накопительная реализация для тестов; потокобезопасна — источники опрашиваются параллельно. */
class ListRatesLog(private val runId: String = "test") : RatesLog {
    val lines: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())
    override fun event(step: String, module: String, function: String, belief: String, observed: String, next: String) {
        lines.add(line(runId, step, module, function, belief, observed, next))
    }

    fun steps(): List<String> = synchronized(lines) { lines.map { it.substringAfter("step_id=").substringBefore(' ') } }
}
