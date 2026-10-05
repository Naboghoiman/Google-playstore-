package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Professional DJ 270-degree rotary potentiometer knob.
 * Value range: normalized 0f .. 1f or centered -1f .. +1f
 */
@Composable
fun DjRotaryKnob(
    label: String,
    value: Float, // Normalized value (e.g. -1f..+1f for EQ/Filter, or 0f..1f)
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    minVal: Float = -1f,
    maxVal: Float = 1f,
    activeColor: Color = Color(0xFF00E5FF),
    valueText: String = ""
) {
    var dragAccumulator by remember { mutableFloatStateOf(value) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .pointerInput(label) {
                    detectDragGestures(
                        onDragStart = {
                            dragAccumulator = value
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Vertical drag (up increases, down decreases)
                            val sensitivity = (maxVal - minVal) / 250f
                            dragAccumulator = (dragAccumulator - dragAmount.y * sensitivity)
                                .coerceIn(minVal, maxVal)
                            onValueChange(dragAccumulator)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.size(size)) {
                val w = this.size.width
                val h = this.size.height
                val center = Offset(w / 2f, h / 2f)
                val radius = (w / 2f) * 0.88f
                val knobRadius = radius * 0.72f

                // Normalized 0f .. 1f for angular mapping
                val norm = ((value - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                // 270 degrees total sweep: from 135 deg (bottom-left) to 405 deg (bottom-right)
                val startAngle = 135f
                val sweepAngle = 270f
                val currentAngle = startAngle + norm * sweepAngle

                // 1. Background LED Arc Track
                drawArc(
                    color = Color(0xFF1E2536),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                )

                // 2. Active Glowing Value Arc
                if (minVal < 0f) {
                    // Bi-polar center-detent knob (arc grows from top center 270 deg)
                    val centerAngle = 270f
                    val sweep = (currentAngle - centerAngle)
                    drawArc(
                        color = activeColor,
                        startAngle = centerAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                    )
                } else {
                    // Unipolar knob: arc grows from start
                    drawArc(
                        color = activeColor,
                        startAngle = startAngle,
                        sweepAngle = norm * sweepAngle,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                    )
                }

                // 3. Center Detent Indicator Tick
                val topRad = Math.toRadians(270.0)
                drawLine(
                    color = Color(0xFF6B7280),
                    start = Offset(center.x + (radius * 1.05f * cos(topRad)).toFloat(), center.y + (radius * 1.05f * sin(topRad)).toFloat()),
                    end = Offset(center.x + (radius * 1.25f * cos(topRad)).toFloat(), center.y + (radius * 1.25f * sin(topRad)).toFloat()),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )

                // 4. Metallic Knob Cap Body
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF2C3549), Color(0xFF171B24)),
                        center = center,
                        radius = knobRadius
                    ),
                    radius = knobRadius,
                    center = center
                )

                // Knob outer metallic rim
                drawCircle(
                    color = Color(0xFF3B4863),
                    radius = knobRadius,
                    center = center,
                    style = Stroke(width = 1.5f)
                )

                // 5. Knob Notch Indicator
                val currentRad = Math.toRadians(currentAngle.toDouble())
                val notchStart = Offset(
                    center.x + (knobRadius * 0.25f * cos(currentRad)).toFloat(),
                    center.y + (knobRadius * 0.25f * sin(currentRad)).toFloat()
                )
                val notchEnd = Offset(
                    center.x + (knobRadius * 0.88f * cos(currentRad)).toFloat(),
                    center.y + (knobRadius * 0.88f * sin(currentRad)).toFloat()
                )
                drawLine(
                    color = Color.White,
                    start = notchStart,
                    end = notchEnd,
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Label
        Text(
            text = label,
            fontSize = 9.sp,
            color = TextSecondary,
            maxLines = 1
        )
        if (valueText.isNotEmpty()) {
            Text(
                text = valueText,
                fontSize = 8.sp,
                color = TextMuted,
                maxLines = 1
            )
        }
    }
}
