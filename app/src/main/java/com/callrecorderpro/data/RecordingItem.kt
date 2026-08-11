package com.callrecorderpro.data

import android.net.Uri

data class RecordingItem(
    val id: String,
    val callerName: String?,        // "Ahmed Khan" or null
    val phoneNumber: String,        // "+923001234567"
    val direction: Direction,
    val type: RecordingType,
    val timestampMs: Long,          // epoch millis (start of call)
    val durationSeconds: Int,       // total call duration
    val fileUri: Uri,
    val fileSizeBytes: Long,
    val fileName: String
) {
    val displayName: String
        get() = callerName?.takeIf { it.isNotBlank() } ?: phoneNumber.ifBlank { "Unknown" }
}

enum class Direction {
    INCOMING,
    OUTGOING,
    UNKNOWN;

    fun label() = when (this) {
        INCOMING -> "Incoming"
        OUTGOING -> "Outgoing"
        UNKNOWN  -> "Unknown"
    }
}

enum class RecordingType {
    SIM,          // Regular phone call recorded by Samsung
    WHATSAPP;     // WhatsApp call recorded by RecordPro

    fun label() = when (this) {
        SIM       -> "SIM Call"
        WHATSAPP  -> "WhatsApp"
    }
}
