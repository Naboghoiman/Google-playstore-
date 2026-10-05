package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DeckId
import com.example.model.DeckState
import com.example.model.PadMode
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * 8 Back-lit RGB Performance Pads with mode selector tabs.
 */
@Composable
fun PerformancePadsView(
    deckState: DeckState,
    onPadModeSelected: (PadMode) -> Unit,
    onHotCueTriggered: (Int, Boolean) -> Unit, // index, isClear
    onAutoLoopTriggered: (Double) -> Unit,
    onBeatJumpTriggered: (Double, Boolean) -> Unit,
    onAutoScratchTriggered: (String) -> Unit,
    onKeyShiftTriggered: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isClearModeActive by remember { mutableStateOf(false) }

    val padColors = listOf(
        Color(0xFF00E676), // 1 - Green
        Color(0xFFFF1744), // 2 - Red
        Color(0xFF00E5FF), // 3 - Cyan
        Color(0xFFFFD600), // 4 - Yellow
        Color(0xFFD500F9), // 5 - Purple
        Color(0xFFFF9100), // 6 - Orange
        Color(0xFFFF4081), // 7 - Pink
        Color(0xFF00B0FF)  // 8 - Light Blue
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF11151E))
            .border(1.dp, Color(0xFF202636), RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        // Mode Selector Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val modes = listOf(
                Pair(PadMode.HOT_CUE, "HOT CUE"),
                Pair(PadMode.AUTO_LOOP, "LOOP"),
                Pair(PadMode.BEAT_JUMP, "JUMP"),
                Pair(PadMode.AUTO_SCRATCH, "SCRATCH"),
                Pair(PadMode.KEY_SHIFT, "KEY")
            )

            modes.forEach { (mode, title) ->
                val isSelected = deckState.activePadMode == mode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) Color(0xFF2A3449) else Color(0xFF161B26))
                        .clickable { onPadModeSelected(mode) }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 8.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else TextMuted
                    )
                }
            }

            if (deckState.activePadMode == PadMode.HOT_CUE) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isClearModeActive) Color(0xFFFF1744) else Color(0xFF161B26))
                        .clickable { isClearModeActive = !isClearModeActive }
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isClearModeActive) "DEL ON" else "DEL",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isClearModeActive) Color.White else Color(0xFFFF5252)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 8 Pads in 2 Rows x 4 Columns
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in 0..1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (col in 0..3) {
                        val padIndex = row * 4 + col
                        val padColor = padColors[padIndex]

                        when (deckState.activePadMode) {
                            PadMode.HOT_CUE -> {
                                val isSet = deckState.hotCuesMs[padIndex] >= 0
                                PadButton(
                                    label = "CUE ${padIndex + 1}",
                                    subLabel = if (isSet) String.format("%.1fs", deckState.hotCuesMs[padIndex] / 1000.0) else "EMPTY",
                                    color = if (isSet) padColor else padColor.copy(alpha = 0.25f),
                                    isActive = isSet,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onHotCueTriggered(padIndex, isClearModeActive) }
                                )
                            }
                            PadMode.AUTO_LOOP -> {
                                val loopDivisions = listOf(0.125, 0.25, 0.5, 1.0, 2.0, 4.0, 8.0, 16.0)
                                val loopLabels = listOf("1/8", "1/4", "1/2", "1", "2", "4", "8", "16")
                                val beats = loopDivisions[padIndex]
                                val isLoopActive = deckState.isLoopActive && deckState.autoLoopBeats == beats
                                PadButton(
                                    label = loopLabels[padIndex],
                                    subLabel = "BEATS",
                                    color = Color(0xFF00B0FF),
                                    isActive = isLoopActive,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onAutoLoopTriggered(beats) }
                                )
                            }
                            PadMode.BEAT_JUMP -> {
                                val jumpJumps = listOf(-4.0, -2.0, -1.0, -0.5, 0.5, 1.0, 2.0, 4.0)
                                val jumpLabels = listOf("<< 4", "<< 2", "<< 1", "<< 1/2", "1/2 >>", "1 >>", "2 >>", "4 >>")
                                val beats = jumpJumps[padIndex]
                                PadButton(
                                    label = jumpLabels[padIndex],
                                    subLabel = "JUMP",
                                    color = Color(0xFFFF9100),
                                    isActive = false,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onBeatJumpTriggered(kotlin.math.abs(beats), beats > 0) }
                                )
                            }
                            PadMode.AUTO_SCRATCH -> {
                                val patterns = listOf("Baby", "Chirp", "X-Former", "Flare", "Orbit", "Scribble", "Crab", "Spinback")
                                val patternName = patterns[padIndex]
                                PadButton(
                                    label = patternName,
                                    subLabel = "SCRATCH",
                                    color = Color(0xFFD500F9),
                                    isActive = false,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onAutoScratchTriggered(patternName) }
                                )
                            }
                            PadMode.KEY_SHIFT -> {
                                val shifts = listOf(-3, -2, -1, 0, 1, 2, 3, 0)
                                val shiftVal = shifts[padIndex]
                                val label = if (padIndex == 7) "RESET" else (if (shiftVal > 0) "+$shiftVal" else "$shiftVal")
                                PadButton(
                                    label = label,
                                    subLabel = "KEY",
                                    color = Color(0xFFFFD600),
                                    isActive = (deckState.keyShiftSemitones == shiftVal && padIndex != 7),
                                    modifier = Modifier.weight(1f),
                                    onClick = { onKeyShiftTriggered(shiftVal) }
                                )
                            }
                            else -> {
                                PadButton(
                                    label = "${padIndex + 1}",
                                    subLabel = "",
                                    color = padColor,
                                    isActive = false,
                                    modifier = Modifier.weight(1f),
                                    onClick = {}
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadButton(
    label: String,
    subLabel: String,
    color: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) color.copy(alpha = 0.85f) else color.copy(alpha = 0.20f))
            .border(
                1.5.dp,
                if (isActive) Color.White else color.copy(alpha = 0.65f),
                RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color.Black else Color.White,
                textAlign = TextAlign.Center
            )
            if (subLabel.isNotEmpty()) {
                Text(
                    text = subLabel,
                    fontSize = 7.sp,
                    color = if (isActive) Color(0xFF212121) else TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
