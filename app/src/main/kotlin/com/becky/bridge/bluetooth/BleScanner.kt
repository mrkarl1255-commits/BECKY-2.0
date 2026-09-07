package com.becky.bridge.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.becky.bridge.logging.BeckyLogger
import com.becky.bridge.logging.LogCategory
import com.becky.bridge.model.ScannedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * BLE scanner wrapper (section 6 of the spec).
 *
 * Exposes discovered devices as a [StateFlow] map keyed by MAC address so
 * the UI list naturally de-duplicates and updates RSSI/last-seen in
 * place instead of growing indefinitely.
 *
 * All Bluetooth permission checks are the responsibility of the caller
 * (typically the ViewModel / Activity) - this class assumes permissions
 * have already been granted, matching Android's own API contracts.
 */
class BleScanner(private val context: Context) {

    private var scanner: BluetoothLeScanner? = null
    private var isScanning = false

    private val _devices = MutableStateFlow<Map<String, ScannedDevice>>(emptyMap())
    val devices: StateFlow<Map<String, ScannedDevice>> = _devices

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device: BluetoothDevice = result.device
            val scanned = ScannedDevice(
                device = device,
                name = try { device.name } catch (se: SecurityException) { null },
                macAddress = try { device.address } catch (se: SecurityException) { null },
                rssi = result.rssi,
                deviceType = describeDeviceType(device),
                isBonded = try { device.bondState == BluetoothDevice.BOND_BONDED } catch (se: SecurityException) { false }
            )
            _devices.update { current -> current + (device.address to scanned) }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            _scanning.value = false
            BeckyLogger.e(LogCategory.SCAN, "Fallo de escaneo BLE. Codigo: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun describeDeviceType(device: BluetoothDevice): String {
        return when (device.type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Clasico"
            BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual (Clasico + BLE)"
            else -> "Desconocido"
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (isScanning) return
        val adapter = BluetoothStateHelper.getAdapter(context) ?: run {
            BeckyLogger.e(LogCategory.SCAN, "No se pudo iniciar escaneo: adaptador Bluetooth no disponible")
            return
        }
        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            BeckyLogger.e(LogCategory.SCAN, "No se pudo obtener BluetoothLeScanner")
            return
        }
        _devices.value = emptyMap()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner?.startScan(null, settings, scanCallback)
            isScanning = true
            _scanning.value = true
            BeckyLogger.i(LogCategory.SCAN, "Escaneo BLE iniciado")
        } catch (se: SecurityException) {
            BeckyLogger.e(LogCategory.SCAN, "Permiso insuficiente para escanear", se)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        try {
            scanner?.stopScan(scanCallback)
            BeckyLogger.i(LogCategory.SCAN, "Escaneo BLE detenido")
        } catch (se: SecurityException) {
            BeckyLogger.e(LogCategory.SCAN, "Permiso insuficiente para detener escaneo", se)
        } finally {
            isScanning = false
            _scanning.value = false
        }
    }
}
