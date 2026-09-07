package com.becky.bridge.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.becky.bridge.logging.BeckyLogger
import com.becky.bridge.logging.LogCategory
import com.becky.bridge.model.BleCharacteristicInfo
import com.becky.bridge.model.BleServiceInfo
import com.becky.bridge.model.ConnectionState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/** Standard Client Characteristic Configuration Descriptor UUID (BLE spec). */
private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/**
 * Manages the full GATT connection lifecycle for a single BLE device:
 * connect, service discovery, read/write/notify and disconnect
 * (sections 5, 6 and 7 of the spec).
 *
 * This class purposefully does NOT assume any specific smartwatch
 * protocol. It only exposes generic GATT primitives; watch-specific
 * command translation belongs in a future module built on top of this
 * one (see [com.becky.bridge.communication.BeckyCommandHandler]).
 */
class GattManager(private val context: Context) {

    private var gatt: BluetoothGatt? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _services = MutableStateFlow<List<BleServiceInfo>>(emptyList())
    val services: StateFlow<List<BleServiceInfo>> = _services

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName

    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress

    /** Emits raw notification/indication payloads: characteristicUuid -> bytes. */
    val notifications = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 32)

    /** Emits results of read operations: characteristicUuid -> bytes. */
    val readResults = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 32)

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    BeckyLogger.i(LogCategory.CONNECTION, "GATT conectado (status=$status)")
                    _connectionState.value = ConnectionState.DISCOVERING_SERVICES
                    try {
                        g.discoverServices()
                    } catch (se: SecurityException) {
                        BeckyLogger.e(LogCategory.CONNECTION, "Permiso insuficiente para descubrir servicios", se)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    BeckyLogger.i(LogCategory.CONNECTION, "GATT desconectado (status=$status)")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _services.value = emptyList()
                    _connectedDeviceName.value = null
                    _connectedDeviceAddress.value = null
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    _connectionState.value = ConnectionState.CONNECTING
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                BeckyLogger.e(LogCategory.GATT, "Descubrimiento de servicios fallo. Status=$status")
                _connectionState.value = ConnectionState.ERROR
                return
            }
            val servicesInfo = g.services.map { service ->
                BleServiceInfo(
                    serviceUuid = service.uuid.toString(),
                    characteristics = service.characteristics.map { characteristic ->
                        BleCharacteristicInfo.from(service.uuid.toString(), characteristic)
                    }
                )
            }
            _services.value = servicesInfo
            _connectionState.value = ConnectionState.READY
            BeckyLogger.i(
                LogCategory.GATT,
                "Servicios descubiertos: ${servicesInfo.size}, caracteristicas: ${servicesInfo.sumOf { it.characteristics.size }}"
            )
        }

        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val bytes = characteristic.value ?: ByteArray(0)
                readResults.tryEmit(characteristic.uuid.toString() to bytes)
                BeckyLogger.i(LogCategory.GATT, "Lectura OK: ${characteristic.uuid} (${bytes.size} bytes)")
            } else {
                BeckyLogger.e(LogCategory.GATT, "Lectura fallo: ${characteristic.uuid}, status=$status")
            }
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                readResults.tryEmit(characteristic.uuid.toString() to value)
                BeckyLogger.i(LogCategory.GATT, "Lectura OK: ${characteristic.uuid} (${value.size} bytes)")
            } else {
                BeckyLogger.e(LogCategory.GATT, "Lectura fallo: ${characteristic.uuid}, status=$status")
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BeckyLogger.i(LogCategory.GATT, "Escritura OK: ${characteristic.uuid}")
            } else {
                BeckyLogger.e(LogCategory.GATT, "Escritura fallo: ${characteristic.uuid}, status=$status")
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val bytes = characteristic.value ?: ByteArray(0)
            notifications.tryEmit(characteristic.uuid.toString() to bytes)
            BeckyLogger.i(LogCategory.GATT, "Notificacion recibida: ${characteristic.uuid} (${bytes.size} bytes)")
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            notifications.tryEmit(characteristic.uuid.toString() to value)
            BeckyLogger.i(LogCategory.GATT, "Notificacion recibida: ${characteristic.uuid} (${value.size} bytes)")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        disconnect()
        _connectionState.value = ConnectionState.CONNECTING
        _connectedDeviceName.value = try { device.name } catch (se: SecurityException) { null }
        _connectedDeviceAddress.value = device.address
        BeckyLogger.i(LogCategory.CONNECTION, "Conectando a ${device.address}...")
        try {
            gatt = device.connectGatt(context, false, gattCallback)
        } catch (se: SecurityException) {
            BeckyLogger.e(LogCategory.CONNECTION, "Permiso insuficiente para conectar", se)
            _connectionState.value = ConnectionState.ERROR
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (se: SecurityException) {
            BeckyLogger.e(LogCategory.CONNECTION, "Permiso insuficiente al desconectar", se)
        }
        gatt = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _services.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(info: BleCharacteristicInfo) {
        val g = gatt ?: return
        try {
            g.readCharacteristic(info.raw)
        } catch (se: SecurityException) {
            BeckyLogger.e(LogCategory.GATT, "Permiso insuficiente para leer", se)
        }
    }

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(info: BleCharacteristicInfo, data: ByteArray) {
        val g = gatt ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val writeType = if (info.canWrite) {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }
                g.writeCharacteristic(info.raw, data, writeType)
            } else {
                @Suppress("DEPRECATION")
                info.raw.value = data
                @Suppress("DEPRECATION")
                info.raw.writeType = if (info.canWrite) {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }
                @Suppress("DEPRECATION")
                g.writeCharacteristic(info.raw)
            }
            BeckyLogger.i(LogCategory.GATT, "Enviando escritura a ${info.characteristicUuid}")
        } catch (se: SecurityException) {
            BeckyLogger.e(LogCategory.GATT, "Permiso insuficiente para escribir", se)
        }
    }

    @SuppressLint("MissingPermission")
    fun setNotificationsEnabled(info: BleCharacteristicInfo, enabled: Boolean) {
        val g = gatt ?: return
        try {
            g.setCharacteristicNotification(info.raw, enabled)
            val descriptor: BluetoothGattDescriptor? = info.raw.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                val value = when {
                    enabled && info.canNotify -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    enabled && info.canIndicate -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    else -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(descriptor, value)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = value
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(descriptor)
                }
            }
            BeckyLogger.i(LogCategory.GATT, "Notificaciones ${if (enabled) "activadas" else "desactivadas"} para ${info.characteristicUuid}")
        } catch (se: SecurityException) {
            BeckyLogger.e(LogCategory.GATT, "Permiso insuficiente para configurar notificaciones", se)
        }
    }
}
