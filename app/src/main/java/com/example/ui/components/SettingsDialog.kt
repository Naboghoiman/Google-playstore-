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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.CrossfaderCurve
import com.example.model.DeckState
import com.example.model.MixerState
import com.example.ui.theme.DeckAPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsDialog(
    deckAState: DeckState,
    mixerState: MixerState,
    onPitchRangeChanged: (Float) -> Unit,
    onCrossfaderCurveChanged: (CrossfaderCurve) -> Unit,
    onHamsterToggled: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10141F))
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = DeckAPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DJ CONSOLE PREFERENCES",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 1. Pitch Fader Range
                Text(text = "TEMPO / PITCH FADER RANGE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val ranges = listOf(8f, 16f, 50f, 100f)
                    ranges.forEach { range ->
                        val isSelected = deckAState.pitchRangePercent == range
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) DeckAPrimary.copy(alpha = 0.85f) else Color(0xFF161C28))
                                .border(1.dp, if (isSelected) Color.White else Color(0xFF232D42), RoundedCornerShape(6.dp))
                                .clickable { onPitchRangeChanged(range) }
                                .padding(vertical = 7.dp)
                        ) {
                            Text(
                                text = "±${range.toInt()}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Crossfader Curve
                Text(text = "CROSSFADER CURVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                val curves = listOf(
                    Pair(CrossfaderCurve.SMOOTH, "SMOOTH (Equal Power Cosine)"),
                    Pair(CrossfaderCurve.LINEAR, "LINEAR (Uniform Constant Slope)"),
                    Pair(CrossfaderCurve.SCRATCH_CUT, "SCRATCH (Fast Sharp Cut)"),
                    Pair(CrossfaderCurve.DIP, "DIP (Exponential Center Drop)")
                )
                curves.forEach { (curve, label) ->
                    val isSelected = mixerState.crossfaderCurve == curve
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF1C273C) else Color(0xFF131824))
                            .border(1.dp, if (isSelected) DeckAPrimary else Color(0xFF222B3D), RoundedCornerShape(6.dp))
                            .clickable { onCrossfaderCurveChanged(curve) }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) DeckAPrimary else TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Hamster / Reverse Crossfader Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF141926))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("HAMSTER (REVERSE CROSSFADER)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Swaps Deck A and Deck B crossfader sides for battle scratch style", fontSize = 8.sp, color = TextMuted)
                    }
                    Switch(
                        checked = mixerState.isHamsterReversed,
                        onCheckedChange = { onHamsterToggled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = DeckAPrimary, checkedTrackColor = Color(0xFF1C2D44))
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. Engine & Build Info
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0C0E14))
                        .padding(10.dp)
                ) {
                    Column {
                        Text("DJ IMAN 3.4 ADVANCE ENGINE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DeckAPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Audio Pipeline: 44.1 kHz 16-bit Stereo Low-Latency Stream", fontSize = 8.sp, color = TextMuted)
                        Text("DSP Modules: 3-Band Isolator EQ, Bi-Polar Filter, Master FX Matrix, Auto-Scratch", fontSize = 8.sp, color = TextMuted)
                        Text("BeatGrid Engine: Autocorrelation BPM Detection with Camelot Key Harmonization", fontSize = 8.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}
