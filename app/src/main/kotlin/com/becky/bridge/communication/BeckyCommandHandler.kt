package com.becky.bridge.communication

import com.becky.bridge.logging.BeckyLogger
import com.becky.bridge.logging.LogCategory
import com.becky.bridge.model.BridgeMessage
import com.becky.bridge.model.MessageEndpoint
import com.becky.bridge.model.MessageType
import com.becky.bridge.model.PayloadKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Known BECKY <-> WATCH commands (section 16 of the spec).
 *
 * All six commands now have a full request/response implementation in
 * Phase 2 (see [DefaultBeckyCommandHandler] and the typed
 * `get*()` methods on [BeckyCommandHandler]). The response *parsing* for
 * each command (see the `asWatchXxx()` extensions below) uses a
 * best-effort, protocol-agnostic convention (`value` for numeric
 * results, `payload` as a text fallback) since the real GATT
 * characteristic layout of the target smartwatch is not yet known. Once
 * the actual watch firmware/protocol is documented, only the parsing
 * inside the corresponding `asWatchXxx()` function needs to change -
 * the request/response/timeout plumbing here stays untouched.
 */
object BeckyCommands {
    const val GET_TIME = "GET_TIME"
    const val GET_NOTIFICATIONS = "GET_NOTIFICATIONS"
    const val GET_STEPS = "GET_STEPS"
    const val GET_HEART_RATE = "GET_HEART_RATE"
    const val GET_BATTERY = "GET_BATTERY"
    const val GET_WATCH_STATUS = "GET_WATCH_STATUS"
}

/**
 * Outcome of a BECKY -> WATCH command request/response cycle
 * (see [BeckyCommandHandler.sendCommandForResult]).
 */
sealed class BeckyCommandResult {
    /** The watch answered before the timeout with a non-error message. */
    data class Success(val response: BridgeMessage) : BeckyCommandResult()

    /** There is no active/ready connection to the watch - command was not sent. */
    object NotConnected : BeckyCommandResult()

    /** The command was sent but no response arrived within the timeout. */
    object Timeout : BeckyCommandResult()

    /** The transport refused to send the message, or the watch replied with an ERROR message. */
    data class Failed(val error: String) : BeckyCommandResult()
}

/** Typed result of [BeckyCommandHandler.getTime]. */
data class WatchTime(val epochMillis: Long, val raw: BridgeMessage)

/** Typed result of [BeckyCommandHandler.getHeartRate]. */
data class WatchHeartRate(val bpm: Int, val raw: BridgeMessage)

/** Typed result of [BeckyCommandHandler.getNotifications]. Phase 1/2 do not parse structured notifications yet - [texts] is a best-effort split of [BridgeMessage.payload]. */
data class WatchNotifications(val count: Int, val texts: List<String>, val raw: BridgeMessage)

/** Typed result of [BeckyCommandHandler.getSteps]. */
data class WatchSteps(val steps: Int, val raw: BridgeMessage)

/** Typed result of [BeckyCommandHandler.getBattery]. [percent] is expected in the 0-100 range. */
data class WatchBattery(val percent: Int, val raw: BridgeMessage)

/** Typed result of [BeckyCommandHandler.getWatchStatus]. [status] is a free-form label (e.g. "OK", "LOW_BATTERY") reported by the watch. */
data class WatchStatus(val status: String, val raw: BridgeMessage)

/** Extracts a [WatchTime] from a successful [BeckyCommandResult], or null otherwise. */
fun BeckyCommandResult.asWatchTime(): WatchTime? {
    val response = (this as? BeckyCommandResult.Success)?.response ?: return null
    val millis = response.value?.toLong() ?: response.payload?.trim()?.toLongOrNull() ?: return null
    return WatchTime(epochMillis = millis, raw = response)
}

/** Extracts a [WatchHeartRate] from a successful [BeckyCommandResult], or null otherwise. */
fun BeckyCommandResult.asWatchHeartRate(): WatchHeartRate? {
    val response = (this as? BeckyCommandResult.Success)?.response ?: return null
    val bpm = response.value?.toInt() ?: response.payload?.trim()?.toIntOrNull() ?: return null
    return WatchHeartRate(bpm = bpm, raw = response)
}

/**
 * Extracts a [WatchNotifications] from a successful [BeckyCommandResult],
 * or null otherwise. Convention (pending real watch protocol): [payload]
 * is a newline-separated list of notification texts; [value] (if
 * present) overrides the reported count, otherwise the line count is
 * used.
 */
fun BeckyCommandResult.asWatchNotifications(): WatchNotifications? {
    val response = (this as? BeckyCommandResult.Success)?.response ?: return null
    val texts = response.payload
        ?.split('\n')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()
    val count = response.value?.toInt() ?: texts.size
    return WatchNotifications(count = count, texts = texts, raw = response)
}

/** Extracts a [WatchSteps] from a successful [BeckyCommandResult], or null otherwise. */
fun BeckyCommandResult.asWatchSteps(): WatchSteps? {
    val response = (this as? BeckyCommandResult.Success)?.response ?: return null
    val steps = response.value?.toInt() ?: response.payload?.trim()?.toIntOrNull() ?: return null
    return WatchSteps(steps = steps, raw = response)
}

/** Extracts a [WatchBattery] from a successful [BeckyCommandResult], or null otherwise. */
fun BeckyCommandResult.asWatchBattery(): WatchBattery? {
    val response = (this as? BeckyCommandResult.Success)?.response ?: return null
    val percent = response.value?.toInt() ?: response.payload?.trim()?.toIntOrNull() ?: return null
    return WatchBattery(percent = percent, raw = response)
}

/**
 * Extracts a [WatchStatus] from a successful [BeckyCommandResult], or
 * null otherwise. Convention (pending real watch protocol): [payload]
 * holds the free-form status label; falls back to [BridgeMessage.command]
 * if payload is absent so the caller always gets a non-blank label when
 * the response was otherwise a valid Success.
 */
fun BeckyCommandResult.asWatchStatus(): WatchStatus? {
    val response = (this as? BeckyCommandResult.Success)?.response ?: return null
    val status = response.payload?.trim()?.takeIf { it.isNotEmpty() }
        ?: response.command?.takeIf { it.isNotEmpty() }
        ?: return null
    return WatchStatus(status = status, raw = response)
}

/**
 * Entry point BECKY (the future voice assistant) uses to talk to the
 * watch through the bridge (section 16).
 *
 * Phase 2 adds real request/response semantics on top of the Phase 1
 * plumbing (interface, transport, message envelope): every command sent
 * via [sendCommandForResult] gets a unique [BridgeMessage.requestId], and
 * the handler correlates the watch's [BridgeMessage.RESPONSE] carrying
 * the same id, with a timeout so BECKY never hangs waiting forever.
 *
 * This still does NOT hardcode a specific smartwatch protocol: the
 * request/response envelope is transport-agnostic JSON
 * ([BridgeMessage]), so once the real watch firmware exists it only
 * needs to answer with a matching `requestId` and fill `value`/`payload`
 * - no changes needed here.
 */
interface BeckyCommandHandler {

    /**
     * Sends a named command to the watch without waiting for a response.
     * Kept for simple fire-and-forget cases (e.g. STATUS pings). Returns
     * true only if the transport accepted the message for sending.
     */
    suspend fun sendCommand(command: String, payload: String? = null): Boolean

    /**
     * Sends a named command and awaits a correlated response (matched by
     * `requestId`) up to [timeoutMs]. This is the building block every
     * typed command (like [getTime] / [getHeartRate]) is implemented on
     * top of, and the one future commands should keep using.
     */
    suspend fun sendCommandForResult(
        command: String,
        payload: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): BeckyCommandResult

    /** Requests the current time from the watch ([BeckyCommands.GET_TIME]). */
    suspend fun getTime(): BeckyCommandResult

    /** Requests the current heart rate reading from the watch ([BeckyCommands.GET_HEART_RATE]). */
    suspend fun getHeartRate(): BeckyCommandResult

    /** Requests pending notifications from the watch ([BeckyCommands.GET_NOTIFICATIONS]). */
    suspend fun getNotifications(): BeckyCommandResult

    /** Requests the current step count from the watch ([BeckyCommands.GET_STEPS]). */
    suspend fun getSteps(): BeckyCommandResult

    /** Requests the current battery level from the watch ([BeckyCommands.GET_BATTERY]). */
    suspend fun getBattery(): BeckyCommandResult

    /** Requests a general status report from the watch ([BeckyCommands.GET_WATCH_STATUS]). */
    suspend fun getWatchStatus(): BeckyCommandResult

    /**
     * Phase 3: stream of messages the watch sent WITHOUT being asked
     * (i.e. NOT a correlated response to [sendCommandForResult]) - for
     * example the watch spontaneously reporting
     * [com.becky.bridge.model.MessageType.WATCH_TO_BECKY] because the
     * user pressed a physical button or spoke to a microphone on the
     * watch. This is what lets a future
     * [com.becky.bridge.assistant.BeckyAssistant] be triggered FROM the
     * watch, not just from the phone's voice pipeline.
     *
     * Purely additive: every message that IS a correlated response
     * still only reaches [sendCommandForResult] as before, never this
     * flow - see [DefaultBeckyCommandHandler]'s single collector.
     */
    val incomingWatchRequests: SharedFlow<BridgeMessage>

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000L
    }
}

/**
 * Default implementation that forwards BECKY commands through any
 * [CommunicationTransport] (Bluetooth today, Wi-Fi in the future) and
 * matches responses by [BridgeMessage.requestId].
 *
 * @param scope coroutine scope used to continuously collect
 *   [CommunicationTransport.incomingMessages] in the background looking
 *   for responses to pending requests. Should be a long-lived scope tied
 *   to the app/repository lifecycle (see [com.becky.bridge.bluetooth.BridgeRepository]),
 *   not a short-lived UI scope.
 */
class DefaultBeckyCommandHandler(
    private val transport: CommunicationTransport,
    private val scope: CoroutineScope
) : BeckyCommandHandler {

    /** requestId -> pending deferred awaiting the matching RESPONSE message. */
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<BridgeMessage>>()

    private val _incomingWatchRequests = MutableSharedFlow<BridgeMessage>(extraBufferCapacity = 16)
    override val incomingWatchRequests: SharedFlow<BridgeMessage> = _incomingWatchRequests

    init {
        scope.launch {
            transport.incomingMessages.collect { message ->
                val requestId = message.requestId
                if (requestId != null) {
                    // Exactly the original Phase 2 behavior: complete the
                    // matching pending request if there is one, otherwise
                    // silently drop it (e.g. a response that arrived after
                    // its own timeout already fired). Never re-routed
                    // elsewhere, so no existing request/response semantics change.
                    pendingRequests.remove(requestId)?.complete(message)
                    return@collect
                }
                // No requestId: the watch is speaking on its own (e.g.
                // MessageType.WATCH_TO_BECKY). Hand it to anyone listening on
                // incomingWatchRequests (Phase 3 voice/assistant layer) - this
                // is the only new behavior added in Phase 3.
                _incomingWatchRequests.tryEmit(message)
            }
        }
    }

    override suspend fun sendCommand(command: String, payload: String?): Boolean {
        val message = BridgeMessage(
            type = MessageType.BECKY_TO_WATCH,
            source = MessageEndpoint.BECKY,
            destination = MessageEndpoint.WATCH,
            command = command,
            payloadKind = PayloadKind.COMMAND,
            payload = payload
        )
        return transport.send(message)
    }

    override suspend fun sendCommandForResult(
        command: String,
        payload: String?,
        timeoutMs: Long
    ): BeckyCommandResult {
        if (!transport.isConnected) {
            BeckyLogger.w(LogCategory.MESSAGE, "Comando '$command' no enviado: sin conexion activa con el reloj")
            return BeckyCommandResult.NotConnected
        }

        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<BridgeMessage>()
        pendingRequests[requestId] = deferred

        val message = BridgeMessage(
            type = MessageType.BECKY_TO_WATCH,
            source = MessageEndpoint.BECKY,
            destination = MessageEndpoint.WATCH,
            command = command,
            payloadKind = PayloadKind.COMMAND,
            payload = payload,
            requestId = requestId
        )

        val sent = try {
            transport.send(message)
        } catch (e: Exception) {
            BeckyLogger.e(LogCategory.MESSAGE, "Excepcion enviando comando '$command'", e)
            false
        }

        if (!sent) {
            pendingRequests.remove(requestId)
            return BeckyCommandResult.Failed("El transporte rechazo el envio del comando '$command'")
        }

        BeckyLogger.i(LogCategory.MESSAGE, "Comando '$command' enviado (requestId=$requestId), esperando respuesta (timeout=${timeoutMs}ms)...")

        val response = try {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } finally {
            pendingRequests.remove(requestId)
        }

        return when {
            response == null -> {
                BeckyLogger.w(LogCategory.MESSAGE, "Timeout esperando respuesta de '$command' (requestId=$requestId)")
                BeckyCommandResult.Timeout
            }
            response.type == MessageType.ERROR -> {
                val errorMsg = response.error ?: "Error desconocido reportado por el reloj"
                BeckyLogger.e(LogCategory.MESSAGE, "El reloj respondio con error a '$command': $errorMsg")
                BeckyCommandResult.Failed(errorMsg)
            }
            else -> {
                BeckyLogger.i(LogCategory.MESSAGE, "Respuesta recibida para '$command' (requestId=$requestId)")
                BeckyCommandResult.Success(response)
            }
        }
    }

    override suspend fun getTime(): BeckyCommandResult =
        sendCommandForResult(BeckyCommands.GET_TIME)

    override suspend fun getHeartRate(): BeckyCommandResult =
        sendCommandForResult(BeckyCommands.GET_HEART_RATE)

    override suspend fun getNotifications(): BeckyCommandResult =
        sendCommandForResult(BeckyCommands.GET_NOTIFICATIONS)

    override suspend fun getSteps(): BeckyCommandResult =
        sendCommandForResult(BeckyCommands.GET_STEPS)

    override suspend fun getBattery(): BeckyCommandResult =
        sendCommandForResult(BeckyCommands.GET_BATTERY)

    override suspend fun getWatchStatus(): BeckyCommandResult =
        sendCommandForResult(BeckyCommands.GET_WATCH_STATUS)
}
