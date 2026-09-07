package com.becky.bridge.assistant

/**
 * BECKY BRIDGE - Assistant/Becky layer (Phase 3).
 *
 * Sits between Voice and Command/Capability:
 *
 * ```
 * Voice -> Assistant/Becky -> Command/Capability -> BLE/Repository
 * ```
 *
 * [InteractionSource] tracks whether an interaction was started from
 * the phone (spoken into [com.becky.bridge.voice.VoiceEngine]) or from
 * the watch (a spontaneous BLE message) so a response can be routed
 * back to wherever it came from - see
 * [com.becky.bridge.voice.VoiceRepository].
 */
enum class InteractionSource { PHONE, WATCH }

/** A single natural-language utterance BECKY needs to respond to. */
data class BeckyAssistantRequest(
    val text: String,
    val source: InteractionSource
)

/**
 * BECKY's answer to a [BeckyAssistantRequest], already formatted as a
 * ready-to-speak sentence.
 *
 * @param capabilityUsed the [com.becky.bridge.capability.Capability] id
 *   that was invoked to produce this response, or null if the request
 *   was answered conversationally (see [ConversationalEngine]) or not
 *   understood at all. Callers (UI, logs) can use this to show *why*
 *   BECKY said what it said without needing to know about capabilities
 *   themselves.
 */
data class BeckyAssistantResponse(
    val spokenText: String,
    val source: InteractionSource,
    val capabilityUsed: String? = null
)
