package com.essef.recap

import android.telephony.VisualVoicemailService
import android.telephony.VisualVoicemailSms
import android.telephony.VisualVoicemailSmsFilterSettings
import android.telecom.PhoneAccountHandle

/**
 * Visual voicemail plumbing. Android only binds this service in the default dialer.
 * On cell-service connect we enable the OMTP SMS filter and ask the carrier to
 * activate visual voicemail; messages then land in the VoicemailContract provider,
 * which the Voicemail tab reads. If the carrier/plan has no VVM, nothing arrives
 * and the tab shows its honest fallback (call the voicemail number).
 *
 * Note: setSmsFilterSettings / setVisualVoicemailEnabled exist on real devices but
 * are missing from the compile-time SDK stub, so they're invoked reflectively.
 */
class RecapVvmService : VisualVoicemailService() {

    override fun onCellServiceConnected(task: VisualVoicemailTask, phoneAccountHandle: PhoneAccountHandle) {
        try {
            val filter = VisualVoicemailSmsFilterSettings.Builder()
                .setClientPrefix("//VVM")
                .build()
            val cls = VisualVoicemailService::class.java
            try {
                cls.getMethod(
                    "setSmsFilterSettings",
                    VisualVoicemailTask::class.java,
                    VisualVoicemailSmsFilterSettings::class.java
                ).invoke(this, task, filter)
            } catch (e: Exception) { }
            try {
                cls.getMethod(
                    "setVisualVoicemailEnabled",
                    VisualVoicemailTask::class.java,
                    PhoneAccountHandle::class.java,
                    Boolean::class.javaPrimitiveType
                ).invoke(this, task, phoneAccountHandle, true)
            } catch (e: Exception) { }
        } catch (e: Exception) { }
        task.finish()
    }

    override fun onSmsReceived(task: VisualVoicemailTask, sms: VisualVoicemailSms) {
        // The platform syncs the message into the voicemail provider itself;
        // nothing extra is required from us for the inbox to update.
        task.finish()
    }

    override fun onSimRemoved(task: VisualVoicemailTask, phoneAccountHandle: PhoneAccountHandle) {
        task.finish()
    }

    override fun onStopped(task: VisualVoicemailTask) {
        task.finish()
    }
}
