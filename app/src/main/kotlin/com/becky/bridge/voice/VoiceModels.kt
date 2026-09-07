package com.becky.bridge.voice

/**
 * BECKY BRIDGE - Voice layer (Phase 3).
 *
 * High-level state machine for the whole "Hey Becky" voice pipeline
 * (wake word -> listening -> processing -> speaking), shared by
 * [VoiceEngine] and any future UI that wants to reflect voice activity.
 *
 * This phase intentionally does NOT wire any of this into an Activity
 * yet (per spec): it only prepares the architecture so a later phase
 * can add a screen/indicator on top of [VoiceEngine.state] with zero
 * changes to the pipeline itself.
 *
 * Note: what happens to a recognized utterance (which capability it
 * maps to, if any, and how BECKY replies) is entirely owned by
 * [com.becky.bridge.assistant.BeckyAssistant] - see
 * [com.becky.bridge.assistant.BeckyAssistantResponse] for that outcome
 * type. [VoiceState] only tracks the mechanical state of this pipeline
 * (listening/speaking/etc.), not what was said or decided.
 */
sealed class VoiceState {
    /** Voice pipeline is not running (not listening for the wake word). */
    object Idle : VoiceState()

    /** [WakeWordDetector] is actively listening for "Hey Becky". */
    object WaitingForWakeWord : VoiceState()

    /** The wake word was just detected; about to start speech recognition. */
    object WakeWordDetected : VoiceState()

    /** [SpeechToTextEngine] is actively listening for a spoken command. */
    object Listening : VoiceState()

    /** A recognized utterance is being routed to [com.becky.bridge.assistant.BeckyAssistant]. */
    object Processing : VoiceState()

    /** BECKY is speaking [text] back to the user via [TextToSpeechEngine]. */
    data class Speaking(val text: String) : VoiceState()

    /** Something failed along the pipeline (STT error, TTS init failure, etc.). */
    data class Error(val message: String) : VoiceState()
}
