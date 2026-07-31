package com.cashmemer.devices

import com.cashmemer.core.util.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LogEntry(val at: Long, val message: String) {
    override fun toString(): String = "${Format.timestamp(at)}  $message"
}

/**
 * Ring buffer of device events, surfaced by "View Integration Logs". Kept in
 * memory only — it is a debugging aid for "why won't my scanner connect", not
 * a record worth persisting.
 */
object IntegrationLog {

    private const val CAPACITY = 200

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun add(message: String) {
        _entries.update { current ->
            (current + LogEntry(System.currentTimeMillis(), message)).takeLast(CAPACITY)
        }
    }

    fun clear() = _entries.update { emptyList() }

    fun asText(): String = _entries.value.joinToString("\n")
}
