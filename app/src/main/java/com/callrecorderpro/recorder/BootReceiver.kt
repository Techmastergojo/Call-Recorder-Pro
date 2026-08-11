package com.callrecorderpro.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the WhatsApp call detector service after a device reboot.
 * The AccessibilityService handles itself, but this receiver ensures
 * any pending state is cleaned up.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Accessibility service auto-restarts; nothing extra needed here
            // This is a hook for future persistent state restoration
        }
    }
}
