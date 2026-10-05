package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import com.example.model.DeckId
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * High-precision interactive DJ Turntable Platter.
 * Supports multi-touch vinyl scratching, spinning animation, concentric groove rendering,
 * center slipmat label, strobe rim dots, and cue angle indicator.
 */
@Composable
fun TurntablePlatter(
    deckId: DeckId,
    isPlaying: Boolean,
    rotationAngle: Float,
    primaryColor: Color,
    secondaryColor: Color,
    onPlatterTouch: (Boolean) -> Unit,
    onPlatterRotate: (deltaAngle: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var previousAngleRad by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(deckId) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        onPlatterTouch(true)
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        previousAngleRad = atan2(offset.y - centerY, offset.x - centerX)
                    },
                    onDragEnd = {
                        isDragging = false
                        onPlatterTouch(false)
                    },
                    onDragCancel = {
                        isDragging = false
                        onPlatterTouch(false)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val currentAngleRad = atan2(change.position.y - centerY, change.position.x - centerX)

                        var deltaRad = currentAngleRad - previousAngleRad
                        // Normalize angle wrap-around across -PI .. +PI
                        if (deltaRad > PI) deltaRad -= (2 * PI).toFloat()
                        if (deltaRad < -PI) deltaRad += (2 * PI).toFloat()

                        val deltaDeg = Math.toDegrees(deltaRad.toDouble()).toFloat()
                        onPlatterRotate(deltaDeg)
                        previousAngleRad = currentAngleRad
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)
            val radius = min(w, h) / 2f * 0.95f

            // 1. Heavy Metallic Platter Base
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF262C3A), Color(0xFF141720)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // 2. Strobe Dots around outer rim (Pioneer CDJ / Technics 1200 style)
            val strobeRadius = radius * 0.94f
            val dotCount = 64
            for (i in 0 until dotCount) {
                val angleDeg = (i * 360f / dotCount) + (if (isPlaying) rotationAngle * 0.2f else 0f)
                val rad = Math.toRadians(angleDeg.toDouble())
                val dotX = center.x + (strobeRadius * cos(rad)).toFloat()
                val dotY = center.y + (strobeRadius * sin(rad)).toFloat()
                val isStrobeHighlight = (i % 8 == 0)
                drawCircle(
                    color = if (isStrobeHighlight) primaryColor.copy(alpha = 0.85f) else Color(0xFF3E4A61),
                    radius = if (isStrobeHighlight) 2.2f else 1.2f,
                    center = Offset(dotX, dotY)
                )
            }

            // 3. Strobe Light Illumination Glow (top left corner)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(center.x - radius * 0.7f, center.y - radius * 0.7f),
                    radius = radius * 0.5f
                ),
                radius = radius * 0.45f,
                center = Offset(center.x - radius * 0.7f, center.y - radius * 0.7f)
            )

            // 4. Rotating Vinyl Record Layer
            rotate(rotationAngle, center) {
                val vinylRadius = radius * 0.86f

                // Vinyl Body with dark sheen
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1E222D), Color(0xFF101217), Color(0xFF0A0C0F)),
                        center = center,
                        radius = vinylRadius
                    ),
                    radius = vinylRadius,
                    center = center
                )

                // Concentric Vinyl Grooves
                val grooveSteps = 8
                val grooveStart = vinylRadius * 0.42f
                val grooveStep = (vinylRadius * 0.54f) / grooveSteps
                for (g in 0 until grooveSteps) {
                    val r = grooveStart + g * grooveStep
                    drawCircle(
                        color = Color(0xFF283042).copy(alpha = if (g % 2 == 0) 0.6f else 0.35f),
                        radius = r,
                        center = center,
                        style = Stroke(width = 1.0f)
                    )
                }

                // Vinyl Reflection Highlights (two dynamic specular arcs)
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = center
                    ),
                    startAngle = 35f,
                    sweepAngle = 290f,
                    useCenter = false,
                    topLeft = Offset(center.x - vinylRadius, center.y - vinylRadius),
                    size = androidx.compose.ui.geometry.Size(vinylRadius * 2, vinylRadius * 2),
                    style = Stroke(width = vinylRadius * 0.55f)
                )

                // Center Slipmat Label (styled with Deck colors)
                val labelRadius = vinylRadius * 0.36f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.95f), secondaryColor),
                        center = center,
                        radius = labelRadius
                    ),
                    radius = labelRadius,
                    center = center
                )

                // Slipmat Logo Ring & Branding
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f),
                    radius = labelRadius * 0.75f,
                    center = center,
                    style = Stroke(width = 1.5f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.15f),
                    radius = labelRadius * 0.55f,
                    center = center,
                    style = Stroke(width = 1.0f)
                )

                // Rotating Position Needle Marker (white stripe on vinyl to see scratching position)
                drawLine(
                    color = Color.White,
                    start = Offset(center.x, center.y - labelRadius),
                    end = Offset(center.x, center.y - vinylRadius + 2f),
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round
                )
                // Glowing tip on outer edge
                drawCircle(
                    color = primaryColor,
                    radius = 3.0f,
                    center = Offset(center.x, center.y - vinylRadius + 4f)
                )

                // Center Spindle
                drawCircle(
                    color = Color(0xFFE0E6ED),
                    radius = labelRadius * 0.22f,
                    center = center
                )
                drawCircle(
                    color = Color(0xFF0F1117),
                    radius = labelRadius * 0.11f,
                    center = center
                )
            }

            // 5. Stylus Tonearm representation in resting/playing position
            val armPivot = Offset(center.x + radius * 0.85f, center.y - radius * 0.75f)
            val armTarget = Offset(center.x + radius * 0.45f, center.y + radius * 0.15f)

            // Arm base
            drawCircle(
                color = Color(0xFF374151),
                radius = 7f,
                center = armPivot
            )
            // Arm rod
            drawLine(
                color = Color(0xFF9CA3AF),
                start = armPivot,
                end = armTarget,
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
            // Cartridge Head
            drawCircle(
                color = if (isPlaying) Color(0xFFFF5722) else Color(0xFF4B5563),
                radius = 4f,
                center = armTarget
            )
        }
    }
}
