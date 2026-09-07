package com.becky.bridge.voice

import com.becky.bridge.assistant.BeckyAssistant
import com.becky.bridge.assistant.BeckyAssistantRequest
import com.becky.bridge.assistant.BeckyAssistantResponse
import com.becky.bridge.assistant.InteractionSource
import com.becky.bridge.logging.BeckyLogger
import com.becky.bridge.logging.LogCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Coordinates the "Hey Becky" voice pipeline on the phone (Phase 3):
 *
 * ```
 * WakeWordDetector -> SpeechToTextEngine -> BeckyAssistant (Assistant/Becky layer)
 *   -> TextToSpeechEngine
 * ```
 *
 * Every stage is an injected interface so each one (wake-word engine,
 * STT provider, [BeckyAssistant] implementation, TTS voice) can be
 * swapped independently - including, later, running an equivalent
 * pipeline ON the Wear OS watch itself instead of the phone (see the
 * class doc on [VoiceRepository]) without this orchestration logic
 * changing.
 *
 * [VoiceEngine] itself has NO notion of "commands": it only ever hands
 * free-form recognized text to [BeckyAssistant], which alone decides
 * whether/which [com.becky.bridge.capability.Capability] to run. This
 * is what keeps voice interaction from being limited to a fixed set of
 * BLE commands.
 */
interface VoiceEngine {
    val state: StateFlow<VoiceState>
    val lastResponse: StateFlow<BeckyAssistantResponse?>

    /** Starts wake-word listening. Call once, e.g. from a foreground service or activity. */
    fun start()

    /** Stops the whole pipeline (wake word + any in-flight listening/speaking). */
    fun stop()

    /**
     * Skips wake-word detection and starts listening for a command right
     * away. Useful for a future manual "hold to talk" UI trigger, and for
     * testing the pipeline before a real wake-word engine is wired in
     * (see [PlaceholderWakeWordDetector]).
     */
    fun listenNow()
}

/**
 * Default [VoiceEngine] implementation wiring the voice stages and
 * [BeckyAssistant] together via Kotlin Flows, matching the reactive
 * style already used by [com.becky.bridge.bluetooth.BridgeRepository].
 *
 * @param scope long-lived coroutine scope (tied to [VoiceRepository],
 *   not a short-lived UI scope) used to collect flows from the injected
 *   engines and to run [BeckyAssistant.handle] in the background.
 */
class DefaultVoiceEngine(
    private val wakeWordDetector: WakeWordDetector,
    private val speechToTextEngine: SpeechToTextEngine,
    private val textToSpeechEngine: TextToSpeechEngine,
    private val beckyAssistant: BeckyAssistant,
    private val scope: CoroutineScope
) : VoiceEngine {

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    override val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _lastResponse = MutableStateFlow<BeckyAssistantResponse?>(null)
    override val lastResponse: StateFlow<BeckyAssistantResponse?> = _lastResponse.asStateFlow()

    init {
        wakeWordDetector.wakeWordDetected
            .onEach { onWakeWordDetected() }
            .launchIn(scope)

        speechToTextEngine.recognizedText
            .onEach { text -> onSpeechRecognized(text) }
            .launchIn(scope)

        speechToTextEngine.error
            .onEach { message ->
                BeckyLogger.w(LogCategory.APP, "VoiceEngine: error de STT: $message")
                _state.value = VoiceState.Error(message)
                wakeWordDetector.start()
            }
            .launchIn(scope)
    }

    override fun start() {
        BeckyLogger.i(LogCategory.APP, "VoiceEngine iniciado, esperando 'Hey Becky'")
        _state.value = VoiceState.WaitingForWakeWord
        wakeWordDetector.start()
    }

    override fun stop() {
        wakeWordDetector.stop()
        speechToTextEngine.stopListening()
        textToSpeechEngine.stop()
        _state.value = VoiceState.Idle
    }

    override fun listenNow() {
        onWakeWordDetected()
    }

    private fun onWakeWordDetected() {
        BeckyLogger.i(LogCategory.APP, "Wake word 'Hey Becky' detectada")
        _state.value = VoiceState.WakeWordDetected
        wakeWordDetector.stop()
        _state.value = VoiceState.Listening
        speechToTextEngine.startListening()
    }

    private fun onSpeechRecognized(text: String) {
        _state.value = VoiceState.Processing
        scope.launch {
            val response = beckyAssistant.handle(
                BeckyAssistantRequest(text = text, source = InteractionSource.PHONE)
            )
            _lastResponse.value = response
            speak(response.spokenText)
        }
    }

    private fun speak(text: String) {
        _state.value = VoiceState.Speaking(text)
        textToSpeechEngine.speak(text)
        // Best-effort: go back to waiting for the wake word right after
        // requesting speech. Precise "still speaking" status remains
        // available via textToSpeechEngine.isSpeaking for any future UI
        // that needs it; the pipeline itself does not need to block on
        // playback completion to listen for the next "Hey Becky".
        _state.value = VoiceState.WaitingForWakeWord
        wakeWordDetector.start()
    }
}
