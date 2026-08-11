package com.callrecorderpro.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.callrecorderpro.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that records WhatsApp calls with BOTH voices:
 *
 *   Stream A (Remote/Other person): AudioPlaybackCaptureConfiguration
 *             → captures what WhatsApp outputs to the speaker
 *
 *   Stream B (Local/Your voice):    AudioRecord (Microphone)
 *             → captures your microphone input
 *
 *   Both streams are written to PCM buffers, then mixed by AudioMixer
 *   and saved as a WAV file, which we later convert metadata for.
 */
class WhatsAppRecorderService : Service() {

    companion object {
        const val CHANNEL_ID       = "RecordPro_Recording"
        const val NOTIFICATION_ID  = 1001
        const val EXTRA_PROJECTION = "media_projection_data"
        const val EXTRA_PROJECTION_RESULT = "media_projection_result"

        private const val SAMPLE_RATE      = 44100
        private const val CHANNEL_CONFIG   = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT     = AudioFormat.ENCODING_PCM_16BIT

        // Shared MediaProjection token — set from MainActivity when user grants permission
        var mediaProjectionIntent: Intent? = null
        var mediaProjectionResult: Int = 0
    }

    private var mediaProjection: MediaProjection? = null
    private var remoteAudioRecord: AudioRecord? = null   // captures WhatsApp speaker output
    private var localAudioRecord: AudioRecord? = null    // captures microphone
    private var recordingJob: Job? = null
    private var outputFile: File? = null
    private var phoneNumber: String = "WhatsApp"
    private var direction: String = "UNKNOWN"
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            WhatsAppCallDetector.ACTION_CALL_STARTED -> {
                phoneNumber = intent.getStringExtra(WhatsAppCallDetector.EXTRA_PHONE_NUMBER)
                    ?: "WhatsApp"
                direction = intent.getStringExtra(WhatsAppCallDetector.EXTRA_DIRECTION)
                    ?: "UNKNOWN"
                startRecording()
            }
            WhatsAppCallDetector.ACTION_CALL_ENDED -> {
                stopRecording()
            }
        }
        return START_STICKY
    }

    private fun startRecording() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Recording WhatsApp call…"))

        val projectionIntent = mediaProjectionIntent ?: run {
            // MediaProjection not yet granted — we cannot record app audio.
            // Still record microphone only as fallback.
            startMicOnlyRecording()
            return
        }

        val projManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager
        mediaProjection = projManager.getMediaProjection(
            mediaProjectionResult, projectionIntent
        )

        outputFile = buildOutputFile()

        recordingJob = scope.launch {
            recordBothVoices()
        }
    }

    /**
     * Records BOTH voices simultaneously:
     * - Remote (other person) via AudioPlaybackCapture from MediaProjection
     * - Local (your voice)    via Microphone AudioRecord
     * Mixed together into a single WAV file.
     */
    private fun recordBothVoices() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufSize = maxOf(minBuf, 4096)

        // ── Stream A: Remote party (WhatsApp speaker output) ──────────────
        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        remoteAudioRecord = AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setAudioPlaybackCaptureConfig(captureConfig)
            .build()

        // ── Stream B: Local party (your microphone) ───────────────────────
        localAudioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufSize
        )

        remoteAudioRecord?.startRecording()
        localAudioRecord?.startRecording()

        val file = outputFile ?: return
        FileOutputStream(file).use { fos ->
            // Write WAV header placeholder (will be filled after recording)
            AudioMixer.writeWavHeader(fos, SAMPLE_RATE, 1, 16, 0)

            val remoteBuf = ShortArray(bufSize / 2)
            val localBuf  = ShortArray(bufSize / 2)
            val mixedBuf  = ShortArray(bufSize / 2)
            var totalBytes = 0L

            while (isRecordingActive()) {
                val remoteRead = remoteAudioRecord?.read(remoteBuf, 0, remoteBuf.size) ?: 0
                val localRead  = localAudioRecord?.read(localBuf, 0, localBuf.size) ?: 0
                val count = maxOf(remoteRead, localRead).coerceAtLeast(0)

                // Mix: average both streams (prevents clipping)
                for (i in 0 until count) {
                    val r = if (i < remoteRead) remoteBuf[i].toInt() else 0
                    val l = if (i < localRead)  localBuf[i].toInt()  else 0
                    mixedBuf[i] = AudioMixer.mix(r, l)
                }

                // Write mixed PCM to file
                val bytes = AudioMixer.shortsToBytes(mixedBuf, count)
                fos.write(bytes)
                totalBytes += bytes.size
            }

            // Update WAV header with actual data size
            AudioMixer.updateWavHeader(file, totalBytes)
        }
    }

    /** Fallback: record microphone only if MediaProjection not available */
    private fun startMicOnlyRecording() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufSize = maxOf(minBuf, 4096)
        localAudioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufSize
        )
        outputFile = buildOutputFile()
        recordingJob = scope.launch {
            localAudioRecord?.startRecording()
            val file = outputFile ?: return@launch
            FileOutputStream(file).use { fos ->
                AudioMixer.writeWavHeader(fos, SAMPLE_RATE, 1, 16, 0)
                val buf = ShortArray(bufSize / 2)
                var totalBytes = 0L
                while (isRecordingActive()) {
                    val read = localAudioRecord?.read(buf, 0, buf.size) ?: 0
                    if (read > 0) {
                        val bytes = AudioMixer.shortsToBytes(buf, read)
                        fos.write(bytes)
                        totalBytes += bytes.size
                    }
                }
                AudioMixer.updateWavHeader(file, totalBytes)
            }
        }
    }

    @Volatile private var active = false
    private fun isRecordingActive() = active

    private fun stopRecording() {
        active = false
        recordingJob?.cancel()
        remoteAudioRecord?.stop()
        remoteAudioRecord?.release()
        remoteAudioRecord = null
        localAudioRecord?.stop()
        localAudioRecord?.release()
        localAudioRecord = null
        mediaProjection?.stop()
        mediaProjection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildOutputFile(): File {
        val dir = File(getExternalFilesDir(null), "WhatsApp").also { it.mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val safeName = phoneNumber.replace("[^\\w+]".toRegex(), "_")
        val suffix = when (direction) { "INCOMING" -> "_IN"; "OUTGOING" -> "_OUT"; else -> "" }
        return File(dir, "WA_${ts}_${safeName}${suffix}.wav")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "RecordPro Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shown while recording a call" }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RecordPro")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()
        active = true
    }
}
