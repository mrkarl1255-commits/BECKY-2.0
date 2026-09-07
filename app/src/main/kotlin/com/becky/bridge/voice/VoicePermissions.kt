package com.becky.bridge.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Runtime permission required by [SpeechToTextEngine] (native Android
 * speech recognition needs microphone access).
 *
 * Mirrors [com.becky.bridge.bluetooth.PermissionsHelper] in spirit, but
 * is kept in the `voice` package - and as its own tiny object - since
 * it is unrelated to Bluetooth/BLE and must not blur that helper's
 * single responsibility.
 */
object VoicePermissions {

    /** The one runtime permission the voice layer needs. */
    const val RECORD_AUDIO: String = Manifest.permission.RECORD_AUDIO

    fun hasRecordAudioPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}
