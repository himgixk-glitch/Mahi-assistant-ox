package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.SessionState
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance futuristic holographic Canvas visualizer.
 * Renders rotating cyber rings, audio waveform nodes, and breathing neon aura.
 */
@Composable
fun FuturisticOrbVisualizer(
    sessionState: SessionState,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cyber_orb")

    // Ambient breathing pulse
    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Outer cyber ring rotation
    val ringRotation1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rot1"
    )

    // Inner counter-rotation
    val ringRotation2 by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rot2"
    )

    // Smooth amplitude transitions
    val animatedAmp = remember { Animatable(0f) }
    LaunchedEffect(amplitude) {
        animatedAmp.animateTo(
            targetValue = amplitude.coerceIn(0f, 1f),
            animationSpec = tween(120, easing = LinearEasing)
        )
    }

    val activeColor = when (sessionState) {
        is SessionState.Disconnected -> NeonPurple.copy(alpha = 0.45f)
        is SessionState.Connecting -> NeonCyan
        is SessionState.Listening -> NeonCyan
        is SessionState.Thinking -> NeonAmber
        is SessionState.Speaking -> NeonPink
        is SessionState.Error -> Color(0xFFFF3366)
    }

    val secondaryColor = when (sessionState) {
        is SessionState.Speaking -> NeonCyan
        is SessionState.Listening -> NeonGreen
        is SessionState.Thinking -> NeonPurple
        else -> NeonPink.copy(alpha = 0.5f)
    }

    Box(
        modifier = modifier.size(280.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) * 0.72f
            val dynamicRadius = baseRadius * breathingPulse * (1f + animatedAmp.value * 0.35f)

            // 1. Radial glow halo
            val glowRadius = dynamicRadius * 1.35f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        activeColor.copy(alpha = 0.35f + animatedAmp.value * 0.3f),
                        secondaryColor.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )

            // 2. Outer segmented cybernetic dashed ring
            drawCircle(
                color = activeColor.copy(alpha = 0.4f),
                radius = dynamicRadius * 1.08f,
                center = center,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), ringRotation1 * 2f)
                )
            )

            // 3. Middle high-tech counter-rotating ring with tick marks
            drawCircle(
                color = secondaryColor.copy(alpha = 0.65f),
                radius = dynamicRadius * 0.95f,
                center = center,
                style = Stroke(
                    width = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(35f, 20f, 8f, 20f), ringRotation2 * 2f)
                )
            )

            // 4. Reactive soundwave nodes around circumference
            val nodeCount = 36
            val angleStep = (2 * PI / nodeCount).toFloat()
            for (i in 0 until nodeCount) {
                val angle = i * angleStep + (ringRotation1 * PI / 180f).toFloat()
                // Modulate height with amplitude and sinusoidal ripple
                val ripple = sin(angle * 3f + ringRotation2 * 0.05f).toFloat()
                val waveHeight = 6.dp.toPx() + (animatedAmp.value * 28.dp.toPx() * (0.5f + 0.5f * ripple))

                val startR = dynamicRadius * 0.88f
                val endR = startR + waveHeight

                val start = Offset(
                    center.x + startR * cos(angle),
                    center.y + startR * sin(angle)
                )
                val end = Offset(
                    center.x + endR * cos(angle),
                    center.y + endR * sin(angle)
                )

                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(activeColor, secondaryColor),
                        start = start,
                        end = end
                    ),
                    start = start,
                    end = end,
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 5. Core glowing sphere
            val coreRadius = dynamicRadius * 0.65f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        activeColor.copy(alpha = 0.9f),
                        secondaryColor.copy(alpha = 0.7f),
                        Color(0xFF0A071E).copy(alpha = 0.85f)
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )

            // 6. Luminous core boundary ring
            drawCircle(
                color = activeColor,
                radius = coreRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
