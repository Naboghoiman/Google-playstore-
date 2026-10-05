package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.model.DeckState
import com.example.ui.theme.CueColor
import com.example.ui.theme.LoopColor
import com.example.ui.theme.WaveformBass
import com.example.ui.theme.WaveformHigh
import com.example.ui.theme.WaveformMid
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Dual-tier professional DJ Waveform Display:
 * - Top: Full track minimap with playhead, loop region, and hot cues (tap to seek)
 * - Bottom: High-speed real-time scrolling multi-frequency waveform with beatgrid lines and cue flags
 */
@Composable
fun WaveformView(
    deckState: DeckState,
    primaryColor: Color,
    onSeekToMs: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val track = deckState.track
    val durationMs = deckState.durationMs
    val currentMs = deckState.currentPositionMs

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F131C))
    ) {
        // ---- 1. OVERVIEW MINIMAP (Height 28dp) ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(Color(0xFF0A0C12))
                .pointerInput(track?.id) {
                    detectTapGestures { offset ->
                        if (durationMs > 0) {
                            val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeekToMs(ratio * durationMs)
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val midY = h / 2f

                // Draw background grid lines
                drawLine(
                    color = Color(0xFF1E2536),
                    start = Offset(0f, midY),
                    end = Offset(w, midY),
                    strokeWidth = 1f
                )

                // Draw precomputed overview waveform peaks
                val overview = track?.waveformOverview
                if (overview != null && overview.isNotEmpty()) {
                    val step = w / overview.size
                    for (i in overview.indices) {
                        val barH = (overview[i] * (h * 0.45f)).coerceAtLeast(1f)
                        val x = i * step
                        val color = when {
                            i % 4 == 0 -> WaveformBass
                            i % 4 == 1 -> WaveformMid
                            else -> WaveformHigh
                        }
                        drawLine(
                            color = color.copy(alpha = 0.75f),
                            start = Offset(x, midY - barH),
                            end = Offset(x, midY + barH),
                            strokeWidth = max(1f, step * 0.8f)
                        )
                    }
                }

                // Draw Active Loop Region on Minimap
                if (deckState.isLoopActive && deckState.loopStartMs >= 0 && deckState.loopEndMs > deckState.loopStartMs && durationMs > 0) {
                    val startX = (deckState.loopStartMs / durationMs * w).toFloat()
                    val endX = (deckState.loopEndMs / durationMs * w).toFloat()
                    drawRect(
                        color = LoopColor.copy(alpha = 0.35f),
                        topLeft = Offset(startX, 0f),
                        size = Size(max(4f, endX - startX), h)
                    )
                }

                // Draw Hot Cue dots
                if (durationMs > 0) {
                    deckState.hotCuesMs.forEachIndexed { index, cueMs ->
                        if (cueMs >= 0) {
                            val cueX = (cueMs / durationMs * w).toFloat()
                            drawCircle(
                                color = getCueColor(index),
                                radius = 3.5f,
                                center = Offset(cueX, h * 0.85f)
                            )
                        }
                    }
                }

                // Playhead indicator
                if (durationMs > 0) {
                    val playheadX = (currentMs / durationMs * w).toFloat().coerceIn(0f, w)
                    drawLine(
                        color = Color.White,
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, h),
                        strokeWidth = 2.5f
                    )
                }
            }
        }

        // ---- 2. DYNAMIC REAL-TIME SCROLLING BEATGRID WAVEFORM (Height 64dp) ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(Color(0xFF131824))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val midY = h / 2f
                val centerX = w / 2f

                // Time window visible on screen (e.g. 4 seconds window = +/- 2 seconds from center)
                val visibleWindowSec = 3.5
                val msPerPixel = (visibleWindowSec * 1000.0) / w
                val windowStartMs = currentMs - (visibleWindowSec * 500.0)
                val windowEndMs = currentMs + (visibleWindowSec * 500.0)

                // Draw Active Loop Region in scrolling view
                if (deckState.isLoopActive && deckState.loopStartMs >= 0 && deckState.loopEndMs > deckState.loopStartMs) {
                    val loopX1 = ((deckState.loopStartMs - windowStartMs) / msPerPixel).toFloat()
                    val loopX2 = ((deckState.loopEndMs - windowStartMs) / msPerPixel).toFloat()
                    if (loopX2 >= 0 && loopX1 <= w) {
                        val drawX1 = max(0f, loopX1)
                        val drawX2 = min(w, loopX2)
                        drawRect(
                            color = LoopColor.copy(alpha = 0.25f),
                            topLeft = Offset(drawX1, 0f),
                            size = Size(drawX2 - drawX1, h)
                        )
                    }
                }

                // Draw Beatgrid Lines
                val bpm = deckState.effectiveBpm
                if (bpm > 40.0) {
                    val beatIntervalMs = (60.0 / bpm) * 1000.0
                    val firstBeatIdx = ((windowStartMs - deckState.beatGridOffsetMs) / beatIntervalMs).toInt() - 1
                    val lastBeatIdx = ((windowEndMs - deckState.beatGridOffsetMs) / beatIntervalMs).toInt() + 1

                    for (beatIdx in firstBeatIdx..lastBeatIdx) {
                        val beatTimeMs = deckState.beatGridOffsetMs + (beatIdx * beatIntervalMs)
                        if (beatTimeMs in windowStartMs..windowEndMs) {
                            val beatX = ((beatTimeMs - windowStartMs) / msPerPixel).toFloat()
                            val isDownbeat = (beatIdx % 4 == 0) // First beat of 4/4 bar
                            drawLine(
                                color = if (isDownbeat) Color(0xFFFFD600).copy(alpha = 0.85f) else Color(0xFF4A5568).copy(alpha = 0.5f),
                                start = Offset(beatX, if (isDownbeat) 0f else h * 0.2f),
                                end = Offset(beatX, if (isDownbeat) h else h * 0.8f),
                                strokeWidth = if (isDownbeat) 2.0f else 1.0f
                            )
                        }
                    }
                }

                // Draw Dynamic Waveform Columns
                val pcm = track?.pcmSamples
                val sampleRate = track?.sampleRate ?: 44100
                if (pcm != null && pcm.isNotEmpty()) {
                    val numBars = 140
                    val barWidth = w / numBars

                    for (b in 0 until numBars) {
                        val barTimeMs = windowStartMs + (b * barWidth * msPerPixel)
                        val frame = (barTimeMs / 1000.0 * sampleRate).toInt()
                        val sampleIdx = frame * 2

                        if (sampleIdx in 0 until pcm.size - 4) {
                            val s1 = abs(pcm[sampleIdx].toInt())
                            val s2 = abs(pcm[sampleIdx + 1].toInt())
                            val amp = (max(s1, s2) / 32768f).coerceIn(0.06f, 1f)

                            val barH = amp * (h * 0.44f)
                            val x = b * barWidth

                            // Tri-band frequency spectrum color simulation based on sample density & beat phase
                            val color = when {
                                amp > 0.65f -> WaveformBass
                                amp > 0.35f -> WaveformMid
                                else -> WaveformHigh
                            }

                            drawLine(
                                color = color,
                                start = Offset(x, midY - barH),
                                end = Offset(x, midY + barH),
                                strokeWidth = max(1.5f, barWidth * 0.75f),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                // Draw Hot Cue Markers in Scrolling View
                deckState.hotCuesMs.forEachIndexed { index, cueMs ->
                    if (cueMs in windowStartMs..windowEndMs) {
                        val cueX = ((cueMs - windowStartMs) / msPerPixel).toFloat()
                        val cueColor = getCueColor(index)
                        // Flag line
                        drawLine(
                            color = cueColor,
                            start = Offset(cueX, 0f),
                            end = Offset(cueX, h),
                            strokeWidth = 2.0f
                        )
                        // Flag badge at top
                        drawRect(
                            color = cueColor,
                            topLeft = Offset(cueX, 2f),
                            size = Size(14f, 12f)
                        )
                    }
                }

                // Center Playhead Marker (Red / White with needle)
                drawLine(
                    color = Color(0xFFFF1744),
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, h),
                    strokeWidth = 3.0f
                )
                // Playhead top triangle notch
                drawCircle(
                    color = Color.White,
                    radius = 3.5f,
                    center = Offset(centerX, 4f)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.5f,
                    center = Offset(centerX, h - 4f)
                )
            }
        }
    }
}

private fun getCueColor(index: Int): Color {
    return when (index % 8) {
        0 -> Color(0xFF00E676) // Green
        1 -> Color(0xFFFF1744) // Red
        2 -> Color(0xFF00E5FF) // Cyan
        3 -> Color(0xFFFFD600) // Yellow
        4 -> Color(0xFFD500F9) // Purple
        5 -> Color(0xFFFF9100) // Orange
        6 -> Color(0xFFFF4081) // Pink
        else -> Color(0xFFFFFFFF) // White
    }
}
