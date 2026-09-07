package com.becky.bridge.model

import kotlinx.serialization.Serializable

/**
 * BECKY BRIDGE - Internal message protocol.
 *
 * This is the canonical envelope used for ALL communication that flows
 * through the bridge, regardless of the underlying transport
 * (Bluetooth Classic, BLE, or a future Wi-Fi transport).
 *
 * The architecture intentionally keeps this generic so that Phase 2
 * (BECKY assistant commands), Phase 3 (avatar/animation payloads) and
 * Phase 4 (full personal assistant) can be built on top of this same
 * envelope without breaking existing code.
 */

/** Logical endpoints that can send or receive a [BridgeMessage]. */
enum class MessageEndpoint {
    PHONE,
    BECKY,
    WATCH,
    BRIDGE
}

/** High level message categories understood by the bridge. */
enum class MessageType {
    PHONE_TO_WATCH,
    WATCH_TO_PHONE,
    BECKY_TO_WATCH,
    WATCH_TO_BECKY,
    STATUS,
    COMMAND,
    RESPONSE,
    ERROR
}

/**
 * Generic payload kinds. Only TEXT/COMMAND/STATUS are actively used in
 * Phase 1. IMAGE / ANIMATION / VOICE are reserved for the future BECKY
 * avatar (Phase 3) so the protocol does not need to change later.
 */
enum class PayloadKind {
    TEXT,
    COMMAND,
    RESPONSE,
    STATUS,
    IMAGE,
    ANIMATION,
    VOICE,
    RAW_BYTES
}

/**
 * The universal envelope exchanged between PHONE, BECKY, BRIDGE and WATCH.
 *
 * @param timestamp epoch millis when the message was created.
 * @param type the high level category of this message.
 * @param source logical origin of the message.
 * @param destination logical destination of the message.
 * @param command optional command name (e.g. "GET_TIME", "GET_HEART_RATE").
 * @param payloadKind describes how [payload] should be interpreted.
 * @param payload optional string payload (JSON, plain text, base64, etc).
 * @param value optional numeric value for simple sensor-like responses.
 * @param error optional error description when [type] is ERROR.
 * @param requestId optional correlation id (Phase 2). When BECKY sends a
 *   COMMAND, it stamps a unique [requestId]; the watch's RESPONSE should
 *   echo the same id back so [com.becky.bridge.communication.BeckyCommandHandler]
 *   can match request/response pairs and apply timeouts. Nullable and
 *   defaulted so this stays 100% backward compatible with Phase 1 payloads
 *   that never set it (e.g. plain STATUS/ERROR messages).
 */
@Serializable
data class BridgeMessage(
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType,
    val source: MessageEndpoint,
    val destination: MessageEndpoint,
    val command: String? = null,
    val payloadKind: PayloadKind = PayloadKind.TEXT,
    val payload: String? = null,
    val value: Double? = null,
    val error: String? = null,
    val requestId: String? = null
)
