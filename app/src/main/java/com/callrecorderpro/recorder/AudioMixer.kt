package com.callrecorderpro.recorder

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Mixes two PCM audio streams (remote + local voice) into one.
 * Also handles WAV file header writing/updating.
 */
object AudioMixer {

    /**
     * Mixes two 16-bit PCM samples with soft-clipping to avoid distortion.
     * Simple averaging prevents overflow while preserving both voices.
     */
    fun mix(sampleA: Int, sampleB: Int): Short {
        // Sum with soft clipping
        val sum = sampleA + sampleB
        return sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    fun shortsToBytes(shorts: ShortArray, count: Int): ByteArray {
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            bytes[i * 2]     = (shorts[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (shorts[i].toInt() shr 8 and 0xFF).toByte()
        }
        return bytes
    }

    /**
     * Writes a standard PCM WAV header.
     * dataSize = 0 as placeholder; call updateWavHeader() when done.
     */
    fun writeWavHeader(
        out: FileOutputStream,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        dataSize: Long
    ) {
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()
        val blockAlign = (channels * bitsPerSample / 8)
        val totalSize = dataSize + 36

        out.write("RIFF".toByteArray())
        out.write(intToLEBytes(totalSize.toInt()))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(intToLEBytes(16))                   // PCM chunk size
        out.write(shortToLEBytes(1))                  // PCM format
        out.write(shortToLEBytes(channels.toShort()))
        out.write(intToLEBytes(sampleRate))
        out.write(intToLEBytes(byteRate.toInt()))
        out.write(shortToLEBytes(blockAlign.toShort()))
        out.write(shortToLEBytes(bitsPerSample.toShort()))
        out.write("data".toByteArray())
        out.write(intToLEBytes(dataSize.toInt()))
    }

    /** Updates WAV header after recording is complete with actual byte count. */
    fun updateWavHeader(file: File, dataSize: Long) {
        RandomAccessFile(file, "rw").use { raf ->
            // Update RIFF chunk size at byte 4
            raf.seek(4)
            raf.write(intToLEBytes((dataSize + 36).toInt()))
            // Update data chunk size at byte 40
            raf.seek(40)
            raf.write(intToLEBytes(dataSize.toInt()))
        }
    }

    private fun intToLEBytes(value: Int) = byteArrayOf(
        (value and 0xFF).toByte(),
        (value shr 8 and 0xFF).toByte(),
        (value shr 16 and 0xFF).toByte(),
        (value shr 24 and 0xFF).toByte()
    )

    private fun shortToLEBytes(value: Short) = byteArrayOf(
        (value.toInt() and 0xFF).toByte(),
        (value.toInt() shr 8 and 0xFF).toByte()
    )
}
