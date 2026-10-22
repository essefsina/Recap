package com.essef.recap

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class InCallActivity : Activity() {

    companion object {
        @Volatile var instance: InCallActivity? = null
        /** True while the in-call screen is in front of the user (bubble hides then). */
        @Volatile var uiVisible: Boolean = false
    }

    private lateinit var th: ThemeColors
    private lateinit var root: LinearLayout
    private lateinit var nameTv: TextView
    private lateinit var subTv: TextView
    private lateinit var simChip: LinearLayout
    private lateinit var simChipTv: TextView
    private lateinit var recPill: TextView
    private lateinit var consentChip: TextView
    private lateinit var controlsGrid: GridLayout
    private lateinit var askBar: LinearLayout
    private lateinit var bottomArea: LinearLayout
    private var dtmfOverlay: FrameLayout? = null
    private var tts: TextToSpeech? = null
    private var askedThisCall = false
    private var callConnectedSince = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var finished = false

    private var muted = false
    private var speaker = false

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        th = Themes.get(this)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        CallHub.listener = { runOnUiThread { refresh() } }
        handler.post(tick)
        if (CallHub.call == null) finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        handler.removeCallbacks(tick)
        tts?.shutdown()
    }

    override fun onStart() {
        super.onStart()
        uiVisible = true
        FloatBubble.hide()
        // The service may have started this call's recording from the
        // background; if it has only captured silence, restart the capture
        // from this foreground screen (see Recorder.restartIfSilent).
        handler.postDelayed({
            if (!finished && Recorder.isRecording) Recorder.restartIfSilent(this, "activity")
        }, 700)
    }

    override fun onStop() {
        super.onStop()
        uiVisible = false
        // Left the call screen mid-call (game, fullscreen app): bubble takes over.
        FloatBubble.refresh(applicationContext)
    }

    private fun number(): String {
        val h = CallHub.call?.details?.handle
        return h?.schemeSpecificPart ?: ""
    }

    private fun displayName(): String {
        val num = number()
        val n = CallLogRepo.contactName(this, num)
        return n.ifBlank { num.ifBlank { "Unknown" } }
    }

    private fun simLabel(): String {
        val h = CallHub.call?.details?.accountHandle ?: return ""
        return CallLogRepo.simLabelFor(this, h.id)
    }

    private fun direction(): Int {
        val d = CallHub.call?.details?.callDirection
        return if (d == Call.Details.DIRECTION_OUTGOING) 2 else 1
    }

    private fun buildUi() {
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(th.bg)
        root.setPadding(dp(22), dp(30), dp(22), dp(28))

        val avatar = avatarView(this, initialsOf(displayName(), number()), th.container, th.primary, 104, 34f)
        val avWrap = LinearLayout(this)
        avWrap.gravity = Gravity.CENTER
        avWrap.addView(avatar)
        root.addView(avWrap)

        nameTv = text(this, displayName(), 27f, th.onSurface, bold = true)
        nameTv.gravity = Gravity.CENTER
        val nLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        nLp.topMargin = dp(18)
        nameTv.layoutParams = nLp
        root.addView(nameTv)

        subTv = text(this, "", 15f, th.muted)
        subTv.gravity = Gravity.CENTER
        val sLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        sLp.topMargin = dp(4)
        subTv.layoutParams = sLp
        root.addView(subTv)

        // SIM chip: which SIM this call is on (Essef, Oct 21) — icon + label,
        // only on dual-SIM phones.
        simChip = LinearLayout(this)
        simChip.orientation = LinearLayout.HORIZONTAL
        simChip.gravity = Gravity.CENTER
        simChip.background = rounded(th.softContainer, 999, this)
        simChip.setPadding(dp(11), dp(5), dp(13), dp(5))
        val simIv = ImageView(this)
        simIv.setImageResource(R.drawable.ic_sim)
        simIv.setColorFilter(th.primary)
        simIv.layoutParams = LinearLayout.LayoutParams(dp(15), dp(15))
        simChip.addView(simIv)
        simChipTv = text(this, "", 12.5f, th.onSurface, bold = true)
        val simTLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        simTLp.leftMargin = dp(6)
        simChipTv.layoutParams = simTLp
        simChip.addView(simChipTv)
        val simWrap = LinearLayout(this)
        simWrap.gravity = Gravity.CENTER
        val simLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        simLp.topMargin = dp(10)
        simWrap.layoutParams = simLp
        simWrap.addView(simChip)
        simChip.visibility = View.GONE
        root.addView(simWrap)

        recPill = chip(this, "●  REC · Recording this call", Color.parseColor("#FDECEA"), Color.parseColor("#C03428"), th)
        recPill.textSize = 13f
        val rWrap = LinearLayout(this); rWrap.gravity = Gravity.CENTER
        val rLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rLp.topMargin = dp(16); recPill.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rWrap.layoutParams = rLp
        rWrap.addView(recPill)
        recPill.visibility = View.GONE
        root.addView(rWrap)

        // waveform strip (visual parity with the approved design)
        val wave = LinearLayout(this)
        wave.orientation = LinearLayout.HORIZONTAL
        wave.gravity = Gravity.CENTER
        val heights = intArrayOf(10, 20, 30, 16, 34, 24, 12, 28, 36, 18, 26, 10, 22, 32, 14, 24)
        for (h in heights) {
            val bar = View(this)
            bar.background = rounded(th.primary, 4, this)
            val lp = LinearLayout.LayoutParams(dp(4), dp(h))
            lp.leftMargin = dp(2); lp.rightMargin = dp(2)
            bar.layoutParams = lp
            bar.alpha = 0.75f
            wave.addView(bar)
        }
        val wLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40))
        wLp.topMargin = dp(14)
        wave.layoutParams = wLp
        root.addView(wave)

        consentChip = text(this, "", 13f, Color.parseColor("#7A5410"), bold = true)
        consentChip.gravity = Gravity.CENTER
        consentChip.background = rounded(Color.parseColor("#FFF6E5"), 16, this)
        consentChip.setPadding(dp(14), dp(10), dp(14), dp(10))
        val cLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        cLp.topMargin = dp(8); cLp.leftMargin = dp(8); cLp.rightMargin = dp(8)
        consentChip.layoutParams = cLp
        consentChip.visibility = View.GONE
        root.addView(consentChip)

        val aiChip = text(this, "AI title & summary will be ready after the call", 13f, th.primary, bold = true)
        aiChip.gravity = Gravity.CENTER
        val aLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        aLp.topMargin = dp(12)
        aiChip.layoutParams = aLp
        root.addView(aiChip)

        askBar = LinearLayout(this)
        askBar.orientation = LinearLayout.HORIZONTAL
        askBar.gravity = Gravity.CENTER_VERTICAL
        askBar.background = rounded(th.surface, 18, this)
        askBar.setPadding(dp(14), dp(10), dp(10), dp(10))
        val askLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        askLp.topMargin = dp(14)
        askBar.layoutParams = askLp
        val askText = text(this, "Record this call?", 14.5f, th.onSurface, bold = true)
        askBar.addView(askText)
        askBar.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        val yes = text(this, "Start", 14f, Color.WHITE, bold = true)
        yes.background = rounded(th.primary, 999, this)
        yes.setPadding(dp(16), dp(8), dp(16), dp(8))
        yes.setOnClickListener { askedThisCall = true; askBar.visibility = View.GONE; startRecording() }
        askBar.addView(yes)
        val no = text(this, "No", 14f, th.muted, bold = true)
        no.setPadding(dp(14), dp(8), dp(10), dp(8))
        no.setOnClickListener { askedThisCall = true; askBar.visibility = View.GONE }
        askBar.addView(no)
        askBar.visibility = View.GONE
        root.addView(askBar)

        controlsGrid = GridLayout(this)
        controlsGrid.columnCount = 3
        val gLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        gLp.topMargin = dp(22); gLp.leftMargin = dp(18); gLp.rightMargin = dp(18)
        controlsGrid.layoutParams = gLp
        root.addView(controlsGrid)

        bottomArea = LinearLayout(this)
        bottomArea.orientation = LinearLayout.HORIZONTAL
        bottomArea.gravity = Gravity.CENTER
        val bLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        bLp.topMargin = dp(26)
        bottomArea.layoutParams = bLp
        root.addView(bottomArea)

        val frame = FrameLayout(this)
        frame.addView(root)
        setContentView(frame)
        buildControls()
    }

    private fun ctlCell(iconRes: Int, label: String, active: Boolean, danger: Boolean = false, onClick: () -> Unit): LinearLayout {
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.VERTICAL
        cell.gravity = Gravity.CENTER
        val lp = GridLayout.LayoutParams()
        lp.width = 0; lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, GridLayout.FILL, 1f)
        lp.setMargins(dp(6), dp(8), dp(6), dp(8))
        cell.layoutParams = lp
        val btn = FrameLayout(this)
        val bg = when {
            danger -> Color.parseColor("#FDECEA")
            active -> th.container
            else -> th.softContainer
        }
        btn.background = circleBg(bg)
        btn.layoutParams = LinearLayout.LayoutParams(dp(62), dp(62))
        val iv = ImageView(this)
        iv.setImageResource(iconRes)
        iv.setColorFilter(if (danger) Color.parseColor("#C03428") else if (active) th.primary else Color.parseColor("#40534A"))
        val ilp = FrameLayout.LayoutParams(dp(25), dp(25)); ilp.gravity = Gravity.CENTER
        iv.layoutParams = ilp
        btn.addView(iv)
        btn.setOnClickListener { onClick() }
        cell.addView(btn)
        val t = text(this, label, 12f, th.muted, bold = true)
        t.gravity = Gravity.CENTER
        val tlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tlp.topMargin = dp(6)
        t.layoutParams = tlp
        cell.addView(t)
        return cell
    }

    private fun buildControls() {
        controlsGrid.removeAllViews()
        val call = CallHub.call
        controlsGrid.addView(ctlCell(R.drawable.ic_mic, "Mute", muted) {
            muted = !muted
            try { CallHub.service?.setMuted(muted) } catch (e: Exception) { }
            buildControls()
        })
        controlsGrid.addView(ctlCell(R.drawable.ic_keypad, "Keypad", false) { showDtmf() })
        controlsGrid.addView(ctlCell(R.drawable.ic_volume, "Speaker", speaker) {
            speaker = !speaker
            try {
                CallHub.service?.setAudioRoute(
                    if (speaker) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
                )
            } catch (e: Exception) { }
            // Legacy nudge: some OEM builds honor the AudioManager speakerphone
            // flag from the incall app even when the Telecom route request is
            // dropped. The button's lit state comes from the real CallAudioState
            // (synced in refresh), so this can never fake success.
            try {
                val am = getSystemService(android.media.AudioManager::class.java)
                am.isSpeakerphoneOn = speaker
            } catch (e: Exception) { }
            buildControls()
        })
        controlsGrid.addView(ctlCell(R.drawable.ic_plus, "Add call", false) {
            val i = Intent(this, MainActivity::class.java)
            i.putExtra("tab", 0)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        })
        val holding = call?.state == Call.STATE_HOLDING
        controlsGrid.addView(ctlCell(R.drawable.ic_pause, "Hold", holding) {
            try {
                if (holding) call?.unhold() else call?.hold()
            } catch (e: Exception) { }
        })
        controlsGrid.addView(ctlCell(R.drawable.ic_dot, "Record", Recorder.isRecording, danger = Recorder.isRecording) {
            if (Recorder.isRecording) stopRecording() else startRecording()
            buildControls(); refresh()
        })
    }

    private fun bigCallButton(resId: Int, bg: Int, rotate: Float, onClick: () -> Unit): FrameLayout {
        val b = FrameLayout(this)
        b.background = circleBg(bg)
        b.layoutParams = LinearLayout.LayoutParams(dp(72), dp(72))
        val iv = ImageView(this)
        iv.setImageResource(resId)
        iv.setColorFilter(Color.WHITE)
        iv.rotation = rotate
        val lp = FrameLayout.LayoutParams(dp(30), dp(30)); lp.gravity = Gravity.CENTER
        iv.layoutParams = lp
        b.addView(iv)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun refresh() {
        val call = CallHub.call
        if (call == null) {
            if (!finished) { finished = true; finish() }
            return
        }
        // Speaker/Mute indicators mirror Telecom's real audio state: if a
        // route request was rejected, the button un-lights instead of lying.
        CallHub.audioState?.let { ast ->
            val realSpeaker = ast.route == CallAudioState.ROUTE_SPEAKER
            val realMuted = ast.isMuted
            if (realSpeaker != speaker || realMuted != muted) {
                speaker = realSpeaker
                muted = realMuted
                buildControls()
            }
        }
        val st = call.state
        val num = number()
        subTv.text = when {
            st == Call.STATE_RINGING -> "${fmtNumber(num)} · Incoming call"
            st == Call.STATE_DIALING || st == Call.STATE_CONNECTING -> "${fmtNumber(num)} · Calling…"
            st == Call.STATE_HOLDING -> "${fmtNumber(num)} · On hold"
            st == Call.STATE_ACTIVE -> {
                if (callConnectedSince == 0L) callConnectedSince = System.currentTimeMillis()
                val el = (System.currentTimeMillis() - callConnectedSince) / 1000
                "${fmtNumber(num)}${if (simLabel().isNotEmpty()) " · " + simLabel() else ""} · ${fmtDuration(el)}"
            }
            else -> fmtNumber(num)
        }
        recPill.visibility = if (Recorder.isRecording) View.VISIBLE else View.GONE

        val sim = simLabel()
        if (sim.isNotEmpty()) {
            simChip.visibility = View.VISIBLE
            simChipTv.text = sim
        } else {
            simChip.visibility = View.GONE
        }

        bottomArea.removeAllViews()
        if (st == Call.STATE_RINGING) {
            controlsGrid.visibility = View.GONE
            val decline = bigCallButton(R.drawable.ic_phone, Color.parseColor("#D93A2B"), 135f) {
                try { call.reject(false, null) } catch (e: Exception) { }
            }
            bottomArea.addView(decline)
            val gap = View(this); gap.layoutParams = LinearLayout.LayoutParams(dp(70), 1)
            bottomArea.addView(gap)
            val answer = bigCallButton(R.drawable.ic_phone, th.primary, 0f) {
                try { call.answer(VideoProfile.STATE_AUDIO_ONLY) } catch (e: Exception) { }
            }
            bottomArea.addView(answer)
        } else {
            controlsGrid.visibility = View.VISIBLE
            val end = bigCallButton(R.drawable.ic_phone, Color.parseColor("#D93A2B"), 135f) {
                try { call.disconnect() } catch (e: Exception) { }
            }
            bottomArea.addView(end)
        }

        if (st == Call.STATE_ACTIVE) {
            val prefs = Prefs.get(this)
            if (prefs.recMode == "auto" && !Recorder.isRecording) {
                startRecording()
            } else if (prefs.recMode == "ask" && !Recorder.isRecording && !askedThisCall) {
                askBar.visibility = View.VISIBLE
            }
        }
        if (st == Call.STATE_DISCONNECTED) {
            if (Recorder.isRecording) stopRecording()
            if (!finished) {
                finished = true
                handler.postDelayed({ finish() }, 700)
            }
        }
    }

    private fun startRecording() {
        if (Recorder.isRecording) return
        val source = Recorder.start(
            this, Recorder.Meta(
                number = number(),
                name = CallLogRepo.contactName(this, number()),
                direction = direction(),
                simLabel = simLabel()
            ), starter = "activity"
        )
        if (source.isEmpty()) {
            Toast.makeText(this, "Recording could not start on this device", Toast.LENGTH_LONG).show()
            return
        }
        val prefs = Prefs.get(this)
        if (prefs.consent && !CallHub.consentSpoken) {
            CallHub.consentSpoken = true
            consentChip.text = "Announcement played: “This call is being recorded.”"
            consentChip.visibility = View.VISIBLE
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    tts?.speak("This call is being recorded.", TextToSpeech.QUEUE_FLUSH, null, "consent")
                }
            }
        }
        buildControls(); refresh()
    }

    private fun stopRecording() {
        val rec = Recorder.stop(this)
        if (rec != null) {
            Toast.makeText(this, "Recording saved · ${fmtDuration(rec.durationSec)}", Toast.LENGTH_SHORT).show()
        }
        buildControls(); refresh()
    }

    private fun showDtmf() {
        if (dtmfOverlay != null) return
        val call = CallHub.call ?: return
        val frame = window.decorView as ViewGroup
        val overlay = FrameLayout(this)
        overlay.setBackgroundColor(th.bg)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(40), dp(90), dp(40), dp(40))
        val grid = GridLayout(this)
        grid.columnCount = 3
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        val letters = mapOf("2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "0" to "+")
        for (k in keys) {
            val key = dialKey(this, th, k, letters[k]) {
                try {
                    call.playDtmfTone(k[0])
                    handler.postDelayed({ try { call.stopDtmfTone() } catch (e: Exception) { } }, 160)
                } catch (e: Exception) { }
            }
            key.layoutParams = gridCellParams(this, 68)
            grid.addView(key)
        }
        col.addView(grid)
        val close = text(this, "Close keypad", 15f, th.primary, bold = true)
        close.gravity = Gravity.CENTER
        val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        clp.topMargin = dp(22)
        close.layoutParams = clp
        close.setOnClickListener {
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            dtmfOverlay = null
        }
        col.addView(close)
        overlay.addView(col)
        frame.addView(overlay)
        dtmfOverlay = overlay
    }

    private fun fmtNumber(n: String): String {
        val d = n.filter { it.isDigit() }
        return if (d.length == 10) "(${d.substring(0, 3)}) ${d.substring(3, 6)}-${d.substring(6)}" else n
    }
}
