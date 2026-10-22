package com.essef.recap

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/**
 * Contact view. Hero: the photo flanked by two icon columns (Essef's layout,
 * Oct 21) — left: Video call, Share · right: Block, Send-to-voicemail.
 * Delete lives in the header. All popups are Recap-styled (Popups), never
 * stock dialogs. Below: Call/Message pills, every saved number, full history.
 */
class ContactActivity : Activity() {

    private lateinit var th: ThemeColors
    private var name: String = ""
    private var number: String = ""
    private var contactId: Long = 0
    private var photoUri: String? = null
    private var allNumbers: List<Pair<String, String>> = emptyList()
    private lateinit var histBox: LinearLayout
    private var blockIv: ImageView? = null
    private var vmIv: ImageView? = null
    private var isBlocked = false
    private var sendToVm = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        th = Themes.get(this)
        name = intent.getStringExtra("name") ?: ""
        number = intent.getStringExtra("number") ?: ""
        contactId = intent.getLongExtra("contactId", 0)
        photoUri = intent.getStringExtra("photoUri")
        val display = name.ifBlank { Calls.fmtNumber(number) }
        val numbers = ContactsRepo.numbersFor(this, contactId)
        allNumbers = if (numbers.isEmpty()) listOf(number to "Phone") else numbers

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(th.bg)

        // Header: back + title + delete (icon only).
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
        val ht = text(this, "Contact", 15.5f, th.onSurface, bold = true)
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
        del.setOnClickListener { confirmDelete(display) }
        head.addView(del)
        root.addView(head)

        val scroll = ScrollView(this)
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(0, dp(10), 0, dp(28))
        scroll.addView(body)
        root.addView(scroll)

        // Hero row: [Video · Share] photo [Block · Voicemail] — icon columns
        // aligned with the photo, no text labels.
        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.HORIZONTAL
        hero.gravity = Gravity.CENTER
        val leftCol = heroColumn()
        val videoIv = heroIcon(leftCol, R.drawable.ic_video, th.primary) { showVideoOptions(display) }
        val shareIv = heroIcon(leftCol, R.drawable.ic_share, th.primary) { shareContact(display) }
        hero.addView(leftCol)

        val ring = LinearLayout(this)
        ring.gravity = Gravity.CENTER
        ring.background = circleBg(th.container)
        ring.layoutParams = LinearLayout.LayoutParams(dp(124), dp(124))
        ring.setPadding(dp(6), dp(6), dp(6), dp(6))
        val rLp = LinearLayout.LayoutParams(dp(124), dp(124))
        rLp.leftMargin = dp(26); rLp.rightMargin = dp(26)
        ring.layoutParams = rLp
        val inner = LinearLayout(this)
        inner.gravity = Gravity.CENTER
        inner.layoutParams = LinearLayout.LayoutParams(dp(112), dp(112))
        inner.addView(avatarView(this, initialsOf(display, number), avatarColorFor(number + display),
            Color.parseColor("#17352A"), 112, 36f))
        ring.addView(inner)
        hero.addView(ring)
        loadPhoto(photoUri, inner)

        val rightCol = heroColumn()
        blockIv = heroIcon(rightCol, R.drawable.ic_block, th.primary) { confirmBlock(display) }
        vmIv = heroIcon(rightCol, R.drawable.ic_voicemail, th.primary) { toggleSendToVoicemail(display) }
        hero.addView(rightCol)
        body.addView(hero)

        val nameTv = text(this, display, 25f, th.onSurface, bold = true)
        nameTv.gravity = Gravity.CENTER
        val nLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        nLp.topMargin = dp(16)
        nameTv.layoutParams = nLp
        body.addView(nameTv)
        val subTv = text(this, Calls.fmtNumber(number), 14.5f, th.muted)
        subTv.gravity = Gravity.CENTER
        val subLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        subLp.topMargin = dp(3)
        subTv.layoutParams = subLp
        body.addView(subTv)

        // Primary actions: Call / Message pills.
        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.gravity = Gravity.CENTER
        val aLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        aLp.topMargin = dp(20)
        actions.layoutParams = aLp
        actions.addView(actionPill(R.drawable.ic_phone, "Call", primary = true) { Calls.place(this, number) })
        val gap = View(this); gap.layoutParams = LinearLayout.LayoutParams(dp(12), 1)
        actions.addView(gap)
        actions.addView(actionPill(R.drawable.ic_message, "Message", primary = false) { Calls.sms(this, number) })
        body.addView(actions)

        // Numbers card.
        val secN = text(this, "NUMBERS", 11.5f, th.faint, bold = true)
        secN.setPadding(dp(24), dp(26), 0, dp(8))
        body.addView(secN)
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = rounded(th.surface, 22, this)
        val cLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        cLp.setMargins(dp(18), 0, dp(18), 0)
        card.layoutParams = cLp
        allNumbers.forEachIndexed { idx, (num, label) ->
            if (idx > 0) {
                val dv = View(this)
                dv.setBackgroundColor(Color.parseColor("#E4EAE5"))
                val dvLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                dvLp.leftMargin = dp(16)
                dv.layoutParams = dvLp
                card.addView(dv)
            }
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(16), dp(13), dp(12), dp(13))
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            col.addView(text(this, Calls.fmtNumber(num), 16f, th.onSurface, bold = true))
            col.addView(text(this, label, 12.5f, th.muted))
            r.addView(col)
            r.addView(iconCircle(R.drawable.ic_message) { Calls.sms(this, num) })
            val sp = View(this); sp.layoutParams = LinearLayout.LayoutParams(dp(8), 1)
            r.addView(sp)
            r.addView(iconCircle(R.drawable.ic_phone) { Calls.place(this, num) })
            card.addView(r)
        }
        body.addView(card)

        // Call history across all of this contact's numbers.
        val secH = text(this, "CALL HISTORY", 11.5f, th.faint, bold = true)
        secH.setPadding(dp(24), dp(26), 0, dp(4))
        body.addView(secH)
        histBox = LinearLayout(this)
        histBox.orientation = LinearLayout.VERTICAL
        body.addView(histBox)

        setContentView(root)

        Thread {
            val merged = allNumbers.flatMap { (num, _) -> CallLogRepo.historyFor(this, num) }
                .distinctBy { it.id }
                .sortedByDescending { it.dateMs }
            val blocked = CallLogRepo.blockedNumbers(this).contains(RecStore.digits(number))
            val vm = readSendToVm()
            runOnUiThread {
                renderHistory(merged)
                isBlocked = blocked
                sendToVm = vm
                refreshHeroIcons()
            }
        }.start()
    }

    private fun heroColumn(): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        return col
    }

    private fun heroIcon(parent: LinearLayout, iconRes: Int, tint: Int, onClick: () -> Unit): ImageView {
        val f = android.widget.FrameLayout(this)
        f.background = circleBg(th.softContainer)
        val fLp = LinearLayout.LayoutParams(dp(46), dp(46))
        if (parent.childCount > 0) fLp.topMargin = dp(14)
        f.layoutParams = fLp
        val iv = ImageView(this)
        iv.setImageResource(iconRes)
        iv.setColorFilter(tint)
        val ilp = android.widget.FrameLayout.LayoutParams(dp(21), dp(21))
        ilp.gravity = Gravity.CENTER
        iv.layoutParams = ilp
        f.addView(iv)
        f.setOnClickListener { onClick() }
        parent.addView(f)
        return iv
    }

    private fun refreshHeroIcons() {
        blockIv?.setColorFilter(if (isBlocked) Rows.RED else th.primary)
        vmIv?.setColorFilter(if (sendToVm) Rows.RED else th.primary)
    }

    private fun actionPill(iconRes: Int, label: String, primary: Boolean, onClick: () -> Unit): LinearLayout {
        val pill = LinearLayout(this)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER
        pill.background = rounded(if (primary) th.primary else th.softContainer, 999, this)
        pill.setPadding(dp(26), dp(13), dp(26), dp(13))
        val iv = ImageView(this)
        iv.setImageResource(iconRes)
        iv.setColorFilter(if (primary) Color.WHITE else th.primary)
        iv.scaleType = ImageView.ScaleType.CENTER_INSIDE
        iv.layoutParams = LinearLayout.LayoutParams(dp(19), dp(19))
        pill.addView(iv)
        val tv = text(this, label, 15f, if (primary) Color.WHITE else th.onSurface, bold = true)
        val tLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tLp.leftMargin = dp(8)
        tv.layoutParams = tLp
        pill.addView(tv)
        pill.setOnClickListener { onClick() }
        return pill
    }

    private fun iconCircle(iconRes: Int, onClick: () -> Unit): View {
        val f = android.widget.FrameLayout(this)
        f.background = circleBg(th.softContainer)
        f.layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        val iv = ImageView(this)
        iv.setImageResource(iconRes)
        iv.setColorFilter(th.primary)
        iv.scaleType = ImageView.ScaleType.CENTER_INSIDE
        val ilp = android.widget.FrameLayout.LayoutParams(dp(19), dp(19))
        ilp.gravity = Gravity.CENTER
        iv.layoutParams = ilp
        f.addView(iv)
        f.setOnClickListener { onClick() }
        return f
    }

    // ---- Hero actions -------------------------------------------------------

    private fun showVideoOptions(display: String) {
        val items = mutableListOf<PopupItem>()
        val actions = mutableListOf<() -> Unit>()
        items.add(PopupItem(R.drawable.ic_video, "Phone video call", "Carrier video on your SIM"))
        actions.add { Calls.placeVideo(this, number) }

        data class VApp(val label: String, val pkg: String)
        val known = listOf(
            VApp("WhatsApp", "com.whatsapp"),
            VApp("Google Meet", "com.google.android.apps.tachyon"),
            VApp("Telegram", "org.telegram.messenger"),
            VApp("Signal", "org.thoughtcrime.securesms"),
            VApp("Messenger", "com.facebook.orca"),
            VApp("Zoom", "us.zoom.videomeetings"),
            VApp("Skype", "com.skype.raider"),
            VApp("Microsoft Teams", "com.microsoft.teams")
        )
        for (app in known) {
            val launch = try { packageManager.getLaunchIntentForPackage(app.pkg) } catch (e: Exception) { null }
            if (launch == null) continue
            items.add(PopupItem(R.drawable.ic_video, app.label, "Video call in ${app.label}"))
            actions.add {
                if (app.pkg == "com.whatsapp") {
                    val d = RecStore.digits(number)
                    val intl = when {
                        d.length == 10 -> "1$d"
                        else -> d
                    }
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$intl")))
                    } catch (e: Exception) {
                        try { startActivity(launch) } catch (e2: Exception) { }
                    }
                } else {
                    try {
                        startActivity(launch)
                        Toast.makeText(this, "Opening ${app.label} — pick $display to video call", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        Popups.list(this, th, "Video call with $display", items) { idx -> actions[idx].invoke() }
    }

    private fun shareContact(display: String) {
        val sb = StringBuilder()
        sb.append("BEGIN:VCARD\nVERSION:3.0\n")
        sb.append("FN:").append(display).append("\n")
        for ((num, _) in allNumbers) sb.append("TEL:").append(num).append("\n")
        sb.append("END:VCARD")
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_SUBJECT, display)
        i.putExtra(Intent.EXTRA_TEXT, sb.toString())
        try {
            startActivity(Intent.createChooser(i, "Share contact"))
        } catch (e: Exception) {
            Toast.makeText(this, "No app available to share with", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmBlock(display: String) {
        if (isBlocked) {
            Popups.confirm(
                this, th, "Unblock $display?",
                "Calls and messages from ${Calls.fmtNumber(number)} will come through again.",
                "Unblock"
            ) { doBlock(false) }
        } else {
            Popups.confirm(
                this, th, "Block $display?",
                "${Calls.fmtNumber(number)} won't be able to call you. Their calls go straight to voicemail and won't ring.",
                "Block", danger = true
            ) { doBlock(true) }
        }
    }

    private fun doBlock(block: Boolean) {
        Thread {
            val ok = if (block) CallLogRepo.block(this, number) else CallLogRepo.unblock(this, number)
            runOnUiThread {
                if (ok) {
                    isBlocked = block
                    refreshHeroIcons()
                    Toast.makeText(
                        this,
                        if (block) "Number blocked" else "Number unblocked",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "Blocking isn't available until Recap is your phone app",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun readSendToVm(): Boolean {
        if (contactId <= 0) return false
        return try {
            contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.SEND_TO_VOICEMAIL),
                ContactsContract.Contacts._ID + " = ?",
                arrayOf(contactId.toString()), null
            )?.use { cur -> cur.moveToFirst() && cur.getInt(0) == 1 } ?: false
        } catch (e: Exception) { false }
    }

    private fun toggleSendToVoicemail(display: String) {
        if (contactId <= 0) {
            Toast.makeText(this, "This contact can't be changed", Toast.LENGTH_SHORT).show()
            return
        }
        val target = !sendToVm
        Thread {
            val ok = try {
                val v = ContentValues()
                v.put(ContactsContract.Contacts.SEND_TO_VOICEMAIL, if (target) 1 else 0)
                contentResolver.update(
                    ContactsContract.Contacts.CONTENT_URI, v,
                    ContactsContract.Contacts._ID + " = ?", arrayOf(contactId.toString())
                ) > 0
            } catch (e: Exception) { false }
            runOnUiThread {
                if (ok) {
                    sendToVm = target
                    refreshHeroIcons()
                    Toast.makeText(
                        this,
                        if (target) "Calls from $display now go straight to voicemail"
                        else "$display's calls will ring again",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this, "Couldn't change that — contacts permission may be missing", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun confirmDelete(display: String) {
        Popups.confirm(
            this, th, "Delete $display?",
            "This removes the contact from your phone's address book. Call history and recordings stay.",
            "Delete", danger = true
        ) {
            Thread {
                val n = try {
                    contentResolver.delete(
                        ContactsContract.RawContacts.CONTENT_URI,
                        ContactsContract.RawContacts.CONTACT_ID + " = ?",
                        arrayOf(contactId.toString())
                    )
                } catch (e: Exception) { 0 }
                runOnUiThread {
                    if (n > 0) {
                        Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, "Couldn't delete — contacts permission may be missing", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }
    }

    // ---- Photo + history ----------------------------------------------------

    private fun loadPhoto(uriStr: String?, inner: LinearLayout) {
        if (uriStr.isNullOrBlank()) return
        Thread {
            val bmp = try {
                contentResolver.openInputStream(Uri.parse(uriStr))?.use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) { null }
            if (bmp != null) {
                val round = circleCrop(bmp)
                runOnUiThread {
                    inner.removeAllViews()
                    val iv = ImageView(this)
                    iv.setImageBitmap(round)
                    iv.layoutParams = LinearLayout.LayoutParams(dp(112), dp(112))
                    inner.addView(iv)
                }
            }
        }.start()
    }

    private fun circleCrop(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = size / 2f
        canvas.drawCircle(r, r, r, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val left = (src.width - size) / 2f
        val top = (src.height - size) / 2f
        canvas.drawBitmap(src, -left, -top, paint)
        return out
    }

    private fun renderHistory(entries: List<CallEntry>) {
        histBox.removeAllViews()
        if (entries.isEmpty()) {
            val t = text(this, "No calls with this contact yet.", 14f, th.muted)
            t.setPadding(dp(24), dp(10), 0, 0)
            histBox.addView(t)
            return
        }
        for (e in entries) {
            val rec = if (e.isMissed) null else RecStore.match(this, e.number, e.dateMs)
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.TOP
            row.setPadding(dp(22), dp(11), dp(22), dp(11))

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
            if (rec != null && rec.title.isNotBlank()) {
                val tt = text(this, "✦ " + rec.title, 13.5f, th.primary, bold = true)
                val tLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                tLp.topMargin = dp(2)
                tt.layoutParams = tLp
                main.addView(tt)
            }
            row.addView(main)

            val sim = CallLogRepo.simLabelFor(this, e.accountId)
            if (sim.isNotEmpty()) row.addView(Rows.simChip(this, sim, th))
            if (rec != null) {
                val chev = text(this, "›", 24f, Color.parseColor("#B9C8BE"))
                chev.setPadding(dp(6), 0, 0, 0)
                row.addView(chev)
                row.setOnClickListener {
                    val i = Intent(this, DetailActivity::class.java)
                    i.putExtra("type", "recording")
                    i.putExtra("recId", rec.id)
                    startActivity(i)
                }
            }
            histBox.addView(row)
        }
    }
}
