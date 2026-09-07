package com.becky.bridge.ui.voice

import android.Manifest
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.becky.bridge.R
import com.becky.bridge.databinding.ActivitySimpleBinding
import com.becky.bridge.logging.BeckyLogger
import com.becky.bridge.logging.LogCategory
import com.becky.bridge.voice.VoicePermissions
import com.becky.bridge.voice.VoiceRepository
import com.becky.bridge.voice.VoiceState
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * "Hablar con BECKY" screen (Phase 3).
 *
 * This Activity only talks to [VoiceRepository.voiceEngine] - it has no
 * idea whether a recognized utterance ends up hitting a BLE watch
 * command or a future conversational engine, and it never touches
 * Bluetooth/GATT classes directly. It requests the
 * [VoicePermissions.RECORD_AUDIO] runtime permission (declared in the
 * manifest since the previous phase but never requested until now),
 * then lets the user tap a single button to start listening and shows
 * [VoiceState] plus BECKY's last spoken response.
 */
class VoiceActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySimpleBinding
    private lateinit var voiceRepository: VoiceRepository

    private lateinit var stateText: TextView
    private lateinit var responseText: TextView
    private lateinit var talkButton: MaterialButton

    private val requestRecordAudioLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                BeckyLogger.i(LogCategory.APP, "Permiso RECORD_AUDIO concedido")
                startTalkingToBecky()
            } else {
                BeckyLogger.w(LogCategory.APP, "Permiso RECORD_AUDIO denegado por el usuario")
                responseText.text = getString(R.string.voice_permission_denied)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        voiceRepository = VoiceRepository.getInstance(applicationContext)
        binding.screenTitle.text = getString(R.string.voice_title)
        binding.recyclerView.visibility = android.view.View.GONE
        binding.emptyText.visibility = android.view.View.GONE

        setupVoicePanel()
        observeVoiceEngine()
    }

    private fun setupVoicePanel() {
        val root = binding.root as LinearLayout

        val subtitle = TextView(this).apply {
            text = getString(R.string.voice_subtitle)
            setTextColor(0xFF8B949E.toInt())
            textSize = 13f
            setPadding(0, 4, 0, 20)
        }

        stateText = TextView(this).apply {
            text = getString(R.string.voice_state_idle)
            setTextColor(0xFFE6EDF3.toInt())
            textSize = 14f
            gravity = Gravity.START
            setPadding(0, 0, 0, 12)
        }

        talkButton = MaterialButton(this).apply {
            text = getString(R.string.voice_btn_talk)
            setOnClickListener { onTalkButtonClicked() }
        }

        responseText = TextView(this).apply {
            text = ""
            setTextColor(0xFF3FB950.toInt())
            textSize = 15f
            gravity = Gravity.START
            setPadding(0, 20, 0, 0)
        }

        root.addView(subtitle, 1)
        root.addView(stateText, 2)
        root.addView(talkButton, 3)
        root.addView(responseText, 4)
    }

    private fun onTalkButtonClicked() {
        if (VoicePermissions.hasRecordAudioPermission(this)) {
            startTalkingToBecky()
        } else {
            requestRecordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startTalkingToBecky() {
        responseText.text = ""
        voiceRepository.voiceEngine.start()
        voiceRepository.voiceEngine.listenNow()
    }

    private fun observeVoiceEngine() {
        voiceRepository.voiceEngine.state
            .onEach { state -> stateText.text = describeState(state) }
            .launchIn(lifecycleScope)

        voiceRepository.voiceEngine.lastResponse
            .onEach { response ->
                if (response != null) {
                    responseText.text = response.spokenText
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun describeState(state: VoiceState): String = when (state) {
        VoiceState.Idle -> getString(R.string.voice_state_idle)
        VoiceState.WaitingForWakeWord -> getString(R.string.voice_state_waiting_wakeword)
        VoiceState.WakeWordDetected -> getString(R.string.voice_state_listening)
        VoiceState.Listening -> getString(R.string.voice_state_listening)
        VoiceState.Processing -> getString(R.string.voice_state_processing)
        is VoiceState.Speaking -> getString(R.string.voice_state_speaking)
        is VoiceState.Error -> getString(R.string.voice_state_error, state.message)
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceRepository.voiceEngine.stop()
    }
}
