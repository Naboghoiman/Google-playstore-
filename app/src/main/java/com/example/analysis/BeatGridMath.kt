package com.example.analysis

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Math utilities for DJ beatgrid, quantization, and musical harmonic key calculation.
 */
object BeatGridMath {

    /**
     * Calculates the frame duration of a single beat based on BPM and sample rate.
     */
    fun beatDurationFrames(bpm: Double, sampleRate: Int = 44100): Double {
        return (60.0 / bpm) * sampleRate
    }

    /**
     * Calculates the millisecond duration of a single beat.
     */
    fun beatDurationMs(bpm: Double): Double {
        return (60.0 / bpm) * 1000.0
    }

    /**
     * Snaps a timestamp in milliseconds to the nearest beat or sub-beat division.
     * quantizeDivision: 1.0 (quarter beat), 0.5 (eighth beat), 0.25 (sixteenth)
     */
    fun snapToGrid(
        currentMs: Double,
        gridOffsetMs: Double,
        bpm: Double,
        quantizeDivision: Double = 1.0
    ): Double {
        val beatMs = beatDurationMs(bpm)
        val stepMs = beatMs * quantizeDivision
        if (stepMs <= 0.0) return currentMs

        val delta = currentMs - gridOffsetMs
        val nearestStep = (delta / stepMs).roundToInt()
        return gridOffsetMs + (nearestStep * stepMs)
    }

    /**
     * Checks harmonic compatibility between two Camelot key notations (e.g. "8A" and "8B", "7A", "9A").
     */
    fun isHarmonicallyCompatible(key1: String, key2: String): Boolean {
        if (key1.length < 2 || key2.length < 2) return false
        val num1 = key1.dropLast(1).toIntOrNull() ?: return false
        val letter1 = key1.last().uppercaseChar()

        val num2 = key2.dropLast(1).toIntOrNull() ?: return false
        val letter2 = key2.last().uppercaseChar()

        // Same key
        if (num1 == num2 && letter1 == letter2) return true
        // Relative Major / Minor (same number, different letter A/B)
        if (num1 == num2 && letter1 != letter2) return true
        // Adjacent key on wheel (+1 or -1 on 12-hour clock, same letter)
        val diff = abs(num1 - num2)
        if ((diff == 1 || diff == 11) && letter1 == letter2) return true

        return false
    }
}
