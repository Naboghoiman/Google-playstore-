package com.example

import com.example.analysis.BeatAnalyzer
import com.example.analysis.BeatGridMath
import com.example.audio.BiquadFilter
import com.example.audio.DjTrackSynthesizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DjEngineUnitTest {

    @Test
    fun testBeatGridMath() {
        val bpm = 120.0
        val beatMs = BeatGridMath.beatDurationMs(bpm)
        assertEquals(500.0, beatMs, 0.001)

        val snapped = BeatGridMath.snapToGrid(currentMs = 520.0, gridOffsetMs = 0.0, bpm = 120.0, quantizeDivision = 1.0)
        assertEquals(500.0, snapped, 0.001)

        assertTrue(BeatGridMath.isHarmonicallyCompatible("8A", "8A"))
        assertTrue(BeatGridMath.isHarmonicallyCompatible("8A", "8B"))
        assertTrue(BeatGridMath.isHarmonicallyCompatible("8A", "7A"))
        assertTrue(BeatGridMath.isHarmonicallyCompatible("8A", "9A"))
    }

    @Test
    fun testTrackSynthesizer() {
        val tracks = DjTrackSynthesizer.createAllDemoTracks()
        assertEquals(8, tracks.size)

        val track1 = tracks[0]
        assertNotNull(track1.pcmSamples)
        assertTrue(track1.pcmSamples.isNotEmpty())
        assertEquals(126.0, track1.bpm, 0.1)
        assertTrue(track1.durationMs > 10000.0)
    }

    @Test
    fun testBiquadFilter() {
        val filter = BiquadFilter()
        filter.setLowPass(44100.0, 1000.0, 1.0)

        val out = FloatArray(2)
        filter.processStereo(0.5f, 0.5f, out)
        assertTrue(out[0] in -1.5f..1.5f)
        assertTrue(out[1] in -1.5f..1.5f)
    }

    @Test
    fun testBeatAnalyzer() {
        val pcm = ShortArray(44100 * 4) // 2 seconds stereo
        val result = BeatAnalyzer.analyze(pcm, channels = 2, sampleRate = 44100)
        assertNotNull(result)
        assertTrue(result.bpm >= 70.0 && result.bpm <= 180.0)
        assertNotNull(result.key)
    }
}
