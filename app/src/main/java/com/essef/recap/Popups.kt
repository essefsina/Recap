package com.essef.recap

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

data class PopupItem(
    val iconRes: Int? = null,
    val label: String,
    val sub: String? = null,
    val selected: Boolean = false,
    val danger: Boolean = false
)

/**
 * Recap's own popup design (Essef, Oct 21): no stock Android AlertDialogs.
 * A themed card — big radius, soft surface, pill buttons, line icons —
 * used for confirmations, SIM pickers, and choosers everywhere in the app.
 */
object Popups {

    private fun base(act: Activity, th: ThemeColors, title: String, message: String?): Pair<Dialog, LinearLayout> {
        val dlg = Dialog(act)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val card = LinearLayout(act)
        card.orientation = LinearLayout.VERTICAL
        card.background = rounded(th.surface, 26, act)
        card.setPadding(act.dp(22), act.dp(20), act.dp(22), act.dp(18))
        if (title.isNotEmpty()) {
            val t = text(act, title, 18f, th.onSurface, bold = true)
            card.addView(t)
        }
        if (!message.isNullOrEmpty()) {
            val m = text(act, message, 14.5f, th.muted)
            val mLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            mLp.topMargin = act.dp(8)
            m.layoutParams = mLp
            m.setLineSpacing(0f, 1.25f)
            card.addView(m)
        }
        dlg.setContentView(card)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val w = act.resources.displayMetrics.widthPixels - act.dp(44)
        dlg.window?.setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        return dlg to card
    }

    private fun pill(act: Activity, th: ThemeColors, label: String, primary: Boolean, danger: Boolean, onClick: () -> Unit): TextView {
        val b = text(act, label, 15f, when {
            danger -> Color.WHITE
            primary -> Color.WHITE
            else -> th.onSurface
        }, bold = true)
        b.gravity = Gravity.CENTER
        b.background = rounded(
            when {
                danger -> Rows.RED
                primary -> th.primary
                else -> th.softContainer
            }, 999, act
        )
        b.setPadding(0, act.dp(13), 0, act.dp(13))
        b.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun buttonRow(
        act: Activity, th: ThemeColors, card: LinearLayout,
        cancelLabel: String?, confirmLabel: String, danger: Boolean,
        onCancel: () -> Unit, onConfirm: () -> Unit
    ) {
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        val rLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rLp.topMargin = act.dp(18)
        row.layoutParams = rLp
        if (cancelLabel != null) {
            row.addView(pill(act, th, cancelLabel, primary = false, danger = false) { onCancel() })
            val gap = View(act); gap.layoutParams = LinearLayout.LayoutParams(act.dp(10), 1)
            row.addView(gap)
        }
        row.addView(pill(act, th, confirmLabel, primary = true, danger = danger) { onConfirm() })
        card.addView(row)
    }

    fun confirm(
        act: Activity, th: ThemeColors, title: String, message: String,
        confirmLabel: String, danger: Boolean = false, onConfirm: () -> Unit
    ) {
        val (dlg, card) = base(act, th, title, message)
        buttonRow(act, th, card, "Cancel", confirmLabel, danger,
            onCancel = { dlg.dismiss() },
            onConfirm = { dlg.dismiss(); onConfirm() })
        dlg.show()
    }

    fun message(
        act: Activity, th: ThemeColors, title: String, message: String,
        buttonLabel: String = "Got it", onOk: (() -> Unit)? = null
    ) {
        val (dlg, card) = base(act, th, title, message)
        buttonRow(act, th, card, null, buttonLabel, danger = false,
            onCancel = { },
            onConfirm = { dlg.dismiss(); onOk?.invoke() })
        dlg.show()
    }

    /** Themed text-input popup (e.g. rename). */
    fun input(
        act: Activity, th: ThemeColors, title: String, initial: String, hint: String,
        confirmLabel: String = "Save", onConfirm: (String) -> Unit
    ) {
        val (dlg, card) = base(act, th, title, null)
        val field = android.widget.EditText(act)
        field.setText(initial)
        field.hint = hint
        field.setSingleLine(true)
        field.textSize = 15.5f
        field.setTextColor(th.onSurface)
        field.setHintTextColor(th.faint)
        field.background = rounded(th.softContainer, 16, act)
        field.setPadding(act.dp(14), act.dp(12), act.dp(14), act.dp(12))
        val fLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        fLp.topMargin = act.dp(12)
        field.layoutParams = fLp
        field.setSelection(initial.length)
        card.addView(field)
        buttonRow(act, th, card, "Cancel", confirmLabel, danger = false,
            onCancel = { dlg.dismiss() },
            onConfirm = { dlg.dismiss(); onConfirm(field.text.toString()) })
        dlg.show()
    }

    /**
     * Themed chooser list. Optional [extra] view (e.g. a checkbox) sits above
     * the Cancel pill. Tapping a row dismisses and reports its index.
     */
    fun list(
        act: Activity, th: ThemeColors, title: String,
        items: List<PopupItem>, extra: View? = null,
        onPick: (Int) -> Unit
    ) {
        val (dlg, card) = base(act, th, title, null)
        val listBox = LinearLayout(act)
        listBox.orientation = LinearLayout.VERTICAL
        val lLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lLp.topMargin = act.dp(8)
        listBox.layoutParams = lLp
        items.forEachIndexed { idx, item ->
            if (idx > 0) {
                val dv = View(act)
                dv.setBackgroundColor(Color.parseColor("#E4EAE5"))
                val dvLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                dvLp.leftMargin = if (item.iconRes != null) act.dp(49) else 0
                dv.layoutParams = dvLp
                listBox.addView(dv)
            }
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(act.dp(4), act.dp(13), act.dp(2), act.dp(13))
            if (item.iconRes != null) {
                val iv = ImageView(act)
                iv.setImageResource(item.iconRes)
                iv.setColorFilter(if (item.danger) Rows.RED else th.primary)
                iv.layoutParams = LinearLayout.LayoutParams(act.dp(22), act.dp(22))
                row.addView(iv)
            }
            val col = LinearLayout(act)
            col.orientation = LinearLayout.VERTICAL
            val cLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (item.iconRes != null) cLp.leftMargin = act.dp(14)
            col.layoutParams = cLp
            col.addView(text(act, item.label, 15.5f, if (item.danger) Rows.RED_DARK else th.onSurface, bold = item.selected))
            if (!item.sub.isNullOrEmpty()) {
                col.addView(text(act, item.sub, 12.5f, th.muted))
            }
            row.addView(col)
            if (item.selected) {
                val ck = ImageView(act)
                ck.setImageResource(R.drawable.ic_check)
                ck.setColorFilter(th.primary)
                ck.layoutParams = LinearLayout.LayoutParams(act.dp(20), act.dp(20))
                row.addView(ck)
            }
            row.setOnClickListener {
                dlg.dismiss()
                onPick(idx)
            }
            listBox.addView(row)
        }
        card.addView(listBox)
        if (extra != null) {
            val eLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            eLp.topMargin = act.dp(10)
            extra.layoutParams = eLp
            card.addView(extra)
        }
        val cancelRow = LinearLayout(act)
        cancelRow.orientation = LinearLayout.HORIZONTAL
        val crLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        crLp.topMargin = act.dp(14)
        cancelRow.layoutParams = crLp
        cancelRow.addView(pill(act, th, "Cancel", primary = false, danger = false) { dlg.dismiss() })
        card.addView(cancelRow)
        dlg.show()
    }
}
