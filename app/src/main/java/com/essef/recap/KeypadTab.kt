package com.essef.recap

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

class KeypadTab(private val act: Activity, private val th: ThemeColors) {

    private var dialed = ""
    private lateinit var display: TextView
    private lateinit var addContact: TextView
    private lateinit var suggest: LinearLayout
    private lateinit var suggestText: TextView
    private lateinit var suggestSub: TextView
    private lateinit var frequentBox: LinearLayout
    private lateinit var simCell: LinearLayout
    private lateinit var simLabelTv: TextView

    fun create(onCall: (String) -> Unit): View {
        val ctx = act
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(0, ctx.dp(14), 0, 0)

        display = text(ctx, "", 37f, th.onSurface)
        display.gravity = Gravity.CENTER
        display.minHeight = ctx.dp(52)
        root.addView(display)

        addContact = text(ctx, "+ Add to contacts", 14f, th.primary, bold = true)
        addContact.gravity = Gravity.CENTER
        addContact.setPadding(0, ctx.dp(6), 0, ctx.dp(2))
        addContact.visibility = View.INVISIBLE
        addContact.setOnClickListener {
            if (dialed.isNotEmpty()) {
                val i = android.content.Intent(android.content.Intent.ACTION_INSERT)
                i.type = android.provider.ContactsContract.Contacts.CONTENT_TYPE
                i.putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, dialed)
                ctx.startActivity(i)
            }
        }
        root.addView(addContact)

        // Smart suggestion: who this number is + what the last call was about.
        suggest = LinearLayout(ctx)
        suggest.orientation = LinearLayout.HORIZONTAL
        suggest.gravity = Gravity.CENTER_VERTICAL
        suggest.background = rounded(th.surface, 22, ctx, strokeColor = android.graphics.Color.parseColor("#E4EAE5"))
        suggest.setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
        val sLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        sLp.setMargins(ctx.dp(22), ctx.dp(12), ctx.dp(22), 0)
        suggest.layoutParams = sLp
        suggestText = text(ctx, "", 15f, th.onSurface, bold = true)
        suggestSub = text(ctx, "", 13f, th.muted)
        val sMain = LinearLayout(ctx)
        sMain.orientation = LinearLayout.VERTICAL
        sMain.addView(suggestText)
        sMain.addView(suggestSub)
        suggest.addView(sMain)
        suggest.visibility = View.GONE
        root.addView(suggest)

        // Frequent calls: the numbers this phone calls most, one tap to call.
        frequentBox = LinearLayout(ctx)
        frequentBox.orientation = LinearLayout.VERTICAL
        val fLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        fLp.setMargins(ctx.dp(22), ctx.dp(14), ctx.dp(22), 0)
        frequentBox.layoutParams = fLp
        frequentBox.visibility = View.GONE
        root.addView(frequentBox)
        Thread {
            val freq = CallLogRepo.frequents(ctx)
            ctx.runOnUiThread { renderFrequents(freq) }
        }.start()

        // Flexible space above the pad (heavier than the bottom one, so the
        // pad sits slightly lower than center — Essef, Oct 22).
        root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.3f)
        })

        val pad = GridLayout(ctx)
        pad.columnCount = 3
        val pLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        pLp.setMargins(ctx.dp(34), 0, ctx.dp(34), 0)
        pad.layoutParams = pLp
        val letters = mapOf("2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "0" to "+")
        for (k in listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")) {
            val key = dialKey(ctx, th, k, letters[k]) {
                dialed += k
                updateSuggestion()
            }
            key.layoutParams = gridCellParams(ctx, 68)
            key.setOnLongClickListener {
                if (k == "0" && dialed.isEmpty()) {
                    dialed = "+"
                    updateSuggestion()
                    true
                } else false
            }
            pad.addView(key)
        }
        root.addView(pad)

        // Call row: SIM chip (dual SIM only) | call button | backspace — call stays centered.
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        val rLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rLp.topMargin = ctx.dp(14)
        row.layoutParams = rLp

        simCell = LinearLayout(ctx)
        simCell.orientation = LinearLayout.VERTICAL
        simCell.gravity = Gravity.CENTER
        simCell.layoutParams = LinearLayout.LayoutParams(ctx.dp(66), ctx.dp(66))
        val simTop = text(ctx, "SIM", 9.5f, th.faint, bold = true)
        simTop.gravity = Gravity.CENTER
        simCell.addView(simTop)
        simLabelTv = text(ctx, "", 15f, th.primary, bold = true)
        simLabelTv.gravity = Gravity.CENTER
        simCell.addView(simLabelTv)
        simCell.setOnClickListener {
            Calls.showDefaultSimPicker(ctx) { refreshSimChip() }
        }
        row.addView(simCell)
        refreshSimChip()

        val callBtn = android.widget.FrameLayout(ctx)
        callBtn.background = circleBg(th.primary)
        callBtn.layoutParams = LinearLayout.LayoutParams(ctx.dp(66), ctx.dp(66))
        val civ = android.widget.ImageView(ctx)
        civ.setImageResource(R.drawable.ic_phone)
        civ.setColorFilter(android.graphics.Color.WHITE)
        val cilp = android.widget.FrameLayout.LayoutParams(ctx.dp(28), ctx.dp(28))
        cilp.gravity = Gravity.CENTER
        civ.layoutParams = cilp
        callBtn.addView(civ)
        callBtn.setOnClickListener { if (dialed.isNotEmpty()) onCall(dialed) }
        row.addView(callBtn)

        val back = text(ctx, "⌫", 26f, th.muted)
        back.gravity = Gravity.CENTER
        back.layoutParams = LinearLayout.LayoutParams(ctx.dp(66), ctx.dp(66))
        back.setOnClickListener {
            if (dialed.isNotEmpty()) {
                dialed = dialed.dropLast(1)
                updateSuggestion()
            }
        }
        back.setOnLongClickListener {
            dialed = ""
            updateSuggestion()
            true
        }
        row.addView(back)
        root.addView(row)

        // Flexible space below the pad (lighter than the top one).
        root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.7f)
        })

        return root
    }

    private fun refreshSimChip() {
        val label = Calls.simShortLabel(act)
        simCell.visibility = if (label.isEmpty()) View.INVISIBLE else View.VISIBLE
        simLabelTv.text = label
    }

    private fun renderFrequents(freq: List<FrequentEntry>) {
        val ctx = act
        frequentBox.removeAllViews()
        if (freq.isEmpty()) {
            frequentBox.visibility = View.GONE
            return
        }
        frequentBox.visibility = View.VISIBLE
        val label = text(ctx, "FREQUENT CALLS", 11f, th.faint, bold = true)
        frequentBox.addView(label)
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        val rLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rLp.topMargin = ctx.dp(10)
        row.layoutParams = rLp
        for (f in freq) {
            val col = LinearLayout(ctx)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            col.addView(avatarView(ctx, initialsOf(f.name, f.number), avatarColorFor(f.number + f.name),
                android.graphics.Color.parseColor("#17352A"), 52, 15f))
            val nm = text(ctx, f.name.ifBlank { Calls.fmtNumber(f.number) }, 11f, th.muted, bold = true)
            nm.gravity = Gravity.CENTER
            nm.maxLines = 1
            nm.ellipsize = android.text.TextUtils.TruncateAt.END
            val nLp = LinearLayout.LayoutParams(ctx.dp(68), ViewGroup.LayoutParams.WRAP_CONTENT)
            nLp.topMargin = ctx.dp(5)
            nm.layoutParams = nLp
            col.addView(nm)
            // Frequent calls follow the dialpad's selected SIM — never a popup.
            col.setOnClickListener { Calls.placeFrequent(ctx, f.number) }
            row.addView(col)
        }
        frequentBox.addView(row)
    }

    private fun updateSuggestion() {
        val ctx = act
        display.text = when {
            dialed.length == 10 && dialed.all { it.isDigit() } -> Calls.fmtNumber(dialed)
            else -> dialed
        }
        addContact.visibility = if (dialed.isNotEmpty()) View.VISIBLE else View.INVISIBLE
        if (RecStore.digits(dialed).length < 7) {
            suggest.visibility = View.GONE
            return
        }
        Thread {
            val name = CallLogRepo.contactName(ctx, dialed)
            val hist = CallLogRepo.historyFor(ctx, dialed)
            val rec = hist.firstOrNull()?.let { RecStore.match(ctx, it.number, it.dateMs) }
            val detected = rec?.detectedCaller ?: ""
            val who = name.ifBlank { detected }
            ctx.runOnUiThread {
                if (who.isNotBlank()) {
                    suggest.visibility = View.VISIBLE
                    suggestText.text = who
                    val lastTitle = rec?.title?.takeIf { it.isNotBlank() }
                    suggestSub.text = when {
                        lastTitle != null -> "Last call: “$lastTitle”"
                        hist.isNotEmpty() -> "Last call: ${fmtDay(hist.first().dateMs)}, ${fmtClock(hist.first().dateMs)}"
                        else -> "Saved contact"
                    }
                } else {
                    suggest.visibility = View.GONE
                }
            }
        }.start()
    }
}
