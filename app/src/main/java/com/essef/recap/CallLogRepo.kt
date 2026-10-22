package com.essef.recap

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager

data class CallEntry(
    val id: Long,
    val number: String,
    val cachedName: String,
    val type: Int, // CallLog.Calls.INCOMING_TYPE / OUTGOING_TYPE / MISSED_TYPE / REJECTED_TYPE
    val dateMs: Long,
    val durationSec: Long,
    val accountId: String
) {
    val isMissed get() = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE
    val isIncoming get() = type == CallLog.Calls.INCOMING_TYPE
    val isOutgoing get() = type == CallLog.Calls.OUTGOING_TYPE
}

data class CallGroup(
    val key: String,
    val entries: List<CallEntry>
) {
    val latest get() = entries.first()
    val count get() = entries.size
    val number get() = latest.number
}

object CallLogRepo {

    fun query(ctx: Context, limit: Int = 500): List<CallEntry> {
        val out = mutableListOf<CallEntry>()
        val proj = arrayOf(
            CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )
        try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI, proj, null, null,
                CallLog.Calls.DATE + " DESC"
            )?.use { cur ->
                var n = 0
                while (cur.moveToNext() && n < limit) {
                    out.add(
                        CallEntry(
                            id = cur.getLong(0),
                            number = cur.getString(1) ?: "",
                            cachedName = cur.getString(2) ?: "",
                            type = cur.getInt(3),
                            dateMs = cur.getLong(4),
                            durationSec = cur.getLong(5),
                            accountId = cur.getString(6) ?: ""
                        )
                    )
                    n++
                }
            }
        } catch (e: SecurityException) { /* permission not granted yet */ }
        return out
    }

    /** Consecutive calls from the same number collapse into one group (approved design). */
    fun group(entries: List<CallEntry>): List<CallGroup> {
        val groups = mutableListOf<CallGroup>()
        var curKey: String? = null
        var curList = mutableListOf<CallEntry>()
        fun flush() {
            if (curList.isNotEmpty()) groups.add(CallGroup(curKey!!, curList.toList()))
        }
        for (e in entries) {
            val k = RecStore.digits(e.number).ifEmpty { e.number }
            if (k == curKey) {
                curList.add(e)
            } else {
                flush()
                curKey = k
                curList = mutableListOf(e)
            }
        }
        flush()
        return groups
    }

    fun historyFor(ctx: Context, number: String): List<CallEntry> {
        val want = RecStore.digits(number)
        return query(ctx, 2000).filter { RecStore.digits(it.number) == want }
    }

    fun contactName(ctx: Context, number: String): String {
        val want = RecStore.digits(number)
        if (want.isEmpty()) return ""
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )?.use { cur ->
                while (cur.moveToNext()) {
                    val num = cur.getString(1) ?: ""
                    if (RecStore.digits(num) == want) return cur.getString(0) ?: ""
                }
            }
        } catch (e: SecurityException) { }
        return ""
    }

    fun isContact(ctx: Context, number: String): Boolean = contactName(ctx, number).isNotEmpty()

    fun blockedNumbers(ctx: Context): Set<String> {
        val out = mutableSetOf<String>()
        try {
            ctx.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                null, null, null
            )?.use { cur -> while (cur.moveToNext()) out.add(RecStore.digits(cur.getString(0) ?: "")) }
        } catch (e: Exception) { }
        return out
    }

    fun block(ctx: Context, number: String): Boolean {
        return try {
            val v = ContentValues()
            v.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
            ctx.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, v) != null
        } catch (e: Exception) { false }
    }

    fun unblock(ctx: Context, number: String): Boolean {
        return try {
            BlockedNumberContract.unblock(ctx, number) > 0
        } catch (e: Exception) { false }
    }

    /** "SIM 1"/"SIM 2" from the order of call-capable phone accounts, when dual SIM. */
    fun simLabelFor(ctx: Context, accountId: String): String {
        return try {
            val tm = ctx.getSystemService(TelecomManager::class.java) ?: return ""
            val accounts = tm.callCapablePhoneAccounts
            if (accounts.size < 2) return ""
            val idx = accounts.indexOfFirst { it.id == accountId }
            if (idx >= 0) "SIM ${idx + 1}" else ""
        } catch (e: Exception) { "" }
    }

    fun isDualSim(ctx: Context): Boolean {
        return try {
            val tm = ctx.getSystemService(TelecomManager::class.java)
            (tm?.callCapablePhoneAccounts?.size ?: 0) >= 2
        } catch (e: Exception) { false }
    }

    /** Most-called numbers from the call log, for the keypad's Frequent row. */
    fun frequents(ctx: Context, limit: Int = 5): List<FrequentEntry> {
        val entries = query(ctx, 1000)
        val counts = LinkedHashMap<String, Int>()
        val sample = HashMap<String, CallEntry>()
        for (e in entries) {
            val d = RecStore.digits(e.number)
            if (d.length < 7) continue
            counts[d] = (counts[d] ?: 0) + 1
            if (!sample.containsKey(d)) sample[d] = e
        }
        return counts.entries.sortedByDescending { it.value }.take(limit).map { (d, n) ->
            val e = sample[d]!!
            val name = e.cachedName.ifBlank { contactName(ctx, e.number) }
            FrequentEntry(number = e.number, name = name, count = n)
        }
    }
}

data class FrequentEntry(val number: String, val name: String, val count: Int)

data class ContactEntry(
    val name: String,
    val number: String,
    val contactId: Long = 0,
    val photoUri: String? = null
)

object ContactsRepo {
    fun query(ctx: Context): List<ContactEntry> {
        val out = mutableListOf<ContactEntry>()
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                ),
                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cur ->
                val seen = mutableSetOf<String>()
                while (cur.moveToNext()) {
                    val name = cur.getString(0) ?: continue
                    val num = cur.getString(1) ?: continue
                    val key = name + "|" + RecStore.digits(num)
                    if (seen.add(key)) out.add(
                        ContactEntry(name, num, cur.getLong(2), cur.getString(3))
                    )
                }
            }
        } catch (e: SecurityException) { }
        return out
    }

    /** Every phone number saved under one contact id, with its type label. */
    fun numbersFor(ctx: Context, contactId: Long): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        if (contactId <= 0) return out
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL
                ),
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                arrayOf(contactId.toString()), null
            )?.use { cur ->
                val seen = mutableSetOf<String>()
                while (cur.moveToNext()) {
                    val num = cur.getString(0) ?: continue
                    if (!seen.add(RecStore.digits(num))) continue
                    val type = cur.getInt(1)
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        ctx.resources, type, cur.getString(2)
                    ).toString()
                    out.add(num to label)
                }
            }
        } catch (e: SecurityException) { }
        return out
    }
}
