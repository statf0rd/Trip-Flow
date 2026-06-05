package com.triloo.ui.theme

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.runtime.Composable

/**
 * Fling-поведение для скроллируемых списков и форм приложения.
 *
 * Сейчас — платформенный сплайновый decay ([ScrollableDefaults.flingBehavior]).
 * Ранее здесь жил кастомный exponentialDecay(friction 0.32) с бустом начальной
 * скорости ×1.45 «под iOS», но на 90Hz-экранах он давал два артефакта:
 *   1. Скачок скорости в момент отпускания пальца (+45% мгновенно) —
 *      пролистывание начиналось заметным рывком.
 *   2. Экспоненциальный хвост гаснет асимптотически: последние секунды список
 *      полз сабпиксельными шагами (~1px раз в несколько кадров) — выглядело
 *      как подёргивание/лаг в конце броска, и анимация впустую жгла кадры
 *      ещё ~8 секунд после визуальной остановки.
 * Сплайновый decay Android лишён обоих: скорость непрерывна на старте,
 * остановка детерминированная и быстрая.
 *
 * Функция оставлена как единая точка тюнинга: если ощущение инерции снова
 * захочется менять — правка в одном месте на все экраны.
 */
@Composable
fun trilooFlingBehavior(): FlingBehavior = ScrollableDefaults.flingBehavior()
