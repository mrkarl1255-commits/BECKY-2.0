package com.becky.bridge.assistant

/**
 * Free-form conversational fallback used by [BeckyAssistant] whenever
 * [IntentResolver] does not match any known
 * [com.becky.bridge.capability.Capability].
 *
 * This is the explicit seam for BECKY's future conversational engine
 * (an on-device or cloud LLM / conversational AI): implement this
 * interface with a real model and wire it into
 * [com.becky.bridge.voice.VoiceRepository] instead of
 * [NoOpConversationalEngine] - nothing else in the
 * Voice/Assistant/Capability/BLE layers needs to change.
 */
interface ConversationalEngine {
    /**
     * Attempts a free-form reply to [request]. Returns null if this
     * engine has nothing to say, letting [BeckyAssistant] fall back to
     * its own generic "I didn't understand" message.
     */
    suspend fun converse(request: BeckyAssistantRequest): String?
}

/**
 * No-op [ConversationalEngine]: always defers to [BeckyAssistant]'s
 * generic fallback message. Deliberately does not embed any AI/NLU
 * model - that is explicitly out of scope for this phase.
 */
class NoOpConversationalEngine : ConversationalEngine {
    override suspend fun converse(request: BeckyAssistantRequest): String? = null
}
