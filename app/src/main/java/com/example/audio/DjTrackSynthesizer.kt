package com.example.audio

import com.example.model.PcmTrack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural DJ audio synthesis engine that generates authentic multi-layered electronic tracks
 * and responsive studio sampler soundbanks directly into memory.
 */
object DjTrackSynthesizer {

    private const val SAMPLE_RATE = 44100

    /**
     * Generates all 8 high-energy DJ demo tracks.
     */
    fun createAllDemoTracks(): List<PcmTrack> {
        return listOf(
            createTechHouseTrack(),
            createFutureRaveTrack(),
            createPeakTimeTechnoTrack(),
            createDeepHouseTrack(),
            createHipHopTrapTrack(),
            createAfrobeatTrack(),
            createDrumAndBassTrack(),
            createSynthwaveTrack()
        )
    }

    /**
     * Track 1: Tech House (126 BPM, Am / 8A)
     */
    fun createTechHouseTrack(): PcmTrack {
        val bpm = 126.0
        val durationBars = 32 // ~61 seconds
        val samples = renderElectronicBeat(
            bpm = bpm,
            bars = durationBars,
            rootFreq = 55.0, // A1
            hasKick = true,
            hasClap = true,
            hasHats = true,
            hasRollingBass = true,
            hasSynthChords = true,
            synthScale = intArrayOf(0, 3, 5, 7, 10), // Minor pentatonic
            swing = 0.05f
        )
        return buildTrack(
            id = "demo_track_01",
            title = "Neon Nights (Tech House)",
            artist = "DJ IMAN",
            bpm = bpm,
            key = "8A",
            pcm = samples
        )
    }

    /**
     * Track 2: Future Rave / EDM (128 BPM, Cm / 5A)
     */
    fun createFutureRaveTrack(): PcmTrack {
        val bpm = 128.0
        val durationBars = 32
        val samples = renderElectronicBeat(
            bpm = bpm,
            bars = durationBars,
            rootFreq = 65.41, // C2
            hasKick = true,
            hasClap = true,
            hasHats = true,
            hasRollingBass = true,
            hasSynthChords = true,
            synthScale = intArrayOf(0, 3, 7, 10, 12),
            swing = 0.0f
        )
        return buildTrack(
            id = "demo_track_02",
            title = "Cyber Drop (Future Rave)",
            artist = "Bugobi Club Mix",
            bpm = bpm,
            key = "5A",
            pcm = samples
        )
    }

    /**
     * Track 3: Peak Time Techno (130 BPM, A / 11B)
     */
    fun createPeakTimeTechnoTrack(): PcmTrack {
        val bpm = 130.0
        val durationBars = 32
        val samples = renderElectronicBeat(
            bpm = bpm,
            bars = durationBars,
            rootFreq = 55.0, // A1
            hasKick = true,
            hasClap = false,
            hasHats = true,
            hasRollingBass = true,
            hasSynthChords = false,
            synthScale = intArrayOf(0, 1, 5, 7, 8),
            swing = 0.0f
        )
        return buildTrack(
            id = "demo_track_03",
            title = "Techno Velocity (Peak Time)",
            artist = "IMAN Advance 3.4",
            bpm = bpm,
            key = "11B",
            pcm = samples
        )
    }

    /**
     * Track 4: Deep House (122 BPM, Bb / 6B)
     */
    fun createDeepHouseTrack(): PcmTrack {
        val bpm = 122.0
        val durationBars = 32
        val samples = renderElectronicBeat(
            bpm = bpm,
            bars = durationBars,
            rootFreq = 58.27, // Bb1
            hasKick = true,
            hasClap = true,
            hasHats = true,
            hasRollingBass = true,
            hasSynthChords = true,
            synthScale = intArrayOf(0, 2, 4, 7, 9), // Major pentatonic
            swing = 0.08f
        )
        return buildTrack(
            id = "demo_track_04",
            title = "Sunset Horizon (Deep House)",
            artist = "DJ IMAN Studio",
            bpm = bpm,
            key = "6B",
            pcm = samples
        )
    }

    /**
     * Track 5: Trap / Hip Hop (100 BPM, Fm / 4A)
     */
    fun createHipHopTrapTrack(): PcmTrack {
        val bpm = 100.0
        val durationBars = 24
        val samples = renderElectronicBeat(
            bpm = bpm,
            bars = durationBars,
            rootFreq = 43.65, // F1
            hasKick = true,
            hasClap = true,
            hasHats = true,
            hasRollingBass = true,
            hasSynthChords = true,
            synthScale = intArrayOf(0, 3, 5, 8, 10),
            swing = 0.0f
        )
        return buildTrack(
            id = "demo_track_05",
            title = "Tokyo Drift (Trap / 808)",
            artist = "Bugobi Beats",
            bpm = bpm,
            key = "4A",
            pcm = samples
        )
    }

    /**
     * Track 6: Afrobeat (115 BPM, Em / 9A)
     */
    fun createAfrobeatTrack(): PcmTrack {
        val bpm = 115.0
        val durationBars = 28
        val samples = renderElectronicBeat(
            bpm = bpm,
            bars = durationBars,
            rootFreq = 41.20, // E1
            hasKick = true,
            hasClap = true,
            hasHats = true,
            hasRollingBass = true,
            hasSynthChords = true,
            synthScale = intArrayOf(0, 3, 5, 7, 10),
            swing = 0.10f
        )
        return buildTrack(
            id = "demo_track_06",
            title = "Afro Pulse (Amapiano)",
            artist = "DJ IMAN Sound",
            bpm = bpm,
            key = "9A",
            pcm = samples
        )
    }

    /**
     * Track 7: Drum & Bass (174 BPM, F# / 2B)
     */
    fun createDrumAndBassTrack(): PcmTrack {
        val bpm = 174.0
        val durationBars = 36
        val samples = renderElectronicBeat(
            bpm = bpm,
            bars = durationBars,
            rootFreq = 46.25, // F#1
            hasKick = true,
            hasClap = true,
            hasHats = true,
            hasRollingBass = true,
            hasSynthChords = true,
            synthScale = intArrayOf(0, 3, 7, 10, 12),
            swing = 0.0f
        )
        return buildTrack(
            id = "demo_track_07",
            title = "Velocity Pulse (DnB)",
            artist = "Advance Breakbeat",
            bpm = bpm,
            key = "2B",
            pcm = samples
        )
    }

    /**
     * Track 8: Synthwave (120 BPM, Dm / 7A)
     */
    fun createSynthwaveTrack(): PcmTrack {
        val bpm = 120.0
        val durationBars = 32
        val samples = renderElectronicBeat(
            bpm = bpm,
            bars = durationBars,
            rootFreq = 73.42, // D2
            hasKick = true,
            hasClap = true,
            hasHats = true,
            hasRollingBass = true,
            hasSynthChords = true,
            synthScale = intArrayOf(0, 2, 3, 5, 7, 8, 10),
            swing = 0.0f
        )
        return buildTrack(
            id = "demo_track_08",
            title = "Retro Wave 1984",
            artist = "DJ IMAN Synth",
            bpm = bpm,
            key = "7A",
            pcm = samples
        )
    }

    /**
     * Generates a 16-bit stereo PCM audio buffer for an electronic track.
     */
    private fun renderElectronicBeat(
        bpm: Double,
        bars: Int,
        rootFreq: Double,
        hasKick: Boolean,
        hasClap: Boolean,
        hasHats: Boolean,
        hasRollingBass: Boolean,
        hasSynthChords: Boolean,
        synthScale: IntArray,
        swing: Float
    ): ShortArray {
        val beatsPerBar = 4
        val totalBeats = bars * beatsPerBar
        val beatSec = 60.0 / bpm
        val totalDurationSec = totalBeats * beatSec
        val totalFrames = (totalDurationSec * SAMPLE_RATE).toInt()
        val interleavedSamples = ShortArray(totalFrames * 2)

        val framesPerBeat = (beatSec * SAMPLE_RATE).toInt()
        val framesPer16th = framesPerBeat / 4

        // Envelope state and random generator
        val random = Random(42)

        for (frame in 0 until totalFrames) {
            val timeSec = frame.toDouble() / SAMPLE_RATE
            val beatPosDouble = timeSec / beatSec
            val currentBeat = beatPosDouble.toInt()
            val beatFraction = (beatPosDouble - currentBeat).toFloat()
            val sixteenthStep = ((beatFraction * 4).toInt()).coerceIn(0, 3)

            var outL = 0f
            var outR = 0f

            // 1. Kick Drum (Quarter note downbeats)
            if (hasKick) {
                val kickTime = beatFraction * beatSec
                if (kickTime < 0.35) {
                    val pitchEnv = 150.0 * exp(-kickTime * 28.0) + 48.0
                    val ampEnv = exp(-kickTime * 9.0).toFloat()
                    val kickSample = (sin(2.0 * PI * pitchEnv * kickTime) * ampEnv).toFloat()
                    outL += kickSample * 0.75f
                    outR += kickSample * 0.75f
                }
            }

            // 2. Clap / Snare (Beats 2 and 4)
            if (hasClap && (currentBeat % 2 == 1)) {
                val clapTime = beatFraction * beatSec
                if (clapTime < 0.22) {
                    val noise = (random.nextFloat() * 2f - 1f)
                    val tone = sin(2.0 * PI * 220.0 * clapTime).toFloat()
                    val env = exp(-clapTime * 20.0).toFloat()
                    val clapSample = (noise * 0.7f + tone * 0.3f) * env
                    outL += clapSample * 0.45f
                    outR += clapSample * 0.45f
                }
            }

            // 3. Hi-Hats (16th notes with open hat on off-beat 8ths)
            if (hasHats) {
                val stepTime = (beatFraction * 4.0 - sixteenthStep) * (beatSec / 4.0)
                if (stepTime >= 0.0 && stepTime < 0.09) {
                    val isOpen = (sixteenthStep == 2)
                    val decayRate = if (isOpen) 22.0 else 75.0
                    val hatNoise = (random.nextFloat() * 2f - 1f)
                    val hatEnv = exp(-stepTime * decayRate).toFloat()
                    val hatPan = if (sixteenthStep % 2 == 0) -0.2f else 0.2f
                    val hatVol = if (isOpen) 0.35f else 0.18f

                    outL += hatNoise * hatEnv * hatVol * (1f - hatPan)
                    outR += hatNoise * hatEnv * hatVol * (1f + hatPan)
                }
            }

            // 4. Bassline (Syncopated 16th bass groove)
            if (hasRollingBass) {
                val barProgress = (currentBeat % 4) + beatFraction
                val stepIdx = (barProgress * 4).toInt() % 16
                val stepTime = (barProgress * 4 - (barProgress * 4).toInt()) * (beatSec / 4.0)

                // Bass triggers on steps 2, 5, 8, 10, 14
                val isBassStep = (stepIdx == 2 || stepIdx == 5 || stepIdx == 8 || stepIdx == 10 || stepIdx == 14)
                if (isBassStep && stepTime < 0.20) {
                    val noteSemitone = synthScale[(stepIdx / 3) % synthScale.size]
                    val freq = rootFreq * 2.0.pow(noteSemitone / 12.0)
                    val bassEnv = exp(-stepTime * 12.0).toFloat()
                    // Warm saturation on bass
                    val rawBass = sin(2.0 * PI * freq * stepTime) + 0.4 * sin(4.0 * PI * freq * stepTime)
                    val satBass = (rawBass / (1.0 + 0.3 * rawBass * rawBass)).toFloat()

                    outL += satBass * bassEnv * 0.55f
                    outR += satBass * bassEnv * 0.55f
                }
            }

            // 5. Synth Chords & Melodic Stabs
            if (hasSynthChords) {
                val chordBar = (currentBeat / 4) % 4
                val chordTime = (beatFraction + (currentBeat % 2)) * beatSec
                if (chordTime < 0.45) {
                    val chordRootOffset = when (chordBar) {
                        0 -> 0
                        1 -> 3
                        2 -> 5
                        else -> 7
                    }
                    val freq1 = rootFreq * 2.0 * 2.0.pow(chordRootOffset / 12.0)
                    val freq2 = freq1 * 2.0.pow(7.0 / 12.0) // Fifth
                    val freq3 = freq1 * 2.0.pow(10.0 / 12.0) // Minor 7th

                    val chordEnv = exp(-chordTime * 7.0).toFloat()
                    val s1 = sin(2.0 * PI * freq1 * chordTime).toFloat()
                    val s2 = sin(2.0 * PI * freq2 * chordTime).toFloat()
                    val s3 = sin(2.0 * PI * freq3 * chordTime).toFloat()
                    val chordSample = (s1 + s2 * 0.8f + s3 * 0.6f) * 0.22f * chordEnv

                    outL += chordSample * 0.8f
                    outR += chordSample * 1.2f
                }
            }

            // Soft master clipper and conversion to 16-bit integer
            val clipL = (outL.coerceIn(-1.5f, 1.5f) * 0.8f)
            val clipR = (outR.coerceIn(-1.5f, 1.5f) * 0.8f)

            interleavedSamples[frame * 2] = (clipL * 32000f).toInt().coerceIn(-32768, 32767).toShort()
            interleavedSamples[frame * 2 + 1] = (clipR * 32000f).toInt().coerceIn(-32768, 32767).toShort()
        }

        return interleavedSamples
    }

    /**
     * Builds a PcmTrack instance with precomputed waveform overview and beat offsets.
     */
    private fun buildTrack(
        id: String,
        title: String,
        artist: String,
        bpm: Double,
        key: String,
        pcm: ShortArray
    ): PcmTrack {
        val channels = 2
        val totalFrames = pcm.size / channels
        val overviewSize = 512
        val step = max(1, totalFrames / overviewSize)
        val overview = FloatArray(overviewSize)

        for (i in 0 until overviewSize) {
            val start = i * step * channels
            val end = min(pcm.size - 1, (i + 1) * step * channels)
            var maxPeak = 0
            var j = start
            while (j < end) {
                val s = kotlin.math.abs(pcm[j].toInt())
                if (s > maxPeak) maxPeak = s
                j += 4 // stride
            }
            overview[i] = (maxPeak / 32768f).coerceIn(0.05f, 1f)
        }

        // Compute beat positions
        val beatIntervalMs = (60.0 / bpm) * 1000.0
        val durationMs = (totalFrames.toDouble() / SAMPLE_RATE.toDouble()) * 1000.0
        val totalBeats = (durationMs / beatIntervalMs).toInt()
        val beatOffsets = DoubleArray(totalBeats) { it * beatIntervalMs }

        return PcmTrack(
            id = id,
            title = title,
            artist = artist,
            sampleRate = SAMPLE_RATE,
            channels = channels,
            pcmSamples = pcm,
            bpm = bpm,
            initialKey = key,
            waveformOverview = overview,
            beatOffsetsMs = beatOffsets
        )
    }

    /**
     * Synthesizes 16 responsive drum and sound effect sampler pads.
     */
    fun synthesizeSamplerPad(padIndex: Int): ShortArray {
        val durationSec = when (padIndex) {
            8, 9, 13, 14, 15 -> 1.5 // longer for siren, airhorn, vocal drops
            else -> 0.4
        }
        val frames = (durationSec * SAMPLE_RATE).toInt()
        val buffer = ShortArray(frames * 2)
        val random = Random(padIndex * 1337)

        for (f in 0 until frames) {
            val t = f.toDouble() / SAMPLE_RATE
            var sample = 0f

            when (padIndex) {
                0 -> { // 808 Kick
                    val p = 140.0 * exp(-t * 30.0) + 42.0
                    val env = exp(-t * 6.0).toFloat()
                    sample = sin(2.0 * PI * p * t).toFloat() * env
                }
                1 -> { // 909 Snare
                    val tone = sin(2.0 * PI * 185.0 * t).toFloat() * exp(-t * 22.0).toFloat()
                    val noise = (random.nextFloat() * 2f - 1f) * exp(-t * 18.0).toFloat()
                    sample = tone * 0.4f + noise * 0.6f
                }
                2 -> { // Trap Clap
                    val env = exp(-t * 24.0).toFloat()
                    val noise = (random.nextFloat() * 2f - 1f)
                    val burst = if (t < 0.03 && (f % 400 < 200)) 1.2f else 1.0f
                    sample = noise * env * burst
                }
                3 -> { // Closed Hat
                    val env = exp(-t * 90.0).toFloat()
                    val noise = (random.nextFloat() * 2f - 1f)
                    sample = noise * env * 0.7f
                }
                4 -> { // Open Hat
                    val env = exp(-t * 18.0).toFloat()
                    val noise = (random.nextFloat() * 2f - 1f)
                    sample = noise * env * 0.8f
                }
                5 -> { // Crash Cymbal
                    val env = exp(-t * 4.5).toFloat()
                    val noise = (random.nextFloat() * 2f - 1f)
                    sample = noise * env * 0.75f
                }
                6 -> { // Low Tom
                    val p = 120.0 * exp(-t * 18.0) + 70.0
                    val env = exp(-t * 9.0).toFloat()
                    sample = sin(2.0 * PI * p * t).toFloat() * env
                }
                7 -> { // Hi Tom
                    val p = 220.0 * exp(-t * 20.0) + 130.0
                    val env = exp(-t * 11.0).toFloat()
                    sample = sin(2.0 * PI * p * t).toFloat() * env
                }
                8 -> { // Airhorn
                    val f1 = 440.0
                    val f2 = 554.37 // C#
                    val f3 = 659.25 // E
                    val env = if (t < 0.9) 1.0f else exp(-(t - 0.9) * 15.0).toFloat()
                    val beep = if ((t % 0.28) < 0.20) 1f else 0f
                    val s = (sin(2.0 * PI * f1 * t) + sin(2.0 * PI * f2 * t) + sin(2.0 * PI * f3 * t)).toFloat()
                    sample = (s * 0.35f * env * beep).coerceIn(-1f, 1f)
                }
                9 -> { // Police Siren
                    val lfo = sin(2.0 * PI * 2.5 * t)
                    val freq = 750.0 + lfo * 350.0
                    val env = if (t < 1.2) 1.0f else exp(-(t - 1.2) * 8.0).toFloat()
                    sample = sin(2.0 * PI * freq * t).toFloat() * 0.6f * env
                }
                10 -> { // Laser Shot
                    val p = 1600.0 * exp(-t * 35.0) + 100.0
                    val env = exp(-t * 12.0).toFloat()
                    sample = sin(2.0 * PI * p * t).toFloat() * env
                }
                11 -> { // Vinyl Chirp Scratch
                    val scratchFreq = 300.0 + sin(2.0 * PI * 18.0 * t) * 400.0
                    val env = exp(-t * 14.0).toFloat()
                    sample = sin(2.0 * PI * scratchFreq * t).toFloat() * env
                }
                12 -> { // Vinyl Backspin
                    val spinFreq = (800.0 - t * 500.0).coerceAtLeast(80.0)
                    val env = exp(-t * 5.0).toFloat()
                    val crackle = if (random.nextFloat() < 0.08f) 0.5f else 0f
                    sample = (sin(2.0 * PI * spinFreq * t).toFloat() * 0.7f + crackle) * env
                }
                13 -> { // Vocal: "Drop The Beat!"
                    val formant1 = sin(2.0 * PI * 420.0 * t) * sin(2.0 * PI * 130.0 * t)
                    val formant2 = sin(2.0 * PI * 850.0 * t) * 0.5
                    val env = (if (t < 0.15 || (t in 0.25..0.45) || (t in 0.55..0.95)) 1f else 0.1f) * exp(-t * 1.5).toFloat()
                    sample = ((formant1 + formant2) * env * 0.7).toFloat()
                }
                14 -> { // Vocal: "Yeah! Let's Go!"
                    val formant = sin(2.0 * PI * 580.0 * t) * sin(2.0 * PI * 165.0 * t)
                    val env = exp(-t * 2.8).toFloat()
                    sample = (formant * env * 0.8).toFloat()
                }
                else -> { // 808 Sub Drop
                    val freq = (95.0 - t * 45.0).coerceAtLeast(32.0)
                    val env = exp(-t * 2.2).toFloat()
                    sample = sin(2.0 * PI * freq * t).toFloat() * env
                }
            }

            val valShort = (sample.coerceIn(-1f, 1f) * 31000f).toInt().toShort()
            buffer[f * 2] = valShort
            buffer[f * 2 + 1] = valShort
        }
        return buffer
    }
}
