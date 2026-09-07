package com.becky.bridge.assistant

import com.becky.bridge.capability.CapabilityRegistry
import com.becky.bridge.logging.BeckyLogger
import com.becky.bridge.logging.LogCategory

/**
 * BECKY's "brain" entry point: turns natural language into a spoken
 * response, deciding on its own which
 * [com.becky.bridge.capability.Capability] (if any) it needs - callers
 * (the Voice layer, or a watch-initiated interaction) never name a
 * specific command, they only ever hand BECKY free text.
 *
 * ```
 * Voice -> Assistant/Becky (this interface) -> Command/Capability -> BLE/Repository
 * ```
 */
interface BeckyAssistant {
    suspend fun handle(request: BeckyAssistantRequest): BeckyAssistantResponse
}

/**
 * Default [BeckyAssistant]: resolves an [Intent] via [IntentResolver],
 * runs the matched capability through [CapabilityRegistry] if any, and
 * falls back to [ConversationalEngine] otherwise.
 *
 * Swapping [intentResolver] and/or [conversationalEngine] for
 * NLU/LLM-backed implementations is the intended way to grow BECKY into
 * a full conversational assistant later - this class itself does not
 * need to change, and neither do the capabilities it calls through
 * [capabilityRegistry].
 */
class DefaultBeckyAssistant(
    private val capabilityRegistry: CapabilityRegistry,
    private val intentResolver: IntentResolver,
    private val conversationalEngine: ConversationalEngine = NoOpConversationalEngine()
) : BeckyAssistant {

    override suspend fun handle(request: BeckyAssistantRequest): BeckyAssistantResponse {
        BeckyLogger.i(LogCategory.APP, "BeckyAssistant procesando (${request.source}): \"${request.text}\"")

        val intent = intentResolver.resolve(request.text, capabilityRegistry.descriptors())
        return when (intent) {
            is Intent.InvokeCapability -> {
                val result = capabilityRegistry.execute(intent.capabilityId)
                BeckyAssistantResponse(
                    spokenText = BeckyResponseFormatter.format(result),
                    source = request.source,
                    capabilityUsed = intent.capabilityId
                )
            }
            is Intent.Unrecognized -> {
                val conversational = conversationalEngine.converse(request)
                BeckyAssistantResponse(
                    spokenText = conversational ?: "No entendi esa peticion todavia.",
                    source = request.source,
                    capabilityUsed = null
                )
            }
        }
    }
}
