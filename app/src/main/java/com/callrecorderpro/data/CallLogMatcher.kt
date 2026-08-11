package com.callrecorderpro.data

import android.content.Context
import android.provider.CallLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enriches RecordingItem list with caller names and call direction
 * from the Android Call Log, matching by timestamp proximity.
 */
class CallLogMatcher(private val context: Context) {

    data class CallLogEntry(
        val number: String,
        val name: String?,
        val direction: Direction,
        val timestampMs: Long,
        val durationSeconds: Int
    )

    suspend fun enrich(recordings: List<RecordingItem>): List<RecordingItem> =
        withContext(Dispatchers.IO) {
            val callLog = fetchCallLog()
            recordings.map { recording ->
                val match = findBestMatch(recording, callLog)
                if (match != null) {
                    recording.copy(
                        callerName = match.name ?: recording.callerName,
                        phoneNumber = match.number.ifBlank { recording.phoneNumber },
                        direction = match.direction,
                        durationSeconds = if (match.durationSeconds > 0)
                            match.durationSeconds else recording.durationSeconds
                    )
                } else recording
            }
        }

    private fun fetchCallLog(): List<CallLogEntry> {
        val entries = mutableListOf<CallLogEntry>()
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null, null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val numCol  = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val nameCol = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val typeCol = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durCol  = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (cursor.moveToNext()) {
                val type = cursor.getInt(typeCol)
                val direction = when (type) {
                    CallLog.Calls.INCOMING_TYPE -> Direction.INCOMING
                    CallLog.Calls.OUTGOING_TYPE -> Direction.OUTGOING
                    else -> Direction.UNKNOWN
                }
                entries.add(
                    CallLogEntry(
                        number = cursor.getString(numCol) ?: "",
                        name = cursor.getString(nameCol)?.takeIf { it.isNotBlank() },
                        direction = direction,
                        timestampMs = cursor.getLong(dateCol),
                        durationSeconds = cursor.getInt(durCol)
                    )
                )
            }
        }
        return entries
    }

    /**
     * Match recording to call log entry within a ±5 minute window.
     */
    private fun findBestMatch(
        recording: RecordingItem,
        callLog: List<CallLogEntry>
    ): CallLogEntry? {
        val window = 5 * 60 * 1000L // 5 minutes in ms
        return callLog
            .filter { entry ->
                val diff = kotlin.math.abs(entry.timestampMs - recording.timestampMs)
                diff <= window
            }
            .minByOrNull { entry ->
                // Prefer number match over pure timestamp proximity
                val numMatch = entry.number == recording.phoneNumber ||
                    entry.number.endsWith(recording.phoneNumber.takeLast(7))
                val timeDiff = kotlin.math.abs(entry.timestampMs - recording.timestampMs)
                if (numMatch) timeDiff else timeDiff + 10_000_000L
            }
    }
}
