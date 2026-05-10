package com.triloo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.triloo.ui.theme.OutfitFontFamily
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/**
 * Liquid-glass нижняя панель навигации, перенесённая из мокапа `App Shell.html`.
 *
 * Реализует все слои HTML-исходника:
 *   1. Реальный backdrop-blur 36dp через [HazeState] / [hazeChild].
 *   2. Базовый тёмный тинт `rgba(28,28,32,0.42)`, наложенный haze'ем поверх blur.
 *   3. Преломления стекла (`::before`):
 *        • радиал из левого-верхнего угла — белое сияние 22%,
 *        • радиал из правого-нижнего — coral-bloom 16%,
 *        • вертикальный градиент сверху — лёгкая «пыль» 5% → 0%.
 *      Все три рисуются с [BlendMode.Plus] (Compose-аналог CSS `screen`).
 *   4. Inset-эффекты по краям контейнера: верхний бликующий «горизонт»,
 *      hairline-обводка 1dp white 8%, drop-shadow 22dp / 50dp радиус.
 *   5. Активная «пилюля»: solid white 16% + полная стопка из 4-х теней
 *      (top inset highlight, bottom inset shadow, outer glow, drop).
 *   6. Иконки/подписи: только активный таб показывает label, иконка чуть
 *      крупнее (24 vs 23). Цвет: 100% white активный, 65% white иначе.
 *   7. Spring-анимация пилюли с лёгким overshoot — близко к
 *      cubic-bezier(0.32, 0.72, 0.18, 1.02) из CSS.
 *
 * Для backdrop-blur нужен [HazeState], создаваемый снаружи и переданный
 * как параметр. Контент, который должен быть размыт под баром, оборачивается
 * в `Modifier.haze(state)` — обычно это весь `NavHost`.
 */
data class LiquidGlassTab(
    val id: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun LiquidGlassNavBar(
    tabs: List<LiquidGlassTab>,
    activeIndex: Int,
    onTabSelected: (Int) -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    centerActionIcon: ImageVector? = null,
    centerActionContentDescription: String? = null,
    onCenterAction: () -> Unit = {}
) {
    if (tabs.isEmpty()) return
    val safeActive = activeIndex.coerceIn(0, tabs.lastIndex)

    // Если задана центральная FAB-кнопка — добавляем дополнительный слот в
    // середину ряда. При чётном числе табов (типичный кейс — 4) слот ставится
    // ровно посередине; при нечётном — между tab[size/2 - 1] и tab[size/2],
    // чуть левее центра, что для 3 табов читается как «середина-левее».
    val hasCenter = centerActionIcon != null
    val centerSlotIndex = if (hasCenter) tabs.size / 2 else -1
    val totalSlots = tabs.size + if (hasCenter) 1 else 0

    val barShape = RoundedCornerShape(28.dp)
    val pillShape = RoundedCornerShape(22.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(70.dp)
            // Drop-shadow по HTML — две тени стопкой:
            //   1. `0 22px 50px black@55%` — большая глубокая тень снизу
            //   2. `0 4px 14px black@35%`  — тонкая ближняя тень
            // В Compose это две `Modifier.shadow` подряд; шейп задаём
            // одинаковый, чтобы обе тени совпадали по контуру с баром.
            .shadow(
                elevation = 32.dp,
                shape = barShape,
                ambientColor = Color.Black.copy(alpha = 0.55f),
                spotColor = Color.Black.copy(alpha = 0.55f)
            )
            .shadow(
                elevation = 8.dp,
                shape = barShape,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.35f)
            )
            .clip(barShape)
            .then(
                if (hazeState != null) {
                    // Backdrop-blur через haze. Оставлено как опциональный путь,
                    // но в обычном режиме выключено из MainActivity: offscreen
                    // capture всего NavHost заметно просаживает FPS при скролле.
                    Modifier.hazeChild(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = Color(0xFF1C1C20),
                            tint = HazeTint(Color(0xFF1C1C20).copy(alpha = 0.42f)),
                            blurRadius = 18.dp,
                            noiseFactor = 0f
                        )
                    )
                } else {
                    Modifier.background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF23242A).copy(alpha = 0.96f),
                                Color(0xFF111217).copy(alpha = 0.94f)
                            )
                        )
                    )
                }
            )
            // Преломления стекла (`::before` → mix-blend-mode: screen).
            // NOTE: убраны оба угловых радиала из CSS-мокапа — белое сияние
            // слева-сверху и coral-bloom справа-снизу. Оба давали асимметричное
            // «засветление», которое поверх blur'а выглядело как пятно, а не
            // как часть стекла. Оставлены только нейтральные эффекты:
            // вертикальный «пылевой» градиент сверху и 1dp top-edge highlight.
            .drawBehind {
                val w = size.width
                val h = size.height
                // Вертикальный градиент сверху — лёгкая «пыль» 5% → 0%.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = h * 0.40f
                    ),
                    blendMode = BlendMode.Plus
                )
                // Inset top edge highlight — `0 1px 0 rgba(255,255,255,0.18) inset`.
                // Тонкая яркая полоса на верхнем крае.
                drawRect(
                    color = Color.White.copy(alpha = 0.18f),
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(w, 1.dp.toPx())
                )
            }
            // Hairline ring 1dp white 8%.
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = barShape
            )
    ) {
        // Геометрия слота: на N слотов приходится (N-1) gap'ов по 4dp + 16dp
        // внутренний padding (по 8dp слева/справа). N = число табов плюс
        // опциональный центральный слот для FAB-кнопки.
        val totalGapsDp = (totalSlots - 1) * 4
        val tabSlotWidth = (maxWidth - 16.dp - totalGapsDp.dp) / totalSlots

        // Активный таб с индексом i в `tabs` рендерится в физическом слоте:
        //   slot = i, если i < centerSlotIndex (или центра нет)
        //   slot = i + 1, иначе (центральная FAB сдвигает всё правее на 1)
        val activeSlotIndex = if (hasCenter && safeActive >= centerSlotIndex) {
            safeActive + 1
        } else {
            safeActive
        }

        // 220ms tween c emphasized-easing'ом из Material 3 (cubic-bezier
        // 0.2, 0, 0, 1) — пилюля стартует мгновенно и финиширует уверенно,
        // без overshoot'а и колебаний. Раньше было 320ms с лёгким bounce'ом,
        // но в сумме с инициализацией Hilt-VM нового таба это ощущалось как
        // «задержка переключения». Без bounce пилюля не залезает в зону
        // центральной FAB-кнопки → проблема «трясущегося +» тоже снимается.
        val pillOffsetX by animateDpAsState(
            targetValue = 8.dp + (tabSlotWidth + 4.dp) * activeSlotIndex,
            animationSpec = tween(
                durationMillis = 220,
                easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
            ),
            label = "lg-pill-offset"
        )

        // ─── Пилюля ───
        // HTML-стопка теней:
        //   1. inset top   `0  1px 0 rgba(255,255,255,0.35)` → линия сверху
        //   2. inset bot   `0 -1px 0 rgba(0,0,0,0.18)`        → линия снизу
        //   3. outer glow  `0  0 24px rgba(255,255,255,0.18)` → бледный glow
        //   4. outer drop  `0  6px 14px rgba(0,0,0,0.25)`     → drop shadow
        // 1+2 рисуем gradient'ом внутри.
        // 3 и 4 — два отдельных shadow-модификатора подряд: верхний с
        // белым ambient (glow), нижний с тёмным spot (drop).
        Box(
            modifier = Modifier
                // translationX через graphicsLayer вместо Modifier.offset(x =) —
                // offset читает x на layout-фазе и форсирует re-layout каждого
                // кадра анимации; graphicsLayer применяет трансформ на GPU,
                // layout не пересчитывается. Y оставлен через .offset(y), он
                // статичен и не анимируется.
                .offset(y = 6.dp)
                .graphicsLayer { translationX = pillOffsetX.toPx() }
                .width(tabSlotWidth)
                .height(58.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = pillShape,
                    ambientColor = Color.White.copy(alpha = 0.18f),
                    spotColor = Color.White.copy(alpha = 0.18f)
                )
                .shadow(
                    elevation = 8.dp,
                    shape = pillShape,
                    ambientColor = Color.Black.copy(alpha = 0.25f),
                    spotColor = Color.Black.copy(alpha = 0.25f)
                )
                .clip(pillShape)
                // Базовая полупрозрачная белая подложка.
                .background(Color.White.copy(alpha = 0.16f))
                // Inset top highlight + bottom shadow вертикальным градиентом.
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.18f)
                            ),
                            startY = 0f,
                            endY = size.height
                        )
                    )
                }
        )

        // ─── Ряд табов ───
        // Тянем на полную высоту бара, чтобы тач-зона = вся плоскость
        // секции, а не только зона с иконкой и подписью. Центральный
        // FAB-слот вставляется в нужное место как отдельный элемент.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                if (hasCenter && index == centerSlotIndex) {
                    LiquidGlassCenterAction(
                        icon = centerActionIcon!!,
                        contentDescription = centerActionContentDescription,
                        onClick = onCenterAction,
                        modifier = Modifier
                            .width(tabSlotWidth)
                            .fillMaxHeight()
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                LiquidGlassTabItem(
                    tab = tab,
                    isActive = index == safeActive,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier
                        .width(tabSlotWidth)
                        .fillMaxHeight()
                )
                if (index != tabs.lastIndex) {
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }
    }
}

/**
 * Центральная FAB-кнопка нав-бара. Визуально выделена коралловой заливкой
 * с белым плюсом — всегда «акцентная», без активного/неактивного состояния
 * (это не таб, а действие). Высота и ширина — те же, что у обычного слота
 * таба, чтобы геометрия пилюли соседних табов оставалась корректной.
 */
@Composable
private fun LiquidGlassCenterAction(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pillShape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(46.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 10.dp,
                    shape = pillShape,
                    ambientColor = Color(0xFFFF6B5C).copy(alpha = 0.4f),
                    spotColor = Color(0xFFFF6B5C).copy(alpha = 0.4f)
                )
                .clip(pillShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFF8A78),
                            Color(0xFFFF5E4D)
                        )
                    )
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun LiquidGlassTabItem(
    tab: LiquidGlassTab,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    val iconColor by animateColorAsState(
        targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.65f),
        animationSpec = tween(220),
        label = "lg-tab-color"
    )
    // Раньше ещё крутилась `iconSize 23dp ↔ 24dp` через animateFloatAsState —
    // разница в 1dp визуально не считывается, но animateFloatAsState на каждый
    // таб тикала кадрами 220ms на любой переход → 4 анимации одновременно.
    // Зафиксировал размер на 24dp, лишний tween убран.

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(animationSpec = tween(200)) + expandVertically(
                animationSpec = tween(200)
            ),
            exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(
                animationSpec = tween(150)
            )
        ) {
            // Outfit Medium 11sp — тот же шрифт, что и у заголовков (`displaySmall`,
            // `headlineMedium`, и т. д. в [TrilooTypography]). Тонкий offset(y = -2dp)
            // и tight lineHeight 11sp поднимают подпись ближе к иконке: визуально
            // text+icon читаются как одна композиция, а не как «иконка вверху
            // и подпись плавает снизу».
            Text(
                text = tab.label,
                modifier = Modifier.offset(y = (-2).dp),
                color = Color.White,
                fontFamily = OutfitFontFamily,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp
            )
        }
    }
}

// ───────────────────────── @Preview ─────────────────────────
//
// HazeState блюрит контент под баром. В превью реального контента нет, поэтому
// под бар кладём цветной градиент — чтобы было видно, что backdrop-blur
// действительно работает (на сплошном цвете blur почти не виден).
// Активный таб хранится в state — Android Studio в interactive preview
// (long-press на превью) даёт кликнуть и проверить spring-анимацию пилюли.

/**
 * Шаблонный layout превью: цветной градиент-подложка с `.haze(state)` —
 * сиблинг бара, и сам бар поверх с `.hazeChild(state)` через переданный
 * [HazeState]. Если положить бар внутрь хейзованного Box'а, haze падает с
 * `Modifier.haze and Modifier.hazeChild can not be descendants of each
 * other` — поэтому подложка и бар лежат в одном корневом `Box` как разные
 * children, а не вложенные друг в друга.
 */
@Composable
private fun LiquidGlassNavBarPreviewScaffold(
    activeIndex: Int,
    withCenterAction: Boolean
) {
    val tabs = remember {
        listOf(
            LiquidGlassTab("trips", "Поездки", IconCompassLucide),
            LiquidGlassTab("groups", "Группы", IconUsersLucide),
            LiquidGlassTab("budget", "Бюджет", IconWalletLucide),
            LiquidGlassTab("settings", "Настр.", IconGearLucide)
        )
    }
    var active by remember { mutableIntStateOf(activeIndex) }
    val haze = remember { HazeState() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        // Подложка с .haze(state) — сиблинг бара, не предок.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6B5BFF),
                            Color(0xFFFF6B5C),
                            Color(0xFFFFC65A)
                        )
                    )
                )
                .haze(haze)
        )
        // Сам бар — отдельный child корневого Box'а.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 18.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            LiquidGlassNavBar(
                tabs = tabs,
                activeIndex = active,
                onTabSelected = { active = it },
                hazeState = haze,
                centerActionIcon = if (withCenterAction) IconPlusLucide else null,
                centerActionContentDescription = "Создать",
                onCenterAction = {}
            )
        }
    }
}

@Preview(name = "NavBar — 4 таба, без центральной", showBackground = true, backgroundColor = 0xFF1A1A20, widthDp = 380)
@Composable
private fun LiquidGlassNavBarPreview_NoCenter() {
    LiquidGlassNavBarPreviewScaffold(activeIndex = 0, withCenterAction = false)
}

@Preview(name = "NavBar — с центральной + (Поездки)", showBackground = true, backgroundColor = 0xFF1A1A20, widthDp = 380)
@Composable
private fun LiquidGlassNavBarPreview_WithCenter_FirstTab() {
    LiquidGlassNavBarPreviewScaffold(activeIndex = 0, withCenterAction = true)
}

@Preview(name = "NavBar — с центральной + (Бюджет)", showBackground = true, backgroundColor = 0xFF1A1A20, widthDp = 380)
@Composable
private fun LiquidGlassNavBarPreview_WithCenter_AfterCenter() {
    // Активный таб справа от центральной FAB — проверяем, что пилюля
    // пропускает центральный слот и встаёт под физический индекс 3.
    LiquidGlassNavBarPreviewScaffold(activeIndex = 2, withCenterAction = true)
}

@Preview(name = "NavBar — с центральной + (Настр.)", showBackground = true, backgroundColor = 0xFF1A1A20, widthDp = 380)
@Composable
private fun LiquidGlassNavBarPreview_WithCenter_LastTab() {
    LiquidGlassNavBarPreviewScaffold(activeIndex = 3, withCenterAction = true)
}
