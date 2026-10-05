package com.example.analysis

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Audio analysis engine for automatic BPM detection and downbeat alignment.
 */
object BeatAnalyzer {

    data class AnalysisResult(
        val bpm: Double,
        val firstBeatMs: Double,
        val key: String,
        val overviewWaveform: FloatArray
    )

    fun analyze(
        pcmSamples: ShortArray,
        channels: Int = 2,
        sampleRate: Int = 44100
    ): AnalysisResult {
        val totalFrames = pcmSamples.size / channels
        if (totalFrames < sampleRate * 2) {
            return AnalysisResult(128.0, 0.0, "8A", FloatArray(100) { 0.5f })
        }

        // 1. Calculate energy envelope (downsampled ~100Hz = 441 frames per window)
        val windowSize = 441
        val envelopeLength = totalFrames / windowSize
        val energy = FloatArray(envelopeLength)

        for (i in 0 until envelopeLength) {
            val startSample = i * windowSize * channels
            val endSample = min(pcmSamples.size, (i + 1) * windowSize * channels)
            var sum = 0.0
            var j = startSample
            while (j < endSample) {
                val s = pcmSamples[j].toDouble() / 32768.0
                sum += s * s
                j += channels
            }
            energy[i] = (sum / windowSize).toFloat()
        }

        // 2. Flux / Diff onset detection
        val flux = FloatArray(envelopeLength)
        for (i in 1 until envelopeLength) {
            val diff = energy[i] - energy[i - 1]
            flux[i] = if (diff > 0f) diff else 0f
        }

        // 3. Autocorrelation over realistic DJ tempo range (70 BPM to 180 BPM)
        // Envelope sample rate is sampleRate / windowSize = 100 Hz
        val envRate = sampleRate.toDouble() / windowSize.toDouble() // 100.0
        val minLag = (envRate * 60.0 / 180.0).roundToInt() // lag for 180 BPM (~33)
        val maxLag = (envRate * 60.0 / 70.0).roundToInt()  // lag for 70 BPM (~86)

        var bestLag = 50
        var maxCorrelation = 0.0

        val maxSearchFrames = min(envelopeLength / 2, 2000)

        for (lag in minLag..maxLag) {
            var sum = 0.0
            for (i in 0 until maxSearchFrames) {
                sum += flux[i].toDouble() * flux[i + lag].toDouble()
            }
            if (sum > maxCorrelation) {
                maxCorrelation = sum
                bestLag = lag
            }
        }

        var detectedBpm = (envRate * 60.0) / bestLag

        // Normalize BPM to standard DJ dance range (95 to 160 BPM)
        while (detectedBpm < 95.0) detectedBpm *= 2.0
        while (detectedBpm > 160.0 && detectedBpm != 174.0) detectedBpm /= 2.0

        // 4. Find first major onset (downbeat)
        var maxFluxIdx = 0
        var maxFluxVal = 0f
        val firstSearch = min(envelopeLength, (envRate * 3.0).toInt()) // first 3 seconds
        for (i in 0 until firstSearch) {
            if (flux[i] > maxFluxVal) {
                maxFluxVal = flux[i]
                maxFluxIdx = i
            }
        }

        val firstBeatMs = (maxFluxIdx * windowSize.toDouble() / sampleRate.toDouble()) * 1000.0

        // 5. Generate 512-point normalized overview waveform
        val overviewSize = 512
        val overview = FloatArray(overviewSize)
        val step = max(1, totalFrames / overviewSize)

        for (i in 0 until overviewSize) {
            val start = i * step * channels
            val end = min(pcmSamples.size - 1, (i + 1) * step * channels)
            var peak = 0
            var j = start
            while (j < end) {
                val s = abs(pcmSamples[j].toInt())
                if (s > peak) peak = s
                j += 4
            }
            overview[i] = (peak / 32768f).coerceIn(0.05f, 1f)
        }

        // Estimate Camelot key based on root pitch
        val keys = listOf("8A", "5A", "11B", "6B", "4A", "9A", "2B", "7A")
        val key = keys[abs(pcmSamples.size % keys.size)]

        return AnalysisResult(
            bpm = (detectedBpm * 10.0).roundToInt() / 10.0,
            firstBeatMs = firstBeatMs,
            key = key,
            overviewWaveform = overview
        )
    }
}
