package com.becky.bridge.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.becky.bridge.logging.BeckyLogger
import com.becky.bridge.logging.LogCategory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Converts spoken audio into text (Phase 3 of the spec).
 *
 * Implementations are expected to use Android's native speech
 * recognition APIs where possible - no third-party SDK, no extra
 * Gradle dependency - per the "APIs nativas de Android cuando sea
 * posible" requirement.
 */
interface SpeechToTextEngine {
    /** True while actively listening for a spoken utterance. */
    val isListening: StateFlow<Boolean>

    /** Emits the best-guess recognized text once a listening session finishes successfully. */
    val recognizedText: SharedFlow<String>

    /** Emits a human-readable error message if recognition fails or is unavailable. */
    val error: SharedFlow<String>

    /** Starts listening for a single spoken command. */
    fun startListening()

    /** Cancels an in-progress listening session, if any. */
    fun stopListening()

    /** Releases the underlying recognizer. Call when the engine is no longer needed. */
    fun destroy()
}

/**
 * [SpeechToTextEngine] backed by [android.speech.SpeechRecognizer], the
 * native Android speech recognition API (uses the on-device Google app
 * / any installed recognition service - no extra dependency).
 *
 * Requires [android.Manifest.permission.RECORD_AUDIO] to be granted at
 * runtime before [startListening] is called (see [VoicePermissions]);
 * this class does not request permissions itself, matching the same
 * separation of concerns used by [com.becky.bridge.bluetooth.PermissionsHelper]
 * for Bluetooth.
 */
class AndroidSpeechToTextEngine(
    private val context: Context,
    private val locale: Locale = Locale("es", "ES")
) : SpeechToTextEngine {

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _recognizedText = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val recognizedText: SharedFlow<String> = _recognizedText.asSharedFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val error: SharedFlow<String> = _error.asSharedFlow()

    private var recognizer: SpeechRecognizer? = null

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _isListening.value = true
        }

        override fun onResults(results: Bundle?) {
            _isListening.value = false
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                BeckyLogger.i(LogCategory.APP, "STT reconocio: \"$text\"")
                _recognizedText.tryEmit(text)
            } else {
                _error.tryEmit("No se reconocio ningun texto")
            }
        }

        override fun onError(errorCode: Int) {
            _isListening.value = false
            val message = "Error de reconocimiento de voz (codigo $errorCode)"
            BeckyLogger.w(LogCategory.APP, message)
            _error.tryEmit(message)
        }

        override fun onEndOfSpeech() {
            _isListening.value = false
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _error.tryEmit("Reconocimiento de voz no disponible en este dispositivo")
            return
        }
        val active = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        active.startListening(intent)
    }

    override fun stopListening() {
        recognizer?.stopListening()
        _isListening.value = false
    }

    override fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}
