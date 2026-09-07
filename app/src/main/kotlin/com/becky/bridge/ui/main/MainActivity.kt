package com.becky.bridge.ui.main

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.becky.bridge.R
import com.becky.bridge.bluetooth.BluetoothStateHelper
import com.becky.bridge.bluetooth.BridgeRepository
import com.becky.bridge.bluetooth.PermissionsHelper
import com.becky.bridge.databinding.ActivityMainBinding
import com.becky.bridge.logging.BeckyLogger
import com.becky.bridge.logging.LogCategory
import com.becky.bridge.model.ConnectionState
import com.becky.bridge.service.BridgeForegroundService
import com.becky.bridge.ui.connection.ConnectionActivity
import com.becky.bridge.ui.devices.DevicesActivity
import com.becky.bridge.ui.diagnostics.DiagnosticsActivity
import com.becky.bridge.ui.logs.LogsActivity
import com.becky.bridge.ui.voice.VoiceActivity
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Main screen (section 14 of the spec).
 *
 * Responsible only for: showing global connection status and routing to
 * the four secondary screens (Devices, Connection, Diagnostics, Logs).
 * All Bluetooth logic lives in [BridgeRepository] / bluetooth package -
 * this Activity is intentionally thin.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: BridgeRepository

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }
            if (allGranted) {
                BeckyLogger.i(LogCategory.PERMISSIONS, "Permisos concedidos")
                startScanFlow()
            } else {
                BeckyLogger.w(LogCategory.PERMISSIONS, "Permisos denegados por el usuario")
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = BridgeRepository.getInstance(applicationContext)

        binding.btnScan.setOnClickListener { handleScanRequest() }
        binding.btnDevices.setOnClickListener {
            startActivity(Intent(this, DevicesActivity::class.java))
        }
        binding.btnConnection.setOnClickListener {
            startActivity(Intent(this, ConnectionActivity::class.java))
        }
        binding.btnDiagnostics.setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        binding.btnLogs.setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
        }
        binding.btnVoice.setOnClickListener {
            startActivity(Intent(this, VoiceActivity::class.java))
        }

        observeConnectionState()
        startForegroundServiceSafely()
    }

    private fun observeConnectionState() {
        repository.connectionState.onEach { state ->
            binding.statusText.text = when (state) {
                ConnectionState.READY, ConnectionState.CONNECTED ->
                    getString(R.string.status_connected, repository.connectedDeviceName.value ?: "reloj")
                ConnectionState.CONNECTING, ConnectionState.DISCOVERING_SERVICES ->
                    getString(R.string.status_connecting)
                ConnectionState.RECONNECTING -> getString(R.string.status_reconnecting)
                ConnectionState.ERROR -> getString(R.string.status_error)
                ConnectionState.DISCONNECTED -> getString(R.string.status_waiting)
            }
        }.launchIn(lifecycleScope)
    }

    private fun handleScanRequest() {
        if (!BluetoothStateHelper.isBluetoothSupported(this)) {
            BeckyLogger.e(LogCategory.BLUETOOTH, "Este dispositivo no soporta Bluetooth")
            return
        }
        if (!PermissionsHelper.hasAllRequiredPermissions(this)) {
            requestPermissionsLauncher.launch(PermissionsHelper.requiredPermissions())
            return
        }
        if (!BluetoothStateHelper.isBluetoothEnabled(this)) {
            showBluetoothDisabledDialog()
            return
        }
        startScanFlow()
    }

    private fun startScanFlow() {
        if (!BluetoothStateHelper.isBluetoothEnabled(this)) {
            showBluetoothDisabledDialog()
            return
        }
        startActivity(Intent(this, DevicesActivity::class.java))
    }

    private fun showBluetoothDisabledDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.bt_disabled_title)
            .setMessage(R.string.bt_disabled_message)
            .setPositiveButton(R.string.bt_disabled_action) { _, _ ->
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startForegroundServiceSafely() {
        val intent = Intent(this, BridgeForegroundService::class.java).apply {
            action = BridgeForegroundService.ACTION_START
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            BeckyLogger.e(LogCategory.APP, "No se pudo iniciar el servicio", e)
        }
    }
}
