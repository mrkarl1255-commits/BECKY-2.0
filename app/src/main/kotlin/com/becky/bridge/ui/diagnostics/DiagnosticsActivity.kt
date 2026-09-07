package com.becky.bridge.ui.diagnostics

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.becky.bridge.R
import com.becky.bridge.bluetooth.BluetoothStateHelper
import com.becky.bridge.bluetooth.BridgeRepository
import com.becky.bridge.bluetooth.PermissionsHelper
import com.becky.bridge.communication.BeckyCommandResult
import com.becky.bridge.communication.BeckyCommands
import com.becky.bridge.databinding.ActivitySimpleBinding
import com.becky.bridge.logging.BeckyLogger
import com.becky.bridge.model.ConnectionState
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostics screen (section 12 of the spec).
 * Shows a live snapshot of Bluetooth/permissions/connection/GATT state
 * and allows exporting the in-memory log as text.
 */
class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySimpleBinding
    private lateinit var repository: BridgeRepository
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = BridgeRepository.getInstance(applicationContext)
        binding.screenTitle.text = getString(R.string.diagnostics_title)
        binding.recyclerView.visibility = android.view.View.GONE

        val infoContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        (binding.root as LinearLayout).addView(infoContainer, 2)

        combine(
            repository.connectionState,
            repository.discoveredServices
        ) { state, services -> state to services }
            .onEach { (state, services) ->
                renderSnapshot(infoContainer, state, services.size, services.sumOf { it.characteristics.size })
            }.launchIn(lifecycleScope)

        setupCommandTestPanel()

        binding.emptyText.visibility = android.view.View.VISIBLE
        binding.emptyText.text = getString(R.string.diag_export)
        binding.emptyText.setOnClickListener {
            val text = BeckyLogger.exportAsText()
            BeckyLogger.i(com.becky.bridge.logging.LogCategory.APP, "Registro exportado (${text.length} caracteres)")
        }
    }

    /**
     * Phase 2: manual test panel for BECKY commands (section 16). Lets a
     * developer/tester trigger [BridgeRepository.commandHandler]'s
     * [com.becky.bridge.communication.BeckyCommandHandler.getTime] and
     * [com.becky.bridge.communication.BeckyCommandHandler.getHeartRate]
     * against a real connected watch and see the raw
     * [BeckyCommandResult] outcome, without writing any code.
     *
     * This does NOT touch the underlying BLE architecture: it only calls
     * the already-existing [BridgeRepository.commandHandler] entry
     * point, exactly like a future BECKY voice assistant would.
     */
    private fun setupCommandTestPanel() {
        val root = binding.root as LinearLayout

        val sectionTitle = TextView(this).apply {
            text = getString(R.string.diag_commands_title)
            setTextColor(0xFFE6EDF3.toInt())
            textSize = 12f
            setPadding(0, 32, 0, 4)
        }
        val sectionSubtitle = TextView(this).apply {
            text = getString(R.string.diag_commands_subtitle)
            setTextColor(0xFF8B949E.toInt())
            textSize = 12f
            setPadding(0, 0, 0, 12)
        }

        val resultText = TextView(this).apply {
            text = getString(R.string.diag_command_result_idle)
            setTextColor(0xFF8B949E.toInt())
            textSize = 13f
            gravity = Gravity.START
            setPadding(0, 12, 0, 0)
        }

        val getTimeButton = MaterialButton(this).apply {
            text = getString(R.string.diag_btn_get_time)
        }
        val getHeartRateButton = MaterialButton(this).apply {
            text = getString(R.string.diag_btn_get_heart_rate)
        }

        fun setButtonsEnabled(enabled: Boolean) {
            getTimeButton.isEnabled = enabled
            getHeartRateButton.isEnabled = enabled
        }

        getTimeButton.setOnClickListener {
            runCommandTest(
                commandName = BeckyCommands.GET_TIME,
                resultText = resultText,
                setButtonsEnabled = ::setButtonsEnabled
            ) { repository.commandHandler.getTime() }
        }

        getHeartRateButton.setOnClickListener {
            runCommandTest(
                commandName = BeckyCommands.GET_HEART_RATE,
                resultText = resultText,
                setButtonsEnabled = ::setButtonsEnabled
            ) { repository.commandHandler.getHeartRate() }
        }

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        buttonsRow.addView(
            getTimeButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            }
        )
        buttonsRow.addView(
            getHeartRateButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 8
            }
        )

        // Insert right before the "emptyText" (export log) view so the
        // command panel sits between the diagnostics snapshot and the
        // export action, without disturbing existing view indices.
        val emptyTextIndex = root.indexOfChild(binding.emptyText)
        root.addView(sectionTitle, emptyTextIndex)
        root.addView(sectionSubtitle, emptyTextIndex + 1)
        root.addView(buttonsRow, emptyTextIndex + 2)
        root.addView(resultText, emptyTextIndex + 3)
    }

    /**
     * Runs a single BECKY command test: disables both buttons, shows a
     * "sending..." state, invokes [invoke] on [lifecycleScope], and
     * renders the resulting [BeckyCommandResult] as plain text -
     * covering all four outcomes explicitly (Success / NotConnected /
     * Timeout / Failed).
     */
    private fun runCommandTest(
        commandName: String,
        resultText: TextView,
        setButtonsEnabled: (Boolean) -> Unit,
        invoke: suspend () -> BeckyCommandResult
    ) {
        setButtonsEnabled(false)
        resultText.setTextColor(0xFF8B949E.toInt())
        resultText.text = getString(R.string.diag_command_running, commandName)

        lifecycleScope.launch {
            val result = try {
                invoke()
            } catch (e: Exception) {
                BeckyLogger.e(com.becky.bridge.logging.LogCategory.MESSAGE, "Excepcion inesperada probando '$commandName'", e)
                BeckyCommandResult.Failed(e.message ?: "Excepcion inesperada")
            }

            when (result) {
                is BeckyCommandResult.Success -> {
                    resultText.setTextColor(0xFF3FB950.toInt())
                    val summary = result.response.payload
                        ?: result.response.value?.toString()
                        ?: "(sin datos en la respuesta)"
                    resultText.text = getString(R.string.diag_command_success, commandName, summary)
                }
                is BeckyCommandResult.NotConnected -> {
                    resultText.setTextColor(0xFFD29922.toInt())
                    resultText.text = getString(R.string.diag_command_not_connected, commandName)
                }
                is BeckyCommandResult.Timeout -> {
                    resultText.setTextColor(0xFFD29922.toInt())
                    resultText.text = getString(R.string.diag_command_timeout, commandName)
                }
                is BeckyCommandResult.Failed -> {
                    resultText.setTextColor(0xFFF85149.toInt())
                    resultText.text = getString(R.string.diag_command_failed, commandName, result.error)
                }
            }

            setButtonsEnabled(true)
        }
    }

    private fun renderSnapshot(
        container: LinearLayout,
        state: ConnectionState,
        serviceCount: Int,
        characteristicCount: Int
    ) {
        container.removeAllViews()
        val btEnabled = BluetoothStateHelper.isBluetoothEnabled(this)
        val permsGranted = PermissionsHelper.hasAllRequiredPermissions(this)
        val lastError = BeckyLogger.lastError()

        val lines = listOf(
            getString(R.string.diag_bluetooth) to if (btEnabled) getString(R.string.diag_enabled) else getString(R.string.diag_disabled),
            getString(R.string.diag_permissions) to if (permsGranted) getString(R.string.diag_granted) else getString(R.string.diag_denied),
            getString(R.string.diag_device) to (repository.connectedDeviceName.value ?: getString(R.string.diag_none)),
            getString(R.string.diag_mac) to (repository.connectedDeviceAddress.value ?: getString(R.string.diag_none)),
            getString(R.string.diag_connection) to state.name,
            getString(R.string.diag_gatt) to if (state == ConnectionState.READY) getString(R.string.diag_enabled) else getString(R.string.diag_disabled),
            getString(R.string.diag_services) to serviceCount.toString(),
            getString(R.string.diag_characteristics) to characteristicCount.toString(),
            getString(R.string.diag_last_error) to (lastError?.message ?: getString(R.string.diag_none))
        )

        lines.forEach { (label, value) ->
            val row = TextView(this).apply {
                text = "$label: $value"
                setTextColor(0xFFE6EDF3.toInt())
                textSize = 14f
                setPadding(0, 10, 0, 10)
            }
            container.addView(row)
        }
    }
}
