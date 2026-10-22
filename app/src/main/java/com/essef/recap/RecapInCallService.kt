package com.essef.recap

import android.app.KeyguardManager
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import java.util.Locale

class RecapInCallService : InCallService() {

    private var tts: TextToSpeech? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            CallHub.notifyChanged()
            when (state) {
                Call.STATE_ACTIVE -> maybeAutoRecord(call)
                Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                    FloatBubble.hide()
                    // Recording finalizes in the activity; if the activity is
                    // gone (bubble flow), it must be finalized here instead.
                    if (Recorder.isRecording && InCallActivity.instance == null) {
                        Recorder.stop(applicationContext)
                    }
                }
            }
            FloatBubble.refresh(applicationContext)
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        // The in-call UI drives its Speaker/Mute indicators from this real
        // state instead of its own optimistic booleans, so a route Telecom
        // rejected shows as rejected rather than pretending it worked.
        CallHub.audioState = audioState
        CallHub.notifyChanged()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallHub.service = this
        CallHub.call = call
        CallHub.consentSpoken = false
        call.registerCallback(callback)
        CallHub.notifyChanged()
        // Essef (Oct 22): an incoming call must not hijack a game or a
        // fullscreen app. When the floating bubble is available, it becomes
        // the ringing UI (answer / decline right on the bubble); the full
        // in-call screen opens only when he taps through. Locked or dark
        // screens keep the classic full-screen ringing UI.
        val pm = getSystemService(PowerManager::class.java)
        val km = getSystemService(KeyguardManager::class.java)
        val bubbleFirst = Prefs.get(this).floatBubble &&
            Settings.canDrawOverlays(this) &&
            pm?.isInteractive == true &&
            km?.isKeyguardLocked != true
        if (bubbleFirst) {
            FloatBubble.refresh(applicationContext)
        } else {
            launchInCallUi()
        }
    }

    private fun launchInCallUi() {
        val i = Intent(this, InCallActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(i)
    }

    /**
     * Auto-record lives here, not only in the activity: in the bubble flow the
     * in-call activity may never be created, and those calls still have to be
     * recorded. When the activity IS alive it starts the recording itself and
     * this no-ops on Recorder.isRecording.
     */
    private fun maybeAutoRecord(call: Call) {
        val prefs = Prefs.get(this)
        if (prefs.recMode != "auto" || Recorder.isRecording) return
        if (InCallActivity.instance != null) return // the activity handles it (has the UI + SIM label)
        val num = call.details?.handle?.schemeSpecificPart ?: ""
        val started = Recorder.start(
            applicationContext,
            Recorder.Meta(
                number = num,
                name = CallLogRepo.contactName(applicationContext, num),
                direction = if (call.details?.callDirection == Call.Details.DIRECTION_OUTGOING) 2 else 1,
                simLabel = ""
            ),
            starter = "service"
        )
        if (started.isNotEmpty() && prefs.consent && !CallHub.consentSpoken) {
            CallHub.consentSpoken = true
            tts = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    tts?.speak("This call is being recorded.", TextToSpeech.QUEUE_FLUSH, null, "consent")
                }
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        try { call.unregisterCallback(callback) } catch (e: Exception) { }
        if (CallHub.call == call) CallHub.call = null
        CallHub.audioState = null
        CallHub.consentSpoken = false
        FloatBubble.hide()
        CallHub.notifyChanged()
    }

    override fun onDestroy() {
        try { tts?.shutdown() } catch (e: Exception) { }
        super.onDestroy()
    }
}
