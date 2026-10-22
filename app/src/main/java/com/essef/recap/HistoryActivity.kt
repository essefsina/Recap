package com.essef.recap

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.CallLog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

class HistoryActivity : Activity() {

    private lateinit var th: ThemeColors
    private lateinit var listBox: LinearLayout
    private var number: String = ""
    private var name: String = ""
    private lateinit var blockBtn: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        th = Themes.get(this)
        number = intent.getStringExtra("number") ?: ""
        name = intent.getStringExtra("name") ?: ""

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(th.bg)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(14), dp(10), dp(14), dp(4))
        val back = ImageView(this)
        back.setImageResource(R.drawable.ic_back)
        back.setColorFilter(th.onSurface)
        back.background = circleBg(th.surface)
        back.setPadding(dp(10), dp(10), dp(10), dp(10))
        back.layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        back.setOnClickListener { finish() }
        head.addView(back)
        val ht = text(this, "Call history", 15.5f, th.onSurface, bold = true)
        val hLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        hLp.leftMargin = dp(12)
        ht.layoutParams = hLp
        head.addView(ht)
        // Delete ALL call logs for this number
        val delAll = ImageView(this)
        delAll.setImageResource(R.drawable.ic_trash)
        delAll.setColorFilter(Rows.RED_DARK)
        delAll.background = circleBg(Rows.RED_BG)
        delAll.setPadding(dp(10), dp(10), dp(10), dp(10))
        delAll.layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        delAll.setOnClickListener { confirmDeleteAll() }
        head.addView(delAll)
        root.addView(head)

        val contact = LinearLayout(this)
        contact.orientation = LinearLayout.HORIZONTAL
        contact.gravity = Gravity.CENTER_VERTICAL
        contact.setPadding(dp(22), dp(14), dp(22), dp(2))
        val display = name.ifBlank {
            CallLogRepo.contactName(this, number).ifBlank { Calls.fmtNumber(number) }
        }
        contact.addView(avatarView(this, initialsOf(display, number), avatarColorFor(number + display),
            Color.parseColor("#17352A"), 56, 17f))
        val cm = LinearLayout(this)
        cm.orientation = LinearLayout.VERTICAL
        val cmLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        cmLp.leftMargin = dp(14)
        cm.layoutParams = cmLp
        cm.addView(text(this, display, 20f, th.onSurface, bold = true))
        cm.addView(text(this, Calls.fmtNumber(number), 14f, th.muted))
        contact.addView(cm)
        root.addView(contact)

        val label = text(this, "ALL CALLS WITH THIS NUMBER", 12f, th.faint, bold = true)
        label.setPadding(dp(24), dp(16), 0, dp(4))
        root.addView(label)

        val scroll = ScrollView(this)
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        listBox.setPadding(0, dp(4), 0, dp(16))
        scroll.addView(listBox)
        root.addView(scroll)

        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.setPadding(dp(18), dp(10), dp(18), dp(20))
        val callBack = text(this, "Call back", 15.5f, Color.WHITE, bold = true)
        callBack.gravity = Gravity.CENTER
        callBack.background = rounded(th.primary, 999, this)
        callBack.setPadding(0, dp(15), 0, dp(15))
        callBack.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        callBack.setOnClickListener { Calls.place(this, number) }
        actions.addView(callBack)
        blockBtn = text(this, "Block number", 15.5f, th.onSurface, bold = true)
        blockBtn.gravity = Gravity.CENTER
        blockBtn.background = rounded(th.softContainer, 999, this)
        blockBtn.setPadding(0, dp(15), 0, dp(15))
        val bLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        bLp.leftMargin = dp(10)
        blockBtn.layoutParams = bLp
        blockBtn.setOnClickListener { toggleBlock() }
        actions.addView(blockBtn)
        root.addView(actions)

        setContentView(root)
        refreshBlockLabel()
        load()
    }

    private fun refreshBlockLabel() {
        val blocked = CallLogRepo.blockedNumbers(this).contains(RecStore.digits(number))
        blockBtn.text = if (blocked) "Unblock number" else "Block number"
    }

    private fun toggleBlock() {
        val blocked = CallLogRepo.blockedNumbers(this).contains(RecStore.digits(number))
        val ok = if (blocked) CallLogRepo.unblock(this, number) else CallLogRepo.block(this, number)
        Toast.makeText(
            this,
            if (ok) (if (blocked) "Number unblocked" else "Number blocked — it will no longer ring you")
            else "Blocking isn't available until Recap is your phone app",
            Toast.LENGTH_SHORT
        ).show()
        refreshBlockLabel()
    }

    private fun deleteByIds(ids: List<Long>, doneMsg: String) {
        Thread {
            var deleted = 0
            try {
                for (id in ids) {
                    deleted += contentResolver.delete(
                        CallLog.Calls.CONTENT_URI, CallLog.Calls._ID + " = ?", arrayOf(id.toString())
                    )
                }
            } catch (e: SecurityException) { }
            val d = deleted
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (d > 0) doneMsg else "Couldn't delete — Recap needs to be your phone app with call log permission",
                    Toast.LENGTH_SHORT
                ).show()
                load()
            }
        }.start()
    }

    private fun confirmDeleteAll() {
        Popups.confirm(
            this, th, "Delete call history?",
            "Every call log entry for ${Calls.fmtNumber(number)} will be removed from this phone's call log. Recordings are kept.",
            "Delete", danger = true
        ) {
            val ids = CallLogRepo.historyFor(this, number).map { it.id }
            if (ids.isEmpty()) {
                Toast.makeText(this, "Nothing to delete", Toast.LENGTH_SHORT).show()
            } else deleteByIds(ids, "Call history deleted")
        }
    }

    private fun confirmDeleteOne(e: CallEntry) {
        Popups.confirm(
            this, th, "Delete this call?",
            "This single call log entry (${fmtDay(e.dateMs)}, ${fmtClock(e.dateMs)}) will be removed. Any recording of it is kept.",
            "Delete", danger = true
        ) {
            deleteByIds(listOf(e.id), "Call deleted")
        }
    }

    private fun load() {
        Thread {
            val entries = CallLogRepo.historyFor(this, number)
            runOnUiThread { render(entries) }
        }.start()
    }

    private fun render(entries: List<CallEntry>) {
        listBox.removeAllViews()
        if (entries.isEmpty()) {
            val t = text(this, "No calls with this number in the call log.", 14f, th.muted)
            t.setPadding(dp(22), dp(14), 0, 0)
            listBox.addView(t)
            return
        }
        for (e in entries) {
            val rec = if (e.isMissed) null else RecStore.match(this, e.number, e.dateMs)
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.TOP
            row.setPadding(dp(22), dp(11), dp(14), dp(11))

            val (bg, arrow) = when {
                e.isMissed -> Rows.RED to "↩"
                e.isOutgoing -> Rows.BLUE to "↗"
                else -> Rows.GREEN to "↙"
            }
            val badge = text(this, arrow, 14f, Color.WHITE, bold = true)
            badge.gravity = Gravity.CENTER
            badge.background = circleBg(bg)
            badge.layoutParams = LinearLayout.LayoutParams(dp(31), dp(31))
            row.addView(badge)

            val main = LinearLayout(this)
            main.orientation = LinearLayout.VERTICAL
            val mLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            mLp.leftMargin = dp(12)
            main.layoutParams = mLp
            main.addView(text(this, "${fmtDay(e.dateMs)} · ${fmtClock(e.dateMs)}", 15f, th.onSurface, bold = true))
            val sub = when {
                e.isMissed -> "Missed call"
                e.isOutgoing -> "Outgoing · ${fmtDuration(e.durationSec)}"
                else -> "Incoming · ${fmtDuration(e.durationSec)}"
            }
            main.addView(text(this, sub, 12.5f, th.muted))
            if (rec != null) {
                if (rec.title.isNotBlank()) {
                    main.addView(text(this, "✦ " + rec.title, 13.5f, th.primary, bold = true))
                }
                val chips = LinearLayout(this)
                chips.orientation = LinearLayout.HORIZONTAL
                val cLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                cLp.topMargin = dp(6)
                chips.layoutParams = cLp
                chips.addView(chip(this, "● REC", Rows.RED_BG, Rows.RED_DARK, th))
                if (rec.hasTranscript) {
                    val sp = View(this); sp.layoutParams = LinearLayout.LayoutParams(dp(6), 1)
                    chips.addView(sp); chips.addView(chip(this, "Transcribed", th.softContainer, th.muted, th))
                }
                if (rec.hasSummary) {
                    val sp = View(this); sp.layoutParams = LinearLayout.LayoutParams(dp(6), 1)
                    chips.addView(sp); chips.addView(chip(this, "Summarized", th.softContainer, th.muted, th))
                }
                main.addView(chips)
            }
            row.addView(main)

            val sim = CallLogRepo.simLabelFor(this, e.accountId)
            if (sim.isNotEmpty()) {
                row.addView(Rows.simChip(this, sim, th))
            }
            if (rec != null) {
                val chev = text(this, "›", 24f, Color.parseColor("#B9C8BE"))
                chev.setPadding(dp(6), 0, 0, 0)
                row.addView(chev)
            }
            val del = ImageView(this)
            del.setImageResource(R.drawable.ic_trash)
            del.setColorFilter(th.faint)
            del.setPadding(dp(8), dp(4), dp(2), dp(4))
            del.layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            del.setOnClickListener { confirmDeleteOne(e) }
            row.addView(del)

            if (rec != null) {
                row.setOnClickListener {
                    val i = Intent(this, DetailActivity::class.java)
                    i.putExtra("type", "recording")
                    i.putExtra("recId", rec.id)
                    startActivity(i)
                }
            }
            listBox.addView(row)
        }
    }
}
