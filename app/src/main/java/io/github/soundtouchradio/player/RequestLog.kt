package io.github.soundtouchradio.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class LogEntry(
    val timeMillis: Long,
    val method: String,
    val target: String,
    val outcome: String,
    val ok: Boolean,
    val durationMs: Long,
)

/** Registro en memoria de las últimas peticiones, para la pantalla Diagnóstico. Nunca sale del móvil. */
class RequestLog(private val capacity: Int = 50, private val clock: () -> Long = System::currentTimeMillis) {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    fun now(): Long = clock()

    fun add(method: String, target: String, outcome: String, ok: Boolean, startedAt: Long) {
        val e = LogEntry(clock(), method, target, outcome, ok, (clock() - startedAt).coerceAtLeast(0))
        _entries.update { (listOf(e) + it).take(capacity) }
    }

    fun clear() { _entries.value = emptyList() }
}
