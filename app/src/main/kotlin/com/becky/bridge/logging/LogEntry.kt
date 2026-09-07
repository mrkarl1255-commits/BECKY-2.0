package com.becky.bridge.logging

/** Severity level for a [LogEntry]. */
enum class LogLevel {
    INFO,
    WARNING,
    ERROR
}

/** Category used to filter/organize logs on the Logs screen. */
enum class LogCategory {
    APP,
    BLUETOOTH,
    PERMISSIONS,
    SCAN,
    CONNECTION,
    GATT,
    MESSAGE,
    ERROR
}

/**
 * A single log line kept in memory (and optionally exported to a text
 * file) by [BeckyLogger]. Intentionally does NOT store personal data -
 * only technical/diagnostic information as required by section 13 and
 * 19 of the project specification.
 */
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val category: LogCategory,
    val message: String
)
