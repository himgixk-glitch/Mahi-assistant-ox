package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.SessionState
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

/**
 * Central glowing futuristic power/mic button for voice interaction.
 */
@Composable
fun CentralMicTrigger(
    sessionState: SessionState,
    amplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_glow")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val isActive = sessionState is SessionState.Listening || sessionState is SessionState.Speaking

    val coreColor = when (sessionState) {
        is SessionState.Speaking -> NeonPink
        is SessionState.Listening -> NeonCyan
        is SessionState.Thinking -> NeonAmber
        is SessionState.Connecting -> NeonPurple
        is SessionState.Disconnected -> NeonCyan.copy(alpha = 0.8f)
        is SessionState.Error -> Color(0xFFFF3366)
    }

    val glowAlpha = if (isActive) (0.4f + amplitude * 0.5f).coerceIn(0.3f, 0.9f) else 0.2f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(104.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer glowing aura
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(coreColor.copy(alpha = glowAlpha), Color.Transparent)
                            )
                        )
                )
            }

            // Outer ring with neon border
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F0A24))
                    .border(2.dp, coreColor, CircleShape)
            )

            // Inner touchable button core
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = if (isActive)
                                listOf(coreColor, coreColor.copy(alpha = 0.7f))
                            else
                                listOf(Color(0xFF1E173D), Color(0xFF130E26))
                        )
                    )
                    .clickable(onClick = onClick)
                    .testTag("central_mic_button"),
                contentAlignment = Alignment.Center
            ) {
                val icon = when (sessionState) {
                    is SessionState.Speaking -> Icons.Default.Stop
                    is SessionState.Listening -> Icons.Default.Mic
                    is SessionState.Connecting -> Icons.Default.Mic
                    is SessionState.Thinking -> Icons.Default.Mic
                    is SessionState.Disconnected -> Icons.Default.Mic
                    is SessionState.Error -> Icons.Default.MicOff
                }

                val iconColor = if (isActive) Color.White else coreColor

                Icon(
                    imageVector = icon,
                    contentDescription = "Voice Control",
                    tint = iconColor,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // State Action Prompt Label
        val actionPrompt = when (sessionState) {
            is SessionState.Speaking -> "Tap to interrupt Mahi"
            is SessionState.Listening -> "Listening to your voice..."
            is SessionState.Thinking -> "Thinking of a reply..."
            is SessionState.Connecting -> "Connecting to live audio..."
            is SessionState.Disconnected -> "Tap to talk with Mahi"
            is SessionState.Error -> "Tap to reconnect"
        }

        Text(
            text = actionPrompt,
            color = if (isActive) coreColor else TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}
