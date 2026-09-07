package com.becky.bridge.communication

import com.becky.bridge.bluetooth.GattManager
import com.becky.bridge.logging.BeckyLogger
import com.becky.bridge.logging.LogCategory
import com.becky.bridge.model.BleCharacteristicInfo
import com.becky.bridge.model.BridgeMessage
import com.becky.bridge.model.ConnectionState
import com.becky.bridge.model.MessageEndpoint
import com.becky.bridge.model.MessageType
import com.becky.bridge.model.PayloadKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

/**
 * [CommunicationTransport] implementation backed by BLE GATT
 * (section 9 - primary transport for Phase 1).
 *
 * This class bridges the generic [BridgeMessage] protocol on top of a
 * concrete BLE characteristic used for phone <-> watch data exchange.
 * The exact characteristic to use is watch-specific and is therefore
 * left configurable via [setActiveCharacteristic] - this is intentional
 * so the modular architecture described in section 4/16 can plug the
 * real watch protocol later without rewriting this class.
 */
class BluetoothTransport(
    private val gattManager: GattManager,
    private val scope: CoroutineScope
) : CommunicationTransport {

    override val name: String = "Bluetooth LE"

    override val isConnected: Boolean
        get() = gattManager.connectionState.value == ConnectionState.READY

    private val _incomingMessages = MutableSharedFlow<BridgeMessage>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<BridgeMessage> = _incomingMessages

    private var activeWriteCharacteristic: BleCharacteristicInfo? = null

    private val json = Json { ignoreUnknownKeys = true }

    init {
        scope.launch {
            gattManager.notifications.collect { (uuid, bytes) ->
                handleIncomingBytes(uuid, bytes)
            }
        }
        scope.launch {
            gattManager.readResults.collect { (uuid, bytes) ->
                handleIncomingBytes(uuid, bytes)
            }
        }
    }

    /** Sets which characteristic should be used to write outgoing messages. */
    fun setActiveCharacteristic(info: BleCharacteristicInfo?) {
        activeWriteCharacteristic = info
    }

    private fun handleIncomingBytes(characteristicUuid: String, bytes: ByteArray) {
        val text = try {
            String(bytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            null
        }

        val message = if (text != null) {
            try {
                json.decodeFromString(BridgeMessage.serializer(), text)
            } catch (e: Exception) {
                // Not a JSON envelope - wrap the raw text as a WATCH_TO_PHONE message.
                BridgeMessage(
                    type = MessageType.WATCH_TO_PHONE,
                    source = MessageEndpoint.WATCH,
                    destination = MessageEndpoint.PHONE,
                    payloadKind = PayloadKind.RAW_BYTES,
                    payload = text
                )
            }
        } else {
            BridgeMessage(
                type = MessageType.WATCH_TO_PHONE,
                source = MessageEndpoint.WATCH,
                destination = MessageEndpoint.PHONE,
                payloadKind = PayloadKind.RAW_BYTES,
                payload = bytes.joinToString(separator = "") { "%02x".format(it) }
            )
        }

        BeckyLogger.i(LogCategory.MESSAGE, "Mensaje recibido de $characteristicUuid: ${message.type}")
        _incomingMessages.tryEmit(message)
    }

    override suspend fun send(message: BridgeMessage): Boolean {
        val characteristic = activeWriteCharacteristic
        if (characteristic == null) {
            BeckyLogger.w(LogCategory.MESSAGE, "No hay caracteristica de escritura activa - mensaje no enviado")
            return false
        }
        if (!characteristic.canWrite && !characteristic.canWriteNoResponse) {
            BeckyLogger.w(LogCategory.MESSAGE, "La caracteristica activa no soporta escritura")
            return false
        }
        val json = Json.encodeToString(message)
        gattManager.writeCharacteristic(characteristic, json.toByteArray(StandardCharsets.UTF_8))
        BeckyLogger.i(LogCategory.MESSAGE, "Mensaje enviado: ${message.type} -> ${message.destination}")
        return true
    }

    override fun close() {
        gattManager.disconnect()
    }
}
