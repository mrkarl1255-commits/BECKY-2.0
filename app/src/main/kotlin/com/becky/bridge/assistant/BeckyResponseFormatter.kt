package com.becky.bridge.assistant

import com.becky.bridge.capability.CapabilityResult

/**
 * Turns a [CapabilityResult] (Command/Capability layer) into a
 * spoken-ready Spanish sentence for the Assistant/Becky layer. Kept
 * separate from [BeckyAssistant] so the wording can evolve
 * independently of the intent-resolution/capability-execution logic.
 */
object BeckyResponseFormatter {
    fun format(result: CapabilityResult): String = when (result) {
        is CapabilityResult.Success -> result.spokenText
        CapabilityResult.NotAvailable -> "No tengo conexion con el reloj en este momento."
        CapabilityResult.Timeout -> "El reloj no respondio a tiempo."
        is CapabilityResult.Failed -> "Hubo un error: ${result.error}"
    }
}
