package com.becky.bridge.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.becky.bridge.logging.BeckyLogger
import com.becky.bridge.logging.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Lets BECKY respond with spoken audio (Phase 3 of the spec).
 *
 * Implementations are expected to use Android's native
 * [android.speech.tts.TextToSpeech] engine where possible - no
 * third-party SDK, no extra Gradle dependency.
 */
interface TextToSpeechEngine {
    /** True while audio is actively being played back. */
    val isSpeaking: StateFlow<Boolean>

    /** Speaks [text] out loud, interrupting anything currently being spoken. */
    fun speak(text: String)

    /** Stops any speech currently in progress. */
    fun stop()

    /** Releases the underlying TTS engine. Call when the engine is no longer needed. */
    fun shutdown()
}

/**
 * [TextToSpeechEngine] backed by the native Android
 * [android.speech.tts.TextToSpeech] API - no extra dependency.
 */
class AndroidTextToSpeechEngine(
    context: Context,
    private val locale: Locale = Locale("es", "ES")
) : TextToSpeechEngine {

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var ready = false

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            engineLocaleOrDefault()
        } else {
            BeckyLogger.w(LogCategory.APP, "No se pudo inicializar Text-to-Speech (status=$status)")
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            @Deprecated("Deprecated in Java", ReplaceWith(""))
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
            }
        })
    }

    private fun engineLocaleOrDefault() {
        val result = tts.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            BeckyLogger.w(LogCategory.APP, "Idioma $locale no soportado por TTS, se usara el idioma por defecto")
        }
    }

    override fun speak(text: String) {
        if (!ready) {
            BeckyLogger.w(LogCategory.APP, "TTS solicitado antes de estar listo: \"$text\"")
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    override fun stop() {
        tts.stop()
        _isSpeaking.value = false
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
