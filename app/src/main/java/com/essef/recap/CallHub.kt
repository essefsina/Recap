package com.essef.recap

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/** Holds the live call so the in-call UI and the service stay in sync. */
object CallHub {
    @Volatile var service: InCallService? = null
    @Volatile var call: Call? = null
    /** Latest audio state reported by Telecom (route + mute truth). */
    @Volatile var audioState: CallAudioState? = null
    @Volatile var incomingUiShownFor: Call? = null
    /** Consent announcement already spoken for the current call (either UI path). */
    @Volatile var consentSpoken: Boolean = false

    var listener: (() -> Unit)? = null

    fun notifyChanged() {
        listener?.invoke()
    }

    val state: Int get() = call?.state ?: Call.STATE_DISCONNECTED
    val isRinging: Boolean get() = call?.state == Call.STATE_RINGING
    val isActive: Boolean get() = call?.state == Call.STATE_ACTIVE
}
