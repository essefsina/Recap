package com.essef.recap

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.Call
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Floating call bubble (Essef, Oct 21): when a call is live and the user
 * leaves the in-call screen — a game, a fullscreen app — this overlay keeps
 * the call visible and one tap away. Drag to move it, tap to return to the
 * call screen, tap the red button to hang up from anywhere.
 */
object FloatBubble {

    private var wm: WindowManager? = null
    private var rootView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var durTv: TextView? = null
    private var shownMode: String = "" // "ringing" | "live"
    private var shownAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            updateDuration()
            handler.postDelayed(this, 500)
        }
    }

    fun refresh(appCtx: Context) {
        val ctx = appCtx.applicationContext
        val call = CallHub.call
        val ok = call != null &&
            call.state != Call.STATE_DISCONNECTED &&
            call.state != Call.STATE_DISCONNECTING &&
            !InCallActivity.uiVisible &&
            Prefs.get(ctx).floatBubble &&
            Settings.canDrawOverlays(ctx)
        if (ok) {
            // Ringing and live calls wear different bubbles; rebuild on change.
            val mode = if (call?.state == Call.STATE_RINGING) "ringing" else "live"
            if (rootView != null && shownMode != mode) hide()
            show(ctx)
        } else hide()
    }

    fun hide() {
        handler.removeCallbacks(tick)
        val v = rootView
        if (v != null) {
            try { wm?.removeViewImmediate(v) } catch (e: Exception) { }
        }
        rootView = null
        durTv = null
        params = null
        shownMode = ""
    }

    private fun show(ctx: Context) {
        if (rootView != null) return
        val call = CallHub.call ?: return
        val th = Themes.get(ctx)
        val m = ctx.getSystemService(WindowManager::class.java) ?: return
        wm = m
        shownMode = if (call.state == Call.STATE_RINGING) "ringing" else "live"
        if (shownMode == "ringing") {
            showRinging(ctx, call, th, m)
            return
        }

        val num = call.details?.handle?.schemeSpecificPart ?: ""
        val name = CallLogRepo.contactName(ctx, num).ifBlank { num }

        val container = LinearLayout(ctx)
        container.orientation = LinearLayout.VERTICAL
        container.gravity = Gravity.CENTER_HORIZONTAL

        val circleWrap = FrameLayout(ctx)
        circleWrap.layoutParams = LinearLayout.LayoutParams(ctx.dp(78), ctx.dp(78))
        val circle = TextView(ctx)
        circle.text = initialsOf(name, num)
        circle.textSize = 24f
        circle.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        circle.setTextColor(Color.WHITE)
        circle.gravity = Gravity.CENTER
        circle.background = circleBg(th.primary)
        circle.layoutParams = FrameLayout.LayoutParams(ctx.dp(78), ctx.dp(78))
        circleWrap.addView(circle)

        val endBtn = FrameLayout(ctx)
        endBtn.background = circleBg(Rows.RED)
        val eLp = FrameLayout.LayoutParams(ctx.dp(32), ctx.dp(32))
        eLp.gravity = Gravity.BOTTOM or Gravity.END
        endBtn.layoutParams = eLp
        val eIv = ImageView(ctx)
        eIv.setImageResource(R.drawable.ic_phone)
        eIv.setColorFilter(Color.WHITE)
        eIv.rotation = 135f
        val eiLp = FrameLayout.LayoutParams(ctx.dp(17), ctx.dp(17))
        eiLp.gravity = Gravity.CENTER
        eIv.layoutParams = eiLp
        endBtn.addView(eIv)
        endBtn.setOnClickListener {
            try { CallHub.call?.disconnect() } catch (e: Exception) { }
            hide()
        }
        circleWrap.addView(endBtn)
        container.addView(circleWrap)

        durTv = TextView(ctx)
        durTv!!.textSize = 12.5f
        durTv!!.setTextColor(Color.WHITE)
        durTv!!.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        durTv!!.gravity = Gravity.CENTER
        durTv!!.background = rounded(Color.parseColor("#CC13211B"), 999, ctx)
        durTv!!.setPadding(ctx.dp(10), ctx.dp(3), ctx.dp(10), ctx.dp(3))
        val dLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        dLp.topMargin = ctx.dp(3)
        durTv!!.layoutParams = dLp
        container.addView(durTv)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = ctx.resources.displayMetrics.widthPixels - ctx.dp(106)
        lp.y = ctx.dp(92)
        params = lp

        // Drag by the circle; a clean tap returns to the call screen.
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragged = false
        circle.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY
                    startX = lp.x; startY = lp.y; dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) dragged = true
                    if (dragged) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        try { m.updateViewLayout(container, lp) } catch (e: Exception) { }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) openCallUi(ctx)
                    true
                }
                else -> false
            }
        }

        try {
            m.addView(container, lp)
            rootView = container
            shownAt = System.currentTimeMillis()
            handler.removeCallbacks(tick)
            handler.post(tick)
        } catch (e: Exception) {
            rootView = null
        }
    }

    /**
     * Incoming-call bubble (Essef, Oct 22): when a call arrives over a game or
     * another fullscreen app, the call rides on top as a compact card —
     * answer or decline right there, or tap the card for the full call
     * screen. Nothing yanks him out of the game.
     */
    private fun showRinging(ctx: Context, call: Call, th: ThemeColors, m: WindowManager) {
        val num = call.details?.handle?.schemeSpecificPart ?: ""
        val name = CallLogRepo.contactName(ctx, num).ifBlank { Calls.fmtNumber(num) }

        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.background = rounded(Color.parseColor("#F213211B"), 30, ctx)
        card.setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))

        val avatar = TextView(ctx)
        avatar.text = initialsOf(name, num)
        avatar.textSize = 19f
        avatar.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        avatar.setTextColor(Color.WHITE)
        avatar.gravity = Gravity.CENTER
        avatar.background = circleBg(th.primary)
        avatar.layoutParams = LinearLayout.LayoutParams(ctx.dp(58), ctx.dp(58))
        card.addView(avatar)

        val texts = LinearLayout(ctx)
        texts.orientation = LinearLayout.VERTICAL
        val tLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        tLp.leftMargin = ctx.dp(12); tLp.rightMargin = ctx.dp(14)
        texts.layoutParams = tLp
        val nameTv = TextView(ctx)
        nameTv.text = name
        nameTv.textSize = 17f
        nameTv.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        nameTv.setTextColor(Color.WHITE)
        texts.addView(nameTv)
        val subTv = TextView(ctx)
        subTv.text = if (name == Calls.fmtNumber(num)) "Incoming call" else "Incoming call · ${Calls.fmtNumber(num)}"
        subTv.textSize = 13.5f
        subTv.setTextColor(Color.parseColor("#B9C6BE"))
        texts.addView(subTv)
        texts.setOnClickListener { openCallUi(ctx) }
        card.addView(texts)

        fun roundBtn(bg: Int, rotation: Float, action: () -> Unit): FrameLayout {
            val b = FrameLayout(ctx)
            b.background = circleBg(bg)
            b.layoutParams = LinearLayout.LayoutParams(ctx.dp(58), ctx.dp(58))
            val iv = ImageView(ctx)
            iv.setImageResource(R.drawable.ic_phone)
            iv.setColorFilter(Color.WHITE)
            iv.rotation = rotation
            val iLp = FrameLayout.LayoutParams(ctx.dp(25), ctx.dp(25))
            iLp.gravity = Gravity.CENTER
            iv.layoutParams = iLp
            b.addView(iv)
            b.setOnClickListener { action() }
            return b
        }
        val decline = roundBtn(Rows.RED, 135f) {
            try { call.reject(false, null) } catch (e: Exception) { }
            hide()
        }
        card.addView(decline)
        val gap = View(ctx)
        gap.layoutParams = LinearLayout.LayoutParams(ctx.dp(12), 1)
        card.addView(gap)
        val answer = roundBtn(Color.parseColor("#1FA855"), 0f) {
            try { call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY) } catch (e: Exception) { }
        }
        card.addView(answer)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = ctx.dp(44)
        params = lp
        try {
            m.addView(card, lp)
            rootView = card
            shownAt = System.currentTimeMillis()
            handler.removeCallbacks(tick)
            handler.post(tick)
        } catch (e: Exception) {
            rootView = null
        }
    }

    private fun openCallUi(ctx: Context) {
        val i = Intent(ctx, InCallActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try { ctx.startActivity(i) } catch (e: Exception) { }
    }

    private fun updateDuration() {
        val tv = durTv ?: return
        val call = CallHub.call
        if (call == null) {
            hide()
            return
        }
        tv.text = when (call.state) {
            Call.STATE_RINGING -> "Incoming…"
            Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling…"
            Call.STATE_HOLDING -> "On hold"
            else -> {
                val connect = call.details?.connectTimeMillis ?: 0L
                val base = if (connect > 0) connect else shownAt
                fmtDuration((System.currentTimeMillis() - base) / 1000)
            }
        }
    }
}
