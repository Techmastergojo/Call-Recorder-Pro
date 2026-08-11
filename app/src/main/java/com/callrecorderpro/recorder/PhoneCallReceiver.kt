package com.callrecorderpro.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/**
 * Detects phone state changes (incoming/outgoing calls)
 * and triggers WhatsAppRecorderService to record the call.
 */
class PhoneCallReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SIM_CALL_STARTED = "com.callrecorderpro.SIM_CALL_STARTED"
        const val ACTION_SIM_CALL_ENDED   = "com.callrecorderpro.SIM_CALL_ENDED"
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var isSearching = false
        private var savedNumber = ""
    }

    override fun onReceive(context: Context, intent: Intent) {
        // We only care about PHONE_STATE intents
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

        if (number.isNotBlank()) {
            savedNumber = number
        }

        val state = when (stateStr) {
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            else -> TelephonyManager.CALL_STATE_IDLE
        }

        onCallStateChanged(context, state)
    }

    private fun onCallStateChanged(context: Context, state: Int) {
        if (lastState == state) {
            return
        }

        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call starts (either outgoing answered, or incoming answered)
                val serviceIntent = Intent(context, WhatsAppRecorderService::class.java).apply {
                    action = ACTION_SIM_CALL_STARTED
                    putExtra(WhatsAppCallDetector.EXTRA_PHONE_NUMBER, savedNumber.ifBlank { "SIM Call" })
                    // We don't know the exact direction reliably from BroadcastReceiver without extra logs,
                    // so we mark it as UNKNOWN and enrich via call log later.
                    putExtra(WhatsAppCallDetector.EXTRA_DIRECTION, "UNKNOWN")
                }
                context.startForegroundService(serviceIntent)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // Call ended
                val serviceIntent = Intent(context, WhatsAppRecorderService::class.java).apply {
                    action = ACTION_SIM_CALL_ENDED
                }
                context.startService(serviceIntent)
                savedNumber = ""
            }
        }
        lastState = state
    }
}
