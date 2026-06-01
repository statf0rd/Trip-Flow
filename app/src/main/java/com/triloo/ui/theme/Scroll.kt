package com.triloo.ui.theme

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.abs

/**
 * Более «инерционный» fling для скроллируемых списков и форм: дефолтный
 * spline-decay Compose ощущается обрывистым — короткий свайп почти не
 * уносит. Используем экспоненциальную кривую с пониженным трением и
 * слегка усиливаем initial velocity, чтобы даже лёгкий жест давал
 * заметный «доезд», ближе к iOS-ощущению.
 */
@Composable
fun trilooFlingBehavior(): FlingBehavior {
    val flingDecay = remember { exponentialDecay<Float>(frictionMultiplier = 0.32f) }
    return remember(flingDecay) {
        TrilooFlingBehavior(flingDecay = flingDecay, velocityMultiplier = 1.45f)
    }
}

private class TrilooFlingBehavior(
    private val flingDecay: DecayAnimationSpec<Float>,
    private val velocityMultiplier: Float
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) <= 1f) return initialVelocity
        val boostedVelocity = initialVelocity * velocityMultiplier
        var velocityLeft = boostedVelocity
        var lastValue = 0f
        AnimationState(initialValue = 0f, initialVelocity = boostedVelocity)
            .animateDecay(flingDecay) {
                val delta = value - lastValue
                val consumed = scrollBy(delta)
                lastValue = value
                velocityLeft = this.velocity
                if (abs(delta - consumed) > 0.5f) cancelAnimation()
            }
        return velocityLeft
    }
}
