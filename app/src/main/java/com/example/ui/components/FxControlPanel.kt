package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MasterFxType
import com.example.model.MixerState
import com.example.ui.theme.SyncColor
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Master FX Control Panel with XY Touchpad, Effect Presets, Dry/Wet, and Beat Sync divisions.
 */
@Composable
fun FxControlPanel(
    mixerState: MixerState,
    onFxTypeSelected: (MasterFxType) -> Unit,
    onToggleFx: () -> Unit,
    onDryWetChanged: (Float) -> Unit,
    onFxParamsChanged: (Float, Float) -> Unit,
    onBeatDivisionChanged: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F121A))
            .border(1.dp, Color(0xFF202738), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // Top Header: FX On/Off and Dry/Wet
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (mixerState.isFxActive) SyncColor else Color(0xFF261C33))
                        .border(1.dp, if (mixerState.isFxActive) Color.White else SyncColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .clickable { onToggleFx() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (mixerState.isFxActive) "FX ON" else "FX OFF",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (mixerState.isFxActive) Color.Black else SyncColor
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = mixerState.activeFxType.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            // Beat Division buttons
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                val divisions = listOf(Pair(0.25, "1/4"), Pair(0.5, "1/2"), Pair(0.75, "3/4"), Pair(1.0, "1"), Pair(2.0, "2"))
                divisions.forEach { (div, label) ->
                    val isSelected = mixerState.fxBeatDivision == div
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isSelected) SyncColor.copy(alpha = 0.8f) else Color(0xFF1B2230))
                            .clickable { onBeatDivisionChanged(div) }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 8.sp,
                            color = if (isSelected) Color.Black else TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // FX Type Selector Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val fxTypes = listOf(
                MasterFxType.ECHO,
                MasterFxType.REVERB,
                MasterFxType.FLANGER,
                MasterFxType.BITCRUSHER,
                MasterFxType.ROLL,
                MasterFxType.BRAKE,
                MasterFxType.SPINBACK
            )
            fxTypes.forEach { type ->
                val isSelected = mixerState.activeFxType == type
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) Color(0xFF2C1E40) else Color(0xFF151924))
                        .border(1.dp, if (isSelected) SyncColor else Color(0xFF232B3D), RoundedCornerShape(4.dp))
                        .clickable { onFxTypeSelected(type) }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = type.name.take(4),
                        fontSize = 8.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) SyncColor else TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // XY Touchpad for real-time parameter modulation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF090B10))
                .border(1.dp, Color(0xFF1A2130), RoundedCornerShape(6.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val px = (offset.x / size.width).coerceIn(0f, 1f)
                        val py = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                        onFxParamsChanged(px, py)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val px = (change.position.x / size.width).coerceIn(0f, 1f)
                        val py = (1f - (change.position.y / size.height)).coerceIn(0f, 1f)
                        onFxParamsChanged(px, py)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(68.dp)) {
                val w = size.width
                val h = size.height

                // Grid background
                val gridLines = 4
                for (i in 1 until gridLines) {
                    val x = i * w / gridLines
                    val y = i * h / gridLines
                    drawLine(Color(0xFF1A2130), Offset(x, 0f), Offset(x, h), 1f)
                    drawLine(Color(0xFF1A2130), Offset(0f, y), Offset(w, y), 1f)
                }

                // Cursor position
                val curX = mixerState.fxParamX * w
                val curY = (1f - mixerState.fxParamY) * h

                // Crosshair lines
                drawLine(SyncColor.copy(alpha = 0.35f), Offset(curX, 0f), Offset(curX, h), 1f)
                drawLine(SyncColor.copy(alpha = 0.35f), Offset(0f, curY), Offset(w, curY), 1f)

                // Glowing cursor puck
                drawCircle(
                    color = SyncColor.copy(alpha = 0.3f),
                    radius = 12f,
                    center = Offset(curX, curY)
                )
                drawCircle(
                    color = SyncColor,
                    radius = 5f,
                    center = Offset(curX, curY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2f,
                    center = Offset(curX, curY)
                )
            }

            Text(
                text = "XY TOUCHPAD [X: ${(mixerState.fxParamX * 100).toInt()}%  Y: ${(mixerState.fxParamY * 100).toInt()}%]",
                fontSize = 8.sp,
                color = TextMuted,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}
