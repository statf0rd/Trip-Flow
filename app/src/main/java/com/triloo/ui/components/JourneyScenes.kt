package com.triloo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.abs

/**
 * Набор готовых сцен-фонов для журнала поездок (карточки прошедших трипов).
 * Каждая сцена — пара «градиент + декоративный силуэт», выбранные так, чтобы
 * перекликаться с реальной природой направления (Кавказ → бирюзовые горы,
 * Бали → пальма с закатом, Стамбул → силуэт мечети, Нью-Йорк → ночные башни
 * с окнами и т. д.). Источник макета — `Trip Cards Gallery.html`.
 *
 * Маппинг destination → сцена сделан грубым keyword-matcher'ом
 * [selectJourneyScene]: чтобы пользователь увидел осмысленный визуал, не
 * настраивая ничего вручную. Если ни один шаблон не подходит — берём
 * детерминированную сцену по hash'у направления (всегда одна и та же для
 * одной строки).
 */
enum class JourneyScene {
    ELBRUS, ALPS, NORTHERN_LIGHTS, KARELIA,
    BALI, SANTORINI, FAROE, CINQUE_TERRE,
    TOKYO, ISTANBUL, PARIS, NEW_YORK,
    SAHARA, ARIZONA
}

/** Сцена со светлым фоном — тайтл и чипы поверх рисуются тёмным цветом. */
fun isJourneySceneLight(scene: JourneyScene): Boolean = when (scene) {
    JourneyScene.SANTORINI, JourneyScene.CINQUE_TERRE,
    JourneyScene.PARIS, JourneyScene.SAHARA -> true
    else -> false
}

/**
 * Простой keyword-matcher. Список ключей подобран так, чтобы покрыть и
 * латинские, и кириллические написания популярных мест. Если новых мест
 * не хватает — добавь сюда: `«ключ» in lower → JourneyScene.X`.
 */
fun selectJourneyScene(destination: String): JourneyScene {
    val l = destination.lowercase()
    fun any(vararg keys: String) = keys.any { it in l }
    return when {
        any("эльбрус", "elbrus", "азау", "терскол", "приэльбрус", "кабардин", "kabardin") -> JourneyScene.ELBRUS
        any("альп", "alp", "шамони", "chamonix", "монблан", "mont blanc", "церматт", "zermatt", "инсбрук", "innsbruck") -> JourneyScene.ALPS
        any("норвег", "norway", "тромс", "trom", "лофотен", "lofot", "исланд", "iceland", "сияни", "aurora") -> JourneyScene.NORTHERN_LIGHTS
        any("карели", "karelia", "ладог", "онежск", "янис", "финлянд", "finland", "тайга") -> JourneyScene.KARELIA
        any("бали", "bali", "чанг", "убуд", "ubud", "индонез", "indonesia", "таилан", "тайлан", "thailand", "пхукет", "phuket", "тропик") -> JourneyScene.BALI
        any("санторин", "santorini", "ия,", "крит", "crete", "родос", "rhodes", "греци", "greece", "греческ", "миконос", "mykon") -> JourneyScene.SANTORINI
        any("фарер", "faroe", "сёрво", "sorvo", "torshavn", "торсхавн") -> JourneyScene.FAROE
        any("чинкве", "cinque", "манарол", "manarol", "лигур", "liguri", "италь", "italia", "italy", "флорен", "florence", "венец", "venice", "роме", " рим ", "naples", "неапол") -> JourneyScene.CINQUE_TERRE
        any("токио", "tokyo", "япон", "japan", "осака", "osaka", "kyoto", "киото", "сибуя", "shibuya") -> JourneyScene.TOKYO
        any("стамбул", "istanbul", "анкар", "ankara", "турц", "turkey", "султанахмет", "каппадок") -> JourneyScene.ISTANBUL
        any("париж", "paris", "франц", "france", "лион", "lyon", "марсель", "marseille", "монмартр", "ницца", "nice") -> JourneyScene.PARIS
        any("нью-йорк", "нью йорк", "new york", "манхэттен", "manhattan", "сша ", " usa", "сиэтл", "seattle", "лос-анджел", "los angel", "чикаго", "chicago") -> JourneyScene.NEW_YORK
        any("сахар", "sahara", "марокк", "morocco", "пустын", "иордан", "jordan", "египет", "egypt", "оаэ", "uae", "дубай", "dubai", "тунис", "tunisia") -> JourneyScene.SAHARA
        any("аризон", "arizona", "юта", "utah", "невад", "nevada", "монумент", "monument", "каньон", "canyon", "лас-вегас", "las vegas") -> JourneyScene.ARIZONA
        else -> JourneyScene.entries[abs(l.hashCode()) % JourneyScene.entries.size]
    }
}

@Composable
fun JourneySceneBackground(scene: JourneyScene, modifier: Modifier = Modifier) {
    val spec = sceneSpecs[scene] ?: sceneSpecs.getValue(JourneyScene.ELBRUS)
    Canvas(modifier = modifier.background(spec.gradient)) {
        spec.draw(this)
    }
}

// ───────────────────── private data ─────────────────────

private data class SceneSpec(
    val gradient: Brush,
    val draw: DrawScope.() -> Unit
)

/**
 * SVG-исходник нарисован в viewBox 380×132. Нормализуем координаты сцены к
 * фактическому размеру canvas через scale-фактор по обеим осям.
 */
private inline fun DrawScope.scaled(crossinline block: (sx: Float, sy: Float) -> Unit) {
    block(size.width / 380f, size.height / 132f)
}

private fun DrawScope.path(builder: Path.() -> Unit): Path = Path().apply(builder)

private fun DrawScope.drawEllipse(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, alpha: Float = 1f) {
    drawOval(
        color = color,
        topLeft = Offset(cx - rx, cy - ry),
        size = Size(rx * 2, ry * 2),
        alpha = alpha
    )
}

// ───────────────────── scene catalog ─────────────────────

private val sceneSpecs: Map<JourneyScene, SceneSpec> = mapOf(
    JourneyScene.ELBRUS to SceneSpec(
        gradient = Brush.linearGradient(listOf(Color(0xFF34D4BE), Color(0xFF18B8A3))),
        draw = {
            scaled { sx, sy ->
                // звёзды
                drawCircle(Color.White, 1.4f * sx, Offset(220f * sx, 22f * sy), alpha = 0.7f)
                drawCircle(Color.White, 1.0f * sx, Offset(260f * sx, 36f * sy), alpha = 0.5f)
                drawCircle(Color.White, 1.2f * sx, Offset(300f * sx, 18f * sy), alpha = 0.6f)
                drawCircle(Color.White, 0.9f * sx, Offset(340f * sx, 48f * sy), alpha = 0.5f)
                // дальняя гора
                drawPath(
                    path {
                        moveTo(150f * sx, 132f * sy); lineTo(210f * sx, 50f * sy)
                        lineTo(255f * sx, 90f * sy); lineTo(310f * sx, 30f * sy)
                        lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF118A7C), alpha = 0.55f
                )
                // ближняя гора
                drawPath(
                    path {
                        moveTo(180f * sx, 132f * sy); lineTo(240f * sx, 60f * sy)
                        lineTo(280f * sx, 95f * sy); lineTo(330f * sx, 55f * sy)
                        lineTo(380f * sx, 100f * sy); lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF0F8C7D), alpha = 0.85f
                )
                // снежные шапки
                drawPath(
                    path {
                        moveTo(232f * sx, 70f * sy); lineTo(240f * sx, 60f * sy); lineTo(248f * sx, 70f * sy)
                        lineTo(244f * sx, 74f * sy); lineTo(240f * sx, 70f * sy); lineTo(236f * sx, 74f * sy); close()
                    },
                    Color.White
                )
                drawPath(
                    path {
                        moveTo(324f * sx, 64f * sy); lineTo(330f * sx, 55f * sy); lineTo(336f * sx, 64f * sy)
                        lineTo(332f * sx, 67f * sy); lineTo(330f * sx, 64f * sy); lineTo(327f * sx, 67f * sy); close()
                    },
                    Color.White
                )
            }
        }
    ),

    JourneyScene.ALPS to SceneSpec(
        gradient = Brush.linearGradient(listOf(Color(0xFF4F7CD6), Color(0xFF1F3A8A))),
        draw = {
            scaled { sx, sy ->
                // луна с тенью
                drawCircle(Color(0xFFF0F4FF), 14f * sx, Offset(290f * sx, 32f * sy), alpha = 0.85f)
                drawCircle(Color(0xFF1F3A8A), 14f * sx, Offset(298f * sx, 30f * sy))
                // дальние горы
                drawPath(
                    path {
                        moveTo(150f * sx, 132f * sy); lineTo(195f * sx, 55f * sy)
                        lineTo(225f * sx, 90f * sy); lineTo(260f * sx, 40f * sy)
                        lineTo(300f * sx, 80f * sy); lineTo(340f * sx, 35f * sy)
                        lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF2A4EA3), alpha = 0.7f
                )
                // ближние горы
                drawPath(
                    path {
                        moveTo(180f * sx, 132f * sy); lineTo(220f * sx, 70f * sy)
                        lineTo(255f * sx, 100f * sy); lineTo(290f * sx, 65f * sy)
                        lineTo(330f * sx, 95f * sy); lineTo(380f * sx, 70f * sy)
                        lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF1A2F6E)
                )
            }
        }
    ),

    JourneyScene.NORTHERN_LIGHTS to SceneSpec(
        gradient = Brush.verticalGradient(listOf(Color(0xFF0A0E2A), Color(0xFF1A2F5A))),
        draw = {
            scaled { sx, sy ->
                // звёзды
                listOf(
                    40f to 20f, 100f to 14f, 160f to 25f, 220f to 12f,
                    290f to 22f, 350f to 14f, 60f to 40f, 130f to 40f,
                    250f to 38f, 330f to 38f
                ).forEach { (cx, cy) ->
                    drawCircle(Color.White, 0.9f * sx, Offset(cx * sx, cy * sy), alpha = 0.85f)
                }
                // ленты сияния — две полупрозрачные «волны»
                val w = size.width
                val auroraGreen = Brush.verticalGradient(
                    0f to Color(0x006DFFB8), 0.4f to Color(0x8C6DFFB8), 1f to Color(0x009B6DFF),
                    startY = 50f * sy, endY = 100f * sy
                )
                val auroraBlue = Brush.verticalGradient(
                    0f to Color(0x007AD7FF), 0.5f to Color(0x807AD7FF), 1f to Color(0x007AD7FF),
                    startY = 70f * sy, endY = 110f * sy
                )
                drawPath(
                    path {
                        moveTo(0f, 50f * sy)
                        quadraticTo(95f * sx, 10f * sy, 190f * sx, 55f * sy)
                        quadraticTo(285f * sx, 100f * sy, w, 50f * sy)
                        lineTo(w, 100f * sy)
                        quadraticTo(285f * sx, 60f * sy, 190f * sx, 100f * sy)
                        quadraticTo(95f * sx, 140f * sy, 0f, 100f * sy)
                        close()
                    },
                    brush = auroraGreen
                )
                drawPath(
                    path {
                        moveTo(0f, 70f * sy)
                        quadraticTo(95f * sx, 30f * sy, 190f * sx, 75f * sy)
                        quadraticTo(285f * sx, 120f * sy, w, 70f * sy)
                        lineTo(w, 110f * sy)
                        quadraticTo(285f * sx, 80f * sy, 190f * sx, 110f * sy)
                        quadraticTo(95f * sx, 150f * sy, 0f, 110f * sy)
                        close()
                    },
                    brush = auroraBlue
                )
                // тёмные горы внизу
                drawPath(
                    path {
                        moveTo(0f, 132f * sy)
                        lineTo(40f * sx, 110f * sy); lineTo(80f * sx, 122f * sy)
                        lineTo(130f * sx, 95f * sy); lineTo(180f * sx, 118f * sy)
                        lineTo(230f * sx, 100f * sy); lineTo(290f * sx, 120f * sy)
                        lineTo(340f * sx, 105f * sy); lineTo(380f * sx, 122f * sy)
                        lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF050714)
                )
            }
        }
    ),

    JourneyScene.KARELIA to SceneSpec(
        gradient = Brush.verticalGradient(listOf(Color(0xFF2A4D3A), Color(0xFF14302A))),
        draw = {
            scaled { sx, sy ->
                drawCircle(Color(0xFFF5F5D0), 16f * sx, Offset(320f * sx, 36f * sy), alpha = 0.9f)
                drawEllipse(190f * sx, 92f * sy, 200f * sx, 14f * sy, Color.White, alpha = 0.08f)
                // дальние ёлочки
                listOf(
                    Triple(40f, 50f, 20f), Triple(70f, 82f, 24f),
                    Triple(110f, 120f, 20f), Triple(150f, 162f, 24f),
                    Triple(190f, 200f, 20f), Triple(225f, 237f, 24f),
                    Triple(270f, 280f, 20f), Triple(305f, 317f, 24f),
                    Triple(345f, 355f, 20f)
                ).forEach { (left, mid, halfWidth) ->
                    val widthPx = halfWidth.coerceAtLeast(20f)
                    drawPath(
                        path {
                            moveTo(left * sx, 132f * sy)
                            lineTo(mid * sx, (132f - widthPx * 2.6f) * sy)
                            lineTo((left + widthPx) * sx, 132f * sy)
                            close()
                        },
                        Color(0xFF1C3D2C)
                    )
                }
                // ближние ёлочки темнее
                listOf(0f to 10f, 28f to 40f, 85f to 100f, 140f to 156f, 205f to 220f, 260f to 275f, 330f to 345f).forEach { (left, mid) ->
                    drawPath(
                        path {
                            moveTo(left * sx, 132f * sy)
                            lineTo(mid * sx, (mid - left + 70f) * sy)
                            lineTo((left + 22f) * sx, 132f * sy)
                            close()
                        },
                        Color(0xFF0C2419)
                    )
                }
            }
        }
    ),

    JourneyScene.BALI to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFFF9A6B), 0.5f to Color(0xFFFF5E8A), 1f to Color(0xFF6B3A96)
        ),
        draw = {
            scaled { sx, sy ->
                // солнце
                drawCircle(Color(0xFFFFF7C2), 26f * sx, Offset(280f * sx, 62f * sy), alpha = 0.95f)
                drawCircle(Color(0xFFFFE07A), 20f * sx, Offset(280f * sx, 62f * sy))
                // вода
                drawRect(Color(0xFF7A4BA8), Offset(0f, 78f * sy), Size(size.width, 54f * sy), alpha = 0.7f)
                // блики на воде
                drawEllipse(280f * sx, 80f * sy, 32f * sx, 3f * sy, Color(0xFFFFE07A), alpha = 0.7f)
                drawEllipse(280f * sx, 92f * sy, 48f * sx, 2.5f * sy, Color(0xFFFFD3AA), alpha = 0.55f)
                drawEllipse(280f * sx, 104f * sy, 60f * sx, 2f * sy, Color(0xFFFFB38A), alpha = 0.5f)
                drawEllipse(280f * sx, 116f * sy, 78f * sx, 2f * sy, Color(0xFFFF9C8A), alpha = 0.45f)
                // ствол пальмы
                val palm = Color(0xFF1A0A26)
                drawPath(
                    path {
                        moveTo(30f * sx, 132f * sy); lineTo(34f * sx, 60f * sy)
                        lineTo(36f * sx, 60f * sy); lineTo(34f * sx, 132f * sy); close()
                    },
                    palm
                )
                // листья
                listOf(
                    listOf(34f to 60f, 18f to 55f, 8f to 70f),
                    listOf(34f to 60f, 50f to 50f, 66f to 60f),
                    listOf(34f to 60f, 28f to 48f, 22f to 38f),
                    listOf(34f to 60f, 42f to 48f, 50f to 40f)
                ).forEach { points ->
                    drawPath(
                        path {
                            moveTo(points[0].first * sx, points[0].second * sy)
                            quadraticTo(
                                points[1].first * sx, points[1].second * sy,
                                points[2].first * sx, points[2].second * sy
                            )
                            close()
                        },
                        palm
                    )
                }
            }
        }
    ),

    JourneyScene.SANTORINI to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFF82D4FF), 0.6f to Color(0xFF5AB4F0), 1f to Color(0xFF2E7FB8)
        ),
        draw = {
            scaled { sx, sy ->
                drawCircle(Color(0xFFFFE9A8), 22f * sx, Offset(320f * sx, 36f * sy), alpha = 0.6f)
                drawCircle(Color(0xFFFFF5C2), 12f * sx, Offset(320f * sx, 36f * sy))
                // белые домики
                listOf(
                    listOf(200f, 60f, 40f, 40f), listOf(240f, 68f, 32f, 32f),
                    listOf(272f, 56f, 44f, 44f), listOf(316f, 70f, 28f, 30f)
                ).forEach { rect ->
                    drawRect(
                        Color.White,
                        Offset(rect[0] * sx, rect[1] * sy),
                        Size(rect[2] * sx, rect[3] * sy)
                    )
                }
                // синие купола
                drawEllipse(220f * sx, 60f * sy, 14f * sx, 10f * sy, Color(0xFF1F6DB8))
                drawEllipse(288f * sx, 56f * sy, 16f * sx, 11f * sy, Color(0xFF1F6DB8))
                // окошки
                listOf(
                    listOf(208f, 74f, 6f, 10f), listOf(248f, 80f, 5f, 8f),
                    listOf(280f, 70f, 6f, 10f), listOf(296f, 70f, 6f, 10f),
                    listOf(324f, 82f, 5f, 8f)
                ).forEach { r ->
                    drawRect(
                        Color(0xFF1F6DB8),
                        Offset(r[0] * sx, r[1] * sy),
                        Size(r[2] * sx, r[3] * sy)
                    )
                }
                // море
                drawRect(
                    Color(0xFF1F6DB8),
                    Offset(0f, 100f * sy),
                    Size(size.width, 32f * sy)
                )
                drawPath(
                    path {
                        moveTo(0f, 100f * sy)
                        quadraticTo(50f * sx, 96f * sy, 100f * sx, 100f * sy)
                        quadraticTo(150f * sx, 104f * sy, 200f * sx, 100f * sy)
                        quadraticTo(250f * sx, 96f * sy, 300f * sx, 100f * sy)
                        quadraticTo(340f * sx, 103f * sy, size.width, 100f * sy)
                        lineTo(size.width, 132f * sy); lineTo(0f, 132f * sy); close()
                    },
                    Color(0xFF185A9C)
                )
            }
        }
    ),

    JourneyScene.FAROE to SceneSpec(
        gradient = Brush.verticalGradient(listOf(Color(0xFF6B8A9C), Color(0xFF2D4658))),
        draw = {
            scaled { sx, sy ->
                drawEllipse(80f * sx, 28f * sy, 40f * sx, 6f * sy, Color.White, alpha = 0.4f)
                drawEllipse(180f * sx, 22f * sy, 50f * sx, 5f * sy, Color.White, alpha = 0.3f)
                drawEllipse(300f * sx, 30f * sy, 45f * sx, 6f * sy, Color.White, alpha = 0.35f)
                // дальние скалы
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(0f, 75f * sy)
                        lineTo(80f * sx, 60f * sy); lineTo(130f * sx, 80f * sy)
                        lineTo(180f * sx, 65f * sy); lineTo(240f * sx, 85f * sy)
                        lineTo(290f * sx, 70f * sy); lineTo(380f * sx, 90f * sy)
                        lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF3D5566), alpha = 0.7f
                )
                // ближние скалы
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(0f, 95f * sy)
                        lineTo(70f * sx, 85f * sy); lineTo(130f * sx, 100f * sy)
                        lineTo(200f * sx, 88f * sy); lineTo(270f * sx, 102f * sy)
                        lineTo(340f * sx, 92f * sy); lineTo(380f * sx, 105f * sy)
                        lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF1F3140)
                )
                // вода
                drawRect(Color(0xFF0F1E2A), Offset(0f, 118f * sy), Size(size.width, 14f * sy))
                drawEllipse(100f * sx, 118f * sy, 40f * sx, 2f * sy, Color.White, alpha = 0.25f)
                drawEllipse(260f * sx, 120f * sy, 50f * sx, 2f * sy, Color.White, alpha = 0.2f)
            }
        }
    ),

    JourneyScene.CINQUE_TERRE to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFFFD97A), 0.5f to Color(0xFFFFB87A), 1f to Color(0xFF62C4D6)
        ),
        draw = {
            scaled { sx, sy ->
                // разноцветные домики
                val warm = Color(0xFFE85A5A)
                val red = Color(0xFFB83D3D)
                listOf(
                    listOf(180f, 55f, 14f, 20f), listOf(196f, 50f, 12f, 25f),
                    listOf(210f, 58f, 14f, 17f), listOf(226f, 48f, 16f, 27f),
                    listOf(244f, 56f, 12f, 19f), listOf(258f, 52f, 14f, 23f),
                    listOf(274f, 60f, 12f, 15f), listOf(288f, 50f, 14f, 25f)
                ).forEach { r ->
                    drawRect(
                        warm,
                        Offset(r[0] * sx, r[1] * sy),
                        Size(r[2] * sx, r[3] * sy),
                        alpha = 0.9f
                    )
                }
                // крыши
                listOf(
                    listOf(180f, 55f, 187f, 48f, 194f, 55f),
                    listOf(196f, 50f, 202f, 44f, 208f, 50f),
                    listOf(226f, 48f, 234f, 42f, 242f, 48f),
                    listOf(258f, 52f, 265f, 45f, 272f, 52f),
                    listOf(288f, 50f, 295f, 44f, 302f, 50f)
                ).forEach { p ->
                    drawPath(
                        path {
                            moveTo(p[0] * sx, p[1] * sy)
                            lineTo(p[2] * sx, p[3] * sy)
                            lineTo(p[4] * sx, p[5] * sy)
                            close()
                        },
                        red, alpha = 0.95f
                    )
                }
                // вода
                drawRect(Color(0xFF3AA5BD), Offset(0f, 75f * sy), Size(size.width, 57f * sy))
                drawEllipse(120f * sx, 90f * sy, 40f * sx, 2f * sy, Color.White, alpha = 0.3f)
                drawEllipse(260f * sx, 105f * sy, 50f * sx, 2f * sy, Color.White, alpha = 0.25f)
                drawEllipse(60f * sx, 118f * sy, 40f * sx, 2f * sy, Color.White, alpha = 0.3f)
            }
        }
    ),

    JourneyScene.TOKYO to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFF1A0E2E), 0.5f to Color(0xFF4A1454), 1f to Color(0xFFFF3D8A)
        ),
        draw = {
            scaled { sx, sy ->
                // башни — матрица пар (x, y, w, h)
                val towers = listOf(
                    intArrayOf(20, 60, 16, 72), intArrayOf(40, 50, 12, 82),
                    intArrayOf(55, 70, 20, 62), intArrayOf(78, 40, 14, 92),
                    intArrayOf(95, 65, 18, 67), intArrayOf(118, 55, 14, 77),
                    intArrayOf(135, 35, 16, 97), intArrayOf(155, 60, 12, 72),
                    intArrayOf(170, 48, 20, 84), intArrayOf(195, 58, 14, 74),
                    intArrayOf(212, 42, 18, 90), intArrayOf(234, 62, 14, 70),
                    intArrayOf(252, 50, 16, 82), intArrayOf(272, 68, 12, 64),
                    intArrayOf(288, 40, 14, 92), intArrayOf(306, 58, 18, 74),
                    intArrayOf(328, 48, 14, 84), intArrayOf(346, 62, 16, 70)
                )
                val tower = Color(0xFF0A0716)
                towers.forEach { t ->
                    drawRect(
                        tower,
                        Offset(t[0] * sx, t[1] * sy),
                        Size(t[2] * sx, t[3] * sy),
                        alpha = 0.85f
                    )
                }
                // неоновые точки — жёлтые
                val neonY = Color(0xFFFFEC5A)
                listOf(
                    24 to 68, 30 to 74, 44 to 60, 82 to 50, 86 to 58, 100 to 74,
                    140 to 44, 158 to 68, 178 to 58, 216 to 52, 220 to 62, 240 to 70,
                    294 to 50, 312 to 66, 332 to 56
                ).forEach { (x, y) ->
                    drawRect(neonY, Offset(x * sx, y * sy), Size(2f * sx, 2f * sy))
                }
                // розовые точки
                val neonP = Color(0xFFFF5AD4)
                listOf(50 to 80, 120 to 62, 200 to 68, 260 to 58, 320 to 74).forEach { (x, y) ->
                    drawRect(neonP, Offset(x * sx, y * sy), Size(2f * sx, 2f * sy))
                }
                // луна
                drawCircle(Color(0xFFFFD9E8), 14f * sx, Offset(340f * sx, 28f * sy), alpha = 0.9f)
            }
        }
    ),

    JourneyScene.ISTANBUL to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFFFB463), 0.6f to Color(0xFFFF7A52), 1f to Color(0xFF842C4A)
        ),
        draw = {
            scaled { sx, sy ->
                drawCircle(Color(0xFFFFF5C2), 22f * sx, Offset(260f * sx, 50f * sy), alpha = 0.85f)
                val mosque = Color(0xFF3A1530)
                // три минарета с иглами
                listOf(120f to 50f, 200f to 40f, 290f to 55f).forEach { (mx, my) ->
                    drawRect(mosque, Offset(mx * sx, my * sy), Size(4f * sx, (132f - my) * sy))
                    drawPath(
                        path {
                            moveTo((mx - 2f) * sx, my * sy)
                            lineTo((mx + 2f) * sx, (my - 8f) * sy)
                            lineTo((mx + 6f) * sx, my * sy)
                            close()
                        },
                        mosque
                    )
                }
                // главный купольный комплекс
                drawEllipse(160f * sx, 95f * sy, 50f * sx, 35f * sy, mosque)
                drawEllipse(160f * sx, 95f * sy, 32f * sx, 22f * sy, mosque)
                drawEllipse(105f * sx, 105f * sy, 22f * sx, 14f * sy, mosque)
                drawEllipse(220f * sx, 105f * sy, 22f * sx, 14f * sy, mosque)
                drawRect(mosque, Offset(80f * sx, 105f * sy), Size(160f * sx, 27f * sy))
                drawEllipse(320f * sx, 108f * sy, 30f * sx, 20f * sy, mosque)
                drawRect(mosque, Offset(290f * sx, 108f * sy), Size(60f * sx, 24f * sy))
                // отражение в воде
                drawRect(Color(0xFF2A0E1F), Offset(0f, 120f * sy), Size(size.width, 12f * sy))
                drawEllipse(200f * sx, 124f * sy, 180f * sx, 2f * sy, Color.White, alpha = 0.2f)
            }
        }
    ),

    JourneyScene.PARIS to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFF5D6E0), 0.6f to Color(0xFFE8A5B8), 1f to Color(0xFFB87A9E)
        ),
        draw = {
            scaled { sx, sy ->
                // османовские здания
                val haussmann = Color(0xFF7C4F6B)
                listOf(
                    listOf(30f, 65f, 50f, 67f), listOf(80f, 72f, 40f, 60f),
                    listOf(120f, 60f, 60f, 72f), listOf(180f, 68f, 40f, 64f),
                    listOf(270f, 60f, 50f, 72f), listOf(320f, 72f, 40f, 60f)
                ).forEach { r ->
                    drawRect(
                        haussmann,
                        Offset(r[0] * sx, r[1] * sy),
                        Size(r[2] * sx, r[3] * sy),
                        alpha = 0.55f
                    )
                }
                // мансардные крыши
                listOf(
                    listOf(30f, 65f, 55f, 52f, 80f, 65f),
                    listOf(120f, 60f, 150f, 48f, 180f, 60f),
                    listOf(270f, 60f, 295f, 50f, 320f, 60f)
                ).forEach { p ->
                    drawPath(
                        path {
                            moveTo(p[0] * sx, p[1] * sy)
                            lineTo(p[2] * sx, p[3] * sy)
                            lineTo(p[4] * sx, p[5] * sy)
                            close()
                        },
                        haussmann, alpha = 0.55f
                    )
                }
                // Эйфелева башня
                val eiffel = Color(0xFF3A1D2E)
                drawPath(
                    path {
                        moveTo(225f * sx, 132f * sy); lineTo(232f * sx, 95f * sy)
                        lineTo(228f * sx, 95f * sy); lineTo(235f * sx, 132f * sy); close()
                    },
                    eiffel, alpha = 0.9f
                )
                drawPath(
                    path {
                        moveTo(232f * sx, 95f * sy); lineTo(238f * sx, 60f * sy)
                        lineTo(242f * sx, 60f * sy); lineTo(248f * sx, 95f * sy)
                        lineTo(246f * sx, 95f * sy); lineTo(243f * sx, 75f * sy)
                        lineTo(237f * sx, 75f * sy); lineTo(234f * sx, 95f * sy); close()
                    },
                    eiffel, alpha = 0.9f
                )
                drawPath(
                    path {
                        moveTo(238f * sx, 60f * sy); lineTo(240f * sx, 35f * sy)
                        lineTo(242f * sx, 60f * sy); close()
                    },
                    eiffel, alpha = 0.9f
                )
                drawRect(eiffel, Offset(240f * sx, 20f * sy), Size(2f * sx, 18f * sy), alpha = 0.9f)
                drawRect(eiffel, Offset(226f * sx, 92f * sy), Size(28f * sx, 2f * sy), alpha = 0.9f)
                drawRect(eiffel, Offset(231f * sx, 78f * sy), Size(18f * sx, 2f * sy), alpha = 0.9f)
            }
        }
    ),

    JourneyScene.NEW_YORK to SceneSpec(
        gradient = Brush.verticalGradient(listOf(Color(0xFF2A3142), Color(0xFF1A1F2E))),
        draw = {
            scaled { sx, sy ->
                val bldg = Color(0xFF0D1018)
                val skyline = listOf(
                    intArrayOf(20, 55, 22, 77), intArrayOf(44, 40, 18, 92),
                    intArrayOf(64, 60, 14, 72), intArrayOf(80, 32, 20, 100),
                    intArrayOf(102, 50, 18, 82), intArrayOf(122, 20, 16, 112),
                    intArrayOf(140, 48, 22, 84), intArrayOf(164, 38, 14, 94),
                    intArrayOf(180, 55, 24, 77), intArrayOf(206, 28, 18, 104),
                    intArrayOf(226, 50, 14, 82), intArrayOf(242, 35, 20, 97),
                    intArrayOf(264, 58, 16, 74), intArrayOf(282, 45, 22, 87),
                    intArrayOf(306, 22, 16, 110), intArrayOf(324, 50, 18, 82),
                    intArrayOf(344, 38, 20, 94)
                )
                skyline.forEach { b ->
                    drawRect(
                        bldg,
                        Offset(b[0] * sx, b[1] * sy),
                        Size(b[2] * sx, b[3] * sy)
                    )
                }
                // окна
                val win = Color(0xFFFFD97A)
                listOf(
                    24 to 60, 32 to 60, 24 to 72, 32 to 78,
                    48 to 50, 56 to 58, 48 to 70, 56 to 80,
                    84 to 42, 92 to 50, 84 to 62, 92 to 74,
                    106 to 58, 114 to 68, 126 to 30, 132 to 42,
                    126 to 58, 132 to 74, 148 to 56, 156 to 68,
                    184 to 62, 194 to 72, 210 to 38, 218 to 52,
                    210 to 66, 218 to 80, 246 to 42, 254 to 56,
                    246 to 70, 254 to 84, 286 to 52, 296 to 66,
                    310 to 32, 316 to 46, 310 to 62, 316 to 80,
                    348 to 46, 356 to 60
                ).forEach { (x, y) ->
                    drawRect(win, Offset(x * sx, y * sy), Size(3f * sx, 3f * sy), alpha = 0.85f)
                }
            }
        }
    ),

    JourneyScene.SAHARA to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFFFD082), 0.6f to Color(0xFFF5A85E), 1f to Color(0xFFCC6F3D)
        ),
        draw = {
            scaled { sx, sy ->
                drawCircle(Color(0xFFFFF7D0), 20f * sx, Offset(60f * sx, 40f * sy), alpha = 0.9f)
                // дальние дюны
                drawPath(
                    path {
                        moveTo(0f, 132f * sy)
                        quadraticTo(100f * sx, 70f * sy, 200f * sx, 95f * sy)
                        quadraticTo(300f * sx, 120f * sy, size.width, 80f * sy)
                        lineTo(size.width, 132f * sy); close()
                    },
                    Color(0xFFD68945), alpha = 0.7f
                )
                // ближние дюны
                drawPath(
                    path {
                        moveTo(0f, 132f * sy)
                        quadraticTo(80f * sx, 95f * sy, 180f * sx, 110f * sy)
                        quadraticTo(280f * sx, 125f * sy, size.width, 100f * sy)
                        lineTo(size.width, 132f * sy); close()
                    },
                    Color(0xFFA85C2A)
                )
                // силуэт верблюда (упрощённо — две овальные тушки на ножках)
                val camel = Color(0xFF3A1D0E)
                drawEllipse(258f * sx, 110f * sy, 12f * sx, 5f * sy, camel)
                drawRect(camel, Offset(252f * sx, 112f * sy), Size(2f * sx, 12f * sy))
                drawRect(camel, Offset(266f * sx, 112f * sy), Size(2f * sx, 12f * sy))
                drawCircle(camel, 3f * sx, Offset(270f * sx, 105f * sy))
                drawRect(camel, Offset(270f * sx, 102f * sy), Size(2f * sx, 5f * sy))
                drawEllipse(285f * sx, 113f * sy, 10f * sx, 4f * sy, camel)
                drawRect(camel, Offset(280f * sx, 115f * sy), Size(2f * sx, 8f * sy))
                drawRect(camel, Offset(290f * sx, 115f * sy), Size(2f * sx, 8f * sy))
            }
        }
    ),

    JourneyScene.ARIZONA to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFFF8A5C), 0.5f to Color(0xFFC84A3D), 1f to Color(0xFF5A1F2E)
        ),
        draw = {
            scaled { sx, sy ->
                drawCircle(Color(0xFFFFE67A), 18f * sx, Offset(320f * sx, 50f * sy), alpha = 0.95f)
                val mesa = Color(0xFF3D1018)
                // плато 1
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(0f, 100f * sy)
                        lineTo(40f * sx, 100f * sy); lineTo(48f * sx, 75f * sy)
                        lineTo(100f * sx, 75f * sy); lineTo(108f * sx, 100f * sy)
                        lineTo(150f * sx, 100f * sy); lineTo(150f * sx, 132f * sy); close()
                    },
                    mesa
                )
                // плато 2
                drawPath(
                    path {
                        moveTo(170f * sx, 132f * sy); lineTo(170f * sx, 95f * sy)
                        lineTo(210f * sx, 95f * sy); lineTo(218f * sx, 70f * sy)
                        lineTo(270f * sx, 70f * sy); lineTo(278f * sx, 95f * sy)
                        lineTo(300f * sx, 95f * sy); lineTo(300f * sx, 132f * sy); close()
                    },
                    mesa
                )
                // плато 3
                drawPath(
                    path {
                        moveTo(300f * sx, 132f * sy); lineTo(300f * sx, 90f * sy)
                        lineTo(340f * sx, 90f * sy); lineTo(348f * sx, 80f * sy)
                        lineTo(380f * sx, 80f * sy); lineTo(380f * sx, 132f * sy); close()
                    },
                    mesa
                )
                // песок
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(size.width, 132f * sy)
                        lineTo(size.width, 115f * sy)
                        quadraticTo(280f * sx, 105f * sy, 190f * sx, 118f * sy)
                        quadraticTo(95f * sx, 130f * sy, 0f, 115f * sy)
                        close()
                    },
                    Color(0xFF8A3A35)
                )
            }
        }
    )
)
