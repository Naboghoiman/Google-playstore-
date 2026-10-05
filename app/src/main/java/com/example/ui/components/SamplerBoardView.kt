package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

/**
 * 16-pad Studio Sampler / Drum & FX trigger board.
 */
@Composable
fun SamplerBoardView(
    onTriggerSample: (padIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var lastTriggeredPad by remember { mutableIntStateOf(-1) }

    val padData = listOf(
        Pair("808 KICK", Color(0xFFFF1744)),
        Pair("909 SNARE", Color(0xFFFF9100)),
        Pair("TRAP CLAP", Color(0xFFFFD600)),
        Pair("CH HI-HAT", Color(0xFF00E676)),
        Pair("OP HI-HAT", Color(0xFF00E5FF)),
        Pair("CRASH", Color(0xFF00B0FF)),
        Pair("LOW TOM", Color(0xFFD500F9)),
        Pair("HI TOM", Color(0xFFFF4081)),
        Pair("AIRHORN", Color(0xFFFF3D00)),
        Pair("SIREN FX", Color(0xFFFFEA00)),
        Pair("LASER DROP", Color(0xFF00E5FF)),
        Pair("VINYL CHIRP", Color(0xFF76FF03)),
        Pair("BACKSPIN", Color(0xFFE040FB)),
        Pair("DROP IT!", Color(0xFFFF6E40)),
        Pair("HEY! HO!", Color(0xFF1DE9B6)),
        Pair("SUB DROP", Color(0xFF651FFF))
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0E121A))
            .border(1.dp, Color(0xFF1E2536), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "16-PAD PERFORMANCE SAMPLER",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            Text(
                text = "BANK A [16 VOICES]",
                fontSize = 8.sp,
                color = TextMuted
            )
        }

        // 4 Rows x 4 Columns
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 6.dp)
        ) {
            for (row in 0..3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (col in 0..3) {
                        val index = row * 4 + col
                        val (name, color) = padData[index]
                        val isRecentlyHit = lastTriggeredPad == index

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isRecentlyHit) color else color.copy(alpha = 0.22f))
                                .border(
                                    1.2.dp,
                                    if (isRecentlyHit) Color.White else color.copy(alpha = 0.6f),
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    lastTriggeredPad = index
                                    onTriggerSample(index)
                                }
                                .padding(2.dp)
                        ) {
                            Text(
                                text = name,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRecentlyHit) Color.Black else Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
