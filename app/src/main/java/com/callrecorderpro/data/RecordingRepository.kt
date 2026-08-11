package com.callrecorderpro.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single source of truth for all recordings (SIM + WhatsApp).
 * Aggregates Samsung scanner results with WhatsApp recordings
 * saved by our own WhatsAppRecorderService.
 */
class RecordingRepository(private val context: Context) {

    private val samsungScanner = SamsungRecordingScanner(context)
    private val callLogMatcher = CallLogMatcher(context)

    /** Directory where RecordPro saves WhatsApp recordings */
    val whatsappRecordingDir: File
        get() = File(context.getExternalFilesDir(null), "WhatsApp").also { it.mkdirs() }

    /**
     * Fetch all recordings from both sources, enriched with call log data.
     */
    suspend fun getAllRecordings(): List<RecordingItem> = withContext(Dispatchers.IO) {
        coroutineScope {
            val samsungDeferred   = async { samsungScanner.scan() }
            val whatsappDeferred  = async { scanWhatsAppRecordings() }

            val samsung   = samsungDeferred.await()
            val whatsapp  = whatsappDeferred.await()
            val combined  = (samsung + whatsapp).sortedByDescending { it.timestampMs }

            // Enrich all SIM recordings with call log data (name, direction)
            callLogMatcher.enrich(combined)
        }
    }

    /**
     * Scans RecordPro's own WhatsApp recording directory.
     * Files are saved by WhatsAppRecorderService with format:
     *   WA_20240811_143022_+923001234567.m4a
     */
    private fun scanWhatsAppRecordings(): List<RecordingItem> {
        val dir = whatsappRecordingDir
        if (!dir.exists()) return emptyList()

        return dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in setOf("m4a", "aac", "wav") }
            ?.mapNotNull { file -> parseWhatsAppFile(file) }
            ?.sortedByDescending { it.timestampMs }
            ?: emptyList()
    }

    private fun parseWhatsAppFile(file: File): RecordingItem? {
        return try {
            val name = file.nameWithoutExtension // e.g. WA_20240811_143022_+923001234567
            val parts = name.split("_")

            // parts[0] = "WA", parts[1] = date, parts[2] = time, parts[3..] = number
            val dateStr = parts.getOrNull(1) ?: ""
            val timeStr = parts.getOrNull(2) ?: ""
            val phone   = parts.drop(3).joinToString("_")

            val tsMs = parseWaTimestamp(dateStr, timeStr)
            val duration = getAudioDuration(file.absolutePath)

            // Direction stored in filename suffix: _IN or _OUT
            val direction = when {
                name.endsWith("_IN")  -> Direction.INCOMING
                name.endsWith("_OUT") -> Direction.OUTGOING
                else                  -> Direction.UNKNOWN
            }

            RecordingItem(
                id = file.absolutePath,
                callerName = null,   // enriched later via contacts lookup
                phoneNumber = phone.replace("_IN","").replace("_OUT",""),
                direction = direction,
                type = RecordingType.WHATSAPP,
                timestampMs = tsMs,
                durationSeconds = duration,
                fileUri = Uri.fromFile(file),
                fileSizeBytes = file.length(),
                fileName = file.name
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseWaTimestamp(date: String, time: String): Long {
        return try {
            val year  = date.substring(0, 4).toInt()
            val month = date.substring(4, 6).toInt() - 1
            val day   = date.substring(6, 8).toInt()
            val hour  = time.substring(0, 2).toInt()
            val min   = time.substring(2, 4).toInt()
            val sec   = time.substring(4, 6).toInt()
            val cal   = java.util.Calendar.getInstance()
            cal.set(year, month, day, hour, min, sec)
            cal.timeInMillis
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun getAudioDuration(path: String): Int {
        return try {
            val r = android.media.MediaMetadataRetriever()
            r.setDataSource(path)
            val ms = r.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLong() ?: 0L
            r.release()
            (ms / 1000).toInt()
        } catch (e: Exception) { 0 }
    }

    fun deleteRecording(item: RecordingItem): Boolean {
        return try {
            val file = File(item.fileUri.path ?: return false)
            file.delete()
        } catch (e: Exception) { false }
    }
}
