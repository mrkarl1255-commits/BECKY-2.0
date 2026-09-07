package com.becky.bridge.logging

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Simple in-memory + logcat logging facility used across the whole
 * application (section 13 of the spec).
 *
 * It is a lightweight singleton on purpose: BECKY BRIDGE does not need a
 * database-backed logging system for Phase 1. The in-memory ring buffer
 * is exposed as a [StateFlow] so any screen (mainly Logs and
 * Diagnostics) can observe it reactively.
 */
object BeckyLogger {

    private const val TAG = "BeckyBridge"
    private const val MAX_ENTRIES = 500

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun i(category: LogCategory, message: String) {
        log(LogLevel.INFO, category, message)
        Log.i(TAG, "[$category] $message")
    }

    fun w(category: LogCategory, message: String) {
        log(LogLevel.WARNING, category, message)
        Log.w(TAG, "[$category] $message")
    }

    fun e(category: LogCategory, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, category, message)
        Log.e(TAG, "[$category] $message", throwable)
    }

    private fun log(level: LogLevel, category: LogCategory, message: String) {
        val entry = LogEntry(level = level, category = category, message = message)
        _entries.update { current ->
            val updated = current + entry
            if (updated.size > MAX_ENTRIES) updated.takeLast(MAX_ENTRIES) else updated
        }
    }

    fun lastError(): LogEntry? = _entries.value.lastOrNull { it.level == LogLevel.ERROR }

    fun lastEntry(): LogEntry? = _entries.value.lastOrNull()

    fun clear() {
        _entries.value = emptyList()
    }

    /** Renders all in-memory entries as a plain text log, newest last. */
    fun exportAsText(): String {
        val builder = StringBuilder()
        builder.append("BECKY BRIDGE - Registro de diagnostico\n")
        builder.append("Generado: ${dateFormat.format(Date())}\n")
        builder.append("=".repeat(60)).append('\n')
        _entries.value.forEach { entry ->
            builder.append(dateFormat.format(Date(entry.timestamp)))
                .append(" [").append(entry.level).append("] ")
                .append("[").append(entry.category).append("] ")
                .append(entry.message)
                .append('\n')
        }
        return builder.toString()
    }
}
