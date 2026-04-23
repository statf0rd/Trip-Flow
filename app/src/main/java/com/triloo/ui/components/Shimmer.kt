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

/**
 * Добавляет shimmer-подсветку для скелетонов загрузки.
 */
private const val ShimmerTravelDistance = 900f
private const val ShimmerGradientSpread = 400f

fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnimation = transition.animateFloat(
        initialValue = -ShimmerGradientSpread,
        targetValue = ShimmerTravelDistance,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = TrilooMotion.easingEmphasized
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAnimation"
    )

    val shimmerColors = listOf(
        Slate200,
        Slate100,
        Slate200,
    )

    background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(
                x = translateAnimation.value - ShimmerGradientSpread,
                y = translateAnimation.value - (ShimmerGradientSpread * 0.44f)
            ),
            end = Offset(
                x = translateAnimation.value + ShimmerGradientSpread,
                y = translateAnimation.value + (ShimmerGradientSpread * 0.44f)
            )
        )
    )
}
