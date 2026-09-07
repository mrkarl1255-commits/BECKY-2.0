package com.becky.bridge.voice

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Detects the "Hey Becky" wake word locally on the phone, without
 * streaming audio off-device (Phase 3 of the spec).
 *
 * This is intentionally an interface: real always-on wake-word
 * detection needs a small on-device keyword-spotting engine (e.g.
 * Picovoice Porcupine, Vosk, or a custom TFLite model). Picking and
 * bundling one of those is a deliberate dependency decision left for a
 * later phase, so this phase does not pull in a heavy library just to
 * prepare the architecture ("no agregar dependencias pesadas
 * innecesarias"). Any such engine plugs in here by implementing this
 * same interface - nothing else in the voice pipeline ([VoiceEngine],
 * [SpeechToTextEngine], [TextToSpeechEngine], [VoiceCommandRouter])
 * needs to change when that happens.
 */
interface WakeWordDetector {
    /** True while the detector is actively "listening" for the wake word. */
    val isListening: StateFlow<Boolean>

    /** Emits once every time the wake word is detected. */
    val wakeWordDetected: SharedFlow<Unit>

    /** Starts local wake-word detection. Safe to call if already started. */
    fun start()

    /** Stops local wake-word detection. Safe to call if already stopped. */
    fun stop()
}

/**
 * Placeholder [WakeWordDetector] that prepares the "Hey Becky"
 * architecture without embedding a real keyword-spotting engine yet
 * (see the interface doc for why). It never triggers on its own;
 * instead it exposes [simulateWakeWord] so the rest of the pipeline
 * (Speech-to-Text -> command routing -> Text-to-Speech) can already be
 * wired and tested end to end today, and later swapped to a real
 * always-on offline detector with a single-line change in
 * [VoiceRepository] - no other class needs to change.
 */
class PlaceholderWakeWordDetector : WakeWordDetector {

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _wakeWordDetected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val wakeWordDetected: SharedFlow<Unit> = _wakeWordDetected.asSharedFlow()

    override fun start() {
        _isListening.value = true
    }

    override fun stop() {
        _isListening.value = false
    }

    /**
     * Manually raises a wake-word event, as if "Hey Becky" had just been
     * heard. Used by tests, and by a future manual "Hablar con BECKY"
     * trigger, until a real local wake-word engine is wired in here.
     */
    fun simulateWakeWord() {
        if (_isListening.value) {
            _wakeWordDetected.tryEmit(Unit)
        }
    }
}
