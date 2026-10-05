package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

/**
 * Vertical linear slider fader for channel volume and pitch/tempo control.
 */
@Composable
fun VerticalFader(
    value: Float, // 0f (bottom) to 1f (top), or -1f (top) to +1f (bottom) for pitch
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isCentered: Boolean = false, // true for pitch fader (0 at center)
    activeColor: Color = Color(0xFF00E5FF),
    faderWidth: Dp = 36.dp,
    label: String = ""
) {
    var dragVal by remember { mutableFloatStateOf(value) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                fontSize = 8.sp,
                color = TextSecondary
            )
        }

        Box(
            modifier = Modifier
                .width(faderWidth)
                .fillMaxHeight()
                .pointerInput(label) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val h = size.height
                            if (h > 0) {
                                val norm = 1f - (offset.y / h).coerceIn(0f, 1f)
                                val newVal = if (isCentered) (norm * 2f - 1f) else norm
                                dragVal = newVal
                                onValueChange(newVal)
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val h = size.height
                            if (h > 0) {
                                val deltaNorm = -dragAmount.y / h
                                val delta = if (isCentered) deltaNorm * 2f else deltaNorm
                                val minV = if (isCentered) -1f else 0f
                                dragVal = (dragVal + delta).coerceIn(minV, 1f)
                                onValueChange(dragVal)
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxHeight().width(faderWidth)) {
                val w = size.width
                val h = size.height
                val centerX = w / 2f

                // Track Slot (Dark recessed groove)
                val trackWidth = 4.dp.toPx()
                drawRoundRect(
                    color = Color(0xFF141721),
                    topLeft = Offset(centerX - trackWidth / 2f, 12.dp.toPx()),
                    size = Size(trackWidth, h - 24.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )

                // Scale Tick Marks on sides
                val numTicks = 9
                val slotTop = 16.dp.toPx()
                val slotBottom = h - 16.dp.toPx()
                val slotSpan = slotBottom - slotTop

                for (i in 0 until numTicks) {
                    val tickY = slotTop + (i * slotSpan / (numTicks - 1))
                    val isCenterTick = (i == numTicks / 2)
                    val tickLen = if (isCenterTick) 7.dp.toPx() else 4.dp.toPx()

                    // Left ticks
                    drawLine(
                        color = if (isCenterTick) Color(0xFF90A4AE) else Color(0xFF2C354A),
                        start = Offset(centerX - trackWidth / 2f - tickLen, tickY),
                        end = Offset(centerX - trackWidth / 2f - 2f, tickY),
                        strokeWidth = 1f
                    )
                    // Right ticks
                    drawLine(
                        color = if (isCenterTick) Color(0xFF90A4AE) else Color(0xFF2C354A),
                        start = Offset(centerX + trackWidth / 2f + 2f, tickY),
                        end = Offset(centerX + trackWidth / 2f + tickLen, tickY),
                        strokeWidth = 1f
                    )
                }

                // Compute Cap Position
                val normPos = if (isCentered) {
                    ((value + 1f) / 2f).coerceIn(0f, 1f)
                } else {
                    value.coerceIn(0f, 1f)
                }
                val capCenterY = slotBottom - (normPos * slotSpan)

                // Fader Cap (Tactile 3D DJ fader block with white indicator line)
                val capW = 28.dp.toPx()
                val capH = 18.dp.toPx()
                val capX = centerX - capW / 2f
                val capY = capCenterY - capH / 2f

                // Cap shadow/border
                drawRoundRect(
                    color = Color(0xFF0F1117),
                    topLeft = Offset(capX - 1f, capY - 1f),
                    size = Size(capW + 2f, capH + 2f),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )

                // Cap body
                drawRoundRect(
                    color = Color(0xFF2D3548),
                    topLeft = Offset(capX, capY),
                    size = Size(capW, capH),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )

                // Cap middle ribbed grip
                drawRoundRect(
                    color = Color(0xFF38435C),
                    topLeft = Offset(capX + 4.dp.toPx(), capY + 3.dp.toPx()),
                    size = Size(capW - 8.dp.toPx(), capH - 6.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )

                // Center White Marker Line
                drawLine(
                    color = Color.White,
                    start = Offset(capX + 3.dp.toPx(), capCenterY),
                    end = Offset(capX + capW - 3.dp.toPx(), capCenterY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
