package com.essef.recap

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

class RecordingsTab(private val act: Activity, private val th: ThemeColors) {

    private lateinit var listBox: LinearLayout

    fun create(): View {
        val ctx = act
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(ctx)
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        listBox = LinearLayout(ctx)
        listBox.orientation = LinearLayout.VERTICAL
        listBox.setPadding(0, ctx.dp(8), 0, ctx.dp(16))
        scroll.addView(listBox)
        root.addView(scroll)

        Thread {
            val recs = RecStore.all(ctx)
            ctx.runOnUiThread { render(recs) }
        }.start()
        return root
    }

    private fun render(recs: List<Recording>) {
        val ctx = act
        listBox.removeAllViews()
        if (recs.isEmpty()) {
            val t = text(ctx, "No recordings yet.\n\nCalls you record — automatically, or with the Record button during a call — will live here with their AI titles, transcripts, and summaries.", 14f, th.muted)
            t.setPadding(ctx.dp(22), ctx.dp(18), ctx.dp(22), 0)
            listBox.addView(t)
            return
        }
        var lastDay = ""
        for (r in recs) {
            val day = fmtDay(r.startMs)
            if (day != lastDay) {
                lastDay = day
                val label = text(ctx, day.uppercase(), 12f, th.faint, bold = true)
                label.setPadding(ctx.dp(24), ctx.dp(14), 0, ctx.dp(2))
                listBox.addView(label)
            }
            val name = r.contactName.ifBlank { r.detectedCaller }.ifBlank { Calls.fmtNumber(r.number) }
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.TOP
            row.setPadding(ctx.dp(22), ctx.dp(11), ctx.dp(22), ctx.dp(11))
            row.addView(avatarView(ctx, initialsOf(name, r.number), avatarColorFor(r.number + name),
                android.graphics.Color.parseColor("#17352A"), 48, 15f))
            val main = LinearLayout(ctx)
            main.orientation = LinearLayout.VERTICAL
            val mLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            mLp.leftMargin = ctx.dp(14)
            main.layoutParams = mLp
            val titleText = if (r.title.isNotBlank()) r.title else name
            main.addView(text(ctx, titleText, 16f, th.onSurface, bold = true))
            main.addView(text(ctx, "${Calls.fmtNumber(r.number)} · ${fmtDuration(r.durationSec)}", 13.5f, th.muted))
            val chips = LinearLayout(ctx)
            chips.orientation = LinearLayout.HORIZONTAL
            val cLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            cLp.topMargin = ctx.dp(6)
            chips.layoutParams = cLp
            chips.addView(chip(ctx, "● REC", Rows.RED_BG, Rows.RED_DARK, th))
            if (r.hasTranscript) {
                val sp = View(ctx); sp.layoutParams = LinearLayout.LayoutParams(ctx.dp(6), 1)
                chips.addView(sp); chips.addView(chip(ctx, "Transcribed", th.softContainer, th.muted, th))
            }
            if (r.hasSummary) {
                val sp = View(ctx); sp.layoutParams = LinearLayout.LayoutParams(ctx.dp(6), 1)
                chips.addView(sp); chips.addView(chip(ctx, "Summarized", th.softContainer, th.muted, th))
            }
            main.addView(chips)
            row.addView(main)
            row.addView(text(ctx, fmtClock(r.startMs), 12.5f, th.faint))
            row.setOnClickListener {
                val i = Intent(ctx, DetailActivity::class.java)
                i.putExtra("type", "recording")
                i.putExtra("recId", r.id)
                ctx.startActivity(i)
            }
            listBox.addView(row)
        }
    }
}
