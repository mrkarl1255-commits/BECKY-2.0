package com.becky.bridge.communication

import com.becky.bridge.model.BridgeMessage
import kotlinx.coroutines.flow.SharedFlow

/**
 * Abstract communication channel (section 9 of the spec).
 *
 * BECKY BRIDGE uses Bluetooth/BLE as its primary transport in Phase 1
 * ([BluetoothTransport]), but the rest of the app never talks to
 * Bluetooth APIs directly - it always goes through this interface. This
 * allows a future Wi-Fi transport ([WifiTransport]) to be added later
 * (or even used simultaneously) without touching UI or business logic.
 */
interface CommunicationTransport {

    /** Human readable name of this transport, e.g. "Bluetooth LE". */
    val name: String

    /** True if the transport currently has an active connection to the watch. */
    val isConnected: Boolean

    /** Stream of messages received from the remote device (the watch). */
    val incomingMessages: SharedFlow<BridgeMessage>

    /** Sends a message through this transport. Returns true if accepted for sending. */
    suspend fun send(message: BridgeMessage): Boolean

    /** Releases all resources held by this transport. */
    fun close()
}
