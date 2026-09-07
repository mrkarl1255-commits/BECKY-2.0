package com.becky.bridge.ui.connection

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
import com.becky.bridge.model.ConnectionState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Connection / GATT services screen (sections 5 and 7 of the spec).
 *
 * Shows current connection state plus the list of discovered GATT
 * services/characteristics, and exposes Connect/Disconnect/Reconnect
 * actions. Reading/writing individual characteristics is wired through
 * [BridgeRepository] and can be extended per-characteristic in a future
 * iteration (e.g. a detail screen) without touching Bluetooth code.
 */
class ConnectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySimpleBinding
    private lateinit var repository: BridgeRepository
    private lateinit var adapter: ServiceListAdapter

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = BridgeRepository.getInstance(applicationContext)
        binding.screenTitle.text = getString(R.string.connection_title)

        adapter = ServiceListAdapter(
            onRead = { info -> repository.readCharacteristic(info) },
            onNotifyToggle = { info, enabled -> repository.setNotificationsEnabled(info, enabled) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        repository.discoveredServices.onEach { services ->
            adapter.submitList(services)
            binding.emptyText.visibility =
                if (services.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.emptyText.text = getString(R.string.connection_no_device)
        }.launchIn(lifecycleScope)

        repository.connectionState.onEach { state ->
            BeckyLogger.i(LogCategory.CONNECTION, "Estado de conexion en pantalla: $state")
        }.launchIn(lifecycleScope)
    }

    fun disconnect() = repository.disconnect()
    fun reconnect() = repository.reconnect()
}
