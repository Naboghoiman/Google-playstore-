package com.example.model

enum class CrossfaderCurve {
    LINEAR,
    SMOOTH,
    SCRATCH_CUT,
    DIP
}

enum class MasterFxType {
    NONE,
    ECHO,
    REVERB,
    FLANGER,
    BITCRUSHER,
    BRAKE,
    SPINBACK,
    ROLL,
    FILTER_SWEEP
}

enum class AutomixTransition {
    CROSSFADE,
    FILTER_FADE,
    ECHO_DROP,
    BRAKE_CUT
}

data class MixerState(
    val crossfaderPosition: Float = 0f, // -1f = Deck A, 0f = Center, +1f = Deck B
    val crossfaderCurve: CrossfaderCurve = CrossfaderCurve.SMOOTH,
    val isHamsterReversed: Boolean = false,
    val masterVolume: Float = 0.9f,
    val masterVuLeft: Float = 0f,
    val masterVuRight: Float = 0f,
    val headphoneVolume: Float = 0.8f,
    val headphoneMix: Float = 0.5f, // 0f = Cue (PFL), 1f = Master
    val activeFxType: MasterFxType = MasterFxType.ECHO,
    val isFxActive: Boolean = false,
    val fxDryWet: Float = 0.5f,
    val fxParamX: Float = 0.5f,
    val fxParamY: Float = 0.5f,
    val fxBeatDivision: Double = 0.5, // 1/2 beat
    val isRecording: Boolean = false,
    val recordingDurationSeconds: Long = 0L,
    val isAutomixActive: Boolean = false,
    val automixTransition: AutomixTransition = AutomixTransition.FILTER_FADE,
    val automixTransitionBeats: Int = 16,
    val automixProgress: Float = 0f
)
