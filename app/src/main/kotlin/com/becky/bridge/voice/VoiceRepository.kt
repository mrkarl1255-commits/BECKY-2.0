package com.becky.bridge.voice

import android.content.Context
import com.becky.bridge.assistant.BeckyAssistant
import com.becky.bridge.assistant.BeckyAssistantRequest
import com.becky.bridge.assistant.DefaultBeckyAssistant
import com.becky.bridge.assistant.InteractionSource
import com.becky.bridge.assistant.KeywordIntentResolver
import com.becky.bridge.bluetooth.BridgeRepository
import com.becky.bridge.capability.DefaultCapabilityRegistry
import com.becky.bridge.capability.GetBatteryCapability
import com.becky.bridge.capability.GetHeartRateCapability
import com.becky.bridge.capability.GetNotificationsCapability
import com.becky.bridge.capability.GetStepsCapability
import com.becky.bridge.capability.GetTimeCapability
import com.becky.bridge.capability.GetWatchStatusCapability
import com.becky.bridge.logging.BeckyLogger
import com.becky.bridge.logging.LogCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Wires the full voice + assistant + capability stack together and
 * exposes a single [VoiceEngine] to the rest of the app, mirroring how
 * [BridgeRepository] wires the Bluetooth/BLE stack (section 15) - but
 * for BECKY's conversational layer (Phase 3):
 *
 * ```
 * Voice -> Assistant/Becky -> Command/Capability -> BLE/Repository
 * ```
 *
 * On purpose this class does NOT modify [BridgeRepository]: it only
 * reads its already-public [BridgeRepository.commandHandler] to build
 * the six watch [com.becky.bridge.capability.Capability]s and to listen
 * for watch-initiated interactions, keeping the voice/assistant layer
 * and the BLE layer fully decoupled. Either one can be built, tested or
 * replaced without touching the other - [com.becky.bridge.bluetooth.BleScanner],
 * [com.becky.bridge.bluetooth.GattManager],
 * [com.becky.bridge.bluetooth.ReconnectionManager] and
 * [com.becky.bridge.communication.BeckyCommandHandler] are all
 * unaffected by this class existing.
 *
 * **Watch-initiated interactions**: [BridgeRepository.commandHandler]'s
 * `incomingWatchRequests` flow carries any message the watch sends
 * WITHOUT being asked (e.g. the user pressed a button or spoke into a
 * microphone on the watch). This repository forwards that text straight
 * into the same [BeckyAssistant] used by phone voice input, tagged
 * [InteractionSource.WATCH], and speaks BECKY's answer out loud through
 * the phone's [TextToSpeechEngine] - proving the same "brain" answers
 * both the phone and the watch. A future Wear OS surface could instead
 * (or additionally) send that same [com.becky.bridge.assistant.BeckyAssistantResponse]
 * back to the watch over BLE for it to display/speak locally; only this
 * repository's forwarding logic would need to change, not
 * [BeckyAssistant] or any [com.becky.bridge.capability.Capability].
 *
 * Nothing calls [VoiceEngine.start] yet from any Activity/Service beyond
 * `VoiceActivity`'s own lifecycle: this phase only prepares the
 * architecture end to end (wake word -> STT -> BeckyAssistant -> TTS,
 * plus the watch-initiated path), ready for a future screen or
 * foreground service to turn it on more broadly.
 *
 * **Future Wear OS extension**: a `WearVoiceRepository` running on the
 * watch could implement the very same [VoiceEngine] / [WakeWordDetector]
 * / [SpeechToTextEngine] / [TextToSpeechEngine] interfaces using Wear
 * OS's own microphone/speaker, routing recognized text either to a
 * watch-local [BeckyAssistant] or back to the phone over the existing
 * BLE bridge. Because every stage here is an interface, "Hey Becky"
 * could eventually be triggered from either the phone or the wrist
 * without changing this orchestration design.
 */
class VoiceRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val voiceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val bridgeRepository = BridgeRepository.getInstance(appContext)

    /**
     * The six BLE watch commands (Phase 2), each exposed as a
     * [com.becky.bridge.capability.Capability]. These are the FIRST
     * capabilities BECKY has, not the only ones it will ever have:
     * future non-BLE capabilities register here the same way, and
     * [beckyAssistant] does not care which kind it is running.
     */
    private val capabilityRegistry = DefaultCapabilityRegistry(
        listOf(
            GetTimeCapability(bridgeRepository.commandHandler),
            GetHeartRateCapability(bridgeRepository.commandHandler),
            GetStepsCapability(bridgeRepository.commandHandler),
            GetBatteryCapability(bridgeRepository.commandHandler),
            GetNotificationsCapability(bridgeRepository.commandHandler),
            GetWatchStatusCapability(bridgeRepository.commandHandler)
        )
    )

    /**
     * BECKY's single "brain" instance, shared by both the phone voice
     * pipeline ([voiceEngine]) and any watch-initiated interaction (see
     * [observeWatchInitiatedInteractions]). Uses [KeywordIntentResolver]
     * today; replace with an NLU/LLM-backed [com.becky.bridge.assistant.IntentResolver]
     * (and/or a real [com.becky.bridge.assistant.ConversationalEngine])
     * here, and nothing else in the app needs to change.
     */
    val beckyAssistant: BeckyAssistant = DefaultBeckyAssistant(
        capabilityRegistry = capabilityRegistry,
        intentResolver = KeywordIntentResolver()
    )

    val wakeWordDetector: WakeWordDetector = PlaceholderWakeWordDetector()
    val speechToTextEngine: SpeechToTextEngine = AndroidSpeechToTextEngine(appContext)
    val textToSpeechEngine: TextToSpeechEngine = AndroidTextToSpeechEngine(appContext)

    val voiceEngine: VoiceEngine = DefaultVoiceEngine(
        wakeWordDetector = wakeWordDetector,
        speechToTextEngine = speechToTextEngine,
        textToSpeechEngine = textToSpeechEngine,
        beckyAssistant = beckyAssistant,
        scope = voiceScope
    )

    init {
        observeWatchInitiatedInteractions()
    }

    /**
     * Lets the watch start a conversation with BECKY: any spontaneous
     * (non-correlated) [com.becky.bridge.model.BridgeMessage] the watch
     * sends is treated as natural language and routed through the same
     * [beckyAssistant] used by phone voice input, then spoken back via
     * [textToSpeechEngine]. See [com.becky.bridge.communication.BeckyCommandHandler.incomingWatchRequests].
     */
    private fun observeWatchInitiatedInteractions() {
        bridgeRepository.commandHandler.incomingWatchRequests
            .onEach { message ->
                val text = message.payload?.takeIf { it.isNotBlank() } ?: return@onEach
                BeckyLogger.i(LogCategory.APP, "Interaccion iniciada por el reloj: \"$text\" (${message.type})")
                voiceScope.launch {
                    val response = beckyAssistant.handle(
                        BeckyAssistantRequest(text = text, source = InteractionSource.WATCH)
                    )
                    textToSpeechEngine.speak(response.spokenText)
                    // Future extension point: also send `response` back to
                    // the watch over BLE (e.g. via bridgeRepository.commandHandler
                    // .sendCommand(...) with type = MessageType.RESPONSE) so the
                    // watch itself can display/speak it - left as a follow-up
                    // since Phase 3 focuses on the phone as BECKY's voice.
                }
            }
            .launchIn(voiceScope)
    }

    companion object {
        @Volatile
        private var instance: VoiceRepository? = null

        fun getInstance(context: Context): VoiceRepository {
            return instance ?: synchronized(this) {
                instance ?: VoiceRepository(context).also { instance = it }
            }
        }
    }
}
