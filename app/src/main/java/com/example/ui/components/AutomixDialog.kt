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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.AutomixTransition
import com.example.ui.theme.DeckAPrimary
import com.example.ui.theme.PlayColor
import com.example.ui.theme.RecordRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AutomixDialog(
    isRunning: Boolean,
    currentTransition: AutomixTransition,
    onTransitionChanged: (AutomixTransition) -> Unit,
    onStartAutomix: (Int) -> Unit,
    onStopAutomix: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedBeats by remember { mutableIntStateOf(16) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10141E))
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = DeckAPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SMART AUTOMIX ENGINE",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Seamlessly beat-matches, syncs tempo, and crossfades between Deck A and Deck B automatically.",
                    fontSize = 10.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Transition Styles
                Text(text = "TRANSITION STYLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))

                val styles = listOf(
                    Pair(AutomixTransition.FILTER_FADE, "FILTER FADE (HPF Out + LPF In)"),
                    Pair(AutomixTransition.CROSSFADE, "SMOOTH CROSSFADE (Equal Power)"),
                    Pair(AutomixTransition.ECHO_DROP, "ECHO FREEZE & DROP"),
                    Pair(AutomixTransition.BRAKE_CUT, "TURNTABLE BRAKE & CUT")
                )

                styles.forEach { (style, name) ->
                    val isSelected = currentTransition == style
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF1C273C) else Color(0xFF131824))
                            .border(1.dp, if (isSelected) DeckAPrimary else Color(0xFF222B3D), RoundedCornerShape(6.dp))
                            .clickable { onTransitionChanged(style) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = name,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) DeckAPrimary else TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Duration in Beats
                Text(text = "TRANSITION DURATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val beatOptions = listOf(8, 16, 32)
                    beatOptions.forEach { b ->
                        val isSelected = selectedBeats == b
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) DeckAPrimary.copy(alpha = 0.85f) else Color(0xFF141926))
                                .border(1.dp, if (isSelected) Color.White else Color(0xFF222B3D), RoundedCornerShape(6.dp))
                                .clickable { selectedBeats = b }
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = "$b BEATS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Start / Stop Automix CTA button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isRunning) RecordRed else PlayColor)
                        .clickable {
                            if (isRunning) {
                                onStopAutomix()
                            } else {
                                onStartAutomix(selectedBeats)
                                onDismiss()
                            }
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRunning) "STOP AUTOMIX" else "START AUTOMIX TRANSITION",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}
