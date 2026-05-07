package com.triloo.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.triloo.ui.theme.TrilooMotion
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * iOS-стиль свайпа-назад с левой грани экрана.
 *
 * - Палец опускается в зоне `edgeWidth` от левого края — иначе жест игнорируется
 *   и события идут вглубь обычным путём (карты, скроллы, кнопки внутри экрана
 *   работают штатно).
 * - Когда движение становится преимущественно горизонтальным вправо, мы
 *   перехватываем pointer и сдвигаем экран целиком за пальцем.
 * - На отпускании: если сместили дальше `triggerRatio` от ширины экрана —
 *   доезжаем до края и зовём [onBack]; иначе пружинно возвращаемся.
 */
fun Modifier.swipeBackFromEdge(
    enabled: Boolean = true,
    edgeWidth: Dp = 24.dp,
    triggerRatio: Float = 0.30f,
    onBack: () -> Unit
): Modifier = composed {
    if (!enabled) return@composed this

    val density = LocalDensity.current
    val edgePx = with(density) { edgeWidth.toPx() }
    val touchSlopPx = with(density) { 8.dp.toPx() }
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var dragJob: Job? = remember { null }

    this
        .onSizeChanged { widthPx = it.width.toFloat() }
        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
        .pointerInput(onBack) {
            // Используем Main-пасс с requireUnconsumed = true: если ребёнок
            // (карта Яндекса, скролл, кнопки) уже забрал down — мы не вмешиваемся.
            // Свайп-назад срабатывает только на «свободные» тачи в левой грани.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                if (down.position.x > edgePx) return@awaitEachGesture

                var horizontal = false
                var directionDecided = false

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                    if (!change.pressed) {
                        dragJob?.cancel()
                        dragJob = scope.launch {
                            if (horizontal && offsetX.value > widthPx * triggerRatio) {
                                offsetX.animateTo(
                                    targetValue = widthPx,
                                    animationSpec = tween(
                                        durationMillis = TrilooMotion.durationMedium,
                                        easing = TrilooMotion.easingStandard
                                    )
                                )
                                onBack()
                                offsetX.snapTo(0f)
                            } else {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        }
                        break
                    }

                    val totalX = change.position.x - down.position.x
                    val totalY = change.position.y - down.position.y

                    if (!directionDecided && (abs(totalX) > touchSlopPx || abs(totalY) > touchSlopPx)) {
                        directionDecided = true
                        horizontal = abs(totalX) > abs(totalY) && totalX > 0
                    }

                    if (horizontal) {
                        change.consume()
                        dragJob?.cancel()
                        dragJob = scope.launch {
                            offsetX.snapTo(totalX.coerceAtLeast(0f))
                        }
                    }
                }
            }
        }
}

/**
 * Обёртка над содержимым внутреннего экрана: добавляет swipe-back-from-edge
 * на корне. Применять в [TrilooNavHost] для всех composable, кроме start-destination.
 */
@Composable
fun SwipeBackContainer(
    onBack: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .swipeBackFromEdge(enabled = enabled, onBack = onBack)
    ) {
        content()
    }
}
