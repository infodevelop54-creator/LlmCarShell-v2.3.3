package com.example.llmcar.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class OrbState { Idle, Listening, Thinking, Speaking }

@Composable
fun VoiceOrb(
    state: OrbState,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF7C4DFF)
) {
    val infinite = rememberInfiniteTransition(label = "orb")
    val pulse by infinite.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse), label = "pulse")
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.28f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse), label = "glow")

    val color: Color = when (state) {
        OrbState.Idle -> accentColor.copy(alpha = 0.55f)
        OrbState.Listening -> accentColor
        OrbState.Thinking -> Color(0xFFFFA726)
        OrbState.Speaking -> Color(0xFF4CAF50)
    }
    val icon: ImageVector = when (state) {
        OrbState.Idle -> Icons.Default.Mic
        OrbState.Listening -> Icons.Default.Mic
        OrbState.Thinking -> Icons.Default.MoreHoriz
        OrbState.Speaking -> Icons.Default.VolumeUp
    }

    Box(modifier.size(180.dp), Alignment.Center) {
        Box(Modifier.size(180.dp).scale(pulse).background(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = glowAlpha), Color.Transparent)),
            shape = CircleShape))
        Box(Modifier.size(130.dp).background(color.copy(alpha = 0.22f), CircleShape))
        Box(Modifier.size(84.dp).background(color, CircleShape), Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(38.dp))
        }
    }
}
