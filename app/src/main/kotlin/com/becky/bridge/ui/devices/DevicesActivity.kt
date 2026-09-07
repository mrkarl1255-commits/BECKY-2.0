package com.becky.bridge.ui.devices

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.becky.bridge.R
import com.becky.bridge.bluetooth.BridgeRepository
import com.becky.bridge.databinding.ActivitySimpleBinding
import com.becky.bridge.logging.BeckyLogger
import com.becky.bridge.logging.LogCategory
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Devices screen (section 6 of the spec).
 * Shows scanned BLE devices and lets the user tap one to connect.
 */
class DevicesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySimpleBinding
    private lateinit var repository: BridgeRepository
    private lateinit var adapter: DeviceListAdapter

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = BridgeRepository.getInstance(applicationContext)
        binding.screenTitle.text = getString(R.string.devices_title)

        adapter = DeviceListAdapter { device ->
            BeckyLogger.i(LogCategory.CONNECTION, "Usuario selecciono ${device.displayName}")
            repository.connect(device.device)
            finish()
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        repository.scannedDevices.onEach { deviceMap ->
            val list = deviceMap.values.toList()
            adapter.submitList(list)
            binding.emptyText.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.emptyText.text = getString(R.string.devices_empty)
        }.launchIn(lifecycleScope)

        repository.startScan()
    }

    override fun onDestroy() {
        repository.stopScan()
        super.onDestroy()
    }
}
