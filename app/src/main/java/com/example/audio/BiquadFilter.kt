package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Standard Audio EQ Cookbook Biquad Filter implementation.
 * Direct Form I / II with double-precision coefficients for numerical stability.
 */
class BiquadFilter {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    // State variables for Left and Right channels
    private var x1L = 0.0
    private var x2L = 0.0
    private var y1L = 0.0
    private var y2L = 0.0

    private var x1R = 0.0
    private var x2R = 0.0
    private var y1R = 0.0
    private var y2R = 0.0

    fun reset() {
        x1L = 0.0; x2L = 0.0; y1L = 0.0; y2L = 0.0
        x1R = 0.0; x2R = 0.0; y1R = 0.0; y2R = 0.0
    }

    fun setLowShelf(sampleRate: Double, frequency: Double, gainDb: Double, q: Double = 0.707) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * frequency / sampleRate
        val cosw0 = cos(w0)
        val sinw0 = sin(w0)
        val alpha = sinw0 / (2.0 * q)
        val sqrtA = sqrt(a)

        val a0 = (a + 1.0) + (a - 1.0) * cosw0 + 2.0 * sqrtA * alpha
        b0 = (a * ((a + 1.0) - (a - 1.0) * cosw0 + 2.0 * sqrtA * alpha)) / a0
        b1 = (2.0 * a * ((a - 1.0) - (a + 1.0) * cosw0)) / a0
        b2 = (a * ((a + 1.0) - (a - 1.0) * cosw0 - 2.0 * sqrtA * alpha)) / a0
        a1 = (-2.0 * ((a - 1.0) + (a + 1.0) * cosw0)) / a0
        a2 = ((a + 1.0) + (a - 1.0) * cosw0 - 2.0 * sqrtA * alpha) / a0
    }

    fun setPeaking(sampleRate: Double, frequency: Double, gainDb: Double, q: Double = 1.0) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * frequency / sampleRate
        val cosw0 = cos(w0)
        val sinw0 = sin(w0)
        val alpha = sinw0 / (2.0 * q)

        val a0 = 1.0 + alpha / a
        b0 = (1.0 + alpha * a) / a0
        b1 = (-2.0 * cosw0) / a0
        b2 = (1.0 - alpha * a) / a0
        a1 = (-2.0 * cosw0) / a0
        a2 = (1.0 - alpha / a) / a0
    }

    fun setHighShelf(sampleRate: Double, frequency: Double, gainDb: Double, q: Double = 0.707) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * frequency / sampleRate
        val cosw0 = cos(w0)
        val sinw0 = sin(w0)
        val alpha = sinw0 / (2.0 * q)
        val sqrtA = sqrt(a)

        val a0 = (a + 1.0) - (a - 1.0) * cosw0 + 2.0 * sqrtA * alpha
        b0 = (a * ((a + 1.0) + (a - 1.0) * cosw0 + 2.0 * sqrtA * alpha)) / a0
        b1 = (-2.0 * a * ((a - 1.0) + (a + 1.0) * cosw0)) / a0
        b2 = (a * ((a + 1.0) + (a - 1.0) * cosw0 - 2.0 * sqrtA * alpha)) / a0
        a1 = (2.0 * ((a - 1.0) - (a + 1.0) * cosw0)) / a0
        a2 = ((a + 1.0) - (a - 1.0) * cosw0 - 2.0 * sqrtA * alpha) / a0
    }

    fun setLowPass(sampleRate: Double, cutoffFreq: Double, q: Double = 1.2) {
        val w0 = 2.0 * PI * cutoffFreq.coerceIn(20.0, sampleRate * 0.48) / sampleRate
        val cosw0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)

        val a0 = 1.0 + alpha
        b0 = ((1.0 - cosw0) / 2.0) / a0
        b1 = (1.0 - cosw0) / a0
        b2 = ((1.0 - cosw0) / 2.0) / a0
        a1 = (-2.0 * cosw0) / a0
        a2 = (1.0 - alpha) / a0
    }

    fun setHighPass(sampleRate: Double, cutoffFreq: Double, q: Double = 1.2) {
        val w0 = 2.0 * PI * cutoffFreq.coerceIn(20.0, sampleRate * 0.48) / sampleRate
        val cosw0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)

        val a0 = 1.0 + alpha
        b0 = ((1.0 + cosw0) / 2.0) / a0
        b1 = (-(1.0 + cosw0)) / a0
        b2 = ((1.0 + cosw0) / 2.0) / a0
        a1 = (-2.0 * cosw0) / a0
        a2 = (1.0 - alpha) / a0
    }

    fun processStereo(inL: Float, inR: Float, out: FloatArray) {
        val inLd = inL.toDouble()
        val outLd = b0 * inLd + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
        x2L = x1L; x1L = inLd; y2L = y1L; y1L = outLd

        val inRd = inR.toDouble()
        val outRd = b0 * inRd + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R
        x2R = x1R; x1R = inRd; y2R = y1R; y1R = outRd

        out[0] = outLd.toFloat().coerceIn(-2.0f, 2.0f)
        out[1] = outRd.toFloat().coerceIn(-2.0f, 2.0f)
    }
}
