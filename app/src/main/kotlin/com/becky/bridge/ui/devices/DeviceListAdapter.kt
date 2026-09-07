package com.becky.bridge.ui.devices

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.becky.bridge.databinding.ItemDeviceBinding
import com.becky.bridge.model.ScannedDevice

/** Simple RecyclerView adapter for the devices list (section 6). */
class DeviceListAdapter(
    private val onDeviceClick: (ScannedDevice) -> Unit
) : RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {

    private val items = mutableListOf<ScannedDevice>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newItems: List<ScannedDevice>) {
        items.clear()
        items.addAll(newItems.sortedByDescending { it.rssi })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(items[position], onDeviceClick)
    }

    override fun getItemCount(): Int = items.size

    class DeviceViewHolder(private val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: ScannedDevice, onClick: (ScannedDevice) -> Unit) {
            binding.deviceName.text = device.displayName
            binding.deviceAddress.text = device.macAddress ?: "MAC no disponible"
            binding.deviceRssi.text = "RSSI: ${device.rssi} dBm  •  ${device.deviceType}"
            binding.deviceStatus.text = if (device.isBonded) "Vinculado" else "No vinculado"
            binding.root.setOnClickListener { onClick(device) }
        }
    }
}
