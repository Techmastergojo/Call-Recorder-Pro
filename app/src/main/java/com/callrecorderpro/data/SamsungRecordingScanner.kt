package com.callrecorderpro.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.CallLog
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Scans Samsung's built-in call recorder folder for recordings.
 *
 * Samsung saves recordings to:
 *   /sdcard/Call/Recordings/        (older One UI)
 *   /sdcard/Recordings/Call/        (some variants)
 *   /sdcard/DCIM/Call/              (rare)
 *
 * Filename format examples:
 *   20240811_143022_+923001234567.m4a
 *   20240811_143022_01_Ahmed.m4a
 */
class SamsungRecordingScanner(private val context: Context) {

    companion object {
        private val SAMSUNG_RECORDING_DIRS = listOf(
            "Call/Recordings",
            "Recordings/Call",
            "DCIM/Call",
            "Call"
        )
        private val AUDIO_EXTENSIONS = setOf("m4a", "aac", "mp3", "ogg", "wav", "opus")
    }

    /**
     * Returns all Samsung call recording files found on the device.
     */
    suspend fun scan(): List<RecordingItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<RecordingItem>()

        // First try well-known Samsung directories
        val sdcard = Environment.getExternalStorageDirectory()
        for (dir in SAMSUNG_RECORDING_DIRS) {
            val folder = File(sdcard, dir)
            if (folder.exists() && folder.isDirectory) {
                folder.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension.lowercase() in AUDIO_EXTENSIONS) {
                        parseToRecordingItem(file)?.let { results.add(it) }
                    }
                }
            }
        }

        // Also scan via MediaStore in case Samsung indexed the files differently
        if (results.isEmpty()) {
            results.addAll(scanViaMediaStore())
        }

        results.sortedByDescending { it.timestampMs }
    }

    private fun parseToRecordingItem(file: File): RecordingItem? {
        return try {
            val name = file.nameWithoutExtension
            // Try to extract phone number from filename
            val phoneRegex = Regex("(\\+?\\d[\\d\\s\\-]{6,}\\d)")
            val phoneMatch = phoneRegex.find(name)?.value?.replace("\\s".toRegex(), "") ?: ""

            // Extract timestamp from filename  e.g. 20240811_143022
            val tsRegex = Regex("(\\d{8})[_-](\\d{6})")
            val tsMatch = tsRegex.find(name)
            val timestampMs = if (tsMatch != null) {
                val (date, time) = tsMatch.destructured
                parseSamsungTimestamp(date, time)
            } else {
                file.lastModified()
            }

            // Duration via MediaMetadataRetriever
            val duration = getAudioDuration(file.absolutePath)

            RecordingItem(
                id = file.absolutePath,
                callerName = null, // Will be enriched by CallLogMatcher
                phoneNumber = phoneMatch,
                direction = Direction.UNKNOWN, // Will be enriched
                type = RecordingType.SIM,
                timestampMs = timestampMs,
                durationSeconds = duration,
                fileUri = Uri.fromFile(file),
                fileSizeBytes = file.length(),
                fileName = file.name
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSamsungTimestamp(date: String, time: String): Long {
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
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(path)
            val ms = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLong() ?: 0L
            retriever.release()
            (ms / 1000).toInt()
        } catch (e: Exception) {
            0
        }
    }

    private fun scanViaMediaStore(): List<RecordingItem> {
        val results = mutableListOf<RecordingItem>()
        val resolver: ContentResolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val selArgs = arrayOf("%Call%")

        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selArgs,
            "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val durCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id       = cursor.getLong(idCol)
                val path     = cursor.getString(dataCol) ?: continue
                val name     = cursor.getString(nameCol) ?: continue
                val dateMs   = cursor.getLong(dateCol) * 1000L
                val size     = cursor.getLong(sizeCol)
                val durMs    = cursor.getLong(durCol)
                val uri      = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString()
                )
                val file = File(path)
                val item = parseToRecordingItem(file) ?: RecordingItem(
                    id = path,
                    callerName = null,
                    phoneNumber = "",
                    direction = Direction.UNKNOWN,
                    type = RecordingType.SIM,
                    timestampMs = dateMs,
                    durationSeconds = (durMs / 1000).toInt(),
                    fileUri = uri,
                    fileSizeBytes = size,
                    fileName = name
                )
                results.add(item)
            }
        }
        return results
    }
}
