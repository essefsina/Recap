package com.essef.recap

import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var th: ThemeColors
    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        th = Themes.get(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(th.bg)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(14), dp(10), dp(14), dp(6))
        val back = ImageView(this)
        back.setImageResource(R.drawable.ic_back)
        back.setColorFilter(th.onSurface)
        back.background = circleBg(th.surface)
        back.setPadding(dp(10), dp(10), dp(10), dp(10))
        back.layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        back.setOnClickListener { finish() }
        head.addView(back)
        val ht = text(this, "Settings", 15.5f, th.onSurface, bold = true)
        val hLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        hLp.leftMargin = dp(12)
        ht.layoutParams = hLp
        head.addView(ht)
        root.addView(head)

        val scroll = ScrollView(this)
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(18), dp(6), dp(18), dp(24))
        scroll.addView(body)
        root.addView(scroll)
        setContentView(root)
        render()
    }

    private fun section(label: String) {
        val t = text(this, label, 13f, th.primary, bold = true)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(20); lp.bottomMargin = dp(8)
        t.layoutParams = lp
        body.addView(t)
    }

    private fun groupCard(): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = rounded(th.surface, 22, this)
        body.addView(card)
        return card
    }

    private fun row(
        parent: LinearLayout, iconRes: Int, label: String, value: String?,
        divider: Boolean, onClick: (() -> Unit)?
    ): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(16), dp(14), dp(16), dp(14))
        val iv = ImageView(this)
        iv.setImageResource(iconRes)
        iv.setColorFilter(th.primary)
        iv.layoutParams = LinearLayout.LayoutParams(dp(21), dp(21))
        r.addView(iv)
        val lt = text(this, label, 15f, th.onSurface)
        val lLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lLp.leftMargin = dp(14)
        lt.layoutParams = lLp
        r.addView(lt)
        if (value != null) {
            r.addView(text(this, value, 14f, th.faint))
        }
        if (onClick != null) r.setOnClickListener { onClick() }
        parent.addView(r)
        if (divider) {
            val d = View(this)
            d.setBackgroundColor(Color.parseColor("#E4EAE5"))
            val dLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            dLp.leftMargin = dp(51)
            d.layoutParams = dLp
            parent.addView(d)
        }
        return r
    }

    private fun switchRow(parent: LinearLayout, iconRes: Int, label: String, checked: Boolean, divider: Boolean, onChange: (Boolean) -> Unit) {
        val r = row(parent, iconRes, label, null, divider, null)
        val sw = Switch(this)
        sw.isChecked = checked
        sw.thumbTintList = tintList(th.primary)
        sw.trackTintList = tintList(th.container)
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }
        r.addView(sw)
        r.setOnClickListener { sw.isChecked = !sw.isChecked }
    }

    private fun render() {
        body.removeAllViews()
        val prefs = Prefs.get(this)

        // Logo header — the logo's background follows the color theme,
        // in here and on the home-screen icon alike.
        val logoHead = LinearLayout(this)
        logoHead.orientation = LinearLayout.VERTICAL
        logoHead.gravity = Gravity.CENTER
        val logoIv = ImageView(this)
        logoIv.setImageResource(AppIcon.drawableFor(prefs.themeName))
        logoIv.layoutParams = LinearLayout.LayoutParams(dp(76), dp(76))
        logoHead.addView(logoIv)
        val logoName = text(this, "Recap", 17f, th.onSurface, bold = true)
        val lnLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lnLp.topMargin = dp(8)
        logoName.layoutParams = lnLp
        logoHead.addView(logoName)
        val lhLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lhLp.topMargin = dp(10); lhLp.bottomMargin = dp(2)
        logoHead.layoutParams = lhLp
        body.addView(logoHead)

        section("Appearance")
        val appearance = groupCard()
        // Theme swatch row
        val themeRow = LinearLayout(this)
        themeRow.orientation = LinearLayout.VERTICAL
        themeRow.setPadding(dp(16), dp(14), dp(16), dp(6))
        themeRow.addView(text(this, "Color theme", 15f, th.onSurface))
        val swatches = LinearLayout(this)
        swatches.orientation = LinearLayout.HORIZONTAL
        val swLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        swLp.topMargin = dp(12)
        swatches.layoutParams = swLp
        for (name in Themes.names) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER
            col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            val dot = View(this)
            val selected = name == prefs.themeName
            dot.background = circleBg(Themes.primaryOf(name))
            if (selected) {
                val ring = android.graphics.drawable.GradientDrawable()
                ring.shape = android.graphics.drawable.GradientDrawable.OVAL
                ring.setColor(Themes.primaryOf(name))
                ring.setStroke(dp(2), th.onSurface)
                dot.background = ring
            }
            dot.layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            col.addView(dot)
            val nm = text(this, name, 11f, if (selected) th.onSurface else th.faint, bold = selected)
            val nLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            nLp.topMargin = dp(5)
            nm.layoutParams = nLp
            col.addView(nm)
            col.setOnClickListener {
                Prefs.put(this, "theme", name)
                AppIcon.apply(this)
                recreate()
            }
            swatches.addView(col)
        }
        themeRow.addView(swatches)
        val iconNote = text(this, "The app icon's background follows your theme — here and on your home screen.", 12f, th.faint)
        val inLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        inLp.topMargin = dp(10); inLp.bottomMargin = dp(8)
        iconNote.layoutParams = inLp
        themeRow.addView(iconNote)
        appearance.addView(themeRow)
        val dv = View(this)
        dv.setBackgroundColor(Color.parseColor("#E4EAE5"))
        val dvLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        dvLp.leftMargin = dp(51); dvLp.topMargin = dp(8)
        dv.layoutParams = dvLp
        appearance.addView(dv)
        switchRow(appearance, R.drawable.ic_moon, "Dark mode", prefs.darkMode, divider = false) { v ->
            Prefs.put(this, "dark", v)
            recreate()
        }

        section("Recording")
        val recording = groupCard()
        // Mode segmented control
        val modeRow = LinearLayout(this)
        modeRow.orientation = LinearLayout.VERTICAL
        modeRow.setPadding(dp(16), dp(14), dp(16), dp(14))
        val modeTop = LinearLayout(this)
        modeTop.orientation = LinearLayout.HORIZONTAL
        modeTop.gravity = Gravity.CENTER_VERTICAL
        val micIv = ImageView(this)
        micIv.setImageResource(R.drawable.ic_mic)
        micIv.setColorFilter(th.primary)
        micIv.layoutParams = LinearLayout.LayoutParams(dp(21), dp(21))
        modeTop.addView(micIv)
        val modeLabel = text(this, "Recording mode", 15f, th.onSurface)
        val mlLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        mlLp.leftMargin = dp(14)
        modeLabel.layoutParams = mlLp
        modeTop.addView(modeLabel)
        modeRow.addView(modeTop)
        val seg = LinearLayout(this)
        seg.orientation = LinearLayout.HORIZONTAL
        seg.background = rounded(th.softContainer, 999, this)
        seg.setPadding(dp(4), dp(4), dp(4), dp(4))
        val segLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        segLp.topMargin = dp(12)
        seg.layoutParams = segLp
        for ((label, value) in listOf("Auto-record all" to "auto", "Ask each time" to "ask")) {
            val on = prefs.recMode == value
            val b = text(this, label, 13.5f, if (on) Color.WHITE else th.muted, bold = true)
            b.gravity = Gravity.CENTER
            if (on) b.background = rounded(th.primary, 999, this)
            b.setPadding(0, dp(9), 0, dp(9))
            b.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            b.setOnClickListener {
                Prefs.put(this, "recMode", value)
                render()
            }
            seg.addView(b)
        }
        modeRow.addView(seg)
        recording.addView(modeRow)
        val dv2 = View(this)
        dv2.setBackgroundColor(Color.parseColor("#E4EAE5"))
        val dv2Lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        dv2Lp.leftMargin = dp(51)
        dv2.layoutParams = dv2Lp
        recording.addView(dv2)
        switchRow(recording, R.drawable.ic_volume, "Consent announcement", prefs.consent, divider = true) { v ->
            Prefs.put(this, "consent", v)
        }
        row(recording, R.drawable.ic_headphones, "Audio quality",
            if (prefs.sampleRate >= 44100) "High · 44.1 kHz" else "Standard · 16 kHz", divider = false) {
            val items = listOf(
                PopupItem(label = "Standard · 16 kHz", sub = "Smaller files, AI-ready", selected = prefs.sampleRate < 44100),
                PopupItem(label = "High · 44.1 kHz", sub = "Bigger files", selected = prefs.sampleRate >= 44100)
            )
            Popups.list(this, th, "Audio quality", items) { which ->
                Prefs.put(this, "sampleRate", if (which == 1) 44100 else 16000)
                render()
            }
        }

        section("AI Features")
        val ai = groupCard()
        switchRow(ai, R.drawable.ic_sparkle, "Auto-generate titles", prefs.autoTitle, divider = true) { v ->
            Prefs.put(this, "autoTitle", v)
        }
        switchRow(ai, R.drawable.ic_file, "Auto-summarize calls", prefs.autoSummarize, divider = true) { v ->
            Prefs.put(this, "autoSummarize", v)
        }
        row(ai, R.drawable.ic_globe, "Transcript language", "English (US)", divider = false) {
            Popups.message(
                this, th, "Transcript language",
                "English (US) is bundled in the app and works fully offline. More languages will arrive as downloadable packs in an update."
            )
        }

        section("Storage")
        val storage = groupCard()
        row(storage, R.drawable.ic_folder, "Recordings folder", "App folder", divider = true) {
            val dir = RecStore.dir(this)
            val count = RecStore.all(this).size
            val bytes = dir.listFiles()?.sumOf { it.length() } ?: 0L
            Popups.message(
                this, th, "Recordings folder",
                "Recordings live in Recap's private app folder:\n\n${dir.absolutePath}\n\n$count recordings · ${"%.1f".format(bytes / 1048576.0)} MB\n\nThey stay on this phone and are never uploaded."
            )
        }
        val autoDel = prefsAutoDeleteDays()
        row(storage, R.drawable.ic_trash, "Auto-delete old recordings",
            if (autoDel == 0) "Never" else "After $autoDel days", divider = false) {
            val labels = listOf("Never", "After 30 days", "After 90 days")
            val days = listOf(0, 30, 90)
            val items = labels.mapIndexed { i, l ->
                PopupItem(label = l, selected = autoDel == days[i])
            }
            Popups.list(this, th, "Auto-delete old recordings", items) { which ->
                Prefs.put(this, "autoDeleteDays", days[which])
                RecStore.autoDelete(this)
                render()
                Toast.makeText(this, "Auto-delete set: ${labels[which]}", Toast.LENGTH_SHORT).show()
            }
        }

        section("Phone app")
        val phone = groupCard()
        val isDefault = try {
            getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        } catch (e: Exception) { false }
        row(phone, R.drawable.ic_phone, "Default phone app", if (isDefault) "Recap" else "Not Recap", divider = true) {
            if (!isDefault) {
                try {
                    val rm = getSystemService(RoleManager::class.java)
                    startActivity(rm!!.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                } catch (e: Exception) {
                    Toast.makeText(this, "Open system Settings → Apps → Default apps → Phone app", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Recap is already your phone app", Toast.LENGTH_SHORT).show()
            }
        }
        val dualSim = CallLogRepo.isDualSim(this)
        row(phone, R.drawable.ic_phone, "Calling SIM",
            if (!dualSim) "Single SIM" else Calls.simShortLabel(this).let { if (it == "Ask") "Ask every time" else it },
            divider = true) {
            if (dualSim) {
                Calls.showDefaultSimPicker(this) { render() }
            } else {
                Toast.makeText(this, "This phone has a single SIM", Toast.LENGTH_SHORT).show()
            }
        }
        switchRow(phone, R.drawable.ic_dot, "Floating call bubble", prefs.floatBubble, divider = false) { v ->
            Prefs.put(this, "floatBubble", v)
            if (v && !android.provider.Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Allow Recap to display over other apps so the bubble can appear during games", Toast.LENGTH_LONG).show()
                try {
                    val i = android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(i)
                } catch (e: Exception) { }
            }
        }

        section("Troubleshooting")
        val trouble = groupCard()
        row(trouble, R.drawable.ic_mic, "Test the microphone", null, divider = true) { micTest() }
        row(trouble, R.drawable.ic_file, "Last recording diagnostics", null, divider = false) {
            val last = RecStore.all(this).firstOrNull()
            if (last == null) {
                Popups.message(this, th, "Diagnostics", "No recordings yet — make a test call first, then check here.")
            } else {
                val levels = last.diag.takeIf { it.isNotBlank() }?.replace(";", "\n")?.replace("=", "  →  ") ?: "(no data)"
                Popups.message(this, th, "Last recording diagnostics",
                    "Saved source: ${last.source}\nCompletely silent: ${if (last.silent) "yes" else "no"}\n\nMeasured level per capture path:\n$levels\n\n" +
                    "0 = digital silence · x = the path refused to start.\n" +
                    "dual / voicecall / voicecomm / mic are AudioRecord levels (RMS; speech is usually 300+).\n" +
                    "mr-* are MediaRecorder peaks out of 32767.")
            }
        }

        val verName = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }
        val ver = text(this, "Recap $verName · On-device AI · Your calls never leave your phone", 12f, th.faint)
        ver.gravity = Gravity.CENTER
        val vLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        vLp.topMargin = dp(26)
        ver.layoutParams = vLp
        body.addView(ver)
    }

    /** Records 3 seconds from the mic OUTSIDE a call, reports the measured
     *  level, and plays the sample back if anything was heard. This separates
     *  "the mic is blocked entirely" from "only call audio is blocked". */
    private fun micTest() {
        Toast.makeText(this, "Speak now — testing the microphone for 3 seconds…", Toast.LENGTH_LONG).show()
        Thread {
            val rate = 16000
            var peak = 0
            var sum = 0.0
            var count = 0L
            val pcm = java.io.ByteArrayOutputStream()
            try {
                val minBuf = android.media.AudioRecord.getMinBufferSize(rate,
                    android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
                val rec = android.media.AudioRecord(android.media.MediaRecorder.AudioSource.MIC, rate,
                    android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuf, 4096))
                if (rec.state == android.media.AudioRecord.STATE_INITIALIZED) {
                    rec.startRecording()
                    val buf = ShortArray(1024)
                    val end = System.currentTimeMillis() + 3000
                    while (System.currentTimeMillis() < end) {
                        val n = rec.read(buf, 0, buf.size)
                        if (n > 0) {
                            for (i in 0 until n) {
                                val v = buf[i].toInt()
                                val a = if (v < 0) -v else v
                                if (a > peak) peak = a
                                sum += v.toDouble() * v
                                pcm.write(v and 0xff); pcm.write(v shr 8 and 0xff)
                            }
                            count += n
                        } else if (n < 0) break
                    }
                    try { rec.stop() } catch (e: Exception) { }
                    rec.release()
                }
            } catch (e: Exception) { }
            val rms = if (count > 0) kotlin.math.sqrt(sum / count).toInt() else 0
            val bytes = pcm.toByteArray()
            runOnUiThread {
                if (peak < 50) {
                    Popups.message(this, th, "Microphone test",
                        "The microphone delivered silence even outside a call (peak level $peak).\n\nCheck:\n" +
                        "• Android Settings → Privacy → Microphone access is ON\n" +
                        "• Recap's microphone permission is allowed\n" +
                        "• No other app is holding the mic\n\nIf all of those are fine, the phone is muting the mic at the system level.")
                } else {
                    Popups.message(this, th, "Microphone test",
                        "The mic hears you fine outside a call — peak level $peak, average $rms.\n\nPlaying your 3-second test back now…")
                    try {
                        val track = android.media.AudioTrack.Builder()
                            .setAudioAttributes(android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                            .setAudioFormat(android.media.AudioFormat.Builder()
                                .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(rate)
                                .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())
                            .setBufferSizeInBytes(maxOf(bytes.size, 4096))
                            .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                            .build()
                        track.write(bytes, 0, bytes.size)
                        track.play()
                    } catch (e: Exception) { }
                }
            }
        }.start()
    }

    private fun prefsAutoDeleteDays(): Int {
        val sp = getSharedPreferences("recap", MODE_PRIVATE)
        return sp.getInt("autoDeleteDays", 0)
    }
}
