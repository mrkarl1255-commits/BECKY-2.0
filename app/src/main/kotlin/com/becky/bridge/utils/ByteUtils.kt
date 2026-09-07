package com.becky.bridge.utils

/** Small helpers for formatting/parsing byte arrays received over BLE. */
object ByteUtils {

    fun toHexString(bytes: ByteArray): String {
        if (bytes.isEmpty()) return "(vacio)"
        return bytes.joinToString(separator = " ") { "%02X".format(it) }
    }

    fun toReadableString(bytes: ByteArray): String {
        return try {
            val text = String(bytes, Charsets.UTF_8)
            if (text.any { it.isISOControl() && it != '\n' && it != '\t' }) {
                toHexString(bytes)
            } else {
                text
            }
        } catch (e: Exception) {
            toHexString(bytes)
        }
    }

    fun fromHexString(hex: String): ByteArray {
        val clean = hex.replace(" ", "").replace("0x", "", ignoreCase = true)
        if (clean.length % 2 != 0) return ByteArray(0)
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
