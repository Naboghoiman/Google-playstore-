package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.VuGreen
import com.example.ui.theme.VuRed
import com.example.ui.theme.VuYellow

/**
 * Vertical stereo LED level meter (Left & Right).
 * Multi-segment LED bar: Green (safe) -> Amber/Yellow (loud) -> Red (peak/clip).
 */
@Composable
fun VuMeter(
    levelL: Float, // 0f .. 1f (or up to 1.2f for clip)
    levelR: Float,
    modifier: Modifier = Modifier,
    meterWidth: Dp = 12.dp,
    segments: Int = 14
) {
    Box(
        modifier = modifier
            .width(meterWidth)
            .fillMaxHeight()
            .background(Color(0xFF0C0E14))
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val channelW = (w - 3.dp.toPx()) / 2f
            val segmentSpacing = 1.5.dp.toPx()
            val segmentH = (h - (segments - 1) * segmentSpacing) / segments

            for (seg in 0 until segments) {
                // Bottom to top: segment 0 is bottom, segment 13 is top
                val normThreshold = seg.toFloat() / segments.toFloat()
                val segY = h - (seg + 1) * segmentH - seg * segmentSpacing

                val baseColor = when {
                    normThreshold >= 0.85f -> VuRed
                    normThreshold >= 0.65f -> VuYellow
                    else -> VuGreen
                }

                // Left channel segment
                val isLitL = levelL >= normThreshold
                drawRoundRect(
                    color = if (isLitL) baseColor else Color(0xFF1E2536).copy(alpha = 0.45f),
                    topLeft = Offset(0f, segY),
                    size = Size(channelW, segmentH),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                )

                // Right channel segment
                val isLitR = levelR >= normThreshold
                drawRoundRect(
                    color = if (isLitR) baseColor else Color(0xFF1E2536).copy(alpha = 0.45f),
                    topLeft = Offset(channelW + 3.dp.toPx(), segY),
                    size = Size(channelW, segmentH),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                )
            }
        }
    }
}
