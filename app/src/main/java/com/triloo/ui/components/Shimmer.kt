package com.triloo.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.triloo.ui.theme.Slate100
import com.triloo.ui.theme.Slate200
import com.triloo.ui.theme.Slate300
import com.triloo.ui.theme.TrilooMotion

fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnimation = transition.animateFloat(
        initialValue = -600f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = TrilooMotion.shimmerDuration,
                easing = TrilooMotion.easingStandard
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerAnimation"
    )

    val shimmerColors = listOf(
        Slate200,
        Slate100,
        Slate200,
        Slate300,
    )

    background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(
                x = translateAnimation.value - 500f,
                y = translateAnimation.value - 220f
            ),
            end = Offset(
                x = translateAnimation.value + 500f,
                y = translateAnimation.value + 220f
            )
        )
    )
}
