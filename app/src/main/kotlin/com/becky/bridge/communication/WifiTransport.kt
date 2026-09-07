package com.becky.bridge.communication

import com.becky.bridge.model.BridgeMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Placeholder Wi-Fi transport (section 9 of the spec).
 *
 * BECKY BRIDGE does NOT assume the smartwatch will use Wi-Fi. Bluetooth
 * LE is the primary channel for Phase 1. This class exists purely to
 * prove the [CommunicationTransport] abstraction is transport-agnostic
 * and to give a concrete starting point for a future implementation
 * (e.g. local Wi-Fi Direct or a socket-based companion protocol).
 *
 * Intentionally not wired into the UI yet - see Phase 2/3 roadmap.
 */
class WifiTransport : CommunicationTransport {

    override val name: String = "Wi-Fi (no implementado aun)"

    override val isConnected: Boolean = false

    private val _incomingMessages = MutableSharedFlow<BridgeMessage>(extraBufferCapacity = 16)
    override val incomingMessages: SharedFlow<BridgeMessage> = _incomingMessages

    override suspend fun send(message: BridgeMessage): Boolean {
        // TODO(Phase 2+): implement Wi-Fi Direct / socket transport here.
        return false
    }

    override fun close() {
        // Nothing to release yet.
    }
}
