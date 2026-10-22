package com.essef.recap

import android.app.Activity
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class RecentsTab(private val act: Activity, private val th: ThemeColors) {

    private val filters = listOf("All", "Incoming", "Missed", "Outgoing", "Contacts", "Spam", "Non-Spam")
    private var activeFilter = "All" // Always "All" when the app opens (approved behavior).
    private var query = ""
    private lateinit var listBox: LinearLayout
    private lateinit var chipsRow: LinearLayout
    private var allGroups: List<CallGroup> = emptyList()
    private var blocked: Set<String> = emptySet()
    private var loaded = false

    fun create(): View {
        val ctx = act
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL

        val search = EditText(ctx)
        search.hint = "Search calls, titles, transcripts…"
        search.setTextSize(14.5f)
        search.setSingleLine(true)
        search.background = rounded(th.softContainer, 999, ctx)
        search.setPadding(ctx.dp(18), ctx.dp(12), ctx.dp(18), ctx.dp(12))
        val sLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        sLp.setMargins(ctx.dp(18), ctx.dp(10), ctx.dp(18), 0)
        search.layoutParams = sLp
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim()?.lowercase() ?: ""
                render()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        root.addView(search)

        val hScroll = HorizontalScrollView(ctx)
        hScroll.isHorizontalScrollBarEnabled = false
        val hLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        hLp.topMargin = ctx.dp(10)
        hScroll.layoutParams = hLp
        chipsRow = LinearLayout(ctx)
        chipsRow.orientation = LinearLayout.HORIZONTAL
        chipsRow.setPadding(ctx.dp(18), 0, ctx.dp(18), 0)
        hScroll.addView(chipsRow)
        root.addView(hScroll)
        buildChips()

        val scroll = ScrollView(ctx)
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        listBox = LinearLayout(ctx)
        listBox.orientation = LinearLayout.VERTICAL
        listBox.setPadding(0, ctx.dp(6), 0, ctx.dp(16))
        scroll.addView(listBox)
        root.addView(scroll)

        load()
        return root
    }

    private fun buildChips() {
        val ctx = act
        chipsRow.removeAllViews()
        for (f in filters) {
            val on = f == activeFilter
            val chipTv = text(ctx, f, 13f, if (on) android.graphics.Color.WHITE else th.muted, bold = true)
            chipTv.background = rounded(if (on) th.primary else th.softContainer, 999, ctx)
            chipTv.setPadding(ctx.dp(15), ctx.dp(8), ctx.dp(15), ctx.dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = ctx.dp(7)
            chipTv.layoutParams = lp
            chipTv.setOnClickListener {
                activeFilter = f
                buildChips()
                render()
            }
            chipsRow.addView(chipTv)
        }
    }

    private fun load() {
        val ctx = act
        Thread {
            val entries = CallLogRepo.query(ctx)
            val groups = CallLogRepo.group(entries)
            val blk = CallLogRepo.blockedNumbers(ctx)
            ctx.runOnUiThread {
                allGroups = groups
                blocked = blk
                loaded = true
                render()
            }
        }.start()
    }

    private fun matches(g: CallGroup): Boolean {
        val e = g.latest
        val digits = RecStore.digits(e.number)
        val isSpam = blocked.contains(digits)
        val isContact = e.cachedName.isNotBlank()
        val filterOk = when (activeFilter) {
            "Incoming" -> e.isIncoming
            "Missed" -> e.isMissed
            "Outgoing" -> e.isOutgoing
            "Contacts" -> isContact
            "Spam" -> isSpam
            "Non-Spam" -> !isSpam
            else -> true
        }
        if (!filterOk) return false
        if (query.isEmpty()) return true
        val rec = RecStore.match(act, e.number, e.dateMs)
        val hay = listOf(
            e.cachedName, e.number, rec?.title ?: "", rec?.transcript ?: "", rec?.detectedCaller ?: ""
        ).joinToString(" ").lowercase()
        return hay.contains(query)
    }

    private fun render() {
        val ctx = act
        if (!loaded) {
            listBox.removeAllViews()
            val t = text(ctx, "Loading calls…", 14f, th.muted)
            t.setPadding(ctx.dp(22), ctx.dp(18), 0, 0)
            listBox.addView(t)
            return
        }
        listBox.removeAllViews()
        val shown = allGroups.filter { matches(it) }.take(150)
        if (shown.isEmpty()) {
            val t = text(ctx, when {
                allGroups.isEmpty() -> "No calls yet. When you make or receive calls as the default phone app, they will appear here."
                else -> "Nothing matches this filter."
            }, 14f, th.muted)
            t.setPadding(ctx.dp(22), ctx.dp(18), ctx.dp(22), 0)
            listBox.addView(t)
            return
        }
        var lastDay = ""
        for (g in shown) {
            val day = fmtDay(g.latest.dateMs)
            if (day != lastDay) {
                lastDay = day
                val label = text(ctx, day.uppercase(), 12f, th.faint, bold = true)
                label.setPadding(ctx.dp(24), ctx.dp(14), 0, ctx.dp(2))
                listBox.addView(label)
            }
            val e = g.latest
            val rec = RecStore.match(ctx, e.number, e.dateMs)
            val sim = CallLogRepo.simLabelFor(ctx, e.accountId)
            val rowView = Rows.groupRow(ctx, th, g, e.cachedName, rec, sim) {
                val i = Intent(ctx, HistoryActivity::class.java)
                i.putExtra("number", e.number)
                i.putExtra("name", e.cachedName)
                ctx.startActivity(i)
            }
            // Long-press a group = delete that number's call logs (with confirmation).
            rowView.setOnLongClickListener {
                Popups.confirm(
                    ctx, th, "Delete call logs?",
                    "Remove every call log entry for ${Calls.fmtNumber(e.number)}? Recordings are kept.",
                    "Delete", danger = true
                ) {
                    Thread {
                        var deleted = 0
                        try {
                            for (entry in g.entries) {
                                deleted += ctx.contentResolver.delete(
                                    android.provider.CallLog.Calls.CONTENT_URI,
                                    android.provider.CallLog.Calls._ID + " = ?",
                                    arrayOf(entry.id.toString())
                                )
                            }
                        } catch (ex: SecurityException) { }
                        val d = deleted
                        ctx.runOnUiThread {
                            android.widget.Toast.makeText(
                                ctx,
                                if (d > 0) "Call logs deleted" else "Couldn't delete — check call log permission",
                                android.widget.Toast.LENGTH_SHORT
                                ).show()
                                load()
                            }
                        }.start()
                    }
                true
            }
            listBox.addView(rowView)
        }
    }
}
