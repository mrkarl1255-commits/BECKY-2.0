package com.becky.bridge.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Centralizes the runtime permissions required to operate Bluetooth/BLE
 * on modern Android versions (section 5 of the spec).
 *
 * Only the strictly necessary permissions are requested, and only the
 * ones relevant to the running OS version - we never ask for permissions
 * the current Android version does not need.
 */
object PermissionsHelper {

    /** Permissions required to scan for and connect to BLE/Bluetooth devices. */
    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+): new granular Bluetooth runtime permissions.
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            // Android 6-11: BLE scanning requires (fine) location permission.
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    /** Optional permission used only if the app ever needs to advertise via BLE. */
    fun advertisePermission(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_ADVERTISE
        } else null
    }

    fun hasAllRequiredPermissions(context: Context): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun missingPermissions(context: Context): List<String> {
        return requiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
}
