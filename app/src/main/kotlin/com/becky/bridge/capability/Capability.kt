package com.becky.bridge.capability

/**
 * BECKY BRIDGE - Command/Capability layer (Phase 3).
 *
 * Sits between the Assistant/Becky layer and the BLE/Repository layer:
 *
 * ```
 * Voice -> Assistant/Becky -> Command/Capability -> BLE/Repository
 * ```
 *
 * [com.becky.bridge.assistant.BeckyAssistant] never talks to
 * [com.becky.bridge.communication.BeckyCommandHandler] (or Bluetooth)
 * directly - it only ever asks a [CapabilityRegistry] to run a named
 * capability and gets back a [CapabilityResult]. This is what keeps
 * BECKY's conversation from being limited to a fixed set of BLE
 * commands: a [Capability] does not even have to be backed by the
 * watch (a future capability could set a phone-only reminder, tell a
 * joke, query a web API, etc.) - it only has to implement this
 * interface and get registered.
 */
interface Capability {

    /** Stable machine identifier, e.g. [com.becky.bridge.communication.BeckyCommands.GET_TIME]. */
    val id: String

    /**
     * Short human/LLM-readable description of what this capability
     * does. Exposed via [CapabilityRegistry.descriptors] so a future
     * NLU/LLM-backed [com.becky.bridge.assistant.IntentResolver] can be
     * given the exact list of things BECKY is currently able to do
     * (e.g. as a function-calling schema) instead of a hardcoded
     * keyword table.
     */
    val description: String

    /** Executes this capability and returns its outcome. */
    suspend fun execute(): CapabilityResult
}

/**
 * Outcome of running a single [Capability]. Deliberately
 * transport/command agnostic: it does not know or care whether the
 * capability was backed by BLE, a local sensor, or nothing at all.
 */
sealed class CapabilityResult {
    /** The capability ran successfully; [spokenText] is ready to hand to TTS as-is. */
    data class Success(val spokenText: String, val raw: Any? = null) : CapabilityResult()

    /** The capability could not run because its backing resource (e.g. the watch) is unavailable. */
    object NotAvailable : CapabilityResult()

    /** The capability was invoked but did not complete in time. */
    object Timeout : CapabilityResult()

    /** The capability failed for a reason described by [error]. */
    data class Failed(val error: String) : CapabilityResult()
}

/** Lightweight, serializable-friendly description of a [Capability] (id + description only). */
data class CapabilityDescriptor(val id: String, val description: String)
