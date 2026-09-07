package com.becky.bridge.model

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic

/**
 * Represents a Bluetooth/BLE device discovered during a scan.
 *
 * MAC address exposure is limited by Android privacy rules on modern
 * OS versions (it may be randomized or hidden depending on the API
 * level and permission state) - see [macAddress] docs.
 */
data class ScannedDevice(
    val device: BluetoothDevice,
    val name: String?,
    val macAddress: String?,
    val rssi: Int,
    val deviceType: String,
    val isBonded: Boolean,
    var lastSeenAt: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: "Dispositivo desconocido"

    val address: String
        get() = device.address
}

/** Connection lifecycle state exposed to the UI. */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCOVERING_SERVICES,
    READY,
    ERROR,
    RECONNECTING
}

/** A GATT characteristic wrapped with human readable property flags. */
data class BleCharacteristicInfo(
    val serviceUuid: String,
    val characteristicUuid: String,
    val canRead: Boolean,
    val canWrite: Boolean,
    val canWriteNoResponse: Boolean,
    val canNotify: Boolean,
    val canIndicate: Boolean,
    val raw: BluetoothGattCharacteristic
) {
    companion object {
        fun from(serviceUuid: String, characteristic: BluetoothGattCharacteristic): BleCharacteristicInfo {
            val props = characteristic.properties
            return BleCharacteristicInfo(
                serviceUuid = serviceUuid,
                characteristicUuid = characteristic.uuid.toString(),
                canRead = props and BluetoothGattCharacteristic.PROPERTY_READ != 0,
                canWrite = props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0,
                canWriteNoResponse = props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0,
                canNotify = props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0,
                canIndicate = props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0,
                raw = characteristic
            )
        }
    }

    val propertiesLabel: String
        get() = buildList {
            if (canRead) add("READ")
            if (canWrite) add("WRITE")
            if (canWriteNoResponse) add("WRITE_NO_RESPONSE")
            if (canNotify) add("NOTIFY")
            if (canIndicate) add("INDICATE")
        }.joinToString(", ").ifBlank { "NONE" }
}

/** A GATT service with its discovered characteristics. */
data class BleServiceInfo(
    val serviceUuid: String,
    val characteristics: List<BleCharacteristicInfo>
)

/** Snapshot of diagnostic information rendered on the Diagnostics screen. */
data class DiagnosticsSnapshot(
    val bluetoothEnabled: Boolean,
    val permissionsGranted: Boolean,
    val deviceName: String?,
    val macAddress: String?,
    val rssi: Int?,
    val connectionState: ConnectionState,
    val gattConnected: Boolean,
    val serviceCount: Int,
    val characteristicCount: Int,
    val lastMessageAt: Long?,
    val lastError: String?
)
