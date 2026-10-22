package com.essef.recap

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast

object Calls {

    fun accounts(ctx: Context): List<PhoneAccountHandle> {
        return try {
            val tm = ctx.getSystemService(TelecomManager::class.java)
            tm?.callCapablePhoneAccounts ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    /** "SIM 1 · T-Mobile" style label for a phone account. */
    fun accountDisplay(ctx: Context, handle: PhoneAccountHandle, index: Int): String {
        val carrier = try {
            val tm = ctx.getSystemService(TelecomManager::class.java)
            tm?.getPhoneAccount(handle)?.label?.toString()?.trim() ?: ""
        } catch (e: Exception) { "" }
        val sim = "SIM ${index + 1}"
        return if (carrier.isNotEmpty() && !carrier.equals(sim, ignoreCase = true)) "$sim · $carrier" else sim
    }

    /** Short label for chips: "SIM 1", "SIM 2", "Ask" (dual SIM, no default), "" (single SIM). */
    fun simShortLabel(ctx: Context): String {
        val accs = accounts(ctx)
        if (accs.size < 2) return ""
        val def = Prefs.get(ctx).defaultSimId
        val idx = accs.indexOfFirst { it.id == def }
        return if (idx >= 0) "SIM ${idx + 1}" else "Ask"
    }

    fun place(ctx: Context, number: String) {
        val num = number.trim()
        if (num.isEmpty()) return
        val accs = accounts(ctx)
        if (accs.size >= 2) {
            val def = accs.firstOrNull { it.id == Prefs.get(ctx).defaultSimId }
            if (def != null) {
                doPlace(ctx, num, def)
                return
            }
            if (ctx is Activity) {
                showSimPicker(ctx, num, accs)
                return
            }
        }
        doPlace(ctx, num, accs.firstOrNull())
    }

    private fun doPlace(ctx: Context, num: String, handle: PhoneAccountHandle?) {
        try {
            val tm = ctx.getSystemService(TelecomManager::class.java)
            val extras = Bundle()
            if (handle != null) {
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            }
            tm?.placeCall(Uri.fromParts("tel", num, null), extras)
        } catch (e: SecurityException) {
            Toast.makeText(ctx, "Call permission is needed — grant it in system settings", Toast.LENGTH_LONG).show()
            val i = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            i.data = Uri.fromParts("package", ctx.packageName, null)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Could not start the call", Toast.LENGTH_SHORT).show()
        }
    }

    /** Per-call SIM chooser (Recap-styled) shown when no default SIM is set. */
    fun showSimPicker(act: Activity, num: String, accs: List<PhoneAccountHandle>) {
        val th = Themes.get(act)
        val remember = CheckBox(act)
        remember.text = "Use this SIM for every call"
        remember.textSize = 14f
        remember.setTextColor(th.muted)
        val items = accs.mapIndexed { i, h ->
            PopupItem(iconRes = R.drawable.ic_sim, label = accountDisplay(act, h, i))
        }
        Popups.list(act, th, "Call ${fmtNumber(num)} with", items, extra = remember) { which ->
            val h = accs[which]
            if (remember.isChecked) {
                Prefs.put(act, "defaultSim", h.id)
                Toast.makeText(act, "${accountDisplay(act, h, which)} is now your calling SIM", Toast.LENGTH_SHORT).show()
            }
            doPlace(act, num, h)
        }
    }

    /** Default-SIM chooser for Settings and the keypad SIM chip. */
    fun showDefaultSimPicker(act: Activity, onChosen: () -> Unit) {
        val accs = accounts(act)
        if (accs.size < 2) {
            Toast.makeText(act, "This phone has a single SIM", Toast.LENGTH_SHORT).show()
            return
        }
        val th = Themes.get(act)
        val cur = Prefs.get(act).defaultSimId
        val items = mutableListOf(
            PopupItem(iconRes = R.drawable.ic_sim, label = "Ask every time", selected = cur.isEmpty())
        )
        items.addAll(accs.mapIndexed { i, h ->
            PopupItem(
                iconRes = R.drawable.ic_sim,
                label = accountDisplay(act, h, i),
                selected = h.id == cur
            )
        })
        Popups.list(act, th, "Calling SIM", items) { which ->
            Prefs.put(act, "defaultSim", if (which == 0) "" else accs[which - 1].id)
            onChosen()
        }
    }

    /**
     * Frequent-call placement: NEVER asks which SIM. Uses the SIM selected on
     * the dialpad (the saved default); when that's "Ask", the SIM this number
     * last called on; otherwise the first SIM. One tap means one tap.
     */
    fun placeFrequent(ctx: Context, number: String) {
        val num = number.trim()
        if (num.isEmpty()) return
        val accs = accounts(ctx)
        val def = accs.firstOrNull { it.id == Prefs.get(ctx).defaultSimId }
        val handle = def ?: run {
            val lastAccId = try {
                CallLogRepo.historyFor(ctx, num).firstOrNull()?.accountId ?: ""
            } catch (e: Exception) { "" }
            accs.firstOrNull { it.id == lastAccId } ?: accs.firstOrNull()
        }
        doPlace(ctx, num, handle)
    }

    /** Carrier (ViLTE) video call through the telecom stack, on the default/first SIM. */
    fun placeVideo(ctx: Context, number: String) {
        val num = number.trim()
        if (num.isEmpty()) return
        try {
            val tm = ctx.getSystemService(TelecomManager::class.java)
            val accs = accounts(ctx)
            val handle = accs.firstOrNull { it.id == Prefs.get(ctx).defaultSimId } ?: accs.firstOrNull()
            val extras = Bundle()
            extras.putInt(
                TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                android.telecom.VideoProfile.STATE_BIDIRECTIONAL
            )
            if (handle != null) {
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            }
            tm?.placeCall(Uri.fromParts("tel", num, null), extras)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Carrier video calling isn't available for this call", Toast.LENGTH_LONG).show()
        }
    }

    /** Opens the phone's messaging app addressed to this number. */
    fun sms(ctx: Context, number: String) {
        try {
            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)))
            ctx.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(ctx, "No messaging app found", Toast.LENGTH_SHORT).show()
        }
    }

    fun fmtNumber(n: String): String {
        val d = n.filter { it.isDigit() }
        return if (d.length == 10) "(${d.substring(0, 3)}) ${d.substring(3, 6)}-${d.substring(6)}"
        else if (d.length == 11 && d.startsWith("1")) "(${d.substring(1, 4)}) ${d.substring(4, 7)}-${d.substring(7)}"
        else n
    }
}
