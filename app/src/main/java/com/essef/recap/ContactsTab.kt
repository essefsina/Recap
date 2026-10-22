package com.essef.recap

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

class ContactsTab(private val act: Activity, private val th: ThemeColors) {

    private lateinit var listBox: LinearLayout
    private var all: List<ContactEntry> = emptyList()
    private var query = ""

    fun create(onCall: (String) -> Unit): View {
        val ctx = act
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL

        val search = EditText(ctx)
        search.hint = "Search contacts…"
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
                render(onCall)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        root.addView(search)

        val scroll = ScrollView(ctx)
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        listBox = LinearLayout(ctx)
        listBox.orientation = LinearLayout.VERTICAL
        listBox.setPadding(0, ctx.dp(8), 0, ctx.dp(16))
        scroll.addView(listBox)
        root.addView(scroll)

        Thread {
            val contacts = ContactsRepo.query(ctx)
            ctx.runOnUiThread {
                all = contacts
                render(onCall)
            }
        }.start()
        return root
    }

    private fun render(onCall: (String) -> Unit) {
        val ctx = act
        listBox.removeAllViews()
        val shown = all.filter {
            query.isEmpty() || it.name.lowercase().contains(query) || it.number.contains(query)
        }
        if (shown.isEmpty()) {
            val t = text(ctx, if (all.isEmpty()) "No contacts found. Grant the contacts permission to see them here."
            else "No contacts match your search.", 14f, th.muted)
            t.setPadding(ctx.dp(22), ctx.dp(18), ctx.dp(22), 0)
            listBox.addView(t)
            return
        }
        for (c in shown.take(300)) {
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(ctx.dp(22), ctx.dp(10), ctx.dp(22), ctx.dp(10))
            row.addView(avatarView(ctx, initialsOf(c.name, c.number), avatarColorFor(c.number + c.name),
                android.graphics.Color.parseColor("#17352A"), 46, 14.5f))
            val main = LinearLayout(ctx)
            main.orientation = LinearLayout.VERTICAL
            val mLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            mLp.leftMargin = ctx.dp(14)
            main.layoutParams = mLp
            main.addView(text(ctx, c.name, 16f, th.onSurface, bold = true))
            main.addView(text(ctx, Calls.fmtNumber(c.number), 13.5f, th.muted))
            row.addView(main)
            // Message button (beside Call, per Essef's ask)
            val msgBtn = android.widget.FrameLayout(ctx)
            msgBtn.background = circleBg(th.softContainer)
            val mbLp = LinearLayout.LayoutParams(ctx.dp(42), ctx.dp(42))
            mbLp.rightMargin = ctx.dp(10)
            msgBtn.layoutParams = mbLp
            val miv = android.widget.ImageView(ctx)
            miv.setImageResource(R.drawable.ic_message)
            miv.setColorFilter(th.primary)
            val milp = android.widget.FrameLayout.LayoutParams(ctx.dp(20), ctx.dp(20))
            milp.gravity = Gravity.CENTER
            miv.layoutParams = milp
            msgBtn.addView(miv)
            msgBtn.setOnClickListener { Calls.sms(ctx, c.number) }
            row.addView(msgBtn)
            val callBtn = android.widget.FrameLayout(ctx)
            callBtn.background = circleBg(th.softContainer)
            callBtn.layoutParams = LinearLayout.LayoutParams(ctx.dp(42), ctx.dp(42))
            val iv = android.widget.ImageView(ctx)
            iv.setImageResource(R.drawable.ic_phone)
            iv.setColorFilter(th.primary)
            val ilp = android.widget.FrameLayout.LayoutParams(ctx.dp(20), ctx.dp(20))
            ilp.gravity = Gravity.CENTER
            iv.layoutParams = ilp
            callBtn.addView(iv)
            callBtn.setOnClickListener { onCall(c.number) }
            row.addView(callBtn)
            // Tapping the contact itself opens its full view — photo, numbers,
            // call history, message/call actions.
            row.setOnClickListener {
                val i = android.content.Intent(ctx, ContactActivity::class.java)
                i.putExtra("name", c.name)
                i.putExtra("number", c.number)
                i.putExtra("contactId", c.contactId)
                if (c.photoUri != null) i.putExtra("photoUri", c.photoUri)
                ctx.startActivity(i)
            }
            listBox.addView(row)
        }
    }
}
