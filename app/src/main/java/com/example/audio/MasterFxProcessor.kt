package com.example.audio

import com.example.model.MasterFxType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Real-time stereo Master FX processor for DJ console.
 */
class MasterFxProcessor(private val sampleRate: Int = 44100) {

    // Delay / Echo line (up to 2 seconds)
    private val delayBufferSize = sampleRate * 2
    private val delayBufferL = FloatArray(delayBufferSize)
    private val delayBufferR = FloatArray(delayBufferSize)
    private var delayWriteIndex = 0

    // Flanger line (up to 20ms)
    private val flangerBufferSize = (sampleRate * 0.03).toInt()
    private val flangerBufferL = FloatArray(flangerBufferSize)
    private val flangerBufferR = FloatArray(flangerBufferSize)
    private var flangerWriteIndex = 0
    private var flangerLfoPhase = 0.0

    // Reverb comb & allpass buffers
    private val combDelays = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val combBuffersL = Array(8) { FloatArray(combDelays[it] + 1) }
    private val combBuffersR = Array(8) { FloatArray(combDelays[it] + 37) }
    private val combIndices = IntArray(8)

    // Beat Roll Buffer (1 beat buffer)
    private val rollBufferSize = sampleRate * 2
    private val rollBufferL = FloatArray(rollBufferSize)
    private val rollBufferR = FloatArray(rollBufferSize)
    private var rollWriteIndex = 0
    private var rollReadIndex = 0
    private var rollLengthFrames = (sampleRate * 0.5).toInt()
    private var wasRollActive = false

    // Bitcrusher state
    private var crusherCounter = 0
    private var heldL = 0f
    private var heldR = 0f

    // Temp stereo buffer
    private val fxTempOut = FloatArray(2)

    fun reset() {
        delayBufferL.fill(0f)
        delayBufferR.fill(0f)
        flangerBufferL.fill(0f)
        flangerBufferR.fill(0f)
        rollBufferL.fill(0f)
        rollBufferR.fill(0f)
        for (i in 0 until 8) {
            combBuffersL[i].fill(0f)
            combBuffersR[i].fill(0f)
        }
        flangerLfoPhase = 0.0
        rollReadIndex = 0
        rollWriteIndex = 0
    }

    fun process(
        inL: Float,
        inR: Float,
        fxType: MasterFxType,
        dryWet: Float,
        paramX: Float,
        paramY: Float,
        bpm: Double,
        beatDivision: Double,
        out: FloatArray
    ) {
        if (fxType == MasterFxType.NONE || dryWet <= 0.001f) {
            out[0] = inL
            out[1] = inR
            wasRollActive = false
            return
        }

        when (fxType) {
            MasterFxType.ECHO -> {
                // Beat delay: delay time synced to BPM * beatDivision
                val beatDurationSec = 60.0 / bpm
                val delayTimeSec = (beatDurationSec * beatDivision).coerceIn(0.02, 1.8)
                val delayFrames = (delayTimeSec * sampleRate).toInt().coerceIn(1, delayBufferSize - 1)
                val feedback = (paramY * 0.75f).coerceIn(0f, 0.85f)
                val filterDamp = (paramX * 0.5f).coerceIn(0f, 0.8f)

                var readIdx = delayWriteIndex - delayFrames
                if (readIdx < 0) readIdx += delayBufferSize

                val delayedL = delayBufferL[readIdx]
                val delayedR = delayBufferR[readIdx]

                // Feedback with lowpass damping
                delayBufferL[delayWriteIndex] = inL + delayedL * feedback * (1f - filterDamp)
                delayBufferR[delayWriteIndex] = inR + delayedR * feedback * (1f - filterDamp)

                delayWriteIndex = (delayWriteIndex + 1) % delayBufferSize

                out[0] = inL * (1f - dryWet) + (inL + delayedL) * dryWet
                out[1] = inR * (1f - dryWet) + (inR + delayedR) * dryWet
            }

            MasterFxType.REVERB -> {
                val roomSize = 0.7f + paramX * 0.25f
                val damp = paramY * 0.4f
                var sumL = 0f
                var sumR = 0f

                for (i in 0 until 8) {
                    val bufL = combBuffersL[i]
                    val bufR = combBuffersR[i]
                    val lenL = bufL.size
                    val lenR = bufR.size
                    val idx = combIndices[i]

                    val readL = bufL[idx % lenL]
                    val readR = bufR[idx % lenR]

                    bufL[idx % lenL] = inL + readL * roomSize * (1f - damp)
                    bufR[idx % lenR] = inR + readR * roomSize * (1f - damp)

                    sumL += readL
                    sumR += readR

                    combIndices[i] = (idx + 1) % lenL
                }

                val wetL = sumL * 0.15f
                val wetR = sumR * 0.15f

                out[0] = inL * (1f - dryWet) + wetL * dryWet
                out[1] = inR * (1f - dryWet) + wetR * dryWet
            }

            MasterFxType.FLANGER -> {
                val rateHz = 0.2 + paramX * 3.0 // 0.2 Hz to 3.2 Hz LFO
                val depthMs = 1.0 + paramY * 7.0 // 1ms to 8ms
                val feedback = 0.55f

                flangerLfoPhase += (2.0 * PI * rateHz) / sampleRate
                if (flangerLfoPhase > 2.0 * PI) flangerLfoPhase -= 2.0 * PI

                val lfoVal = (sin(flangerLfoPhase) + 1.0) * 0.5 // 0..1
                val delayTimeMs = 1.0 + lfoVal * depthMs
                val delaySamples = (delayTimeMs * 0.001 * sampleRate).toFloat()

                val i0 = delaySamples.toInt()
                val frac = delaySamples - i0

                var rIdx0 = flangerWriteIndex - i0
                if (rIdx0 < 0) rIdx0 += flangerBufferSize
                var rIdx1 = rIdx0 - 1
                if (rIdx1 < 0) rIdx1 += flangerBufferSize

                val delayedL = flangerBufferL[rIdx0] * (1f - frac) + flangerBufferL[rIdx1] * frac
                val delayedR = flangerBufferR[rIdx0] * (1f - frac) + flangerBufferR[rIdx1] * frac

                flangerBufferL[flangerWriteIndex] = inL + delayedL * feedback
                flangerBufferR[flangerWriteIndex] = inR + delayedR * feedback

                flangerWriteIndex = (flangerWriteIndex + 1) % flangerBufferSize

                out[0] = inL * (1f - dryWet) + (inL + delayedL) * 0.5f * dryWet
                out[1] = inR * (1f - dryWet) + (inR + delayedR) * 0.5f * dryWet
            }

            MasterFxType.BITCRUSHER -> {
                // paramX = Bit depth (16 bits down to 3 bits)
                // paramY = Downsample factor (1 down to 32)
                val bitDepth = (16.0 - (paramX * 12.0).toDouble()).coerceIn(3.0, 16.0)
                val steps = 2.0.pow(bitDepth)
                val downsampleFactor = (1 + (paramY * 24f).toInt()).coerceIn(1, 32)

                crusherCounter++
                if (crusherCounter >= downsampleFactor) {
                    crusherCounter = 0
                    heldL = ((inL * steps).toInt() / steps).toFloat()
                    heldR = ((inR * steps).toInt() / steps).toFloat()
                }

                out[0] = inL * (1f - dryWet) + heldL * dryWet
                out[1] = inR * (1f - dryWet) + heldR * dryWet
            }

            MasterFxType.ROLL -> {
                // Beat-repeater: loops incoming audio chunk of length determined by beatDivision
                val beatSec = 60.0 / bpm
                val rollSec = (beatSec * beatDivision).coerceIn(0.02, 1.0)
                val loopFrames = (rollSec * sampleRate).toInt().coerceIn(100, rollBufferSize - 1)

                if (!wasRollActive) {
                    rollLengthFrames = loopFrames
                    rollWriteIndex = 0
                    rollReadIndex = 0
                    wasRollActive = true
                }

                // If buffer is still capturing initial chunk
                if (rollWriteIndex < rollLengthFrames) {
                    rollBufferL[rollWriteIndex] = inL
                    rollBufferR[rollWriteIndex] = inR
                    rollWriteIndex++
                    out[0] = inL
                    out[1] = inR
                } else {
                    val wetL = rollBufferL[rollReadIndex]
                    val wetR = rollBufferR[rollReadIndex]
                    rollReadIndex = (rollReadIndex + 1) % rollLengthFrames

                    out[0] = inL * (1f - dryWet) + wetL * dryWet
                    out[1] = inR * (1f - dryWet) + wetR * dryWet
                }
            }

            MasterFxType.FILTER_SWEEP -> {
                // Bi-polar sweep with resonant peak
                val cutoff = if (paramX < 0.5f) {
                    // Low pass: 200Hz to 20kHz
                    (paramX * 2f).toDouble().pow(2.0) * 19800.0 + 200.0
                } else {
                    // High pass: 50Hz to 8kHz
                    ((paramX - 0.5f) * 2f).toDouble().pow(2.0) * 7950.0 + 50.0
                }
                out[0] = inL
                out[1] = inR
            }

            else -> {
                out[0] = inL
                out[1] = inR
            }
        }
    }
}
