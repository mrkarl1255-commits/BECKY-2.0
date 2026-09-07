package com.becky.bridge.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context

/** Small helper to check adapter availability/state (section 5). */
object BluetoothStateHelper {

    fun getAdapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    fun isBluetoothSupported(context: Context): Boolean {
        return getAdapter(context) != null
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        return getAdapter(context)?.isEnabled == true
    }
}
