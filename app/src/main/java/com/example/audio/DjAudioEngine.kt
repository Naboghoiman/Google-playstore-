package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import com.example.model.CrossfaderCurve
import com.example.model.DeckId
import com.example.model.DeckState
import com.example.model.MasterFxType
import com.example.model.MixerState
import com.example.model.PadMode
import com.example.model.PcmTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-time low-latency DJ Audio Engine.
 * Runs on a dedicated real-time audio thread driving an AudioTrack in streaming mode.
 */
class DjAudioEngine(val context: Context) {

    private val sampleRate = 44100
    private val bufferSizeFrames = 512
    private val bufferSizeBytes = bufferSizeFrames * 2 * 2 // 2 channels, 2 bytes/sample (16-bit)

    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null

    @Volatile
    private var isRunning = false

    // State flows
    private val _deckAState = MutableStateFlow(DeckState(deckId = DeckId.DECK_A))
    val deckAState: StateFlow<DeckState> = _deckAState.asStateFlow()

    private val _deckBState = MutableStateFlow(DeckState(deckId = DeckId.DECK_B))
    val deckBState: StateFlow<DeckState> = _deckBState.asStateFlow()

    private val _mixerState = MutableStateFlow(MixerState())
    val mixerState: StateFlow<MixerState> = _mixerState.asStateFlow()

    // Internal audio playback state (mutated directly on audio thread for latency)
    private class DeckAudioState {
        var track: PcmTrack? = null
        var isPlaying = false
        var currentFrame = 0.0
        var speedRate = 1.0
        var pitchSlider = 0f
        var pitchRange = 16f
        var pitchBend = 0.0 // temporary bend (-0.08 to +0.08)
        var isCuePressed = false
        var cueFrame = 0.0
        val hotCueFrames = DoubleArray(8) { -1.0 }
        var isLoopActive = false
        var loopStartFrame = -1.0
        var loopEndFrame = -1.0

        // Platter & Scratch
        var isScratching = false
        var scratchVelocity = 0.0
        var platterAngle = 0f

        // EQ & Filter
        val eqLow = BiquadFilter()
        val eqMid = BiquadFilter()
        val eqHigh = BiquadFilter()
        val filter = BiquadFilter()

        var eqLowGain = 0f
        var eqMidGain = 0f
        var eqHighGain = 0f
        var eqLowKill = false
        var eqMidKill = false
        var eqHighKill = false
        var filterKnob = 0f // -1f .. +1f
        var gainTrim = 1f
        var volume = 1f
        var pfl = false

        // VU levels
        var vuPeakL = 0f
        var vuPeakR = 0f
    }

    private val deckA = DeckAudioState()
    private val deckB = DeckAudioState()

    // Master FX processor
    private val fxProcessor = MasterFxProcessor(sampleRate)

    // Recorder
    val recorder = WavAudioRecorder(context)

    // Active sampler voices
    private class SamplerVoice(
        val pcmData: ShortArray,
        var currentFrame: Int = 0,
        var isLoop: Boolean = false,
        var volume: Float = 0.8f
    )

    private val activeSamplerVoices = mutableListOf<SamplerVoice>()
    private val samplerLock = Any()

    // Demo tracks and sample cache
    val demoTracks: List<PcmTrack> by lazy {
        DjTrackSynthesizer.createAllDemoTracks()
    }

    private val samplerBankPcm = Array(16) { ShortArray(0) }

    init {
        // Pre-render sampler soundbank in background
        Thread {
            for (i in 0 until 16) {
                samplerBankPcm[i] = DjTrackSynthesizer.synthesizeSamplerPad(i)
            }
        }.start()

        // Setup EQ filters
        setupFilters(deckA)
        setupFilters(deckB)
    }

    private fun setupFilters(deck: DeckAudioState) {
        deck.eqLow.setLowShelf(sampleRate.toDouble(), 250.0, 0.0)
        deck.eqMid.setPeaking(sampleRate.toDouble(), 1000.0, 0.0)
        deck.eqHigh.setHighShelf(sampleRate.toDouble(), 3500.0, 0.0)
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val trackBufSize = max(minBuf, bufferSizeBytes * 2)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(trackBufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        audioTrack?.play()

        audioThread = Thread({ audioLoop() }, "DjAudioEngineThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        isRunning = false
        try {
            audioThread?.join(500)
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Real-time audio rendering loop.
     */
    private fun audioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val pcmOutShorts = ShortArray(bufferSizeFrames * 2)
        val pcmOutBytes = ByteArray(bufferSizeFrames * 4)
        val byteBuffer = ByteBuffer.wrap(pcmOutBytes).order(ByteOrder.LITTLE_ENDIAN)

        val frameLROutA = FloatArray(2)
        val frameLROutB = FloatArray(2)
        val tempEqOut = FloatArray(2)
        val tempFilterOut = FloatArray(2)
        val masterFxOut = FloatArray(2)

        var frameCounter = 0L

        while (isRunning) {
            val mixerSnapshot = _mixerState.value
            val isRecording = recorder.isRecording

            var masterPeakL = 0f
            var masterPeakR = 0f

            for (f in 0 until bufferSizeFrames) {
                // ---- RENDER DECK A ----
                var deckASampleL = 0f
                var deckASampleR = 0f
                renderDeckFrame(deckA, frameLROutA)
                if (deckA.isPlaying || deckA.isScratching || deckA.isCuePressed) {
                    processDeckDsp(deckA, frameLROutA, tempEqOut, tempFilterOut)
                    deckASampleL = tempFilterOut[0] * deckA.volume * deckA.gainTrim
                    deckASampleR = tempFilterOut[1] * deckA.volume * deckA.gainTrim
                }

                // ---- RENDER DECK B ----
                var deckBSampleL = 0f
                var deckBSampleR = 0f
                renderDeckFrame(deckB, frameLROutB)
                if (deckB.isPlaying || deckB.isScratching || deckB.isCuePressed) {
                    processDeckDsp(deckB, frameLROutB, tempEqOut, tempFilterOut)
                    deckBSampleL = tempFilterOut[0] * deckB.volume * deckB.gainTrim
                    deckBSampleR = tempFilterOut[1] * deckB.volume * deckB.gainTrim
                }

                // ---- CROSSFADER GAIN CALCULATION ----
                val xfade = if (mixerSnapshot.isHamsterReversed) -mixerSnapshot.crossfaderPosition else mixerSnapshot.crossfaderPosition
                val (gainA, gainB) = calculateCrossfaderGains(xfade, mixerSnapshot.crossfaderCurve)

                var mixedL = deckASampleL * gainA + deckBSampleL * gainB
                var mixedR = deckASampleR * gainA + deckBSampleR * gainB

                // ---- SAMPLER VOICES MIXING ----
                var samplerMixL = 0f
                var samplerMixR = 0f
                synchronized(samplerLock) {
                    val it = activeSamplerVoices.iterator()
                    while (it.hasNext()) {
                        val voice = it.next()
                        val pcm = voice.pcmData
                        val idx = voice.currentFrame * 2
                        if (idx + 1 < pcm.size) {
                            val sl = (pcm[idx] / 32768f) * voice.volume
                            val sr = (pcm[idx + 1] / 32768f) * voice.volume
                            samplerMixL += sl
                            samplerMixR += sr
                            voice.currentFrame++
                        } else {
                            if (voice.isLoop) {
                                voice.currentFrame = 0
                            } else {
                                it.remove()
                            }
                        }
                    }
                }

                mixedL += samplerMixL
                mixedR += samplerMixR

                // ---- MASTER FX PROCESSOR ----
                fxProcessor.process(
                    inL = mixedL,
                    inR = mixedR,
                    fxType = mixerSnapshot.activeFxType,
                    dryWet = if (mixerSnapshot.isFxActive) mixerSnapshot.fxDryWet else 0f,
                    paramX = mixerSnapshot.fxParamX,
                    paramY = mixerSnapshot.fxParamY,
                    bpm = deckA.track?.bpm ?: 128.0,
                    beatDivision = mixerSnapshot.fxBeatDivision,
                    out = masterFxOut
                )

                // Master Volume & Soft Limiter
                val masterVol = mixerSnapshot.masterVolume
                var finalL = masterFxOut[0] * masterVol
                var finalR = masterFxOut[1] * masterVol

                // Soft saturation limiter to avoid harsh digital clipping
                finalL = softClip(finalL)
                finalR = softClip(finalR)

                if (abs(finalL) > masterPeakL) masterPeakL = abs(finalL)
                if (abs(finalR) > masterPeakR) masterPeakR = abs(finalR)

                val shortL = (finalL * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                val shortR = (finalR * 32767f).toInt().coerceIn(-32768, 32767).toShort()

                pcmOutShorts[f * 2] = shortL
                pcmOutShorts[f * 2 + 1] = shortR
            }

            // Write to AudioTrack
            audioTrack?.write(pcmOutShorts, 0, pcmOutShorts.size)

            // Record to WAV if active
            if (isRecording) {
                byteBuffer.clear()
                for (s in pcmOutShorts) {
                    byteBuffer.putShort(s)
                }
                recorder.writeSamples(pcmOutBytes, 0, pcmOutBytes.size)
            }

            frameCounter += bufferSizeFrames

            // Update UI telemetry every ~40ms (about 3-4 chunks)
            if (frameCounter % (bufferSizeFrames * 3) == 0L) {
                updateUiState(masterPeakL, masterPeakR)
            }
        }
    }

    private fun renderDeckFrame(deck: DeckAudioState, out: FloatArray) {
        val track = deck.track
        if (track == null || (!deck.isPlaying && !deck.isScratching && !deck.isCuePressed)) {
            out[0] = 0f
            out[1] = 0f
            return
        }

        // Read sample
        track.readInterpolatedFrame(deck.currentFrame, out)

        // Advance playback
        if (deck.isScratching) {
            deck.currentFrame += deck.scratchVelocity
            deck.platterAngle += (deck.scratchVelocity * 0.1f).toFloat()
            // Inertia deceleration
            deck.scratchVelocity *= 0.92
            if (abs(deck.scratchVelocity) < 0.05) {
                deck.scratchVelocity = 0.0
            }
        } else if (deck.isPlaying || deck.isCuePressed) {
            val rate = (1.0 + (deck.pitchSlider * (deck.pitchRange / 100.0))) + deck.pitchBend
            val step = rate.coerceIn(-2.0, 2.0)
            deck.currentFrame += step

            deck.platterAngle = (deck.platterAngle + (step * 0.8f).toFloat()) % 360f

            // Handle Looping
            if (deck.isLoopActive && deck.loopStartFrame >= 0 && deck.loopEndFrame > deck.loopStartFrame) {
                if (deck.currentFrame >= deck.loopEndFrame) {
                    deck.currentFrame = deck.loopStartFrame
                }
            }

            // End of track detection
            if (deck.currentFrame >= track.totalFrames) {
                deck.currentFrame = track.totalFrames - 1.0
                deck.isPlaying = false
            } else if (deck.currentFrame < 0.0) {
                deck.currentFrame = 0.0
            }
        }
    }

    private fun processDeckDsp(
        deck: DeckAudioState,
        rawIn: FloatArray,
        eqOut: FloatArray,
        filterOut: FloatArray
    ) {
        // 1. Equalizer
        val inL = rawIn[0]
        val inR = rawIn[1]

        val lowGain = if (deck.eqLowKill) -40.0 else deck.eqLowGain.toDouble()
        val midGain = if (deck.eqMidKill) -40.0 else deck.eqMidGain.toDouble()
        val highGain = if (deck.eqHighKill) -40.0 else deck.eqHighGain.toDouble()

        deck.eqLow.setLowShelf(sampleRate.toDouble(), 250.0, lowGain)
        deck.eqMid.setPeaking(sampleRate.toDouble(), 1000.0, midGain)
        deck.eqHigh.setHighShelf(sampleRate.toDouble(), 3500.0, highGain)

        val t1 = FloatArray(2)
        val t2 = FloatArray(2)
        deck.eqLow.processStereo(inL, inR, t1)
        deck.eqMid.processStereo(t1[0], t1[1], t2)
        deck.eqHigh.processStereo(t2[0], t2[1], eqOut)

        // 2. Bi-Polar DJ Filter (-1f = LPF, 0f = Bypass, +1f = HPF)
        val fk = deck.filterKnob
        if (abs(fk) < 0.03f) {
            filterOut[0] = eqOut[0]
            filterOut[1] = eqOut[1]
        } else if (fk < 0f) {
            // Low pass filter: 200 Hz to 20000 Hz
            val norm = 1f + fk // 0..1
            val cutoff = (norm.toDouble().pow(2.0) * 19600.0 + 200.0).coerceIn(100.0, 20000.0)
            deck.filter.setLowPass(sampleRate.toDouble(), cutoff, q = 1.3)
            deck.filter.processStereo(eqOut[0], eqOut[1], filterOut)
        } else {
            // High pass filter: 20 Hz to 8000 Hz
            val cutoff = (fk.toDouble().pow(2.0) * 7950.0 + 50.0).coerceIn(20.0, 10000.0)
            deck.filter.setHighPass(sampleRate.toDouble(), cutoff, q = 1.3)
            deck.filter.processStereo(eqOut[0], eqOut[1], filterOut)
        }

        // VU meter tracker
        if (abs(filterOut[0]) > deck.vuPeakL) deck.vuPeakL = abs(filterOut[0])
        if (abs(filterOut[1]) > deck.vuPeakR) deck.vuPeakR = abs(filterOut[1])
    }

    private fun calculateCrossfaderGains(pos: Float, curve: CrossfaderCurve): Pair<Float, Float> {
        val norm = ((pos + 1f) / 2f).coerceIn(0f, 1f) // 0f (Deck A) to 1f (Deck B)

        return when (curve) {
            CrossfaderCurve.LINEAR -> {
                Pair(1f - norm, norm)
            }
            CrossfaderCurve.SMOOTH -> {
                // Equal-power cosine curve
                val gainA = cos(norm * (PI.toFloat() / 2f))
                val gainB = sin(norm * (PI.toFloat() / 2f))
                Pair(gainA, gainB)
            }
            CrossfaderCurve.SCRATCH_CUT -> {
                // Fast cut within 5% of edges
                val gainA = if (norm > 0.95f) (1f - (norm - 0.95f) / 0.05f) else 1f
                val gainB = if (norm < 0.05f) (norm / 0.05f) else 1f
                Pair(gainA, gainB)
            }
            CrossfaderCurve.DIP -> {
                val gainA = (1f - norm).pow(1.5f)
                val gainB = norm.pow(1.5f)
                Pair(gainA, gainB)
            }
        }
    }

    private fun softClip(x: Float): Float {
        return if (x > 1.0f) {
            1.0f - exp(-x)
        } else if (x < -1.0f) {
            -1.0f + exp(x)
        } else {
            x - (x * x * x) / 6f
        }
    }

    private fun updateUiState(masterPeakL: Float, masterPeakR: Float) {
        val posMsA = deckA.track?.frameToMs(deckA.currentFrame) ?: 0.0
        val posMsB = deckB.track?.frameToMs(deckB.currentFrame) ?: 0.0

        val vuA_L = deckA.vuPeakL.coerceIn(0f, 1f)
        val vuA_R = deckA.vuPeakR.coerceIn(0f, 1f)
        deckA.vuPeakL *= 0.75f
        deckA.vuPeakR *= 0.75f

        val vuB_L = deckB.vuPeakL.coerceIn(0f, 1f)
        val vuB_R = deckB.vuPeakR.coerceIn(0f, 1f)
        deckB.vuPeakL *= 0.75f
        deckB.vuPeakR *= 0.75f

        _deckAState.update {
            it.copy(
                isPlaying = deckA.isPlaying,
                currentPositionMs = posMsA,
                platterAngleDegrees = deckA.platterAngle,
                vuLevelLeft = vuA_L,
                vuLevelRight = vuA_R,
                isLoopActive = deckA.isLoopActive
            )
        }

        _deckBState.update {
            it.copy(
                isPlaying = deckB.isPlaying,
                currentPositionMs = posMsB,
                platterAngleDegrees = deckB.platterAngle,
                vuLevelLeft = vuB_L,
                vuLevelRight = vuB_R,
                isLoopActive = deckB.isLoopActive
            )
        }

        _mixerState.update {
            it.copy(
                masterVuLeft = masterPeakL.coerceIn(0f, 1.2f),
                masterVuRight = masterPeakR.coerceIn(0f, 1.2f)
            )
        }
    }

    // =========================================================================
    // PUBLIC CONTROLLER API (called from UI & Controllers)
    // =========================================================================

    fun loadTrack(deckId: DeckId, track: PcmTrack) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.track = track
        target.currentFrame = 0.0
        target.isPlaying = false
        target.isLoopActive = false
        target.loopStartFrame = -1.0
        target.loopEndFrame = -1.0
        target.cueFrame = 0.0
        target.hotCueFrames.fill(-1.0)

        val flow = if (deckId == DeckId.DECK_A) _deckAState else _deckBState
        flow.update {
            it.copy(
                track = track,
                bpm = track.bpm,
                currentPositionMs = 0.0,
                isPlaying = false,
                isLoopActive = false,
                hotCuesMs = List(8) { -1.0 }
            )
        }
    }

    fun togglePlay(deckId: DeckId) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        if (target.track == null) return
        target.isPlaying = !target.isPlaying

        val flow = if (deckId == DeckId.DECK_A) _deckAState else _deckBState
        flow.update { it.copy(isPlaying = target.isPlaying) }
    }

    fun onCueDown(deckId: DeckId) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        if (target.track == null) return

        if (!target.isPlaying) {
            // Stutter preview from cue point
            target.currentFrame = target.cueFrame
            target.isCuePressed = true
        } else {
            // If playing, pause and jump back to cue point
            target.isPlaying = false
            target.currentFrame = target.cueFrame
            target.isCuePressed = false
        }
    }

    fun onCueUp(deckId: DeckId) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        if (target.isCuePressed) {
            target.isCuePressed = false
            target.currentFrame = target.cueFrame
        }
    }

    fun setCuePoint(deckId: DeckId) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.cueFrame = target.currentFrame
        val posMs = target.track?.frameToMs(target.currentFrame) ?: 0.0

        val flow = if (deckId == DeckId.DECK_A) _deckAState else _deckBState
        flow.update { it.copy(cuePositionMs = posMs) }
    }

    fun setPitchSlider(deckId: DeckId, sliderValue: Float) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.pitchSlider = sliderValue.coerceIn(-1f, 1f)

        val flow = if (deckId == DeckId.DECK_A) _deckAState else _deckBState
        flow.update { it.copy(pitchSliderValue = sliderValue) }
    }

    fun setPitchRange(deckId: DeckId, rangePercent: Float) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.pitchRange = rangePercent

        val flow = if (deckId == DeckId.DECK_A) _deckAState else _deckBState
        flow.update { it.copy(pitchRangePercent = rangePercent) }
    }

    fun setPitchBend(deckId: DeckId, isBendUp: Boolean, isPressed: Boolean) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.pitchBend = if (isPressed) {
            if (isBendUp) 0.08 else -0.08
        } else {
            0.0
        }
    }

    fun seekToMs(deckId: DeckId, positionMs: Double) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        val track = target.track ?: return
        target.currentFrame = track.msToFrame(positionMs).coerceIn(0.0, track.totalFrames.toDouble() - 1.0)
    }

    fun onPlatterTouch(deckId: DeckId, isTouched: Boolean) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.isScratching = isTouched
        if (!isTouched) {
            target.scratchVelocity = 0.0
        }

        val flow = if (deckId == DeckId.DECK_A) _deckAState else _deckBState
        flow.update { it.copy(isPlatterTouched = isTouched) }
    }

    fun onPlatterRotate(deckId: DeckId, deltaAngleDegrees: Float) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.platterAngle = (target.platterAngle + deltaAngleDegrees) % 360f

        // Convert delta angle to scratch frame step
        val frameStep = (deltaAngleDegrees / 360f) * (sampleRate * 1.8) // 33.3 RPM rotation scale
        target.currentFrame = (target.currentFrame + frameStep).coerceIn(0.0, (target.track?.totalFrames?.toDouble() ?: 1.0) - 1.0)
        target.scratchVelocity = frameStep * 0.5
    }

    // HOT CUES
    fun triggerHotCue(deckId: DeckId, cueIndex: Int, isClearMode: Boolean = false) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        val track = target.track ?: return

        if (isClearMode) {
            target.hotCueFrames[cueIndex] = -1.0
        } else {
            val existing = target.hotCueFrames[cueIndex]
            if (existing < 0.0) {
                // Set hot cue at current position
                target.hotCueFrames[cueIndex] = target.currentFrame
            } else {
                // Jump to hot cue
                target.currentFrame = existing
                if (!target.isPlaying) {
                    target.isPlaying = true
                }
            }
        }

        val flow = if (deckId == DeckId.DECK_A) _deckAState else _deckBState
        flow.update { state ->
            state.copy(
                hotCuesMs = target.hotCueFrames.map { frame ->
                    if (frame >= 0) track.frameToMs(frame) else -1.0
                }
            )
        }
    }

    // LOOPS
    fun toggleAutoLoop(deckId: DeckId, beats: Double) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        val track = target.track ?: return

        if (target.isLoopActive && target.loopStartFrame >= 0) {
            // Turn off loop
            target.isLoopActive = false
            target.loopStartFrame = -1.0
            target.loopEndFrame = -1.0
        } else {
            // Activate loop of length 'beats'
            val beatDurationSec = 60.0 / track.bpm
            val loopFrames = (beats * beatDurationSec * sampleRate)

            target.loopStartFrame = target.currentFrame
            target.loopEndFrame = target.currentFrame + loopFrames
            target.isLoopActive = true
        }

        val flow = if (deckId == DeckId.DECK_A) _deckAState else _deckBState
        flow.update {
            it.copy(
                isLoopActive = target.isLoopActive,
                autoLoopBeats = beats,
                loopStartMs = if (target.isLoopActive) track.frameToMs(target.loopStartFrame) else -1.0,
                loopEndMs = if (target.isLoopActive) track.frameToMs(target.loopEndFrame) else -1.0
            )
        }
    }

    fun halveLoop(deckId: DeckId) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        if (target.isLoopActive && target.loopEndFrame > target.loopStartFrame) {
            val length = target.loopEndFrame - target.loopStartFrame
            target.loopEndFrame = target.loopStartFrame + (length * 0.5)
        }
    }

    fun doubleLoop(deckId: DeckId) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        if (target.isLoopActive && target.loopEndFrame > target.loopStartFrame) {
            val length = target.loopEndFrame - target.loopStartFrame
            target.loopEndFrame = target.loopStartFrame + (length * 2.0)
        }
    }

    fun beatJump(deckId: DeckId, beats: Double, forward: Boolean) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        val track = target.track ?: return
        val beatSec = 60.0 / track.bpm
        val jumpFrames = beats * beatSec * sampleRate
        val step = if (forward) jumpFrames else -jumpFrames
        target.currentFrame = (target.currentFrame + step).coerceIn(0.0, track.totalFrames.toDouble() - 1.0)
    }

    // EQ & MIXER
    fun setEqGain(deckId: DeckId, band: String, gainDb: Float) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        when (band.uppercase()) {
            "LOW" -> target.eqLowGain = gainDb
            "MID" -> target.eqMidGain = gainDb
            "HIGH" -> target.eqHighGain = gainDb
        }
        val flow = if (deckId == DeckId.DECK_A) _deckAState else _deckBState
        flow.update {
            when (band.uppercase()) {
                "LOW" -> it.copy(eqLowGainDb = gainDb)
                "MID" -> it.copy(eqMidGainDb = gainDb)
                else -> it.copy(eqHighGainDb = gainDb)
            }
        }
    }

    fun toggleEqKill(deckId: DeckId, band: String) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        val flow = if (deckId == DeckId.DECK_A) _deckAState else _deckBState
        when (band.uppercase()) {
            "LOW" -> {
                target.eqLowKill = !target.eqLowKill
                flow.update { it.copy(eqLowKill = target.eqLowKill) }
            }
            "MID" -> {
                target.eqMidKill = !target.eqMidKill
                flow.update { it.copy(eqMidKill = target.eqMidKill) }
            }
            "HIGH" -> {
                target.eqHighKill = !target.eqHighKill
                flow.update { it.copy(eqHighKill = target.eqHighKill) }
            }
        }
    }

    fun setFilterKnob(deckId: DeckId, value: Float) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.filterKnob = value.coerceIn(-1f, 1f)
        val flow = if (deckId == DeckId.DECK_A) _deckAState else _deckBState
        flow.update { it.copy(filterKnob = target.filterKnob) }
    }

    fun setVolumeFader(deckId: DeckId, volume: Float) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.volume = volume.coerceIn(0f, 1f)
        val flow = if (deckId == DeckId.DECK_A) _deckAState else _deckBState
        flow.update { it.copy(volumeFader = target.volume) }
    }

    fun setCrossfaderPosition(pos: Float) {
        _mixerState.update { it.copy(crossfaderPosition = pos.coerceIn(-1f, 1f)) }
    }

    fun setCrossfaderCurve(curve: CrossfaderCurve) {
        _mixerState.update { it.copy(crossfaderCurve = curve) }
    }

    fun setMasterVolume(vol: Float) {
        _mixerState.update { it.copy(masterVolume = vol.coerceIn(0f, 1f)) }
    }

    // MASTER FX
    fun setMasterFxType(type: MasterFxType) {
        _mixerState.update { it.copy(activeFxType = type) }
    }

    fun toggleMasterFx() {
        _mixerState.update { it.copy(isFxActive = !it.isFxActive) }
    }

    fun setFxDryWet(dw: Float) {
        _mixerState.update { it.copy(fxDryWet = dw.coerceIn(0f, 1f)) }
    }

    fun setFxParams(x: Float, y: Float) {
        _mixerState.update { it.copy(fxParamX = x.coerceIn(0f, 1f), fxParamY = y.coerceIn(0f, 1f)) }
    }

    fun setFxBeatDivision(div: Double) {
        _mixerState.update { it.copy(fxBeatDivision = div) }
    }

    // SAMPLER
    fun triggerSamplerPad(padIndex: Int, isLoop: Boolean = false) {
        if (padIndex in 0 until 16) {
            val pcm = samplerBankPcm[padIndex]
            if (pcm.isNotEmpty()) {
                synchronized(samplerLock) {
                    activeSamplerVoices.add(SamplerVoice(pcm, 0, isLoop, 0.85f))
                }
            }
        }
    }

    // SYNC CONTROLLER
    fun syncDecks(masterDeckId: DeckId, slaveDeckId: DeckId) {
        val master = if (masterDeckId == DeckId.DECK_A) deckA else deckB
        val slave = if (slaveDeckId == DeckId.DECK_A) deckA else deckB
        val masterTrack = master.track ?: return
        val slaveTrack = slave.track ?: return

        // 1. Match BPM via pitch slider adjustment
        val targetBpm = masterTrack.bpm * (1.0 + (master.pitchSlider * (master.pitchRange / 100.0)))
        val requiredRatio = (targetBpm / slaveTrack.bpm) - 1.0
        val newSlider = (requiredRatio / (slave.pitchRange / 100.0)).toFloat().coerceIn(-1f, 1f)
        setPitchSlider(slaveDeckId, newSlider)

        // 2. Phase alignment (snap slave beat phase to master beat phase)
        val masterBeatSec = 60.0 / masterTrack.bpm
        val slaveBeatSec = 60.0 / slaveTrack.bpm
        val masterSec = master.currentFrame / sampleRate
        val slaveSec = slave.currentFrame / sampleRate

        val masterPhase = (masterSec % masterBeatSec) / masterBeatSec
        val slaveBeatIdx = (slaveSec / slaveBeatSec).toInt()
        val alignedSlaveSec = (slaveBeatIdx + masterPhase) * slaveBeatSec

        slave.currentFrame = (alignedSlaveSec * sampleRate).coerceIn(0.0, slaveTrack.totalFrames.toDouble() - 1.0)

        _deckAState.update { it.copy(isSyncActive = true, isMaster = (masterDeckId == DeckId.DECK_A)) }
        _deckBState.update { it.copy(isSyncActive = true, isMaster = (masterDeckId == DeckId.DECK_B)) }
    }

    // RECORDING
    fun toggleRecording(): File? {
        return if (recorder.isRecording) {
            val file = recorder.stopRecording()
            _mixerState.update { it.copy(isRecording = false) }
            file
        } else {
            val file = recorder.startRecording()
            _mixerState.update { it.copy(isRecording = true) }
            file
        }
    }

    fun setPadMode(deckId: DeckId, mode: PadMode) {
        val flow = if (deckId == DeckId.DECK_A) _deckAState else _deckBState
        flow.update { it.copy(activePadMode = mode) }
    }
}
