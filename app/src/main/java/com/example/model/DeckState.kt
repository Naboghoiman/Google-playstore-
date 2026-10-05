package com.example.model

enum class DeckId {
    DECK_A,
    DECK_B
}

enum class PadMode {
    HOT_CUE,
    AUTO_LOOP,
    MANUAL_LOOP,
    BEAT_JUMP,
    AUTO_SCRATCH,
    KEY_SHIFT
}

/**
 * Full state snapshot for a single DJ deck.
 */
data class DeckState(
    val deckId: DeckId = DeckId.DECK_A,
    val track: PcmTrack? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Double = 0.0,
    val playbackRate: Double = 1.0,
    val pitchSliderValue: Float = 0f, // -1.0f (max speed down) to +1.0f (max speed up)
    val pitchRangePercent: Float = 16f, // 8%, 16%, 50%, 100%
    val isKeyLocked: Boolean = true,
    val keyShiftSemitones: Int = 0,
    val cuePositionMs: Double = 0.0,
    val isCueHeld: Boolean = false,
    val hotCuesMs: List<Double> = List(8) { -1.0 },
    val loopStartMs: Double = -1.0,
    val loopEndMs: Double = -1.0,
    val isLoopActive: Boolean = false,
    val autoLoopBeats: Double = 4.0,
    val bpm: Double = 128.0,
    val beatGridOffsetMs: Double = 0.0,
    val isSyncActive: Boolean = false,
    val isMaster: Boolean = false,
    val isQuantized: Boolean = true,
    val eqLowGainDb: Float = 0f, // -26dB to +6dB
    val eqMidGainDb: Float = 0f,
    val eqHighGainDb: Float = 0f,
    val eqLowKill: Boolean = false,
    val eqMidKill: Boolean = false,
    val eqHighKill: Boolean = false,
    val filterKnob: Float = 0f, // -1f (full LPF) .. 0f (neutral) .. +1f (full HPF)
    val gainTrimDb: Float = 0f,
    val volumeFader: Float = 1.0f,
    val pflHeadphone: Boolean = false,
    val vuLevelLeft: Float = 0f,
    val vuLevelRight: Float = 0f,
    val platterAngleDegrees: Float = 0f,
    val isPlatterTouched: Boolean = false,
    val activePadMode: PadMode = PadMode.HOT_CUE,
    val activeScratchPattern: String = "Baby Scratch"
) {
    val effectiveBpm: Double
        get() = bpm * (1.0 + (pitchSliderValue * (pitchRangePercent / 100.0)))

    val durationMs: Double
        get() = track?.durationMs ?: 0.0

    val progress: Float
        get() {
            val total = durationMs
            return if (total > 0) (currentPositionMs / total).coerceIn(0.0, 1.0).toFloat() else 0f
        }
}
