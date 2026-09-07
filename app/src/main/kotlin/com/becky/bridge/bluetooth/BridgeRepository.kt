package com.becky.bridge.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.becky.bridge.communication.BeckyCommandHandler
import com.becky.bridge.communication.BluetoothTransport
import com.becky.bridge.communication.CommunicationTransport
import com.becky.bridge.communication.DefaultBeckyCommandHandler
import com.becky.bridge.logging.BeckyLogger
import com.becky.bridge.logging.LogCategory
import com.becky.bridge.model.BleCharacteristicInfo
import com.becky.bridge.model.BleServiceInfo
import com.becky.bridge.model.BridgeMessage
import com.becky.bridge.model.ConnectionState
import com.becky.bridge.model.DiagnosticsSnapshot
import com.becky.bridge.model.ScannedDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Single source of truth that wires together [BleScanner], [GattManager],
 * [ReconnectionManager] and [BluetoothTransport] (section 15 - modular
 * architecture).
 *
 * UI ViewModels observe this repository instead of touching Bluetooth
 * classes directly, which keeps Bluetooth/BLE concerns fully isolated
 * from the UI layer as required by the spec.
 *
 * This class is intentionally a plain singleton-style repository
 * (no DI framework) to keep the project lightweight and easy to open
 * directly in Android Studio without extra build complexity.
 */
class BridgeRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val scanner = BleScanner(appContext)
    val gattManager = GattManager(appContext)
    val reconnectionManager = ReconnectionManager(gattManager, repoScope)
    val transport: CommunicationTransport = BluetoothTransport(gattManager, repoScope)

    /**
     * Phase 2: entry point for BECKY (the future voice assistant) to send
     * commands to the watch (GET_TIME, GET_HEART_RATE, etc.) without ever
     * touching Bluetooth/GATT classes directly. See
     * [com.becky.bridge.communication.BeckyCommandHandler].
     */
    val commandHandler: BeckyCommandHandler = DefaultBeckyCommandHandler(transport, repoScope)

    val scannedDevices: StateFlow<Map<String, ScannedDevice>> = scanner.devices
    val isScanning: StateFlow<Boolean> = scanner.scanning
    val connectionState: StateFlow<ConnectionState> = gattManager.connectionState
    val discoveredServices: StateFlow<List<BleServiceInfo>> = gattManager.services
    val connectedDeviceName: StateFlow<String?> = gattManager.connectedDeviceName
    val connectedDeviceAddress: StateFlow<String?> = gattManager.connectedDeviceAddress
    val autoReconnectEnabled: StateFlow<Boolean> = reconnectionManager.autoReconnectEnabled
    val isReconnecting: StateFlow<Boolean> = reconnectionManager.isReconnecting
    val incomingMessages: SharedFlow<BridgeMessage> = transport.incomingMessages

    private var lastConnectionState: ConnectionState = ConnectionState.DISCONNECTED

    init {
        gattManager.connectionState.onEach { state ->
            if (lastConnectionState in listOf(ConnectionState.CONNECTED, ConnectionState.READY, ConnectionState.DISCOVERING_SERVICES) &&
                state == ConnectionState.DISCONNECTED
            ) {
                reconnectionManager.onUnexpectedDisconnect()
            }
            lastConnectionState = state
        }.launchIn(repoScope)
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        reconnectionManager.trackDevice(device)
        gattManager.connect(device)
        BeckyLogger.i(LogCategory.CONNECTION, "Solicitud de conexion enviada")
    }

    fun disconnect() {
        reconnectionManager.cancel()
        gattManager.disconnect()
    }

    fun reconnect() {
        val address = connectedDeviceAddress.value
        val device = scannedDevices.value.values.firstOrNull { it.address == address }?.device
        if (device != null) {
            connect(device)
        } else {
            BeckyLogger.w(LogCategory.CONNECTION, "No se puede reconectar: dispositivo no encontrado en la lista de escaneo")
        }
    }

    fun startScan() = scanner.startScan()
    fun stopScan() = scanner.stopScan()

    fun setActiveCharacteristic(info: BleCharacteristicInfo?) {
        (transport as? BluetoothTransport)?.setActiveCharacteristic(info)
    }

    fun readCharacteristic(info: BleCharacteristicInfo) = gattManager.readCharacteristic(info)

    fun writeCharacteristic(info: BleCharacteristicInfo, data: ByteArray) =
        gattManager.writeCharacteristic(info, data)

    fun setNotificationsEnabled(info: BleCharacteristicInfo, enabled: Boolean) =
        gattManager.setNotificationsEnabled(info, enabled)

    fun setAutoReconnectEnabled(enabled: Boolean) =
        reconnectionManager.setAutoReconnectEnabled(enabled)

    fun buildDiagnosticsSnapshot(
        bluetoothEnabled: Boolean,
        permissionsGranted: Boolean,
        rssi: Int?
    ): DiagnosticsSnapshot {
        val services = discoveredServices.value
        return DiagnosticsSnapshot(
            bluetoothEnabled = bluetoothEnabled,
            permissionsGranted = permissionsGranted,
            deviceName = connectedDeviceName.value,
            macAddress = connectedDeviceAddress.value,
            rssi = rssi,
            connectionState = connectionState.value,
            gattConnected = connectionState.value == ConnectionState.READY,
            serviceCount = services.size,
            characteristicCount = services.sumOf { it.characteristics.size },
            lastMessageAt = BeckyLogger.lastEntry()?.timestamp,
            lastError = BeckyLogger.lastError()?.message
        )
    }

    companion object {
        @Volatile
        private var instance: BridgeRepository? = null

        fun getInstance(context: Context): BridgeRepository {
            return instance ?: synchronized(this) {
                instance ?: BridgeRepository(context).also { instance = it }
            }
        }
    }
}
