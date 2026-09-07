package com.becky.bridge.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import com.becky.bridge.logging.BeckyLogger
import com.becky.bridge.logging.LogCategory
import com.becky.bridge.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Handles automatic reconnection with exponential backoff
 * (section 11 of the spec).
 *
 * Design goals explicitly requested by the spec:
 *  - Detect disconnection and notify the user ("Reloj desconectado").
 *  - Retry in a controlled way (never an aggressive infinite loop).
 *  - Use exponential backoff between attempts.
 *  - Let the user disable automatic reconnection entirely.
 */
class ReconnectionManager(
    private val gattManager: GattManager,
    private val scope: CoroutineScope
) {
    companion object {
        private const val INITIAL_DELAY_MS = 2_000L
        private const val MAX_DELAY_MS = 60_000L
        private const val MAX_ATTEMPTS = 6
    }

    private val _autoReconnectEnabled = MutableStateFlow(true)
    val autoReconnectEnabled: StateFlow<Boolean> = _autoReconnectEnabled

    private val _isReconnecting = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = _isReconnecting

    private var reconnectJob: Job? = null
    private var lastKnownDevice: BluetoothDevice? = null

    fun setAutoReconnectEnabled(enabled: Boolean) {
        _autoReconnectEnabled.value = enabled
        if (!enabled) {
            cancel()
        }
    }

    fun trackDevice(device: BluetoothDevice) {
        lastKnownDevice = device
    }

    /** Call this whenever GATT reports an unexpected disconnection. */
    @SuppressLint("MissingPermission")
    fun onUnexpectedDisconnect() {
        val device = lastKnownDevice ?: return
        if (!_autoReconnectEnabled.value) {
            BeckyLogger.i(LogCategory.CONNECTION, "Reconexion automatica desactivada por el usuario")
            return
        }
        if (reconnectJob?.isActive == true) return

        BeckyLogger.w(LogCategory.CONNECTION, "Reloj desconectado. Iniciando reconexion controlada...")
        reconnectJob = scope.launch {
            _isReconnecting.value = true
            var delayMs = INITIAL_DELAY_MS
            var attempt = 1
            while (isActive && attempt <= MAX_ATTEMPTS) {
                if (gattManager.connectionState.value == ConnectionState.READY) {
                    BeckyLogger.i(LogCategory.CONNECTION, "Reconexion exitosa en el intento $attempt")
                    break
                }
                BeckyLogger.i(LogCategory.CONNECTION, "Intento de reconexion $attempt/$MAX_ATTEMPTS en ${delayMs}ms")
                delay(delayMs)
                if (!_autoReconnectEnabled.value) break
                try {
                    gattManager.connect(device)
                } catch (e: Exception) {
                    BeckyLogger.e(LogCategory.CONNECTION, "Error durante reconexion", e)
                }
                delayMs = (delayMs * 2).coerceAtMost(MAX_DELAY_MS)
                attempt++
            }
            _isReconnecting.value = false
        }
    }

    fun cancel() {
        reconnectJob?.cancel()
        reconnectJob = null
        _isReconnecting.value = false
    }
}
