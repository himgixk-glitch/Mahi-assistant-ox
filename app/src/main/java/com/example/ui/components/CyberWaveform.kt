package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.SessionState
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import kotlin.math.sin

/**
 * Animated cyber soundwave equalizer bars reflecting live voice amplitude.
 */
@Composable
fun CyberWaveform(
    sessionState: SessionState,
    amplitude: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 18
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_phase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val (barStartColor, barEndColor) = when (sessionState) {
        is SessionState.Speaking -> Pair(NeonPink, NeonCyan)
        is SessionState.Listening -> Pair(NeonCyan, NeonGreen)
        is SessionState.Thinking -> Pair(NeonPurple, NeonPink)
        else -> Pair(NeonPurple.copy(alpha = 0.3f), NeonCyan.copy(alpha = 0.2f))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val normalizedIdx = (i - barCount / 2f) / (barCount / 2f)
            val bellCurve = 1f - (normalizedIdx * normalizedIdx).coerceIn(0f, 0.85f)
            val wave = (sin(phase + i * 0.45f) + 1f) / 2f

            val baseHeight = when (sessionState) {
                is SessionState.Disconnected -> 0.15f
                is SessionState.Connecting -> 0.35f * wave
                is SessionState.Thinking -> 0.4f * wave
                else -> (0.2f + (amplitude.coerceIn(0f, 1f) * 0.8f * wave * bellCurve)).coerceIn(0.12f, 1f)
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(baseHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(barStartColor, barEndColor)
                        )
                    )
            )
        }
    }
}
