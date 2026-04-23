package com.triloo.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment

/**
 * Централизованные кривые, длительности и переходы анимации дизайн-системы Triloo.
 */
object TrilooMotion {
    val easingStandard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val easingEmphasized = CubicBezierEasing(0.2f, 0.9f, 0.1f, 1f)
    val easingExit = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    const val durationShort = 160
    const val durationMedium = 320
    const val durationLong = 520
    const val durationExtraLong = 760
    const val shimmerDuration = 1400

    val pressSpring = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMedium
    )
    val selectSpring = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessLow
    )

    fun enterVerticalStagger(delayMillis: Int = 0): EnterTransition =
        fadeIn(
            animationSpec = tween(
                durationMillis = durationMedium,
                delayMillis = delayMillis,
                easing = easingEmphasized
            )
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = durationLong,
                delayMillis = delayMillis,
                easing = easingEmphasized
            ),
            initialOffsetY = { it / 6 }
        ) + scaleIn(
            initialScale = 0.98f,
            animationSpec = tween(
                durationMillis = durationMedium,
                delayMillis = delayMillis,
                easing = easingEmphasized
            )
        )

    fun enterHorizontalStagger(delayMillis: Int = 0): EnterTransition =
        fadeIn(
            animationSpec = tween(
                durationMillis = durationMedium,
                delayMillis = delayMillis,
                easing = easingEmphasized
            )
        ) + slideInHorizontally(
            animationSpec = tween(
                durationMillis = durationLong,
                delayMillis = delayMillis,
                easing = easingEmphasized
            ),
            initialOffsetX = { it / 5 }
        ) + scaleIn(
            initialScale = 0.98f,
            animationSpec = tween(
                durationMillis = durationMedium,
                delayMillis = delayMillis,
                easing = easingEmphasized
            )
        )

    fun exitStagger(): ExitTransition =
        fadeOut(
            animationSpec = tween(
                durationMillis = durationShort,
                easing = easingExit
            )
        ) + slideOutVertically(
            animationSpec = tween(
                durationMillis = durationMedium,
                easing = easingExit
            ),
            targetOffsetY = { -it / 10 }
        ) + scaleOut(
            targetScale = 0.98f,
            animationSpec = tween(
                durationMillis = durationShort,
                easing = easingExit
            )
        )

    fun enterExpand(delayMillis: Int = 0): EnterTransition =
        fadeIn(
            animationSpec = tween(
                durationMillis = durationShort,
                delayMillis = delayMillis,
                easing = easingStandard
            )
        ) + expandVertically(
            animationSpec = tween(
                durationMillis = durationLong,
                delayMillis = delayMillis,
                easing = easingEmphasized
            ),
            expandFrom = Alignment.Top
        )

    fun exitShrink(): ExitTransition =
        fadeOut(
            animationSpec = tween(
                durationMillis = durationShort,
                easing = easingExit
            )
        ) + shrinkVertically(
            animationSpec = tween(
                durationMillis = durationMedium,
                easing = easingExit
            ),
            shrinkTowards = Alignment.Top
        )

    fun enterNavForward(): EnterTransition =
        fadeIn(
            animationSpec = tween(
                durationMillis = durationLong,
                easing = easingStandard
            )
        ) + slideInHorizontally(
            animationSpec = tween(
                durationMillis = durationLong,
                easing = easingEmphasized
            ),
            initialOffsetX = { it / 10 }
        ) + scaleIn(
            initialScale = 0.992f,
            animationSpec = tween(
                durationMillis = durationLong,
                easing = easingEmphasized
            )
        )

    fun exitNavForward(): ExitTransition =
        fadeOut(
            animationSpec = tween(
                durationMillis = durationMedium,
                easing = easingExit
            )
        ) + slideOutHorizontally(
            animationSpec = tween(
                durationMillis = durationLong,
                easing = easingExit
            ),
            targetOffsetX = { -it / 12 }
        ) + scaleOut(
            targetScale = 0.992f,
            animationSpec = tween(
                durationMillis = durationMedium,
                easing = easingExit
            )
        )

    fun enterNavBack(): EnterTransition =
        fadeIn(
            animationSpec = tween(
                durationMillis = durationLong,
                easing = easingStandard
            )
        ) + slideInHorizontally(
            animationSpec = tween(
                durationMillis = durationLong,
                easing = easingEmphasized
            ),
            initialOffsetX = { -it / 10 }
        ) + scaleIn(
            initialScale = 0.994f,
            animationSpec = tween(
                durationMillis = durationLong,
                easing = easingEmphasized
            )
        )

    fun exitNavBack(): ExitTransition =
        fadeOut(
            animationSpec = tween(
                durationMillis = durationMedium,
                easing = easingExit
            )
        ) + slideOutHorizontally(
            animationSpec = tween(
                durationMillis = durationLong,
                easing = easingExit
            ),
            targetOffsetX = { it / 12 }
        ) + scaleOut(
            targetScale = 0.992f,
            animationSpec = tween(
                durationMillis = durationMedium,
                easing = easingExit
            )
        )

    fun enterBottomSheet(): EnterTransition =
        fadeIn(
            animationSpec = tween(
                durationMillis = durationLong,
                easing = easingStandard
            )
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = durationLong,
                easing = easingEmphasized
            ),
            initialOffsetY = { it / 5 }
        ) + scaleIn(
            initialScale = 0.994f,
            animationSpec = tween(
                durationMillis = durationLong,
                easing = easingEmphasized
            )
        )

    fun exitBottomSheet(): ExitTransition =
        fadeOut(
            animationSpec = tween(
                durationMillis = durationMedium,
                easing = easingExit
            )
        ) + slideOutVertically(
            animationSpec = tween(
                durationMillis = durationLong,
                easing = easingExit
            ),
            targetOffsetY = { it / 6 }
        )

    fun enterNavFromOverlay(): EnterTransition =
        fadeIn(
            animationSpec = tween(
                durationMillis = durationLong,
                easing = easingStandard
            )
        ) + scaleIn(
            initialScale = 0.996f,
            animationSpec = tween(
                durationMillis = durationLong,
                easing = easingEmphasized
            )
        )

    fun enterNavUnderOverlay(): EnterTransition =
        fadeIn(
            animationSpec = tween(
                durationMillis = durationMedium,
                easing = easingStandard
            )
        ) + scaleIn(
            initialScale = 0.998f,
            animationSpec = tween(
                durationMillis = durationMedium,
                easing = easingStandard
            )
        )

    fun exitNavForOverlay(): ExitTransition =
        fadeOut(
            animationSpec = tween(
                durationMillis = durationMedium,
                easing = easingExit
            )
        ) + scaleOut(
            targetScale = 0.996f,
            animationSpec = tween(
                durationMillis = durationMedium,
                easing = easingExit
            )
        )
}
