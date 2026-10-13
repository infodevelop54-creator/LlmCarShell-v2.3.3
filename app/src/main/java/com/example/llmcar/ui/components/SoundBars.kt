package com.example.llmcar.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun SoundBars(
    amplitude: Float,
    active: Boolean,
    barCount: Int = 11,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "bars")
    val time by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart), label = "time")

    val amp = amplitude.coerceIn(0f, 1f)

    Row(modifier, Arrangement.spacedBy(6.dp), Alignment.CenterVertically) {
        for (i in 0 until barCount) {
            val phase = i * 0.65f
            val wave = if (active) (sin(time + phase) * 0.5f + 0.5f) else 0.12f
            val factor = 0.35f + amp * 0.65f
            val h = (10f + wave * 46f * factor).dp
            Box(Modifier.width(4.dp).height(h)
                .background(Color(0xFF7C4DFF), RoundedCornerShape(2.dp)))
        }
    }
}