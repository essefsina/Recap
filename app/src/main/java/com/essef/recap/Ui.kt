package com.essef.recap

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun rounded(color: Int, radiusDp: Int, ctx: Context, strokeColor: Int? = null, strokeDp: Int = 1): GradientDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.RECTANGLE
    g.cornerRadius = ctx.dp(radiusDp).toFloat()
    g.setColor(color)
    if (strokeColor != null) g.setStroke(ctx.dp(strokeDp), strokeColor)
    return g
}

fun circleBg(color: Int): GradientDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.OVAL
    g.setColor(color)
    return g
}

fun text(ctx: Context, s: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView {
    val t = TextView(ctx)
    t.text = s
    t.textSize = sizeSp
    t.setTextColor(color)
    if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    t.includeFontPadding = false
    return t
}

fun icon(ctx: Context, resId: Int, sizeDp: Int, tint: Int): ImageView {
    val iv = ImageView(ctx)
    iv.setImageResource(resId)
    iv.setColorFilter(tint)
    iv.layoutParams = ViewGroup.LayoutParams(ctx.dp(sizeDp), ctx.dp(sizeDp))
    return iv
}

fun chip(ctx: Context, label: String, bg: Int, fg: Int, th: ThemeColors): TextView {
    val t = text(ctx, label, 11f, fg, bold = true)
    t.background = rounded(bg, 999, ctx)
    t.setPadding(ctx.dp(9), ctx.dp(3), ctx.dp(9), ctx.dp(3))
    return t
}

fun hLine(ctx: Context, weight: Float = 1f): LinearLayout {
    val l = LinearLayout(ctx)
    l.orientation = LinearLayout.HORIZONTAL
    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
    l.layoutParams = lp
    return l
}

fun vSpacer(ctx: Context, hDp: Int): View {
    val v = View(ctx)
    v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(hDp))
    return v
}

fun avatarView(ctx: Context, initials: String, bg: Int, fg: Int, sizeDp: Int, textSp: Float): TextView {
    val t = text(ctx, initials, textSp, fg, bold = true)
    t.gravity = Gravity.CENTER
    t.background = circleBg(bg)
    t.layoutParams = LinearLayout.LayoutParams(ctx.dp(sizeDp), ctx.dp(sizeDp))
    return t
}

fun initialsOf(name: String, number: String): String {
    val n = name.trim()
    if (n.isEmpty() || n == number) return "?"
    val parts = n.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> ("" + parts[0][0] + parts[1][0]).uppercase()
        parts.isNotEmpty() -> parts[0].take(1).uppercase()
        else -> "?"
    }
}

val AVATAR_COLORS = listOf("#D9EDE2", "#FBE3C2", "#DCE7F7", "#F3DCF0", "#E4E7EB", "#FDE8C8", "#D8F0EE")

fun avatarColorFor(key: String): Int {
    var h = 0
    for (ch in key) h = (h * 31 + ch.code) and 0x7fffffff
    return Color.parseColor(AVATAR_COLORS[h % AVATAR_COLORS.size])
}

fun fmtDuration(sec: Long): String {
    if (sec <= 0) return "—"
    val m = sec / 60
    val s = sec % 60
    return if (m > 0) "%d:%02d".format(m, s) else "0:%02d".format(s)
}

fun fmtClock(ms: Long): String {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = ms
    val h24 = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val m = cal.get(java.util.Calendar.MINUTE)
    val ampm = if (h24 < 12) "AM" else "PM"
    val h = if (h24 % 12 == 0) 12 else h24 % 12
    return "%d:%02d %s".format(h, m, ampm)
}

fun fmtDay(ms: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    val sameDay = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    val yest = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    val isYest = yest.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        yest.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameDay -> "Today"
        isYest -> "Yesterday"
        else -> {
            val f = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.US)
            f.format(java.util.Date(ms))
        }
    }
}

/** Tone a stock switch without androidx: a simple checkable pill is built where needed instead. */
fun tintList(color: Int): ColorStateList = ColorStateList.valueOf(color)

/**
 * One dialpad key: a pill that fills its grid cell, with the digit (and its
 * letters) truly centered via a single gravity-centered TextView. v0.1 built
 * the key as a vertical LinearLayout inside a weighted GridLayout cell
 * without FILL alignment, which pinned the text to the key's left edge.
 */
fun dialKey(ctx: Context, th: ThemeColors, digit: String, letters: String?, onClick: () -> Unit): View {
    val frame = FrameLayout(ctx)
    // Squared rounded-rectangle keys with a border (Essef, Oct 22) — was a
    // full pill (radius 999).
    frame.background = rounded(th.surface, 18, ctx, strokeColor = Color.parseColor("#C4D2C9"))
    val tv = TextView(ctx)
    val body = if (letters.isNullOrEmpty()) digit else digit + "\n" + letters
    val span = SpannableString(body)
    if (!letters.isNullOrEmpty()) {
        val start = digit.length + 1
        span.setSpan(RelativeSizeSpan(0.34f), start, body.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(ForegroundColorSpan(th.faint), start, body.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(StyleSpan(Typeface.BOLD), start, body.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    tv.text = span
    tv.textSize = 28.5f
    tv.setTextColor(th.onSurface)
    tv.gravity = Gravity.CENTER
    tv.includeFontPadding = false
    tv.layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
    )
    frame.addView(tv)
    frame.setOnClickListener { onClick() }
    return frame
}

/** GridLayout params that actually stretch the child across its weighted cell. */
fun gridCellParams(ctx: Context, heightDp: Int, marginDp: Int = 6): android.widget.GridLayout.LayoutParams {
    val lp = android.widget.GridLayout.LayoutParams()
    lp.width = 0
    lp.height = ctx.dp(heightDp)
    lp.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1, android.widget.GridLayout.FILL, 1f)
    lp.setMargins(ctx.dp(marginDp), ctx.dp(marginDp), ctx.dp(marginDp), ctx.dp(marginDp))
    return lp
}
