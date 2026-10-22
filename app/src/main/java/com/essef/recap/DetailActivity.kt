package com.essef.recap

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Calendar

class DetailActivity : Activity() {

    private lateinit var th: ThemeColors
    private var type = "recording"
    private var rec: Recording? = null
    private var vmMeta: VmMeta? = null
    private var vmUri: Uri? = null
    private var number = ""
    private var contactName = ""
    private var dateMs = 0L
    private var durationSec = 0L

    private var player: MediaPlayer? = null
    private var playing = false
    private lateinit var playBtn: ImageView
    private lateinit var seek: SeekBar
    private lateinit var posTv: TextView
    private lateinit var titleTv: TextView
    private lateinit var summaryBox: LinearLayout
    private lateinit var transcriptBox: LinearLayout
    private lateinit var statusTv: TextView
    private lateinit var transcribeBtn: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var busy = false

    private val ticker = object : Runnable {
        override fun run() {
            val p = player
            if (p != null && playing) {
                try {
                    seek.progress = p.currentPosition
                    posTv.text = fmtDuration(p.currentPosition / 1000L)
                } catch (e: Exception) { }
                handler.postDelayed(this, 300)
            }
        }
    }

    private fun getTranscript(): String = if (type == "recording") rec?.transcript ?: "" else vmMeta?.transcript ?: ""
    private fun getSummary(): String = if (type == "recording") rec?.summary ?: "" else vmMeta?.summary ?: ""
    private fun recTitle(): String = if (type == "recording") rec?.title ?: "" else vmMeta?.title ?: ""
    private fun getDetectedCaller(): String = if (type == "recording") rec?.detectedCaller ?: "" else vmMeta?.detectedCaller ?: ""

    private fun saveMeta() {
        val ctx = this
        if (type == "recording") rec?.save(ctx) else vmMeta?.let { VmStore.save(ctx, it) }
    }

    private fun setTranscript(v: String) { if (type == "recording") rec?.transcript = v else vmMeta?.transcript = v }
    private fun setSummary(v: String) { if (type == "recording") rec?.summary = v else vmMeta?.summary = v }
    private fun setTitle(v: String) { if (type == "recording") rec?.title = v else vmMeta?.title = v }
    private fun setDetected(v: String) { if (type == "recording") rec?.detectedCaller = v else vmMeta?.detectedCaller = v }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        th = Themes.get(this)
        type = intent.getStringExtra("type") ?: "recording"
        if (type == "recording") {
            val id = intent.getStringExtra("recId") ?: ""
            rec = RecStore.byId(this, id)
            if (rec == null) { finish(); return }
            number = rec!!.number
            contactName = rec!!.contactName
            dateMs = rec!!.startMs
            durationSec = rec!!.durationSec
        } else {
            val vmId = intent.getLongExtra("vmId", -1)
            vmUri = Uri.parse(intent.getStringExtra("vmUri") ?: "")
            vmMeta = VmStore.load(this, vmId)
            number = intent.getStringExtra("number") ?: ""
            contactName = intent.getStringExtra("name") ?: ""
            dateMs = intent.getLongExtra("dateMs", 0)
            durationSec = intent.getLongExtra("durationSec", 0)
        }
        buildUi()
        renderAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { player?.release() } catch (e: Exception) { }
        handler.removeCallbacks(ticker)
    }

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(th.bg)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(14), dp(10), dp(14), dp(2))
        val back = ImageView(this)
        back.setImageResource(R.drawable.ic_back)
        back.setColorFilter(th.onSurface)
        back.background = circleBg(th.surface)
        back.setPadding(dp(10), dp(10), dp(10), dp(10))
        back.layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        back.setOnClickListener { finish() }
        head.addView(back)
        val ht = text(this, if (type == "recording") "Recording" else "Voicemail", 15.5f, th.onSurface, bold = true)
        val hLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        hLp.leftMargin = dp(12)
        ht.layoutParams = hLp
        head.addView(ht)
        val del = ImageView(this)
        del.setImageResource(R.drawable.ic_trash)
        del.setColorFilter(Rows.RED_DARK)
        del.background = circleBg(Rows.RED_BG)
        del.setPadding(dp(10), dp(10), dp(10), dp(10))
        del.layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        del.setOnClickListener { confirmDelete() }
        head.addView(del)
        root.addView(head)

        val scroll = ScrollView(this)
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(22), dp(10), dp(22), dp(20))
        scroll.addView(body)
        root.addView(scroll)

        val titleRow = LinearLayout(this)
        titleRow.orientation = LinearLayout.HORIZONTAL
        titleRow.gravity = Gravity.TOP
        titleTv = text(this, "", 25f, th.onSurface, bold = true)
        titleTv.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        titleRow.addView(titleTv)
        val pencil = ImageView(this)
        pencil.setImageResource(R.drawable.ic_pencil)
        pencil.setColorFilter(th.faint)
        pencil.layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        pencil.setOnClickListener { editTitle() }
        titleRow.addView(pencil)
        body.addView(titleRow)

        val display = contactName.ifBlank { getDetectedCaller() }.ifBlank { Calls.fmtNumber(number) }
        val meta = text(this, "$display · ${fmtDay(dateMs)}, ${fmtClock(dateMs)} · ${fmtDuration(durationSec)}" +
            if (type == "recording") (if (rec?.direction == 2) " · Outgoing" else " · Incoming") else "", 13.5f, th.muted)
        val mLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        mLp.topMargin = dp(6)
        meta.layoutParams = mLp
        body.addView(meta)

        if (type == "recording" && rec?.silent == true) {
            val diagTxt = rec?.diag?.takeIf { it.isNotBlank() }?.replace(";", "  ·  ")?.replace("=", " ")
            val warn = text(this, "Honest note: this recording came back silent. Recap measured every capture path while recording" +
                (if (diagTxt != null) " — levels: $diagTxt (0 = digital silence, x = path refused to start)" else "") +
                " — and none of them delivered sound. Nothing here is simulated, and there is nothing to transcribe in a silent file. " +
                "Settings → Troubleshooting has a microphone test that shows whether the mic itself works outside a call.", 12.5f, Color.parseColor("#7A5410"))
            warn.background = rounded(Color.parseColor("#FFF6E5"), 14, this)
            warn.setPadding(dp(12), dp(10), dp(12), dp(10))
            val wLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            wLp.topMargin = dp(12)
            warn.layoutParams = wLp
            body.addView(warn)
        } else if (type == "recording" && rec?.callerSilent == true) {
            val warn = text(this, "Honest note: on this device only your side was captured clearly — the caller's side is silent in this recording (source: ${rec?.source}). " +
                "Android limits what non-system apps can hear; nothing here is simulated.", 12.5f, Color.parseColor("#7A5410"))
            warn.background = rounded(Color.parseColor("#FFF6E5"), 14, this)
            warn.setPadding(dp(12), dp(10), dp(12), dp(10))
            val wLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            wLp.topMargin = dp(12)
            warn.layoutParams = wLp
            body.addView(warn)
        }

        // Player card
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = rounded(th.playerBg, 24, this)
        card.setPadding(dp(18), dp(16), dp(18), dp(14))
        val cLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        cLp.topMargin = dp(16)
        card.layoutParams = cLp
        val prow = LinearLayout(this)
        prow.orientation = LinearLayout.HORIZONTAL
        prow.gravity = Gravity.CENTER_VERTICAL
        playBtn = ImageView(this)
        playBtn.setImageResource(R.drawable.ic_play)
        playBtn.setColorFilter(Color.WHITE)
        playBtn.background = circleBg(th.primary)
        playBtn.setPadding(dp(13), dp(13), dp(13), dp(13))
        playBtn.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        playBtn.setOnClickListener { togglePlay() }
        prow.addView(playBtn)
        seek = SeekBar(this)
        seek.max = (durationSec * 1000).toInt().coerceAtLeast(1)
        val sLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        sLp.leftMargin = dp(14)
        seek.layoutParams = sLp
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    try { player?.seekTo(progress) } catch (e: Exception) { }
                    posTv.text = fmtDuration(progress / 1000L)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        prow.addView(seek)
        card.addView(prow)
        val times = LinearLayout(this)
        times.orientation = LinearLayout.HORIZONTAL
        posTv = text(this, "0:00", 12f, Color.parseColor("#9DB4A8"))
        times.addView(posTv)
        times.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        times.addView(text(this, fmtDuration(durationSec), 12f, Color.parseColor("#9DB4A8")))
        card.addView(times)
        body.addView(card)

        statusTv = text(this, "", 13.5f, th.primary, bold = true)
        val stLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        stLp.topMargin = dp(14)
        statusTv.layoutParams = stLp
        statusTv.visibility = View.GONE
        body.addView(statusTv)

        val sumLabel = LinearLayout(this)
        sumLabel.orientation = LinearLayout.HORIZONTAL
        sumLabel.gravity = Gravity.CENTER_VERTICAL
        val slLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        slLp.topMargin = dp(18)
        sumLabel.layoutParams = slLp
        val spark = ImageView(this)
        spark.setImageResource(R.drawable.ic_sparkle)
        spark.setColorFilter(th.primary)
        spark.layoutParams = LinearLayout.LayoutParams(dp(15), dp(15))
        sumLabel.addView(spark)
        val sl = text(this, "  AI SUMMARY", 13f, th.primary, bold = true)
        sumLabel.addView(sl)
        body.addView(sumLabel)

        summaryBox = LinearLayout(this)
        summaryBox.orientation = LinearLayout.VERTICAL
        summaryBox.background = rounded(th.surface, 20, this)
        summaryBox.setPadding(dp(16), dp(14), dp(16), dp(14))
        val sbLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        sbLp.topMargin = dp(8)
        summaryBox.layoutParams = sbLp
        body.addView(summaryBox)

        val tLabel = text(this, "TRANSCRIPT", 12f, th.faint, bold = true)
        val tLp2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tLp2.topMargin = dp(22)
        tLabel.layoutParams = tLp2
        body.addView(tLabel)

        transcriptBox = LinearLayout(this)
        transcriptBox.orientation = LinearLayout.VERTICAL
        body.addView(transcriptBox)

        // Bottom actions
        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.setPadding(dp(18), dp(10), dp(18), dp(20))
        transcribeBtn = actionBtn(
            if (getTranscript().isNotBlank()) "✓ Transcribed" else "Transcribe",
            primary = false
        ) { runTranscribe() }
        actions.addView(transcribeBtn)
        val sumBtn = actionBtn("✦ Summarize", primary = true) { runSummarize() }
        val aLp2 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        aLp2.leftMargin = dp(8)
        sumBtn.layoutParams = aLp2
        transcribeBtn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        actions.addView(sumBtn)
        val shareBtn = actionBtn("Share", primary = false) { share() }
        val shLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        shLp.leftMargin = dp(8)
        shareBtn.layoutParams = shLp
        actions.addView(shareBtn)
        root.addView(actions)

        setContentView(root)
    }

    private fun actionBtn(label: String, primary: Boolean, onClick: () -> Unit): TextView {
        val b = text(this, label, 14f, if (primary) Color.WHITE else th.onSurface, bold = true)
        b.gravity = Gravity.CENTER
        b.background = rounded(if (primary) th.primary else th.softContainer, 999, this)
        b.setPadding(0, dp(13), 0, dp(13))
        b.setOnClickListener { onClick() }
        return b
    }

    private fun renderAll() {
        val display = contactName.ifBlank { getDetectedCaller() }.ifBlank { Calls.fmtNumber(number) }
        titleTv.text = recTitle().ifBlank {
            if (type == "recording") "Recording with $display" else "Voicemail from $display"
        }
        renderSummary()
        renderTranscript()
    }

    private fun renderSummary() {
        summaryBox.removeAllViews()
        val s = getSummary()
        if (s.isBlank()) {
            summaryBox.addView(text(this, "No summary yet. Transcribe the call, then tap Summarize — or turn on auto-summarize in Settings.", 14f, th.muted))
            return
        }
        val parts = s.split("\n")
        val paragraph = parts.firstOrNull() ?: ""
        if (paragraph.isNotBlank()) summaryBox.addView(text(this, paragraph, 14.5f, th.onSurface))
        val bullets = parts.drop(1).filter { it.startsWith("•") }
        for (b in bullets) {
            val bt = text(this, b, 14.5f, th.onSurface)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(6)
            bt.layoutParams = lp
            summaryBox.addView(bt)
        }
        // Add-to-calendar appears only when the summary actually names a day/time.
        if (findEventTime(s) != null) {
            val cal = text(this, "Add to calendar", 13.5f, th.primary, bold = true)
            cal.background = rounded(th.softContainer, 999, this)
            cal.setPadding(dp(14), dp(8), dp(14), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12)
            cal.layoutParams = lp
            cal.setOnClickListener { addToCalendar(s) }
            summaryBox.addView(cal)
        }
    }

    private fun findEventTime(s: String): Pair<Int, Int>? {
        // Returns (weekday Calendar constant, minutes from midnight) if found.
        val low = s.lowercase()
        val days = mapOf(
            "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY, "wednesday" to Calendar.WEDNESDAY,
            "thursday" to Calendar.THURSDAY, "friday" to Calendar.FRIDAY, "saturday" to Calendar.SATURDAY,
            "sunday" to Calendar.SUNDAY
        )
        var wd: Int? = null
        for ((k, v) in days) if (low.contains(k)) { wd = v; break }
        val wdFound = wd ?: return null
        var minutes = 9 * 60
        val m = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)""").find(low)
        if (m != null) {
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toIntOrNull() ?: 0
            val ap = m.groupValues[3]
            if (ap == "pm" && h < 12) h += 12
            if (ap == "am" && h == 12) h = 0
            minutes = h * 60 + min
        }
        return wdFound to minutes
    }

    private fun addToCalendar(s: String) {
        val (weekday, minutes) = findEventTime(s) ?: return
        val cal = Calendar.getInstance()
        var guard = 0
        while (cal.get(Calendar.DAY_OF_WEEK) != weekday && guard < 8) {
            cal.add(Calendar.DAY_OF_YEAR, 1); guard++
        }
        if (cal.timeInMillis < System.currentTimeMillis() && guard == 0) cal.add(Calendar.DAY_OF_YEAR, 7)
        cal.set(Calendar.HOUR_OF_DAY, minutes / 60)
        cal.set(Calendar.MINUTE, minutes % 60)
        cal.set(Calendar.SECOND, 0)
        val i = Intent(Intent.ACTION_INSERT)
        i.data = CalendarContract.Events.CONTENT_URI
        i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
        i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, cal.timeInMillis + 30 * 60 * 1000)
        i.putExtra(CalendarContract.Events.TITLE, recTitle().ifBlank { "Call follow-up" })
        i.putExtra(CalendarContract.Events.DESCRIPTION, s)
        startActivity(i)
    }

    private fun renderTranscript() {
        transcriptBox.removeAllViews()
        val t = getTranscript()
        if (t.isBlank()) {
            val tv = text(this, "No transcript yet — tap Transcribe and Recap will listen to this recording on your phone and write it out, speaker by speaker.", 14f, th.muted)
            tv.setPadding(0, dp(8), 0, 0)
            transcriptBox.addView(tv)
            return
        }
        for (line in t.split("\n")) {
            val parts = line.split("|", limit = 3)
            if (parts.size < 3) continue
            val (speaker, startStr, content) = parts
            val start = startStr.toDoubleOrNull() ?: 0.0
            val rowL = LinearLayout(this)
            rowL.orientation = LinearLayout.HORIZONTAL
            val rLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            rLp.topMargin = dp(12)
            rowL.layoutParams = rLp
            val isYou = speaker.equals("You", ignoreCase = true)
            val who = text(this, speaker.uppercase(), 12f, if (isYou) th.muted else th.primary, bold = true)
            who.layoutParams = LinearLayout.LayoutParams(dp(86), ViewGroup.LayoutParams.WRAP_CONTENT)
            rowL.addView(who)
            val main = LinearLayout(this)
            main.orientation = LinearLayout.VERTICAL
            main.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            main.addView(text(this, content, 14.5f, th.onSurface))
            main.addView(text(this, fmtDuration(start.toLong()), 11.5f, th.faint))
            rowL.addView(main)
            transcriptBox.addView(rowL)
        }
    }

    private fun audioFileForPlayer(): Any? {
        return if (type == "recording") rec?.file else vmUri
    }

    private fun togglePlay() {
        try {
            if (player == null) {
                player = MediaPlayer()
                val src = audioFileForPlayer()
                if (src is File) player?.setDataSource(src.absolutePath) else player?.setDataSource(this, src as Uri)
                player?.setOnCompletionListener {
                    playing = false
                    playBtn.setImageResource(R.drawable.ic_play)
                    seek.progress = 0
                    posTv.text = "0:00"
                }
                player?.prepare()
                val dur = (player?.duration ?: 0)
                if (dur > 0) seek.max = dur
            }
            if (playing) {
                player?.pause()
                playing = false
                playBtn.setImageResource(R.drawable.ic_play)
            } else {
                player?.start()
                playing = true
                playBtn.setImageResource(R.drawable.ic_pause)
                handler.post(ticker)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not play this audio", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setBusy(msg: String?) {
        busy = msg != null
        if (msg == null) {
            statusTv.visibility = View.GONE
        } else {
            statusTv.visibility = View.VISIBLE
            statusTv.text = msg
        }
    }

    private fun runTranscribe() {
        if (busy) return
        setBusy("Preparing the speech model…")
        Thread {
            val ctx = this
            val ok = AiEngine.ensureModel(ctx)
            if (!ok) {
                runOnUiThread {
                    setBusy(null)
                    Toast.makeText(ctx, "The speech model could not be loaded", Toast.LENGTH_LONG).show()
                }
                return@Thread
            }
            try {
                val segs: List<AiEngine.Segment>
                if (type == "recording") {
                    val r = rec!!
                    // MediaRecorder captures are .m4a: decode to WAV first.
                    var srcFile = r.file
                    var srcKind = r.source
                    if (r.file.extension.equals("m4a", ignoreCase = true)) {
                        val tmp = File(ctx.cacheDir, "dec_${r.id}.wav")
                        val okDec = AiEngine.decodeToWav(ctx, android.net.Uri.fromFile(r.file), tmp)
                        if (!okDec) {
                            runOnUiThread {
                                setBusy(null)
                                Toast.makeText(ctx, "This recording's audio format could not be read", Toast.LENGTH_LONG).show()
                            }
                            return@Thread
                        }
                        srcFile = tmp
                        srcKind = "mic" // decoded m4a is a single mixed channel
                    }
                    segs = AiEngine.transcribeWav(ctx, srcFile, srcKind, contactName.ifBlank { "Caller" }) { msg ->
                        runOnUiThread { if (busy) statusTv.text = msg }
                    }
                } else {
                    val tmp = File(ctx.cacheDir, "vm_${vmMeta!!.vmId}.wav")
                    val decoded = AiEngine.decodeToWav(ctx, vmUri!!, tmp)
                    if (!decoded) {
                        runOnUiThread {
                            setBusy(null)
                            Toast.makeText(ctx, "This voicemail's audio format could not be read", Toast.LENGTH_LONG).show()
                        }
                        return@Thread
                    }
                    segs = AiEngine.transcribeWav(ctx, tmp, "voicemail", contactName.ifBlank { "Caller" }) { msg ->
                        runOnUiThread { if (busy) statusTv.text = msg }
                    }
                }
                runOnUiThread {
                    setBusy(null)
                    if (segs.isEmpty()) {
                        Toast.makeText(ctx, "No speech could be heard in this recording", Toast.LENGTH_LONG).show()
                    } else {
                        setTranscript(AiEngine.segmentsToText(segs))
                        val plain = AiEngine.plainText(segs)
                        val detected = AiEngine.detectCaller(plain)
                        if (detected.isNotBlank() && getDetectedCaller().isBlank()) setDetected(detected)
                        val prefs = Prefs.get(ctx)
                        if (prefs.autoTitle) {
                            val t = AiEngine.makeTitle(plain)
                            if (t.isNotBlank()) setTitle(t)
                        }
                        saveMeta()
                        transcribeBtn.text = "✓ Transcribed"
                        renderAll()
                        if (prefs.autoSummarize) runSummarize()
                        Toast.makeText(ctx, "Transcribed on this phone", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setBusy(null)
                    Toast.makeText(ctx, "Transcription failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun runSummarize() {
        if (busy) return
        val t = getTranscript()
        if (t.isBlank()) {
            Toast.makeText(this, "Transcribe first — the summary is written from the transcript", Toast.LENGTH_LONG).show()
            return
        }
        val plain = t.split("\n").joinToString(" ") { it.split("|", limit = 3).getOrNull(2) ?: "" }
        val s = AiEngine.summarize(plain)
        if (s.isBlank()) {
            Toast.makeText(this, "Not enough speech to summarize", Toast.LENGTH_SHORT).show()
            return
        }
        setSummary(s)
        val prefs = Prefs.get(this)
        if (prefs.autoTitle && recTitle().isBlank()) {
            val title = AiEngine.makeTitle(plain)
            if (title.isNotBlank()) setTitle(title)
        }
        saveMeta()
        renderAll()
        Toast.makeText(this, "Summary ready", Toast.LENGTH_SHORT).show()
    }

    private fun share() {
        val sb = StringBuilder()
        sb.append(recTitle().ifBlank { "Call recording" }).append("\n")
        sb.append("${Calls.fmtNumber(number)} · ${fmtDay(dateMs)}, ${fmtClock(dateMs)}\n\n")
        if (getSummary().isNotBlank()) sb.append("Summary:\n").append(getSummary()).append("\n\n")
        if (getTranscript().isNotBlank()) {
            sb.append("Transcript:\n")
            for (line in getTranscript().split("\n")) {
                val p = line.split("|", limit = 3)
                if (p.size == 3) sb.append(p[0]).append(": ").append(p[2]).append("\n")
            }
        }
        if (sb.length < 60) {
            Toast.makeText(this, "Nothing to share yet — transcribe the call first", Toast.LENGTH_SHORT).show()
            return
        }
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_TEXT, sb.toString())
        startActivity(Intent.createChooser(i, "Share call notes"))
    }

    private fun editTitle() {
        Popups.input(this, th, "Edit title", recTitle(), "Title for this recording") { newTitle ->
            setTitle(newTitle.trim())
            saveMeta()
            renderAll()
        }
    }

    private fun confirmDelete() {
        val isRec = type == "recording"
        Popups.confirm(
            this, th,
            if (isRec) "Delete this recording?" else "Delete voicemail data?",
            if (isRec) "The audio file and its AI title, transcript, and summary will be permanently deleted. The call log entry is kept."
            else "Recap's AI title, transcript, and summary for this voicemail will be deleted. The voicemail itself stays with your carrier — delete it there to remove the message.",
            "Delete", danger = true
        ) {
            if (isRec) {
                rec?.file?.delete()
                rec?.sidecar()?.delete()
                Toast.makeText(this, "Recording deleted", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                vmMeta?.let {
                    it.title = ""; it.summary = ""; it.transcript = ""; it.detectedCaller = ""
                    VmStore.save(this, it)
                }
                renderAll()
                Toast.makeText(this, "AI data deleted", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
