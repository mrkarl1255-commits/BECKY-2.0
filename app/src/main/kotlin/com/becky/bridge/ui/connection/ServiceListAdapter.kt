package com.becky.bridge.ui.connection

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.becky.bridge.model.BleCharacteristicInfo
import com.becky.bridge.model.BleServiceInfo

/**
 * Renders discovered GATT services and their characteristics
 * (section 7 of the spec). Built programmatically (no XML) to keep
 * this Phase-1 screen lightweight; can be replaced with a richer
 * per-characteristic detail screen later without touching Bluetooth code.
 */
class ServiceListAdapter(
    private val onRead: (BleCharacteristicInfo) -> Unit,
    private val onNotifyToggle: (BleCharacteristicInfo, Boolean) -> Unit
) : RecyclerView.Adapter<ServiceListAdapter.ServiceViewHolder>() {

    private val items = mutableListOf<BleServiceInfo>()

    fun submitList(newItems: List<BleServiceInfo>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val container = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        return ServiceViewHolder(container)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        holder.bind(items[position], onRead, onNotifyToggle)
    }

    override fun getItemCount(): Int = items.size

    class ServiceViewHolder(private val container: LinearLayout) : RecyclerView.ViewHolder(container) {
        fun bind(
            service: BleServiceInfo,
            onRead: (BleCharacteristicInfo) -> Unit,
            onNotifyToggle: (BleCharacteristicInfo, Boolean) -> Unit
        ) {
            container.removeAllViews()
            val context = container.context

            val serviceTitle = TextView(context).apply {
                text = "Servicio: ${service.serviceUuid}"
                setTextColor(0xFFE6EDF3.toInt())
                textSize = 14f
                setPadding(0, 16, 0, 8)
            }
            container.addView(serviceTitle)

            service.characteristics.forEach { characteristic ->
                val charView = TextView(context).apply {
                    text = "  • ${characteristic.characteristicUuid}\n    Propiedades: ${characteristic.propertiesLabel}"
                    setTextColor(0xFF8B949E.toInt())
                    textSize = 12f
                    setPadding(16, 4, 0, 4)
                    setOnClickListener {
                        if (characteristic.canRead) onRead(characteristic)
                        if (characteristic.canNotify || characteristic.canIndicate) {
                            onNotifyToggle(characteristic, true)
                        }
                    }
                }
                container.addView(charView)
            }
        }
    }
}
