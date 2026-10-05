package com.example.model

enum class SampleCategory {
    DRUMS,
    FX,
    VOCAL,
    SCRATCH,
    SYNTH
}

data class SamplePad(
    val id: Int,
    val name: String,
    val category: SampleCategory,
    val keyNote: String,
    val colorHex: Long,
    val isPlaying: Boolean = false,
    val isLoop: Boolean = false
)

data class TrackInfo(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Int,
    val bpm: Double,
    val musicalKey: String,
    val genre: String,
    val uriString: String? = null,
    val isDemoTrack: Boolean = true
)
