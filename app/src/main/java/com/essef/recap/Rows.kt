package com.essef.recap

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

object Rows {
    val GREEN: Int = Color.parseColor("#2E9E62")
    val BLUE: Int = Color.parseColor("#3E7CB1")
    val RED: Int = Color.parseColor("#D93A2B")
    val RED_DARK: Int = Color.parseColor("#C03428")
    val RED_BG: Int = Color.parseColor("#FDECEA")

    fun dirBadge(ctx: Context, e: CallEntry, th: ThemeColors): View {
        val (bg, arrow) = when {
            e.isMissed -> RED to "↩"
            e.isOutgoing -> BLUE to "↗"
            else -> GREEN to "↙"
        }
        val t = TextView(ctx)
        t.text = arrow
        t.textSize = 10.5f
        t.setTextColor(Color.WHITE)
        t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        t.gravity = Gravity.CENTER
        t.background = circleBg(bg)
        val lp = FrameLayout.LayoutParams(ctx.dp(20), ctx.dp(20))
        lp.gravity = Gravity.BOTTOM or Gravity.END
        lp.rightMargin = -ctx.dp(2)
        lp.bottomMargin = -ctx.dp(2)
        t.layoutParams = lp
        return t
    }

    fun avatarWithBadge(ctx: Context, name: String, number: String, e: CallEntry, th: ThemeColors): View {
        val frame = FrameLayout(ctx)
        frame.layoutParams = LinearLayout.LayoutParams(ctx.dp(52), ctx.dp(52))
        val av = avatarView(ctx, initialsOf(name, number), avatarColorFor(number + name), Color.parseColor("#17352A"), 48, 15f)
        frame.addView(av)
        frame.addView(dirBadge(ctx, e, th))
        return frame
    }

    fun simChip(ctx: Context, label: String, th: ThemeColors): TextView {
        val t = text(ctx, label, 10f, th.muted, bold = true)
        t.background = rounded(th.surface, 6, ctx, strokeColor = Color.parseColor("#B9C8BE"))
        t.setPadding(ctx.dp(7), ctx.dp(2), ctx.dp(7), ctx.dp(2))
        return t
    }

    /** The approved call row: Name (count) / Number / AI title-or-status, with meta on the right. */
    fun groupRow(
        ctx: Context, th: ThemeColors, g: CallGroup,
        contactName: String, recording: Recording?, simLabel: String,
        onClick: () -> Unit
    ): View {
        val e = g.latest
        val name = contactName.ifBlank { e.cachedName }
        val displayName = when {
            name.isNotBlank() -> name
            recording != null && recording.detectedCaller.isNotBlank() -> recording.detectedCaller
            else -> Calls.fmtNumber(e.number)
        }
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(ctx.dp(22), ctx.dp(11), ctx.dp(22), ctx.dp(11))
        row.gravity = Gravity.TOP
        row.addView(avatarWithBadge(ctx, displayName, e.number, e, th))

        val main = LinearLayout(ctx)
        main.orientation = LinearLayout.VERTICAL
        val mLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        mLp.leftMargin = ctx.dp(14)
        main.layoutParams = mLp

        val titleLine = LinearLayout(ctx)
        titleLine.orientation = LinearLayout.HORIZONTAL
        val nt = text(ctx, displayName, 16.5f, th.onSurface, bold = true)
        titleLine.addView(nt)
        if (g.count > 1) {
            val ct = text(ctx, " (${g.count})", 14.5f, th.faint, bold = true)
            titleLine.addView(ct)
        }
        main.addView(titleLine)

        val numT = text(ctx, Calls.fmtNumber(e.number), 13.5f, th.muted)
        val nLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        nLp.topMargin = ctx.dp(2)
        numT.layoutParams = nLp
        main.addView(numT)

        val third: TextView = when {
            e.isMissed -> text(ctx, "Missed call", 13.5f, RED_DARK, bold = true)
            recording != null && recording.title.isNotBlank() ->
                text(ctx, "✦ " + recording.title, 13.5f, th.primary, bold = true)
            recording != null -> text(ctx, "✦ No title yet", 13.5f, th.faint, bold = true)
            else -> text(ctx, "✦ No title yet", 13.5f, th.faint, bold = true)
        }
        val tLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tLp.topMargin = ctx.dp(2)
        third.layoutParams = tLp
        main.addView(third)

        val chips = LinearLayout(ctx)
        chips.orientation = LinearLayout.HORIZONTAL
        val cLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        cLp.topMargin = ctx.dp(6)
        chips.layoutParams = cLp
        if (recording != null) {
            chips.addView(chip(ctx, "● REC", RED_BG, RED_DARK, th))
            if (recording.hasTranscript) {
                val sp = View(ctx); sp.layoutParams = LinearLayout.LayoutParams(ctx.dp(6), 1)
                chips.addView(sp)
                chips.addView(chip(ctx, "Transcribed", th.softContainer, th.muted, th))
            }
            if (recording.hasSummary) {
                val sp = View(ctx); sp.layoutParams = LinearLayout.LayoutParams(ctx.dp(6), 1)
                chips.addView(sp)
                chips.addView(chip(ctx, "Summarized", th.softContainer, th.muted, th))
            }
        }
        if (chips.childCount > 0) main.addView(chips)
        row.addView(main)

        val meta = LinearLayout(ctx)
        meta.orientation = LinearLayout.VERTICAL
        meta.gravity = Gravity.END
        meta.addView(text(ctx, fmtClock(e.dateMs), 12.5f, th.faint))
        val dur = text(ctx, if (e.isMissed) "—" else fmtDuration(e.durationSec), 12.5f, th.muted, bold = true)
        val dLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dLp.topMargin = ctx.dp(3); dLp.gravity = Gravity.END
        dur.layoutParams = dLp
        meta.addView(dur)
        if (simLabel.isNotEmpty()) {
            val sc = simChip(ctx, simLabel, th)
            val sLp2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            sLp2.topMargin = ctx.dp(4); sLp2.gravity = Gravity.END
            sc.layoutParams = sLp2
            meta.addView(sc)
        }
        row.addView(meta)

        row.setOnClickListener { onClick() }
        return row
    }
}
