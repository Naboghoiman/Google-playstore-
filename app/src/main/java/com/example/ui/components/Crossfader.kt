package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CrossfaderCurve
import com.example.ui.theme.DeckAPrimary
import com.example.ui.theme.DeckBPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

/**
 * Professional DJ Horizontal Crossfader.
 * Range: -1.0f (Deck A) .. 0.0f (Center 50/50) .. +1.0f (Deck B)
 */
@Composable
fun Crossfader(
    position: Float, // -1f .. +1f
    curve: CrossfaderCurve,
    isReversed: Boolean,
    onPositionChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragVal by remember { mutableFloatStateOf(position) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF131722))
            .border(1.dp, Color(0xFF22293A), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Labels Deck A <---> Deck B
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isReversed) "DECK B" else "DECK A",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isReversed) DeckBPrimary else DeckAPrimary
            )
            Text(
                text = "CROSSFADER [${curve.name}]",
                fontSize = 8.sp,
                color = TextMuted,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (isReversed) "DECK A" else "DECK B",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isReversed) DeckAPrimary else DeckBPrimary
            )
        }

        // Horizontal Fader Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val w = size.width
                        val norm = (offset.x / w).coerceIn(0f, 1f)
                        val newPos = norm * 2f - 1f
                        onPositionChange(newPos)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val w = size.width
                            val norm = (offset.x / w).coerceIn(0f, 1f)
                            dragVal = norm * 2f - 1f
                            onPositionChange(dragVal)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val w = size.width
                            val deltaNorm = dragAmount.x / w
                            val deltaPos = deltaNorm * 2f
                            dragVal = (dragVal + deltaPos).coerceIn(-1f, 1f)
                            onPositionChange(dragVal)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(34.dp)) {
                val w = size.width
                val h = size.height
                val centerY = h / 2f

                // Track Slot (Groove)
                val trackH = 5.dp.toPx()
                val marginX = 14.dp.toPx()
                val trackW = w - (marginX * 2f)

                drawRoundRect(
                    color = Color(0xFF0B0D14),
                    topLeft = Offset(marginX, centerY - trackH / 2f),
                    size = Size(trackW, trackH),
                    cornerRadius = CornerRadius(2.5f, 2.5f)
                )

                // Center Detent Tick
                val midX = w / 2f
                drawLine(
                    color = Color(0xFF90A4AE),
                    start = Offset(midX, centerY - 8.dp.toPx()),
                    end = Offset(midX, centerY + 8.dp.toPx()),
                    strokeWidth = 2f
                )

                // Intermediate ticks
                val tickCount = 9
                for (i in 0 until tickCount) {
                    val tx = marginX + (i * trackW / (tickCount - 1))
                    if (i != tickCount / 2) {
                        drawLine(
                            color = Color(0xFF2C354A),
                            start = Offset(tx, centerY - 4.dp.toPx()),
                            end = Offset(tx, centerY + 4.dp.toPx()),
                            strokeWidth = 1f
                        )
                    }
                }

                // Calculate Cap Position
                val norm = ((position + 1f) / 2f).coerceIn(0f, 1f)
                val capCenterX = marginX + (norm * trackW)

                val capW = 20.dp.toPx()
                val capH = 26.dp.toPx()
                val capX = capCenterX - capW / 2f
                val capY = centerY - capH / 2f

                // Cap Shadow
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset(capX - 1f, capY - 1f),
                    size = Size(capW + 2f, capH + 2f),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // Cap Outer Body
                drawRoundRect(
                    color = Color(0xFF2E384D),
                    topLeft = Offset(capX, capY),
                    size = Size(capW, capH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // Cap Inner Grip
                drawRoundRect(
                    color = Color(0xFF3E4A63),
                    topLeft = Offset(capX + 3.dp.toPx(), capY + 3.dp.toPx()),
                    size = Size(capW - 6.dp.toPx(), capH - 6.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )

                // Center White Indicator Line on Crossfader Cap
                drawLine(
                    color = Color.White,
                    start = Offset(capCenterX, capY + 4.dp.toPx()),
                    end = Offset(capCenterX, capY + capH - 4.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
