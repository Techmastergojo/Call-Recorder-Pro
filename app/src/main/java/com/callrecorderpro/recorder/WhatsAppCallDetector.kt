package com.callrecorderpro.recorder

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility Service that monitors WhatsApp's UI to detect
 * when a voice/video call starts or ends, then triggers our
 * WhatsAppRecorderService accordingly.
 *
 * Detection strategy: WhatsApp's in-call screen has specific
 * view IDs / window titles we can watch for.
 */
class WhatsAppCallDetector : AccessibilityService() {

    companion object {
        const val ACTION_CALL_STARTED = "com.callrecorderpro.WA_CALL_STARTED"
        const val ACTION_CALL_ENDED   = "com.callrecorderpro.WA_CALL_ENDED"
        const val EXTRA_PHONE_NUMBER  = "phone_number"
        const val EXTRA_DIRECTION     = "direction"

        // WhatsApp in-call screen identifiers
        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val CALL_SCREEN_KEYWORDS = setOf(
            "voip_activity",        // WhatsApp internal activity name
            "calling",
            "voice call",
            "video call",
            "ringing"
        )
        private val END_CALL_BUTTON_IDS = setOf(
            "com.whatsapp:id/end_call_btn",
            "com.whatsapp:id/footer_end_call_btn",
            "com.whatsapp.w4b:id/end_call_btn"
        )
    }

    private var isRecording = false
    private var currentCallNumber = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() !in WHATSAPP_PACKAGES) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val rootNode = rootInActiveWindow ?: return
                handleWindowChange(rootNode, event.packageName.toString())
            }
        }
    }

    private fun handleWindowChange(root: AccessibilityNodeInfo, pkg: String) {
        val isCallScreen = isOnCallScreen(root)

        if (isCallScreen && !isRecording) {
            // Call just started
            isRecording = true
            currentCallNumber = extractPhoneNumber(root)
            val direction = detectDirection(root)
            startRecording(currentCallNumber, direction)
        } else if (!isCallScreen && isRecording) {
            // Call ended
            isRecording = false
            stopRecording()
        }
    }

    private fun isOnCallScreen(root: AccessibilityNodeInfo): Boolean {
        // Check for the end-call button — the clearest signal we're in a call
        for (id in END_CALL_BUTTON_IDS) {
            if (root.findAccessibilityNodeInfosByViewId(id).isNotEmpty()) return true
        }
        // Fallback: check window title keywords
        val title = root.packageName?.toString()?.lowercase() ?: ""
        return CALL_SCREEN_KEYWORDS.any { title.contains(it) }
    }

    private fun extractPhoneNumber(root: AccessibilityNodeInfo): String {
        // WhatsApp shows caller name/number in a text view during calls
        val phoneRegex = Regex("\\+?\\d[\\d\\s\\-]{6,}\\d")
        return try {
            findAllText(root)
                .firstOrNull { phoneRegex.containsMatchIn(it) }
                ?.let { phoneRegex.find(it)?.value?.replace("\\s".toRegex(), "") }
                ?: "WhatsApp"
        } catch (e: Exception) { "WhatsApp" }
    }

    private fun detectDirection(root: AccessibilityNodeInfo): String {
        val texts = findAllText(root).joinToString(" ").lowercase()
        return when {
            texts.contains("incoming") || texts.contains("ringing") -> "INCOMING"
            texts.contains("calling") || texts.contains("dialing")  -> "OUTGOING"
            else -> "UNKNOWN"
        }
    }

    private fun findAllText(node: AccessibilityNodeInfo): List<String> {
        val texts = mutableListOf<String>()
        node.text?.toString()?.let { texts.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { texts.addAll(findAllText(it)) }
        }
        return texts
    }

    private fun startRecording(number: String, direction: String) {
        val intent = Intent(this, WhatsAppRecorderService::class.java).apply {
            action = ACTION_CALL_STARTED
            putExtra(EXTRA_PHONE_NUMBER, number)
            putExtra(EXTRA_DIRECTION, direction)
        }
        startForegroundService(intent)
    }

    private fun stopRecording() {
        val intent = Intent(this, WhatsAppRecorderService::class.java).apply {
            action = ACTION_CALL_ENDED
        }
        startService(intent)
    }

    override fun onInterrupt() {
        if (isRecording) stopRecording()
        isRecording = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Service is ready
    }
}
