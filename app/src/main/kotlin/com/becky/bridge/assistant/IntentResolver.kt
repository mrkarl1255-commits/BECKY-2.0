package com.becky.bridge.assistant

import com.becky.bridge.capability.CapabilityDescriptor
import com.becky.bridge.communication.BeckyCommands
import java.util.Locale

/** What BECKY should do about a piece of natural language. */
sealed class Intent {
    /** [text] matched a known capability; run it. */
    data class InvokeCapability(val capabilityId: String) : Intent()

    /** [text] did not match any known capability - defer to [ConversationalEngine]. */
    data class Unrecognized(val text: String) : Intent()
}

/**
 * Decides which [com.becky.bridge.capability.Capability] (if any) a
 * piece of free-form natural language is asking for.
 *
 * This is THE single seam meant to be replaced by a real NLU/LLM engine
 * later: [availableCapabilities] is already shaped like a
 * function-calling schema (id + human description) that a language
 * model could choose from directly. Swapping [KeywordIntentResolver]
 * for e.g. an on-device or cloud LLM-backed resolver requires touching
 * only this one class - [BeckyAssistant], [com.becky.bridge.voice.VoiceEngine]
 * and every [com.becky.bridge.capability.Capability] stay exactly as
 * they are.
 */
interface IntentResolver {
    suspend fun resolve(text: String, availableCapabilities: List<CapabilityDescriptor>): Intent
}

/**
 * Default [IntentResolver]: a small, fixed Spanish keyword table. This
 * is deliberately NOT an NLU/AI model (explicitly out of scope for this
 * phase) - it is the thinnest possible implementation that proves the
 * Voice -> Assistant -> Capability -> BLE pipeline end to end today,
 * ready to be swapped out later.
 */
class KeywordIntentResolver : IntentResolver {

    override suspend fun resolve(text: String, availableCapabilities: List<CapabilityDescriptor>): Intent {
        val normalized = text.lowercase(Locale.getDefault())
        val availableIds = availableCapabilities.map { it.id }.toSet()

        val matchedId = KEYWORDS_BY_CAPABILITY.entries
            .firstOrNull { (capabilityId, keywords) ->
                capabilityId in availableIds && keywords.any { normalized.contains(it) }
            }
            ?.key

        return matchedId?.let { Intent.InvokeCapability(it) } ?: Intent.Unrecognized(text)
    }

    companion object {
        private val KEYWORDS_BY_CAPABILITY: Map<String, List<String>> = mapOf(
            BeckyCommands.GET_TIME to listOf("hora", "que hora es", "qué hora es"),
            BeckyCommands.GET_HEART_RATE to listOf(
                "pulso", "ritmo cardiaco", "ritmo cardíaco", "frecuencia cardiaca", "corazon", "corazón"
            ),
            BeckyCommands.GET_STEPS to listOf("pasos", "cuantos pasos", "cuántos pasos"),
            BeckyCommands.GET_BATTERY to listOf("bateria", "batería", "carga del reloj"),
            BeckyCommands.GET_NOTIFICATIONS to listOf("notificacion", "notificación", "notificaciones"),
            BeckyCommands.GET_WATCH_STATUS to listOf(
                "estado del reloj", "como esta el reloj", "cómo está el reloj"
            )
        )
    }
}
