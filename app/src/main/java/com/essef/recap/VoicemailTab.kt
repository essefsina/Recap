package com.essef.recap

import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.provider.VoicemailContract
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

data class VmEntry(
    val id: Long,
    val number: String,
    val dateMs: Long,
    val durationSec: Long,
    val isNew: Boolean,
    val uri: android.net.Uri
)

class VoicemailTab(private val act: Activity, private val th: ThemeColors) {

    private lateinit var listBox: LinearLayout
    private var player: MediaPlayer? = null
    private var playingId: Long = -1

    fun create(): View {
        val ctx = act
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL

        val scroll = ScrollView(ctx)
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        listBox = LinearLayout(ctx)
        listBox.orientation = LinearLayout.VERTICAL
        listBox.setPadding(0, ctx.dp(4), 0, ctx.dp(16))
        scroll.addView(listBox)
        root.addView(scroll)

        load()
        return root
    }

    private fun queryVoicemails(): List<VmEntry> {
        val ctx = act
        val out = mutableListOf<VmEntry>()
        val proj = arrayOf(
            VoicemailContract.Voicemails._ID,
            VoicemailContract.Voicemails.NUMBER,
            VoicemailContract.Voicemails.DATE,
            VoicemailContract.Voicemails.DURATION,
            VoicemailContract.Voicemails.NEW
        )
        try {
            ctx.contentResolver.query(
                VoicemailContract.Voicemails.CONTENT_URI, proj, null, null,
                VoicemailContract.Voicemails.DATE + " DESC"
            )?.use { cur ->
                while (cur.moveToNext()) {
                    val id = cur.getLong(0)
                    out.add(
                        VmEntry(
                            id = id,
                            number = cur.getString(1) ?: "",
                            dateMs = cur.getLong(2),
                            durationSec = cur.getLong(3),
                            isNew = cur.getInt(4) == 1,
                            uri = ContentUris.withAppendedId(VoicemailContract.Voicemails.CONTENT_URI, id)
                        )
                    )
                }
            }
        } catch (e: Exception) { /* carrier VVM not provisioned or not default dialer */ }
        return out
    }

    private fun load() {
        val ctx = act
        Thread {
            val vms = queryVoicemails()
            val dual = CallLogRepo.isDualSim(ctx)
            ctx.runOnUiThread { render(vms, dual) }
        }.start()
    }

    private fun render(vms: List<VmEntry>, dual: Boolean) {
        val ctx = act
        listBox.removeAllViews()

        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.background = rounded(th.softContainer, 18, ctx)
        card.setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(16), ctx.dp(12))
        val cLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        cLp.setMargins(ctx.dp(18), ctx.dp(10), ctx.dp(18), 0)
        card.layoutParams = cLp
        val dot = View(ctx)
        dot.background = circleBg(if (vms.isNotEmpty()) Rows.GREEN else th.faint)
        dot.layoutParams = LinearLayout.LayoutParams(ctx.dp(9), ctx.dp(9))
        card.addView(dot)
        val cardText = text(ctx, if (vms.isNotEmpty()) "Visual Voicemail connected" else "Visual Voicemail", 13.5f, th.primary, bold = true)
        val tLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tLp.leftMargin = ctx.dp(10)
        cardText.layoutParams = tLp
        card.addView(cardText)
        if (dual) card.addView(text(ctx, "DUAL SIM", 11f, th.muted, bold = true))
        listBox.addView(card)

        if (vms.isEmpty()) {
            val msg = text(ctx, "No visual voicemail messages on this line yet.\n\n" +
                "Visual voicemail depends on your carrier and plan. If your plan doesn't include it, " +
                "you can still listen the classic way:", 14f, th.muted)
            msg.setPadding(ctx.dp(22), ctx.dp(18), ctx.dp(22), 0)
            listBox.addView(msg)
            val btn = text(ctx, "Call voicemail", 15f, Color.WHITE, bold = true)
            btn.background = rounded(th.primary, 999, ctx)
            btn.setPadding(ctx.dp(20), ctx.dp(12), ctx.dp(20), ctx.dp(12))
            val bLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            bLp.setMargins(ctx.dp(22), ctx.dp(14), 0, 0)
            btn.layoutParams = bLp
            btn.setOnClickListener {
                val vmNumber = try {
                    val tm = ctx.getSystemService(TelephonyManager::class.java)
                    tm?.voiceMailNumber
                } catch (e: Exception) { null }
                if (!vmNumber.isNullOrBlank()) Calls.place(ctx, vmNumber)
                else Toast.makeText(ctx, "Your carrier hasn't set a voicemail number on this SIM", Toast.LENGTH_LONG).show()
            }
            listBox.addView(btn)
            return
        }

        var lastDay = ""
        for (vm in vms) {
            val day = fmtDay(vm.dateMs)
            if (day != lastDay) {
                lastDay = day
                val label = text(ctx, day.uppercase(), 12f, th.faint, bold = true)
                label.setPadding(ctx.dp(24), ctx.dp(14), 0, ctx.dp(2))
                listBox.addView(label)
            }
            listBox.addView(vmRow(vm))
        }
    }

    private fun vmRow(vm: VmEntry): View {
        val ctx = act
        val name = CallLogRepo.contactName(ctx, vm.number)
        val display = name.ifBlank { Calls.fmtNumber(vm.number) }
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.TOP
        row.setPadding(ctx.dp(22), ctx.dp(11), ctx.dp(22), ctx.dp(11))
        row.addView(avatarView(ctx, initialsOf(display, vm.number), avatarColorFor(vm.number),
            Color.parseColor("#17352A"), 48, 15f))
        val main = LinearLayout(ctx)
        main.orientation = LinearLayout.VERTICAL
        val mLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        mLp.leftMargin = ctx.dp(14)
        main.layoutParams = mLp
        main.addView(text(ctx, display, 16.5f, th.onSurface, bold = true))
        main.addView(text(ctx, Calls.fmtNumber(vm.number), 13.5f, th.muted))
        val chips = LinearLayout(ctx)
        chips.orientation = LinearLayout.HORIZONTAL
        val cLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        cLp.topMargin = ctx.dp(6)
        chips.layoutParams = cLp
        if (vm.isNew) chips.addView(chip(ctx, "NEW", th.softContainer, th.primary, th))
        main.addView(chips)
        row.addView(main)

        val meta = LinearLayout(ctx)
        meta.orientation = LinearLayout.VERTICAL
        meta.gravity = Gravity.END
        meta.addView(text(ctx, fmtClock(vm.dateMs), 12.5f, th.faint))
        val dur = text(ctx, fmtDuration(vm.durationSec), 12.5f, th.muted, bold = true)
        val dLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dLp.topMargin = ctx.dp(3); dLp.gravity = Gravity.END
        dur.layoutParams = dLp
        meta.addView(dur)
        val playBtn = android.widget.FrameLayout(ctx)
        playBtn.background = circleBg(th.surface)
        playBtn.background = rounded(th.surface, 999, ctx, strokeColor = th.primary)
        val pLp = LinearLayout.LayoutParams(ctx.dp(36), ctx.dp(36))
        pLp.topMargin = ctx.dp(6); pLp.gravity = Gravity.END
        playBtn.layoutParams = pLp
        val piv = android.widget.ImageView(ctx)
        piv.setImageResource(if (playingId == vm.id) R.drawable.ic_pause else R.drawable.ic_play)
        piv.setColorFilter(th.primary)
        val pilp = android.widget.FrameLayout.LayoutParams(ctx.dp(16), ctx.dp(16))
        pilp.gravity = Gravity.CENTER
        piv.layoutParams = pilp
        playBtn.addView(piv)
        playBtn.setOnClickListener { togglePlay(vm) }
        meta.addView(playBtn)
        row.addView(meta)

        row.setOnClickListener {
            val i = Intent(ctx, DetailActivity::class.java)
            i.putExtra("type", "voicemail")
            i.putExtra("vmUri", vm.uri.toString())
            i.putExtra("vmId", vm.id)
            i.putExtra("number", vm.number)
            i.putExtra("name", name)
            i.putExtra("dateMs", vm.dateMs)
            i.putExtra("durationSec", vm.durationSec)
            ctx.startActivity(i)
        }
        return row
    }

    private fun togglePlay(vm: VmEntry) {
        val ctx = act
        try {
            if (playingId == vm.id) {
                player?.pause()
                playingId = -1
            } else {
                player?.release()
                player = MediaPlayer()
                player?.setDataSource(ctx, vm.uri)
                player?.setOnCompletionListener {
                    playingId = -1
                    load()
                }
                player?.prepare()
                player?.start()
                playingId = vm.id
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "Could not play this voicemail", Toast.LENGTH_SHORT).show()
            playingId = -1
        }
        load()
    }
}
