package com.example.model

/**
 * Represents a decoded audio track stored in stereo 16-bit PCM memory.
 * Provides high-speed access for real-time scratching, pitch-shifting, filtering, and looping.
 */
class PcmTrack(
    val id: String,
    val title: String,
    val artist: String,
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val pcmSamples: ShortArray, // interleaved L, R, L, R
    val bpm: Double = 128.0,
    val initialKey: String = "8A",
    val waveformOverview: FloatArray = FloatArray(0), // Normalized peak amplitudes (0f..1f)
    val beatOffsetsMs: DoubleArray = DoubleArray(0)
) {
    val totalFrames: Long = pcmSamples.size / channels.toLong()
    val durationMs: Double = (totalFrames.toDouble() / sampleRate.toDouble()) * 1000.0

    /**
     * Reads interpolated stereo sample at fractional frame position.
     * output[0] = Left (-1.0f .. 1.0f), output[1] = Right (-1.0f .. 1.0f)
     */
    fun readInterpolatedFrame(frameIndexDouble: Double, output: FloatArray) {
        val total = totalFrames
        if (total <= 1 || frameIndexDouble < 0.0 || frameIndexDouble >= (total - 1)) {
            output[0] = 0f
            output[1] = 0f
            return
        }

        val i0 = frameIndexDouble.toInt()
        val frac = (frameIndexDouble - i0).toFloat()
        val i1 = i0 + 1

        val sampleIndex0 = i0 * channels
        val sampleIndex1 = i1 * channels

        val l0 = pcmSamples[sampleIndex0] / 32768f
        val r0 = pcmSamples[sampleIndex0 + 1] / 32768f
        val l1 = pcmSamples[sampleIndex1] / 32768f
        val r1 = pcmSamples[sampleIndex1 + 1] / 32768f

        output[0] = l0 + frac * (l1 - l0)
        output[1] = r0 + frac * (r1 - r0)
    }

    /**
     * Converts millisecond timestamp to frame index.
     */
    fun msToFrame(ms: Double): Double {
        return (ms / 1000.0) * sampleRate
    }

    /**
     * Converts frame index to millisecond timestamp.
     */
    fun frameToMs(frame: Double): Double {
        return (frame / sampleRate) * 1000.0
    }
}
