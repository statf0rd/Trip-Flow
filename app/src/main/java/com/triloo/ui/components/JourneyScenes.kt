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
    SAHARA, ARIZONA,
    // ── расширение, gallery v2 ──
    PATAGONIA, ICELAND, FJORD, HALONG,
    LAVENDER, SAKURA, CAPPADOCIA, MARRAKESH,
    SERENGETI, ANTARCTICA,
    MALDIVES, HAVANA,
    MACHU_PICCHU, PRAGUE, VENICE, LONDON,
    GRAND_CANYON, VOLCANO, MATTERHORN, BAIKAL,
    DUBAI, RIO, HAWAII, SEYCHELLES
}

/** Сцена со светлым фоном — тайтл и чипы поверх рисуются тёмным цветом. */
fun isJourneySceneLight(scene: JourneyScene): Boolean = when (scene) {
    JourneyScene.SANTORINI, JourneyScene.CINQUE_TERRE,
    JourneyScene.PARIS, JourneyScene.SAHARA,
    JourneyScene.LAVENDER, JourneyScene.SAKURA,
    JourneyScene.MARRAKESH, JourneyScene.ANTARCTICA,
    JourneyScene.HAVANA, JourneyScene.PRAGUE,
    JourneyScene.VENICE, JourneyScene.GRAND_CANYON,
    JourneyScene.BAIKAL, JourneyScene.HAWAII,
    JourneyScene.SEYCHELLES -> true
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
        // Точные ключи проверяем раньше широких: «каппадок» должно дать
        // CAPPADOCIA, а не ISTANBUL по «турц», поэтому новые сцены идут
        // выше старых там, где есть конфликт.
        any("каппадок", "cappadoc", "гёреме", "goreme") -> JourneyScene.CAPPADOCIA
        any("марракеш", "marrakech", "marrakesh", "касабланк", "casablanca", "феc ", "fez ", "медина", "medina") -> JourneyScene.MARRAKESH
        any("дубай", "dubai", "оаэ", "uae", "абу-даби", "abu dhabi", "доха", "doha") -> JourneyScene.DUBAI
        any("камчат", "kamchatka", "ключев", "klyuchev", "вулкан", "volcano", "этна", "etna", "стромбол", "stromboli") -> JourneyScene.VOLCANO
        any("байкал", "baikal", "ольхон", "olkhon") -> JourneyScene.BAIKAL
        any("мачу", "machu", "пикчу", "picchu", "куско", "cuzco", "cusco", "перу", "peru", "анды", "andes") -> JourneyScene.MACHU_PICCHU
        any("прага", "prague", "praha", "чехи", "czech", "брно", "brno", "будапешт", "budapest") -> JourneyScene.PRAGUE
        any("венец", "venice", "venezia", "веронa", "verona", "гранд-канал", "grand canal") -> JourneyScene.VENICE
        any("лондон", "london", "тауэр", "tower bridge", "темз", "thames", "англи", "england", "british") -> JourneyScene.LONDON
        any("гранд-каньон", "grand canyon", "колорадо", "colorado", "антилоп", "antelope") -> JourneyScene.GRAND_CANYON
        any("маттер", "matterhorn", "церматт", "zermatt", "халльш", "hallstatt", "альпийск", "alpine") -> JourneyScene.MATTERHORN
        any("патагон", "patagonia", "торрес", "torres", "чили", "chile", "огненная земля") -> JourneyScene.PATAGONIA
        any("исланд", "iceland", "йёкюль", "jokul", "рейкьявик", "reykjavik", "ледник", "glacier", "diamond beach") -> JourneyScene.ICELAND
        any("фьорд", "fjord", "гейрангер", "geiranger", "берген", "bergen") -> JourneyScene.FJORD
        any("халонг", "halong", "ha long", "вьетнам", "vietnam") -> JourneyScene.HALONG
        any("прованс", "provence", "лаванд", "lavender", "валансол", "valensole") -> JourneyScene.LAVENDER
        any("сакур", "sakura", "kyoto", "киото", "ханами", "hanami") -> JourneyScene.SAKURA
        any("серенгети", "serengeti", "танзани", "tanzania", "кения", "kenya", "сафари", "safari", "нгоронгоро", "ngorongoro") -> JourneyScene.SERENGETI
        any("антаркт", "antarctic", "южный полюс", "south pole") -> JourneyScene.ANTARCTICA
        any("мальдив", "maldive", "атолл", "atoll") -> JourneyScene.MALDIVES
        any("гаван", "havana", "куба", "cuba") -> JourneyScene.HAVANA
        any("рио", "rio", "копакабан", "copacaban", "браз", "brazil", "сан-паулу", "sao paulo") -> JourneyScene.RIO
        any("гавай", "hawaii", "оаху", "oahu", "вайкики", "waikiki", "гонолул", "honolulu", "мауи", "maui") -> JourneyScene.HAWAII
        any("сейшел", "seychelles", "маврик", "mauritius", "праслин", "praslin", "ла-диг", "la digue") -> JourneyScene.SEYCHELLES
        // ── существующие сцены ──
        any("эльбрус", "elbrus", "азау", "терскол", "приэльбрус", "кабардин", "kabardin") -> JourneyScene.ELBRUS
        any("альп", "alp", "шамони", "chamonix", "монблан", "mont blanc", "инсбрук", "innsbruck") -> JourneyScene.ALPS
        any("норвег", "norway", "тромс", "trom", "лофотен", "lofot", "сияни", "aurora") -> JourneyScene.NORTHERN_LIGHTS
        any("карели", "karelia", "ладог", "онежск", "янис", "финлянд", "finland", "тайга") -> JourneyScene.KARELIA
        any("бали", "bali", "чанг", "убуд", "ubud", "индонез", "indonesia", "таилан", "тайлан", "thailand", "пхукет", "phuket", "тропик") -> JourneyScene.BALI
        any("санторин", "santorini", "ия,", "крит", "crete", "родос", "rhodes", "греци", "greece", "греческ", "миконос", "mykon") -> JourneyScene.SANTORINI
        any("фарер", "faroe", "сёрво", "sorvo", "torshavn", "торсхавн") -> JourneyScene.FAROE
        any("чинкве", "cinque", "манарол", "manarol", "лигур", "liguri", "италь", "italia", "italy", "флорен", "florence", "роме", " рим ", "naples", "неапол") -> JourneyScene.CINQUE_TERRE
        any("токио", "tokyo", "япон", "japan", "осака", "osaka", "сибуя", "shibuya") -> JourneyScene.TOKYO
        any("стамбул", "istanbul", "анкар", "ankara", "турц", "turkey", "султанахмет") -> JourneyScene.ISTANBUL
        any("париж", "paris", "франц", "france", "лион", "lyon", "марсель", "marseille", "монмартр", "ницца", "nice") -> JourneyScene.PARIS
        any("нью-йорк", "нью йорк", "new york", "манхэттен", "manhattan", "сша ", " usa", "сиэтл", "seattle", "лос-анджел", "los angel", "чикаго", "chicago") -> JourneyScene.NEW_YORK
        any("сахар", "sahara", "марокк", "morocco", "пустын", "иордан", "jordan", "египет", "egypt", "тунис", "tunisia") -> JourneyScene.SAHARA
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
    ),

    // ───────────────────── gallery v2 ─────────────────────

    JourneyScene.PATAGONIA to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFF5B8C4), 0.55f to Color(0xFF8A9CC8), 1f to Color(0xFF2A3A5C)
        ),
        draw = {
            scaled { sx, sy ->
                drawCircle(Color(0xFFFFF5D8), 14f * sx, Offset(60f * sx, 28f * sy), alpha = 0.8f)
                // дальние снежные пики
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(0f, 88f * sy)
                        lineTo(50f * sx, 60f * sy); lineTo(100f * sx, 80f * sy)
                        lineTo(130f * sx, 50f * sy); lineTo(180f * sx, 75f * sy)
                        lineTo(220f * sx, 45f * sy); lineTo(260f * sx, 70f * sy)
                        lineTo(300f * sx, 55f * sy); lineTo(340f * sx, 75f * sy)
                        lineTo(380f * sx, 60f * sy); lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFFE8D4DC), alpha = 0.55f
                )
                // средний хребет
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(20f * sx, 95f * sy)
                        lineTo(70f * sx, 70f * sy); lineTo(110f * sx, 85f * sy)
                        lineTo(160f * sx, 65f * sy); lineTo(210f * sx, 80f * sy)
                        lineTo(260f * sx, 60f * sy); lineTo(320f * sx, 78f * sy)
                        lineTo(380f * sx, 70f * sy); lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF5A6EA0), alpha = 0.85f
                )
                // гранитные иглы Torres
                val granite = Color(0xFF1A0E1A)
                drawPath(
                    path {
                        moveTo(120f * sx, 132f * sy); lineTo(128f * sx, 70f * sy)
                        lineTo(135f * sx, 86f * sy); lineTo(141f * sx, 60f * sy)
                        lineTo(148f * sx, 78f * sy); lineTo(154f * sx, 55f * sy)
                        lineTo(161f * sx, 80f * sy); lineTo(168f * sx, 70f * sy)
                        lineTo(174f * sx, 132f * sy); close()
                    },
                    granite
                )
                drawPath(
                    path {
                        moveTo(180f * sx, 132f * sy); lineTo(188f * sx, 76f * sy)
                        lineTo(194f * sx, 92f * sy); lineTo(200f * sx, 62f * sy)
                        lineTo(208f * sx, 84f * sy); lineTo(214f * sx, 70f * sy)
                        lineTo(222f * sx, 132f * sy); close()
                    },
                    granite
                )
                drawPath(
                    path {
                        moveTo(228f * sx, 132f * sy); lineTo(236f * sx, 78f * sy)
                        lineTo(242f * sx, 68f * sy); lineTo(248f * sx, 84f * sy)
                        lineTo(254f * sx, 58f * sy); lineTo(262f * sx, 80f * sy)
                        lineTo(270f * sx, 132f * sy); close()
                    },
                    granite
                )
                // снежные шапки на иглах
                drawPath(
                    path {
                        moveTo(147f * sx, 76f * sy); lineTo(154f * sx, 55f * sy); lineTo(161f * sx, 76f * sy)
                        lineTo(156f * sx, 78f * sy); lineTo(154f * sx, 72f * sy); lineTo(151f * sx, 78f * sy); close()
                    },
                    Color.White, alpha = 0.9f
                )
                drawPath(
                    path {
                        moveTo(198f * sx, 66f * sy); lineTo(200f * sx, 62f * sy)
                        lineTo(204f * sx, 68f * sy); lineTo(201f * sx, 70f * sy); close()
                    },
                    Color.White, alpha = 0.85f
                )
                drawPath(
                    path {
                        moveTo(252f * sx, 62f * sy); lineTo(254f * sx, 58f * sy)
                        lineTo(258f * sx, 64f * sy); lineTo(255f * sx, 66f * sy); close()
                    },
                    Color.White, alpha = 0.85f
                )
                // ледниковое озеро
                drawRect(Color(0xFF3A587E), Offset(0f, 118f * sy), Size(size.width, 14f * sy))
                drawEllipse(120f * sx, 121f * sy, 60f * sx, 1.6f * sy, Color.White, alpha = 0.35f)
                drawEllipse(280f * sx, 125f * sy, 80f * sx, 1.6f * sy, Color.White, alpha = 0.3f)
            }
        }
    ),

    JourneyScene.ICELAND to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFB8C8D4), 0.5f to Color(0xFF6A7A8C), 1f to Color(0xFF1A2028)
        ),
        draw = {
            scaled { sx, sy ->
                // низкая облачность
                drawEllipse(120f * sx, 22f * sy, 80f * sx, 6f * sy, Color.White, alpha = 0.3f)
                drawEllipse(290f * sx, 28f * sy, 70f * sx, 5f * sy, Color.White, alpha = 0.25f)
                // дальний ледник
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(0f, 75f * sy)
                        lineTo(40f * sx, 68f * sy); lineTo(80f * sx, 72f * sy)
                        lineTo(130f * sx, 62f * sy); lineTo(180f * sx, 70f * sy)
                        lineTo(240f * sx, 60f * sy); lineTo(290f * sx, 68f * sy)
                        lineTo(340f * sx, 62f * sy); lineTo(380f * sx, 70f * sy)
                        lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFFDCE4EC), alpha = 0.9f
                )
                // трещины ледника
                val crevasse = Color(0xFF8A9AAC)
                drawPath(
                    path {
                        moveTo(50f * sx, 78f * sy); lineTo(80f * sx, 92f * sy)
                        lineTo(110f * sx, 80f * sy); lineTo(100f * sx, 86f * sy)
                        lineTo(78f * sx, 96f * sy); lineTo(52f * sx, 84f * sy); close()
                    },
                    crevasse, alpha = 0.55f
                )
                drawPath(
                    path {
                        moveTo(200f * sx, 78f * sy); lineTo(235f * sx, 94f * sy)
                        lineTo(280f * sx, 80f * sy); lineTo(262f * sx, 88f * sy)
                        lineTo(232f * sx, 98f * sy); lineTo(202f * sx, 86f * sy); close()
                    },
                    crevasse, alpha = 0.55f
                )
                drawPath(
                    path {
                        moveTo(308f * sx, 76f * sy); lineTo(335f * sx, 88f * sy)
                        lineTo(360f * sx, 78f * sy); lineTo(340f * sx, 84f * sy)
                        lineTo(318f * sx, 90f * sy); close()
                    },
                    crevasse, alpha = 0.55f
                )
                // чёрный песок
                drawRect(Color(0xFF0A0D12), Offset(0f, 100f * sy), Size(size.width, 32f * sy))
                drawEllipse(200f * sx, 102f * sy, 180f * sx, 1f * sy, Color.White, alpha = 0.15f)
                // ледяные «бриллианты»
                listOf(
                    50f to 110f, 120f to 116f, 210f to 112f, 298f to 117f
                ).forEachIndexed { idx, (bx, by) ->
                    drawPath(
                        path {
                            moveTo(bx * sx, by * sy)
                            lineTo((bx + 10f) * sx, (by - 6f) * sy)
                            lineTo((bx + 18f) * sx, (by + 2f) * sy)
                            lineTo((bx + 12f) * sx, (by + 12f) * sy)
                            lineTo((bx + 2f) * sx, (by + 10f) * sy)
                            close()
                        },
                        Color(0xFFA8E0F0), alpha = 0.95f
                    )
                    drawPath(
                        path {
                            moveTo((bx + 2f) * sx, (by - 2f) * sy)
                            lineTo((bx + 10f) * sx, (by - 6f) * sy)
                            lineTo((bx + 14f) * sx, (by) * sy)
                            lineTo((bx + 10f) * sx, (by + 4f) * sy)
                            close()
                        },
                        Color.White, alpha = 0.75f
                    )
                    // подавим неиспользуемую переменную
                    @Suppress("UNUSED_EXPRESSION") idx
                }
            }
        }
    ),

    JourneyScene.FJORD to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFCAD4DC), 0.5f to Color(0xFF5A8298), 1f to Color(0xFF1A2832)
        ),
        draw = {
            scaled { sx, sy ->
                // дальний пик
                drawPath(
                    path {
                        moveTo(150f * sx, 132f * sy); lineTo(180f * sx, 50f * sy)
                        lineTo(210f * sx, 80f * sy); lineTo(230f * sx, 132f * sy); close()
                    },
                    Color(0xFF5A7488), alpha = 0.7f
                )
                drawPath(
                    path {
                        moveTo(165f * sx, 132f * sy); lineTo(195f * sx, 35f * sy)
                        lineTo(225f * sx, 132f * sy); close()
                    },
                    Color(0xFF3A5468), alpha = 0.85f
                )
                // снег на пике
                drawPath(
                    path {
                        moveTo(191f * sx, 50f * sy); lineTo(195f * sx, 35f * sy)
                        lineTo(200f * sx, 50f * sy); lineTo(197f * sx, 53f * sy)
                        lineTo(195f * sx, 47f * sy); lineTo(193f * sx, 53f * sy); close()
                    },
                    Color.White
                )
                // левая скала
                val cliff = Color(0xFF1C2A36)
                drawPath(
                    path {
                        moveTo(0f, 0f); lineTo(0f, 132f * sy)
                        lineTo(130f * sx, 132f * sy); lineTo(120f * sx, 118f * sy)
                        lineTo(135f * sx, 95f * sy); lineTo(115f * sx, 72f * sy)
                        lineTo(130f * sx, 48f * sy); lineTo(100f * sx, 30f * sy)
                        lineTo(95f * sx, 0f); close()
                    },
                    cliff
                )
                drawPath(
                    path {
                        moveTo(0f, 0f); lineTo(60f * sx, 0f)
                        lineTo(80f * sx, 30f * sy); lineTo(55f * sx, 55f * sy)
                        lineTo(75f * sx, 80f * sy); lineTo(40f * sx, 105f * sy)
                        lineTo(55f * sx, 132f * sy); lineTo(0f, 132f * sy); close()
                    },
                    Color(0xFF2A3B48), alpha = 0.85f
                )
                // правая скала
                drawPath(
                    path {
                        moveTo(380f * sx, 0f); lineTo(380f * sx, 132f * sy)
                        lineTo(250f * sx, 132f * sy); lineTo(260f * sx, 118f * sy)
                        lineTo(245f * sx, 95f * sy); lineTo(265f * sx, 72f * sy)
                        lineTo(250f * sx, 48f * sy); lineTo(280f * sx, 30f * sy)
                        lineTo(285f * sx, 0f); close()
                    },
                    cliff
                )
                drawPath(
                    path {
                        moveTo(380f * sx, 0f); lineTo(320f * sx, 0f)
                        lineTo(300f * sx, 30f * sy); lineTo(325f * sx, 55f * sy)
                        lineTo(305f * sx, 80f * sy); lineTo(340f * sx, 105f * sy)
                        lineTo(325f * sx, 132f * sy); lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF2A3B48), alpha = 0.85f
                )
                // водопад
                drawRect(Color.White, Offset(118f * sx, 48f * sy), Size(2f * sx, 70f * sy), alpha = 0.75f)
                drawEllipse(119f * sx, 118f * sy, 6f * sx, 2f * sy, Color.White, alpha = 0.6f)
                // вода
                drawRect(Color(0xFF0A1620), Offset(130f * sx, 118f * sy), Size(120f * sx, 14f * sy))
                drawEllipse(190f * sx, 122f * sy, 48f * sx, 1.4f * sy, Color.White, alpha = 0.3f)
                // лодочка
                drawPath(
                    path {
                        moveTo(186f * sx, 116f * sy); lineTo(202f * sx, 116f * sy)
                        lineTo(199f * sx, 119f * sy); lineTo(189f * sx, 119f * sy); close()
                    },
                    Color.White, alpha = 0.9f
                )
                drawRect(Color.White, Offset(193f * sx, 111f * sy), Size(1.5f * sx, 5f * sy), alpha = 0.9f)
            }
        }
    ),

    JourneyScene.HALONG to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFCAD8D0), 0.55f to Color(0xFF82A89A), 1f to Color(0xFF2A4A4A)
        ),
        draw = {
            scaled { sx, sy ->
                // туман
                drawEllipse(100f * sx, 50f * sy, 120f * sx, 8f * sy, Color.White, alpha = 0.35f)
                drawEllipse(300f * sx, 55f * sy, 110f * sx, 6f * sy, Color.White, alpha = 0.3f)
                // дальние карсты
                val farKarst = Color(0xFFA8C4BA)
                listOf(
                    Triple(30f, 50f, 72f), Triple(95f, 118f, 142f),
                    Triple(170f, 195f, 220f), Triple(250f, 278f, 305f),
                    Triple(320f, 342f, 364f)
                ).forEachIndexed { i, t ->
                    val peakY = if (i == 0) 60f else if (i == 1) 50f else if (i == 2) 65f else if (i == 3) 55f else 70f
                    drawPath(
                        path {
                            moveTo(t.first * sx, 132f * sy)
                            quadraticTo(t.second * sx, peakY * sy, t.third * sx, 132f * sy)
                            close()
                        },
                        farKarst, alpha = 0.5f
                    )
                }
                // средние карсты
                val midKarst = Color(0xFF4A7A6E)
                listOf(
                    Triple(0f, 25f, 55f) to 60f, Triple(75f, 108f, 138f) to 48f,
                    Triple(165f, 192f, 218f) to 62f, Triple(250f, 285f, 318f) to 52f
                ).forEach { (t, peakY) ->
                    drawPath(
                        path {
                            moveTo(t.first * sx, 132f * sy)
                            quadraticTo(t.second * sx, peakY * sy, t.third * sx, 132f * sy)
                            close()
                        },
                        midKarst, alpha = 0.85f
                    )
                }
                // ближние карсты
                val nearKarst = Color(0xFF1C3832)
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(0f, 110f * sy)
                        quadraticTo(18f * sx, 75f * sy, 38f * sx, 110f * sy)
                        lineTo(42f * sx, 132f * sy); close()
                    },
                    nearKarst
                )
                drawPath(
                    path {
                        moveTo(285f * sx, 132f * sy)
                        quadraticTo(310f * sx, 70f * sy, 340f * sx, 132f * sy)
                        close()
                    },
                    nearKarst
                )
                // вода
                drawRect(Color(0xFF173834), Offset(0f, 116f * sy), Size(size.width, 16f * sy))
                drawEllipse(200f * sx, 120f * sy, 180f * sx, 1.6f * sy, Color.White, alpha = 0.22f)
                drawEllipse(120f * sx, 126f * sy, 50f * sx, 1.2f * sy, Color.White, alpha = 0.2f)
                // джонка
                val junk = Color(0xFF1A0A0A)
                drawPath(
                    path {
                        moveTo(142f * sx, 113f * sy); lineTo(172f * sx, 113f * sy)
                        lineTo(168f * sx, 119f * sy); lineTo(146f * sx, 119f * sy); close()
                    },
                    junk
                )
                drawRect(junk, Offset(154f * sx, 98f * sy), Size(2.5f * sx, 15f * sy))
                drawPath(
                    path {
                        moveTo(156f * sx, 99f * sy); lineTo(172f * sx, 104f * sy)
                        lineTo(156f * sx, 109f * sy); close()
                    },
                    junk
                )
                drawRect(junk, Offset(148f * sx, 101f * sy), Size(2f * sx, 12f * sy))
                drawPath(
                    path {
                        moveTo(150f * sx, 102f * sy); lineTo(162f * sx, 105f * sy)
                        lineTo(150f * sx, 108f * sy); close()
                    },
                    junk
                )
            }
        }
    ),

    JourneyScene.LAVENDER to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFFFE2B0), 0.5f to Color(0xFFD8A8C8), 1f to Color(0xFF6A4A8C)
        ),
        draw = {
            scaled { sx, sy ->
                drawCircle(Color(0xFFFFF5D0), 18f * sx, Offset(70f * sx, 28f * sy), alpha = 0.7f)
                drawCircle(Color.White, 9f * sx, Offset(70f * sx, 28f * sy))
                // дальний хребет
                drawPath(
                    path {
                        moveTo(0f, 75f * sy); lineTo(60f * sx, 65f * sy)
                        lineTo(120f * sx, 72f * sy); lineTo(200f * sx, 60f * sy)
                        lineTo(280f * sx, 70f * sy); lineTo(380f * sx, 62f * sy)
                        lineTo(380f * sx, 80f * sy); lineTo(0f, 80f * sy); close()
                    },
                    Color(0xFF9A78A8), alpha = 0.5f
                )
                // полосы лаванды от светлой к тёмной
                val rows = listOf(
                    Pair(80f, 82f) to Pair(Color(0xFF9C7EB4), 0.55f),
                    Pair(85f, 89f) to Pair(Color(0xFF8C6EA8), 0.65f),
                    Pair(92f, 97f) to Pair(Color(0xFF7E5E9C), 0.7f),
                    Pair(100f, 106f) to Pair(Color(0xFF704E8E), 0.78f),
                    Pair(109f, 116f) to Pair(Color(0xFF5E3E80), 0.86f),
                    Pair(119f, 128f) to Pair(Color(0xFF4E2E70), 0.94f)
                )
                rows.forEach { (yRange, style) ->
                    drawRect(
                        style.first,
                        Offset(0f, yRange.first * sy),
                        Size(size.width, (yRange.second - yRange.first) * sy),
                        alpha = style.second
                    )
                }
                drawPath(
                    path {
                        moveTo(0f, 130f * sy); lineTo(size.width, 132f * sy)
                        lineTo(0f, 132f * sy); close()
                    },
                    Color(0xFF3E1F60)
                )
                // фермерский домик и кипарисы
                val farm = Color(0xFF3A1C2A)
                drawRect(farm, Offset(240f * sx, 55f * sy), Size(44f * sx, 22f * sy))
                drawPath(
                    path {
                        moveTo(236f * sx, 55f * sy); lineTo(262f * sx, 42f * sy)
                        lineTo(288f * sx, 55f * sy); close()
                    },
                    farm
                )
                drawRect(Color(0xFF1A0A18), Offset(254f * sx, 65f * sy), Size(6f * sx, 12f * sy))
                drawRect(Color(0xFFFFD082), Offset(266f * sx, 60f * sy), Size(5f * sx, 6f * sy))
                // кипарисы
                drawEllipse(224f * sx, 60f * sy, 4f * sx, 16f * sy, farm)
                drawEllipse(294f * sx, 58f * sy, 4f * sx, 18f * sy, farm)
                drawEllipse(304f * sx, 64f * sy, 3.5f * sx, 12f * sy, farm)
            }
        }
    ),

    JourneyScene.SAKURA to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFFFE4EC), 0.5f to Color(0xFFFFBCD2), 1f to Color(0xFFC884A4)
        ),
        draw = {
            scaled { sx, sy ->
                // дальние холмы
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(40f * sx, 95f * sy)
                        lineTo(90f * sx, 105f * sy); lineTo(160f * sx, 82f * sy)
                        lineTo(220f * sx, 100f * sy); lineTo(280f * sx, 88f * sy)
                        lineTo(340f * sx, 105f * sy); lineTo(380f * sx, 96f * sy)
                        lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFFA05A82), alpha = 0.42f
                )
                // пагода 5-ярусная
                val pagoda = Color(0xFF4A1428)
                drawRect(pagoda, Offset(58f * sx, 100f * sy), Size(40f * sx, 22f * sy))
                drawPath(
                    path {
                        moveTo(50f * sx, 100f * sy); lineTo(78f * sx, 90f * sy)
                        lineTo(106f * sx, 100f * sy); close()
                    },
                    pagoda
                )
                drawRect(pagoda, Offset(62f * sx, 84f * sy), Size(32f * sx, 14f * sy))
                drawPath(
                    path {
                        moveTo(54f * sx, 84f * sy); lineTo(78f * sx, 75f * sy)
                        lineTo(102f * sx, 84f * sy); close()
                    },
                    pagoda
                )
                drawRect(pagoda, Offset(64f * sx, 70f * sy), Size(28f * sx, 12f * sy))
                drawPath(
                    path {
                        moveTo(58f * sx, 70f * sy); lineTo(78f * sx, 62f * sy)
                        lineTo(98f * sx, 70f * sy); close()
                    },
                    pagoda
                )
                drawRect(pagoda, Offset(66f * sx, 58f * sy), Size(24f * sx, 10f * sy))
                drawPath(
                    path {
                        moveTo(60f * sx, 58f * sy); lineTo(78f * sx, 50f * sy)
                        lineTo(96f * sx, 58f * sy); close()
                    },
                    pagoda
                )
                drawRect(pagoda, Offset(68f * sx, 46f * sy), Size(20f * sx, 10f * sy))
                drawPath(
                    path {
                        moveTo(62f * sx, 46f * sy); lineTo(78f * sx, 38f * sy)
                        lineTo(94f * sx, 46f * sy); close()
                    },
                    pagoda
                )
                drawRect(pagoda, Offset(76f * sx, 22f * sy), Size(4f * sx, 16f * sy))
                drawCircle(pagoda, 2f * sx, Offset(78f * sx, 20f * sy))
                // ветки сакуры — тонкие штрихи в виде узких path'ов
                val branch = Color(0xFF6A3848)
                listOf(
                    Triple(0f, 50f, 60f) to Triple(30f, 38f, 52f),
                    Triple(0f, 64f, 90f) to Triple(35f, 56f, 66f),
                    Triple(268f, 30f, 360f) to Triple(310f, 36f, 28f),
                    Triple(258f, 45f, 380f) to Triple(310f, 40f, 50f)
                ).forEach { (start, ctrl) ->
                    drawPath(
                        path {
                            moveTo(start.first * sx, start.second * sy)
                            quadraticTo(ctrl.first * sx, ctrl.second * sy, start.third * sx, ctrl.third * sy)
                        },
                        branch
                    )
                }
                // кластеры цветов
                val pink = Color(0xFFFFC4D8)
                listOf(
                    Triple(8f, 46f, 3f), Triple(18f, 42f, 2.5f), Triple(28f, 48f, 3f),
                    Triple(42f, 44f, 2.5f), Triple(52f, 48f, 3f),
                    Triple(6f, 60f, 2.5f), Triple(22f, 62f, 3f), Triple(38f, 58f, 2.5f),
                    Triple(54f, 64f, 3f), Triple(70f, 62f, 2.5f), Triple(84f, 66f, 3f),
                    Triple(270f, 28f, 3f), Triple(285f, 32f, 2.5f), Triple(298f, 28f, 3f),
                    Triple(318f, 34f, 2.5f), Triple(334f, 30f, 3f), Triple(350f, 26f, 2.5f),
                    Triple(264f, 46f, 2.5f), Triple(284f, 42f, 3f), Triple(308f, 44f, 2.5f),
                    Triple(330f, 48f, 3f), Triple(354f, 50f, 2.5f)
                ).forEach { (cx, cy, r) ->
                    drawCircle(pink, r * sx, Offset(cx * sx, cy * sy))
                }
                // падающие лепестки
                val petal = Color(0xFFFF9CBE)
                listOf(
                    140f to 68f, 180f to 86f, 208f to 58f,
                    200f to 102f, 230f to 72f, 160f to 100f
                ).forEach { (cx, cy) ->
                    drawCircle(petal, 1.4f * sx, Offset(cx * sx, cy * sy))
                }
            }
        }
    ),

    JourneyScene.CAPPADOCIA to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFFFD8A0), 0.5f to Color(0xFFFF8E72), 1f to Color(0xFF5A2A4A)
        ),
        draw = {
            scaled { sx, sy ->
                drawCircle(Color(0xFFFFF7C8), 16f * sx, Offset(330f * sx, 40f * sy), alpha = 0.95f)
                // сказочные дымоходы
                val chimney = Color(0xFF4A1F28)
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(0f, 110f * sy)
                        quadraticTo(18f * sx, 82f * sy, 28f * sx, 110f * sy)
                        quadraticTo(38f * sx, 132f * sy, 50f * sx, 108f * sy)
                        quadraticTo(60f * sx, 132f * sy, 72f * sx, 110f * sy)
                        quadraticTo(84f * sx, 132f * sy, 96f * sx, 105f * sy)
                        quadraticTo(108f * sx, 132f * sy, 118f * sx, 110f * sy)
                        lineTo(120f * sx, 132f * sy); close()
                    },
                    chimney
                )
                drawPath(
                    path {
                        moveTo(150f * sx, 132f * sy); lineTo(150f * sx, 116f * sy)
                        quadraticTo(162f * sx, 95f * sy, 174f * sx, 116f * sy)
                        quadraticTo(186f * sx, 132f * sy, 198f * sx, 115f * sy)
                        quadraticTo(210f * sx, 132f * sy, 222f * sx, 132f * sy); close()
                    },
                    chimney
                )
                drawPath(
                    path {
                        moveTo(260f * sx, 132f * sy); lineTo(260f * sx, 120f * sy)
                        quadraticTo(276f * sx, 100f * sy, 292f * sx, 120f * sy)
                        quadraticTo(308f * sx, 132f * sy, 322f * sx, 110f * sy)
                        quadraticTo(338f * sx, 132f * sy, 354f * sx, 116f * sy)
                        lineTo(380f * sx, 132f * sy); close()
                    },
                    chimney
                )
                // тросы шаров (тонкие)
                val rope = Color(0xFF3A1A18)
                listOf(
                    Quad(88f, 60f, 98f, 72f), Quad(112f, 60f, 102f, 72f),
                    Quad(156f, 46f, 160f, 54f), Quad(216f, 64f, 220f, 76f),
                    Quad(56f, 44f, 60f, 50f), Quad(266f, 48f, 270f, 56f)
                ).forEach { q ->
                    drawLine(
                        rope,
                        Offset(q.a * sx, q.b * sy),
                        Offset(q.c * sx, q.d * sy),
                        strokeWidth = 0.5f * sx,
                        alpha = 0.7f
                    )
                }
                // воздушные шары
                drawEllipse(100f * sx, 46f * sy, 14f * sx, 18f * sy, Color(0xFFC4453A))
                drawPath(
                    path {
                        moveTo(86f * sx, 46f * sy)
                        quadraticTo(100f * sx, 66f * sy, 114f * sx, 46f * sy)
                        lineTo(114f * sx, 50f * sy)
                        quadraticTo(100f * sx, 70f * sy, 86f * sx, 50f * sy)
                        close()
                    },
                    Color(0xFFA02F25)
                )
                drawEllipse(100f * sx, 40f * sy, 3f * sx, 14f * sy, Color(0xFFE87A6A), alpha = 0.5f)
                drawRect(Color(0xFF3A1A18), Offset(95f * sx, 72f * sy), Size(10f * sx, 6f * sy))

                drawEllipse(160f * sx, 36f * sy, 11f * sx, 14f * sy, Color(0xFFE87A3A))
                drawEllipse(160f * sx, 32f * sy, 2.5f * sx, 11f * sy, Color(0xFFFFB084), alpha = 0.5f)
                drawRect(Color(0xFF3A1A18), Offset(156f * sx, 50f * sy), Size(8f * sx, 5f * sy))

                drawEllipse(220f * sx, 54f * sy, 13f * sx, 16f * sy, Color(0xFFF5C25A))
                drawEllipse(220f * sx, 50f * sy, 3f * sx, 13f * sy, Color(0xFFFFF080), alpha = 0.5f)
                drawRect(Color(0xFF3A1A18), Offset(215f * sx, 70f * sy), Size(10f * sx, 6f * sy))

                drawEllipse(58f * sx, 38f * sy, 10f * sx, 13f * sy, Color(0xFF7A3A5A))
                drawEllipse(58f * sx, 34f * sy, 2.2f * sx, 10f * sy, Color(0xFFB0688A), alpha = 0.5f)
                drawRect(Color(0xFF3A1A18), Offset(54f * sx, 51f * sy), Size(8f * sx, 4f * sy))

                drawEllipse(270f * sx, 42f * sy, 11f * sx, 14f * sy, Color(0xFF3A5A8A))
                drawEllipse(270f * sx, 38f * sy, 2.5f * sx, 11f * sy, Color(0xFF7C98C4), alpha = 0.5f)
                drawRect(Color(0xFF3A1A18), Offset(266f * sx, 56f * sy), Size(8f * sx, 5f * sy))
            }
        }
    ),

    JourneyScene.MARRAKESH to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFFFE2B4), 0.6f to Color(0xFFF0A86A), 1f to Color(0xFFC45838)
        ),
        draw = {
            scaled { sx, sy ->
                drawCircle(Color(0xFFFFF5C2), 18f * sx, Offset(60f * sx, 30f * sy), alpha = 0.7f)
                // минарет
                val minar = Color(0xFF6A1E14)
                drawRect(minar, Offset(232f * sx, 38f * sy), Size(22f * sx, 94f * sy))
                drawRect(minar, Offset(236f * sx, 32f * sy), Size(14f * sx, 8f * sy))
                drawPath(
                    path {
                        moveTo(234f * sx, 32f * sy); lineTo(243f * sx, 18f * sy)
                        lineTo(252f * sx, 32f * sy); close()
                    },
                    minar
                )
                drawRect(minar, Offset(241f * sx, 6f * sy), Size(4f * sx, 14f * sy))
                drawCircle(minar, 2f * sx, Offset(243f * sx, 4f * sy))
                // окна минарета (арки)
                val darkArch = Color(0xFF3A0A04)
                drawPath(
                    path {
                        moveTo(236f * sx, 50f * sy)
                        quadraticTo(236f * sx, 44f * sy, 243f * sx, 44f * sy)
                        quadraticTo(250f * sx, 44f * sy, 250f * sx, 50f * sy)
                        lineTo(250f * sx, 58f * sy); lineTo(236f * sx, 58f * sy); close()
                    },
                    darkArch
                )
                drawPath(
                    path {
                        moveTo(236f * sx, 72f * sy)
                        quadraticTo(236f * sx, 66f * sy, 243f * sx, 66f * sy)
                        quadraticTo(250f * sx, 66f * sy, 250f * sx, 72f * sy)
                        lineTo(250f * sx, 80f * sy); lineTo(236f * sx, 80f * sy); close()
                    },
                    darkArch
                )
                // городская стена
                drawRect(Color(0xFFA04220), Offset(0f, 82f * sy), Size(size.width, 50f * sy))
                // зубцы
                listOf(
                    0f, 18f, 36f, 54f, 72f, 90f, 108f, 126f, 144f, 162f,
                    180f, 198f, 216f, 262f, 280f, 298f, 316f, 334f, 352f, 370f
                ).forEach { x ->
                    drawRect(minar, Offset(x * sx, 76f * sy), Size(10f * sx, 8f * sy))
                }
                // арочные ворота
                drawPath(
                    path {
                        moveTo(138f * sx, 132f * sy); lineTo(138f * sx, 102f * sy)
                        quadraticTo(158f * sx, 86f * sy, 178f * sx, 102f * sy)
                        lineTo(178f * sx, 132f * sy); close()
                    },
                    Color(0xFF2A0804)
                )
                drawPath(
                    path {
                        moveTo(148f * sx, 132f * sy); lineTo(148f * sx, 105f * sy)
                        quadraticTo(158f * sx, 96f * sy, 168f * sx, 105f * sy)
                        lineTo(168f * sx, 132f * sy); close()
                    },
                    Color(0xFF5A1408), alpha = 0.6f
                )
                // пальмы
                val palm = Color(0xFF1A0808)
                drawRect(palm, Offset(318f * sx, 52f * sy), Size(3f * sx, 34f * sy))
                drawPath(
                    path {
                        moveTo(319f * sx, 54f * sy)
                        quadraticTo(300f * sx, 50f * sy, 290f * sx, 60f * sy)
                        quadraticTo(308f * sx, 50f * sy, 319f * sx, 54f * sy)
                        close()
                    },
                    palm
                )
                drawPath(
                    path {
                        moveTo(320f * sx, 54f * sy)
                        quadraticTo(339f * sx, 50f * sy, 349f * sx, 60f * sy)
                        quadraticTo(332f * sx, 50f * sy, 320f * sx, 54f * sy)
                        close()
                    },
                    palm
                )
                drawPath(
                    path {
                        moveTo(319f * sx, 54f * sy)
                        quadraticTo(312f * sx, 42f * sy, 304f * sx, 34f * sy)
                        quadraticTo(317f * sx, 46f * sy, 319f * sx, 54f * sy)
                        close()
                    },
                    palm
                )
                drawPath(
                    path {
                        moveTo(320f * sx, 54f * sy)
                        quadraticTo(326f * sx, 42f * sy, 334f * sx, 34f * sy)
                        quadraticTo(322f * sx, 46f * sy, 320f * sx, 54f * sy)
                        close()
                    },
                    palm
                )
                drawRect(palm, Offset(350f * sx, 62f * sy), Size(2.5f * sx, 22f * sy))
                drawPath(
                    path {
                        moveTo(351f * sx, 64f * sy)
                        quadraticTo(338f * sx, 60f * sy, 332f * sx, 68f * sy)
                        close()
                    },
                    palm
                )
                drawPath(
                    path {
                        moveTo(351f * sx, 64f * sy)
                        quadraticTo(363f * sx, 60f * sy, 369f * sx, 68f * sy)
                        close()
                    },
                    palm
                )
                drawPath(
                    path {
                        moveTo(351f * sx, 64f * sy)
                        quadraticTo(348f * sx, 54f * sy, 344f * sx, 48f * sy)
                        close()
                    },
                    palm
                )
            }
        }
    ),

    JourneyScene.SERENGETI to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFF5A430), 0.55f to Color(0xFFD8542A), 1f to Color(0xFF4A1A14)
        ),
        draw = {
            scaled { sx, sy ->
                // огромное солнце
                drawCircle(Color(0xFFFFE07A), 34f * sx, Offset(100f * sx, 65f * sy), alpha = 0.95f)
                drawCircle(Color(0xFFFFC24A), 22f * sx, Offset(100f * sx, 65f * sy))
                // дальняя равнина
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(380f * sx, 132f * sy)
                        lineTo(380f * sx, 92f * sy)
                        quadraticTo(200f * sx, 88f * sy, 0f, 98f * sy)
                        close()
                    },
                    Color(0xFF2A1008)
                )
                // акация
                val tree = Color(0xFF0E0404)
                drawRect(tree, Offset(278f * sx, 52f * sy), Size(3f * sx, 44f * sy))
                drawEllipse(280f * sx, 48f * sy, 38f * sx, 9f * sy, tree)
                drawEllipse(262f * sx, 44f * sy, 14f * sx, 5f * sy, tree)
                drawEllipse(302f * sx, 46f * sy, 16f * sx, 6f * sy, tree)
                // жираф
                drawRect(tree, Offset(186f * sx, 80f * sy), Size(22f * sx, 11f * sy))
                drawRect(tree, Offset(202f * sx, 64f * sy), Size(4f * sx, 20f * sy))
                drawPath(
                    path {
                        moveTo(202f * sx, 66f * sy); lineTo(195f * sx, 70f * sy)
                        lineTo(195f * sx, 64f * sy); close()
                    },
                    tree
                )
                drawRect(tree, Offset(200f * sx, 58f * sy), Size(9f * sx, 7f * sy))
                drawRect(tree, Offset(201f * sx, 54f * sy), Size(1.4f * sx, 4f * sy))
                drawRect(tree, Offset(206f * sx, 54f * sy), Size(1.4f * sx, 4f * sy))
                listOf(188f, 194f, 200f, 205f).forEach { lx ->
                    drawRect(tree, Offset(lx * sx, 90f * sy), Size(2.4f * sx, 9f * sy))
                }
                drawRect(tree, Offset(206f * sx, 84f * sy), Size(3f * sx, 3f * sy))
                // маленький жираф позади
                drawRect(tree, Offset(225f * sx, 84f * sy), Size(14f * sx, 7f * sy), alpha = 0.85f)
                drawRect(tree, Offset(235f * sx, 72f * sy), Size(3f * sx, 14f * sy), alpha = 0.85f)
                drawRect(tree, Offset(234f * sx, 68f * sy), Size(6f * sx, 5f * sy), alpha = 0.85f)
                listOf(226f, 231f, 236f, 239f).forEach { lx ->
                    drawRect(tree, Offset(lx * sx, 91f * sy), Size(2f * sx, 7f * sy), alpha = 0.85f)
                }
                // кустики травы
                listOf(30f, 160f, 340f).forEach { gx ->
                    drawPath(
                        path {
                            moveTo(gx * sx, 100f * sy); lineTo((gx + 2f) * sx, 95f * sy)
                            lineTo((gx + 4f) * sx, 100f * sy); lineTo((gx + 6f) * sx, 96f * sy)
                            lineTo((gx + 8f) * sx, 100f * sy); close()
                        },
                        tree, alpha = 0.8f
                    )
                }
            }
        }
    ),

    JourneyScene.ANTARCTICA to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFE0EAF0), 0.55f to Color(0xFFA0BCD0), 1f to Color(0xFF4A6480)
        ),
        draw = {
            scaled { sx, sy ->
                // дальняя стена ледника
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(0f, 70f * sy)
                        lineTo(60f * sx, 64f * sy); lineTo(100f * sx, 72f * sy)
                        lineTo(150f * sx, 58f * sy); lineTo(200f * sx, 70f * sy)
                        lineTo(250f * sx, 60f * sy); lineTo(300f * sx, 68f * sy)
                        lineTo(380f * sx, 56f * sy); lineTo(380f * sx, 132f * sy); close()
                    },
                    Color.White, alpha = 0.88f
                )
                // полосы тени
                val band = Color(0xFF9CBACC)
                drawRect(band, Offset(60f * sx, 74f * sy), Size(40f * sx, 22f * sy), alpha = 0.55f)
                drawRect(band, Offset(150f * sx, 68f * sy), Size(50f * sx, 24f * sy), alpha = 0.55f)
                drawRect(band, Offset(250f * sx, 68f * sy), Size(50f * sx, 22f * sy), alpha = 0.55f)
                // трещины
                val crack = Color(0xFF6A8AA4)
                drawLine(crack, Offset(80f * sx, 70f * sy), Offset(78f * sx, 92f * sy), strokeWidth = 0.8f * sx, alpha = 0.5f)
                drawLine(crack, Offset(180f * sx, 64f * sy), Offset(178f * sx, 90f * sy), strokeWidth = 0.8f * sx, alpha = 0.5f)
                drawLine(crack, Offset(270f * sx, 64f * sy), Offset(268f * sx, 88f * sy), strokeWidth = 0.8f * sx, alpha = 0.5f)
                // вода
                drawRect(Color(0xFF3A587A), Offset(0f, 105f * sy), Size(size.width, 27f * sy))
                drawEllipse(200f * sx, 108f * sy, 180f * sx, 1.4f * sy, Color.White, alpha = 0.25f)
                // айсберги
                listOf(
                    listOf(40f, 110f, 62f, 100f, 82f, 108f, 80f, 117f, 42f, 117f),
                    listOf(196f, 112f, 218f, 102f, 242f, 110f, 240f, 119f, 198f, 119f),
                    listOf(308f, 110f, 325f, 100f, 348f, 108f, 344f, 119f, 310f, 119f)
                ).forEach { pts ->
                    drawPath(
                        path {
                            moveTo(pts[0] * sx, pts[1] * sy)
                            lineTo(pts[2] * sx, pts[3] * sy)
                            lineTo(pts[4] * sx, pts[5] * sy)
                            lineTo(pts[6] * sx, pts[7] * sy)
                            lineTo(pts[8] * sx, pts[9] * sy)
                            close()
                        },
                        Color.White
                    )
                }
                // подводные части айсбергов
                listOf(
                    listOf(44f, 117f, 80f, 117f, 76f, 122f, 48f, 122f),
                    listOf(200f, 119f, 240f, 119f, 236f, 124f, 204f, 124f),
                    listOf(312f, 119f, 344f, 119f, 340f, 124f, 316f, 124f)
                ).forEach { pts ->
                    drawPath(
                        path {
                            moveTo(pts[0] * sx, pts[1] * sy)
                            lineTo(pts[2] * sx, pts[3] * sy)
                            lineTo(pts[4] * sx, pts[5] * sy)
                            lineTo(pts[6] * sx, pts[7] * sy)
                            close()
                        },
                        band, alpha = 0.55f
                    )
                }
                // пингвины
                val peng = Color(0xFF0A0A0A)
                listOf(55f to 103f, 64f to 104f, 218f to 106f).forEach { (px, py) ->
                    drawEllipse(px * sx, py * sy, 2.6f * sx, 4.6f * sy, peng)
                    drawEllipse(px * sx, (py + 1f) * sy, 1.4f * sx, 2.8f * sy, Color.White)
                    drawCircle(peng, 1.4f * sx, Offset(px * sx, (py - 4.6f) * sy))
                }
            }
        }
    ),

    JourneyScene.MALDIVES to SceneSpec(
        gradient = Brush.linearGradient(
            0f to Color(0xFF1F6A98), 0.5f to Color(0xFF2A8EB8), 1f to Color(0xFF1A4A6A),
            start = Offset(0f, 0f), end = Offset(380f, 132f)
        ),
        draw = {
            scaled { sx, sy ->
                // внешняя лагуна
                drawEllipse(190f * sx, 70f * sy, 170f * sx, 46f * sy, Color(0xFF62C8D4), alpha = 0.9f)
                drawEllipse(190f * sx, 70f * sy, 142f * sx, 34f * sy, Color(0xFF9CE0E6))
                // песчаное кольцо
                drawEllipse(190f * sx, 70f * sy, 118f * sx, 22f * sy, Color(0xFFF5DEA8))
                // внутренняя лагуна
                drawEllipse(190f * sx, 70f * sy, 92f * sx, 13f * sy, Color(0xFF7CD8E0))
                // пальмы на песке
                val palm = Color(0xFF1A3A2A)
                listOf(
                    148f to 64f, 170f to 60f, 195f to 58f, 220f to 60f, 244f to 64f,
                    140f to 82f, 180f to 86f, 220f to 86f, 248f to 82f
                ).forEach { (cx, cy) ->
                    drawCircle(palm, 3f * sx, Offset(cx * sx, cy * sy))
                }
                // мостик и бунгало
                val bung = Color(0xFF3A1D12)
                drawRect(bung, Offset(90f * sx, 100f * sy), Size(200f * sx, 2.5f * sy))
                listOf(100f, 124f, 148f, 172f, 208f, 232f, 256f).forEach { bx ->
                    drawRect(bung, Offset(bx * sx, 92f * sy), Size(14f * sx, 10f * sy))
                    drawPath(
                        path {
                            moveTo((bx - 2f) * sx, 92f * sy)
                            lineTo((bx + 7f) * sx, 86f * sy)
                            lineTo((bx + 16f) * sx, 92f * sy)
                            close()
                        },
                        bung
                    )
                }
                // блики
                drawEllipse(60f * sx, 120f * sy, 40f * sx, 1.4f * sy, Color.White, alpha = 0.4f)
                drawEllipse(320f * sx, 118f * sy, 55f * sx, 1.4f * sy, Color.White, alpha = 0.35f)
            }
        }
    ),

    JourneyScene.HAVANA to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFFFDCA8), 0.6f to Color(0xFFF5A878), 1f to Color(0xFFC46850)
        ),
        draw = {
            scaled { sx, sy ->
                // фасады
                drawRect(Color(0xFFE8A8C4), Offset(0f, 36f * sy), Size(62f * sx, 96f * sy))
                drawRect(Color(0xFFF5D878), Offset(62f * sx, 36f * sy), Size(62f * sx, 96f * sy))
                drawRect(Color(0xFF5AB8C4), Offset(124f * sx, 36f * sy), Size(62f * sx, 96f * sy))
                drawRect(Color(0xFFEE8A8A), Offset(186f * sx, 36f * sy), Size(62f * sx, 96f * sy))
                drawRect(Color(0xFFD8D878), Offset(248f * sx, 36f * sy), Size(62f * sx, 96f * sy))
                drawRect(Color(0xFFA8D8A8), Offset(310f * sx, 36f * sy), Size(70f * sx, 96f * sy))
                // карниз
                drawRect(Color.Black, Offset(0f, 36f * sy), Size(size.width, 3f * sy), alpha = 0.18f)
                drawRect(Color.Black, Offset(0f, 40f * sy), Size(size.width, 1f * sy), alpha = 0.18f)
                // арочные окна (12 штук)
                val winDark = Color(0xFF5A2A3A)
                listOf(
                    14f, 38f, 76f, 100f, 138f, 162f, 200f, 224f, 262f, 286f, 325f, 349f
                ).forEach { wx ->
                    val cx = wx + 10f
                    drawPath(
                        path {
                            moveTo(wx * sx, 60f * sy)
                            quadraticTo(wx * sx, 50f * sy, cx * sx, 50f * sy)
                            quadraticTo((wx + 20f) * sx, 50f * sy, (wx + 20f) * sx, 60f * sy)
                            lineTo((wx + 20f) * sx, 76f * sy)
                            lineTo(wx * sx, 76f * sy)
                            close()
                        },
                        winDark
                    )
                }
                // светлые рамы
                val winLight = Color(0x66FFFFFF)
                listOf(16f, 78f, 140f, 202f, 264f, 327f).forEach { wx ->
                    val cx = wx + 8f
                    drawPath(
                        path {
                            moveTo(wx * sx, 62f * sy)
                            quadraticTo(wx * sx, 52f * sy, cx * sx, 52f * sy)
                            quadraticTo((wx + 16f) * sx, 52f * sy, (wx + 16f) * sx, 62f * sy)
                            lineTo((wx + 16f) * sx, 74f * sy)
                            lineTo(wx * sx, 74f * sy)
                            close()
                        },
                        winLight
                    )
                }
                // балконы
                val bal = Color(0xFF3A1A2A)
                listOf(
                    Triple(8f, 46f, 3f), Triple(70f, 46f, 3f),
                    Triple(132f, 46f, 3f), Triple(194f, 46f, 3f),
                    Triple(256f, 46f, 3f), Triple(318f, 54f, 3f)
                ).forEach { (bx, bw, bh) ->
                    drawRect(bal, Offset(bx * sx, 86f * sy), Size(bw * sx, bh * sy))
                }
                // решётки балконов
                listOf(
                    14f, 22f, 30f, 38f, 46f,
                    76f, 84f, 92f, 100f, 108f,
                    138f, 146f, 154f, 162f, 170f,
                    200f, 208f, 216f, 224f, 232f,
                    262f, 270f, 278f, 286f, 294f,
                    324f, 334f, 344f, 354f, 364f
                ).forEach { lx ->
                    drawLine(bal, Offset(lx * sx, 89f * sy), Offset(lx * sx, 96f * sy), strokeWidth = 0.7f * sx)
                }
                // улица
                drawRect(Color(0xFF5A382A), Offset(0f, 118f * sy), Size(size.width, 14f * sy))
                drawLine(Color(0xFF3A1F14), Offset(0f, 125f * sy), Offset(size.width, 125f * sy), strokeWidth = 0.8f * sx, alpha = 0.5f)
                // ретро-кар
                drawPath(
                    path {
                        moveTo(120f * sx, 118f * sy); lineTo(130f * sx, 105f * sy)
                        lineTo(195f * sx, 105f * sy); lineTo(210f * sx, 96f * sy)
                        lineTo(260f * sx, 96f * sy); lineTo(272f * sx, 105f * sy)
                        lineTo(290f * sx, 105f * sy); lineTo(290f * sx, 121f * sy)
                        lineTo(120f * sx, 121f * sy); close()
                    },
                    Color(0xFF3A6AA4)
                )
                drawPath(
                    path {
                        moveTo(138f * sx, 105f * sy); lineTo(146f * sx, 99f * sy)
                        lineTo(196f * sx, 99f * sy); lineTo(204f * sx, 105f * sy); close()
                    },
                    Color(0xFFA8C8D8)
                )
                drawPath(
                    path {
                        moveTo(210f * sx, 105f * sy); lineTo(220f * sx, 99f * sy)
                        lineTo(255f * sx, 99f * sy); lineTo(262f * sx, 105f * sy); close()
                    },
                    Color(0xFFA8C8D8)
                )
                drawRect(Color(0xFF2A4A78), Offset(160f * sx, 100f * sy), Size(2f * sx, 5f * sy))
                drawRect(Color(0xFF2A4A78), Offset(228f * sx, 100f * sy), Size(2f * sx, 5f * sy))
                drawRect(Color(0xFFE8E8E8), Offset(120f * sx, 118f * sy), Size(170f * sx, 1.4f * sy))
                drawCircle(Color(0xFFFFF080), 2.4f * sx, Offset(287f * sx, 113f * sy))
                // колёса
                drawCircle(Color(0xFF1A1A1A), 7f * sx, Offset(148f * sx, 120f * sy))
                drawCircle(Color(0xFF5A5A5A), 2.6f * sx, Offset(148f * sx, 120f * sy))
                drawCircle(Color(0xFF1A1A1A), 7f * sx, Offset(265f * sx, 120f * sy))
                drawCircle(Color(0xFF5A5A5A), 2.6f * sx, Offset(265f * sx, 120f * sy))
            }
        }
    ),

    JourneyScene.MACHU_PICCHU to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFA8C4C0), 0.55f to Color(0xFF5A8A8A), 1f to Color(0xFF1A3A3A)
        ),
        draw = {
            scaled { sx, sy ->
                // дальние пики
                drawPath(
                    path {
                        moveTo(30f * sx, 132f * sy); lineTo(80f * sx, 55f * sy)
                        lineTo(130f * sx, 132f * sy); close()
                    },
                    Color(0xFF2A4A52), alpha = 0.6f
                )
                drawPath(
                    path {
                        moveTo(250f * sx, 132f * sy); lineTo(300f * sx, 48f * sy)
                        lineTo(350f * sx, 132f * sy); close()
                    },
                    Color(0xFF2A4A52), alpha = 0.55f
                )
                // главный пик Huayna Picchu
                drawPath(
                    path {
                        moveTo(140f * sx, 132f * sy); lineTo(210f * sx, 18f * sy)
                        lineTo(280f * sx, 132f * sy); close()
                    },
                    Color(0xFF15303A)
                )
                drawPath(
                    path {
                        moveTo(210f * sx, 18f * sy); lineTo(226f * sx, 60f * sy)
                        lineTo(218f * sx, 64f * sy); close()
                    },
                    Color(0xFF3A5A60), alpha = 0.45f
                )
                // снежный кончик
                drawPath(
                    path {
                        moveTo(203f * sx, 34f * sy); lineTo(210f * sx, 18f * sy)
                        lineTo(217f * sx, 34f * sy); lineTo(213f * sx, 36f * sy)
                        lineTo(210f * sx, 28f * sy); lineTo(207f * sx, 36f * sy); close()
                    },
                    Color(0xFFE0EEE8), alpha = 0.85f
                )
                // полосы тумана через градиент
                val mist = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.5f),
                    1f to Color.Transparent,
                    startY = 58f * sy, endY = 80f * sy
                )
                drawRect(mist, Offset(0f, 58f * sy), Size(size.width, 22f * sy))
                val mistLow = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.35f),
                    1f to Color.Transparent,
                    startY = 86f * sy, endY = 100f * sy
                )
                drawRect(mistLow, Offset(0f, 86f * sy), Size(size.width, 14f * sy))
                // террасы
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(0f, 100f * sy)
                        lineTo(80f * sx, 100f * sy); lineTo(80f * sx, 95f * sy)
                        lineTo(130f * sx, 95f * sy); lineTo(130f * sx, 90f * sy)
                        lineTo(180f * sx, 90f * sy); lineTo(180f * sx, 85f * sy)
                        lineTo(240f * sx, 85f * sy); lineTo(240f * sx, 90f * sy)
                        lineTo(300f * sx, 90f * sy); lineTo(300f * sx, 95f * sy)
                        lineTo(380f * sx, 95f * sy); lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF496244)
                )
                val terShadow = Color(0xFF314A30)
                drawRect(terShadow, Offset(0f, 100f * sy), Size(size.width, 2f * sy), alpha = 0.85f)
                drawRect(terShadow, Offset(80f * sx, 95f * sy), Size(220f * sx, 2f * sy), alpha = 0.85f)
                drawRect(terShadow, Offset(130f * sx, 90f * sy), Size(170f * sx, 2f * sy), alpha = 0.85f)
                drawRect(terShadow, Offset(180f * sx, 85f * sy), Size(60f * sx, 2f * sy), alpha = 0.85f)
                // руинные стены
                val ruin = Color(0xFF8A907A)
                drawRect(ruin, Offset(160f * sx, 76f * sy), Size(6f * sx, 9f * sy))
                drawRect(ruin, Offset(170f * sx, 74f * sy), Size(8f * sx, 11f * sy))
                drawRect(ruin, Offset(182f * sx, 74f * sy), Size(10f * sx, 11f * sy))
                drawRect(ruin, Offset(196f * sx, 76f * sy), Size(6f * sx, 9f * sy))
                drawRect(ruin, Offset(208f * sx, 74f * sy), Size(9f * sx, 11f * sy))
                drawRect(ruin, Offset(220f * sx, 76f * sy), Size(6f * sx, 9f * sy))
                // лама
                val llama = Color(0xFF3A2A1A)
                drawRect(llama, Offset(60f * sx, 92f * sy), Size(6f * sx, 4f * sy))
                drawRect(llama, Offset(62f * sx, 88f * sy), Size(2f * sx, 5f * sy))
                drawRect(llama, Offset(63f * sx, 86f * sy), Size(3f * sx, 3f * sy))
                drawRect(llama, Offset(60f * sx, 96f * sy), Size(1.4f * sx, 3f * sy))
                drawRect(llama, Offset(64f * sx, 96f * sy), Size(1.4f * sx, 3f * sy))
            }
        }
    ),

    JourneyScene.PRAGUE to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFFFD8B8), 0.55f to Color(0xFFE8A8B8), 1f to Color(0xFFA87AA0)
        ),
        draw = {
            scaled { sx, sy ->
                // дальний холм
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(0f, 75f * sy)
                        lineTo(380f * sx, 65f * sy); lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF7A4A5A), alpha = 0.35f
                )
                // Пражский Град
                val castle = Color(0xFF5A2A4A)
                drawRect(castle, Offset(232f * sx, 56f * sy), Size(116f * sx, 18f * sy), alpha = 0.7f)
                drawRect(castle, Offset(232f * sx, 52f * sy), Size(14f * sx, 6f * sy), alpha = 0.7f)
                drawRect(castle, Offset(256f * sx, 50f * sy), Size(10f * sx, 8f * sy), alpha = 0.7f)
                drawRect(castle, Offset(276f * sx, 48f * sy), Size(12f * sx, 10f * sy), alpha = 0.7f)
                drawRect(castle, Offset(296f * sx, 50f * sy), Size(10f * sx, 8f * sy), alpha = 0.7f)
                drawRect(castle, Offset(316f * sx, 50f * sy), Size(10f * sx, 6f * sy), alpha = 0.7f)
                drawRect(castle, Offset(334f * sx, 52f * sy), Size(14f * sx, 6f * sy), alpha = 0.7f)
                // шпили Святого Вита
                drawRect(castle, Offset(278f * sx, 40f * sy), Size(3f * sx, 12f * sy), alpha = 0.7f)
                drawPath(
                    path {
                        moveTo(277f * sx, 40f * sy); lineTo(279.5f * sx, 32f * sy)
                        lineTo(282f * sx, 40f * sy); close()
                    },
                    castle, alpha = 0.7f
                )
                drawRect(castle, Offset(284f * sx, 36f * sy), Size(3f * sx, 16f * sy), alpha = 0.7f)
                drawPath(
                    path {
                        moveTo(283f * sx, 36f * sy); lineTo(285.5f * sx, 26f * sy)
                        lineTo(288f * sx, 36f * sy); close()
                    },
                    castle, alpha = 0.7f
                )
                // готические башни Тын
                val tyn = Color(0xFF3A1A2A)
                drawRect(tyn, Offset(118f * sx, 48f * sy), Size(14f * sx, 50f * sy))
                drawPath(
                    path {
                        moveTo(118f * sx, 48f * sy); lineTo(125f * sx, 24f * sy)
                        lineTo(132f * sx, 48f * sy); close()
                    },
                    tyn
                )
                drawRect(tyn, Offset(124f * sx, 14f * sy), Size(2f * sx, 12f * sy))
                drawRect(tyn, Offset(116f * sx, 44f * sy), Size(3f * sx, 6f * sy))
                drawPath(
                    path {
                        moveTo(115f * sx, 44f * sy); lineTo(117.5f * sx, 38f * sy)
                        lineTo(120f * sx, 44f * sy); close()
                    },
                    tyn
                )
                drawRect(tyn, Offset(131f * sx, 44f * sy), Size(3f * sx, 6f * sy))
                drawPath(
                    path {
                        moveTo(130f * sx, 44f * sy); lineTo(132.5f * sx, 38f * sy)
                        lineTo(135f * sx, 44f * sy); close()
                    },
                    tyn
                )
                drawRect(tyn, Offset(152f * sx, 50f * sy), Size(14f * sx, 48f * sy))
                drawPath(
                    path {
                        moveTo(152f * sx, 50f * sy); lineTo(159f * sx, 26f * sy)
                        lineTo(166f * sx, 50f * sy); close()
                    },
                    tyn
                )
                drawRect(tyn, Offset(158f * sx, 16f * sy), Size(2f * sx, 12f * sy))
                drawRect(tyn, Offset(150f * sx, 46f * sy), Size(3f * sx, 6f * sy))
                drawPath(
                    path {
                        moveTo(149f * sx, 46f * sy); lineTo(151.5f * sx, 40f * sy)
                        lineTo(154f * sx, 46f * sy); close()
                    },
                    tyn
                )
                drawRect(tyn, Offset(165f * sx, 46f * sy), Size(3f * sx, 6f * sy))
                drawPath(
                    path {
                        moveTo(164f * sx, 46f * sy); lineTo(166.5f * sx, 40f * sy)
                        lineTo(169f * sx, 46f * sy); close()
                    },
                    tyn
                )
                // красные крыши и стены
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(0f, 96f * sy)
                        lineTo(15f * sx, 80f * sy); lineTo(30f * sx, 96f * sy)
                        lineTo(45f * sx, 82f * sy); lineTo(60f * sx, 96f * sy)
                        lineTo(75f * sx, 84f * sy); lineTo(90f * sx, 96f * sy)
                        lineTo(106f * sx, 80f * sy); lineTo(120f * sx, 96f * sy)
                        lineTo(135f * sx, 84f * sy); lineTo(150f * sx, 96f * sy)
                        lineTo(166f * sx, 80f * sy); lineTo(180f * sx, 96f * sy)
                        lineTo(195f * sx, 84f * sy); lineTo(210f * sx, 96f * sy)
                        lineTo(225f * sx, 82f * sy); lineTo(240f * sx, 96f * sy)
                        lineTo(255f * sx, 84f * sy); lineTo(270f * sx, 96f * sy)
                        lineTo(285f * sx, 80f * sy); lineTo(300f * sx, 96f * sy)
                        lineTo(315f * sx, 84f * sy); lineTo(330f * sx, 96f * sy)
                        lineTo(345f * sx, 82f * sy); lineTo(360f * sx, 96f * sy)
                        lineTo(380f * sx, 84f * sy); lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFFC44A3A)
                )
                drawRect(Color(0xFFF5D8A8), Offset(0f, 96f * sy), Size(size.width, 36f * sy))
                // окна
                val win = Color(0xFF8A3A2A)
                listOf(
                    6f, 18f, 36f, 50f, 66f, 80f, 96f, 110f, 126f, 140f, 156f, 170f,
                    186f, 200f, 216f, 230f, 246f, 260f, 276f, 290f, 306f, 320f, 336f, 350f, 366f
                ).forEach { wx ->
                    drawRect(win, Offset(wx * sx, 102f * sy), Size(4f * sx, 6f * sy))
                }
                listOf(22f, 82f, 200f, 298f).forEach { dx ->
                    drawRect(win, Offset(dx * sx, 120f * sy), Size(6f * sx, 12f * sy))
                }
            }
        }
    ),

    JourneyScene.VENICE to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFFFE2D0), 0.5f to Color(0xFFF5C0C8), 1f to Color(0xFFC47898)
        ),
        draw = {
            scaled { sx, sy ->
                // дальние церкви
                val far = Color(0xFF7A3A58)
                drawRect(far, Offset(48f * sx, 50f * sy), Size(24f * sx, 14f * sy), alpha = 0.4f)
                drawEllipse(60f * sx, 50f * sy, 14f * sx, 9f * sy, far, alpha = 0.4f)
                drawRect(far, Offset(306f * sx, 48f * sy), Size(28f * sx, 14f * sy), alpha = 0.4f)
                drawEllipse(320f * sx, 48f * sy, 16f * sx, 10f * sy, far, alpha = 0.4f)
                drawRect(far, Offset(318f * sx, 36f * sy), Size(3f * sx, 14f * sy), alpha = 0.4f)
                drawCircle(far, 2f * sx, Offset(319.5f * sx, 33f * sy), alpha = 0.4f)
                // палаццо слева
                drawRect(Color(0xFFF5B890), Offset(0f, 38f * sy), Size(48f * sx, 62f * sy))
                drawRect(Color(0xFFD88A78), Offset(48f * sx, 44f * sy), Size(40f * sx, 56f * sy))
                drawRect(Color(0xFFE8A890), Offset(88f * sx, 36f * sy), Size(48f * sx, 64f * sy))
                drawRect(Color(0xFFC47878), Offset(136f * sx, 46f * sy), Size(34f * sx, 54f * sy))
                // палаццо справа
                drawRect(Color(0xFFD8987A), Offset(210f * sx, 44f * sy), Size(40f * sx, 56f * sy))
                drawRect(Color(0xFFF5B8A8), Offset(250f * sx, 36f * sy), Size(52f * sx, 64f * sy))
                drawRect(Color(0xFFC87090), Offset(302f * sx, 42f * sy), Size(40f * sx, 58f * sy))
                drawRect(Color(0xFFE89898), Offset(342f * sx, 46f * sy), Size(38f * sx, 54f * sy))
                // арочные окна
                val winD = Color(0xFF5A2A3A)
                listOf(
                    Triple(10f, 16f, 22f), Triple(28f, 34f, 40f),
                    Triple(58f, 64f, 70f), Triple(74f, 80f, 86f),
                    Triple(98f, 105f, 112f), Triple(118f, 125f, 132f),
                    Triple(144f, 150f, 156f),
                    Triple(218f, 224f, 230f), Triple(234f, 240f, 246f),
                    Triple(258f, 265f, 272f), Triple(280f, 287f, 294f),
                    Triple(310f, 316f, 322f),
                    Triple(348f, 354f, 360f), Triple(364f, 370f, 376f)
                ).forEach { (x1, cx, x2) ->
                    drawPath(
                        path {
                            moveTo(x1 * sx, 58f * sy)
                            quadraticTo(x1 * sx, 52f * sy, cx * sx, 52f * sy)
                            quadraticTo(x2 * sx, 52f * sy, x2 * sx, 58f * sy)
                            lineTo(x2 * sx, 70f * sy); lineTo(x1 * sx, 70f * sy); close()
                        },
                        winD
                    )
                }
                // прямоугольные окна
                listOf(
                    Triple(14f, 78f, 14f), Triple(32f, 78f, 14f),
                    Triple(62f, 80f, 14f), Triple(78f, 80f, 14f),
                    Triple(104f, 76f, 14f), Triple(124f, 76f, 14f),
                    Triple(220f, 80f, 14f), Triple(236f, 80f, 14f),
                    Triple(262f, 76f, 14f), Triple(284f, 76f, 14f),
                    Triple(350f, 80f, 14f)
                ).forEach { (rx, ry, rh) ->
                    drawRect(winD, Offset(rx * sx, ry * sy), Size(6f * sx, rh * sy))
                }
                // канал
                drawRect(Color(0xFF3A586A), Offset(0f, 100f * sy), Size(size.width, 32f * sy))
                drawEllipse(200f * sx, 104f * sy, 180f * sx, 1.4f * sy, Color.White, alpha = 0.32f)
                drawEllipse(100f * sx, 118f * sy, 60f * sx, 1.3f * sy, Color.White, alpha = 0.25f)
                drawEllipse(300f * sx, 124f * sy, 50f * sx, 1.3f * sy, Color.White, alpha = 0.22f)
                drawRect(Color(0xFFF5B890), Offset(0f, 100f * sy), Size(size.width, 6f * sy), alpha = 0.15f)
                // гондола
                val gond = Color(0xFF0C0610)
                drawPath(
                    path {
                        moveTo(148f * sx, 116f * sy)
                        quadraticTo(200f * sx, 110f * sy, 252f * sx, 116f * sy)
                        lineTo(248f * sx, 121f * sy)
                        quadraticTo(200f * sx, 116f * sy, 152f * sx, 121f * sy)
                        close()
                    },
                    gond
                )
                drawPath(
                    path {
                        moveTo(146f * sx, 116f * sy); lineTo(139f * sx, 109f * sy)
                        lineTo(150f * sx, 114f * sy); close()
                    },
                    gond
                )
                drawEllipse(240f * sx, 105f * sy, 2.4f * sx, 3.6f * sy, gond)
                drawRect(gond, Offset(237f * sx, 107f * sy), Size(6f * sx, 10f * sy))
                drawLine(gond, Offset(247f * sx, 107f * sy), Offset(256f * sx, 125f * sy), strokeWidth = 1.2f * sx)
            }
        }
    ),

    JourneyScene.LONDON to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFF6A7A8C), 0.5f to Color(0xFF4A5A70), 1f to Color(0xFF1A2538)
        ),
        draw = {
            scaled { sx, sy ->
                // туман
                drawEllipse(190f * sx, 30f * sy, 220f * sx, 8f * sy, Color.White, alpha = 0.25f)
                drawEllipse(190f * sx, 65f * sy, 220f * sx, 6f * sy, Color.White, alpha = 0.16f)
                // дальний город
                val far = Color(0xFF2A3548)
                listOf(
                    Triple(0f, 62f, 14f) to 40f,
                    Triple(16f, 56f, 10f) to 46f,
                    Triple(32f, 66f, 12f) to 36f,
                    Triple(50f, 60f, 14f) to 42f,
                    Triple(335f, 60f, 14f) to 42f,
                    Triple(352f, 56f, 12f) to 46f
                ).forEach { (t, h) ->
                    drawRect(far, Offset(t.first * sx, t.second * sy), Size(t.third * sx, h * sy), alpha = 0.7f)
                }
                // Биг-Бен в дали
                val bigBen = Color(0xFF2A3548)
                drawRect(bigBen, Offset(70f * sx, 44f * sy), Size(10f * sx, 58f * sy), alpha = 0.85f)
                drawCircle(Color(0xFFD8B878), 3.2f * sx, Offset(75f * sx, 56f * sy))
                drawPath(
                    path {
                        moveTo(70f * sx, 44f * sy); lineTo(75f * sx, 28f * sy)
                        lineTo(80f * sx, 44f * sy); close()
                    },
                    bigBen, alpha = 0.85f
                )
                drawRect(bigBen, Offset(74.2f * sx, 20f * sy), Size(1.6f * sx, 10f * sy), alpha = 0.85f)
                // Тауэрский мост
                val tb = Color(0xFF1A2230)
                drawRect(tb, Offset(130f * sx, 74f * sy), Size(22f * sx, 44f * sy))
                drawRect(tb, Offset(228f * sx, 74f * sy), Size(22f * sx, 44f * sy))
                drawRect(tb, Offset(124f * sx, 34f * sy), Size(34f * sx, 42f * sy))
                drawRect(tb, Offset(222f * sx, 34f * sy), Size(34f * sx, 42f * sy))
                // пинаклы
                listOf(128f, 148f, 226f, 246f).forEach { px ->
                    drawRect(tb, Offset(px * sx, 22f * sy), Size(6f * sx, 12f * sy))
                    drawPath(
                        path {
                            moveTo((px - 2f) * sx, 22f * sy); lineTo((px + 3f) * sx, 12f * sy)
                            lineTo((px + 8f) * sx, 22f * sy); close()
                        },
                        tb
                    )
                }
                // переходы
                drawRect(tb, Offset(158f * sx, 42f * sy), Size(64f * sx, 6f * sy))
                drawRect(tb, Offset(158f * sx, 54f * sy), Size(64f * sx, 6f * sy))
                drawRect(tb, Offset(98f * sx, 82f * sy), Size(184f * sx, 5f * sy))
                // цепи моста
                drawPath(
                    path {
                        moveTo(0f, 92f * sy)
                        quadraticTo(65f * sx, 94f * sy, 130f * sx, 82f * sy)
                    },
                    tb
                )
                drawPath(
                    path {
                        moveTo(380f * sx, 92f * sy)
                        quadraticTo(315f * sx, 94f * sy, 250f * sx, 82f * sy)
                    },
                    tb
                )
                // освещённые окна
                val winY = Color(0xFFFFD482)
                listOf(
                    Triple(135f, 50f, 3f), Triple(145f, 60f, 3f),
                    Triple(237f, 50f, 3f), Triple(247f, 60f, 3f)
                ).forEach { (wx, wy, w) ->
                    drawRect(winY, Offset(wx * sx, wy * sy), Size(w * sx, 4f * sy), alpha = 0.85f)
                }
                // Темза
                drawRect(Color(0xFF0A1828), Offset(0f, 118f * sy), Size(size.width, 14f * sy))
                drawEllipse(200f * sx, 122f * sy, 180f * sx, 1.4f * sy, Color.White, alpha = 0.18f)
                drawEllipse(100f * sx, 128f * sy, 50f * sx, 1.2f * sy, Color.White, alpha = 0.15f)
            }
        }
    ),

    JourneyScene.GRAND_CANYON to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFFFD8A0), 0.55f to Color(0xFFFF8E52), 1f to Color(0xFF8A2A28)
        ),
        draw = {
            scaled { sx, sy ->
                // дымка
                drawEllipse(190f * sx, 32f * sy, 220f * sx, 7f * sy, Color.White, alpha = 0.3f)
                // слои
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(0f, 92f * sy)
                        lineTo(40f * sx, 88f * sy); lineTo(80f * sx, 95f * sy)
                        lineTo(130f * sx, 84f * sy); lineTo(180f * sx, 92f * sy)
                        lineTo(240f * sx, 82f * sy); lineTo(300f * sx, 90f * sy)
                        lineTo(380f * sx, 84f * sy); lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFFE8A878), alpha = 0.7f
                )
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(0f, 104f * sy)
                        lineTo(50f * sx, 100f * sy); lineTo(90f * sx, 108f * sy)
                        lineTo(150f * sx, 100f * sy); lineTo(200f * sx, 106f * sy)
                        lineTo(250f * sx, 96f * sy); lineTo(320f * sx, 104f * sy)
                        lineTo(380f * sx, 100f * sy); lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFFC47844), alpha = 0.85f
                )
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(0f, 115f * sy)
                        lineTo(40f * sx, 110f * sy); lineTo(100f * sx, 116f * sy)
                        lineTo(160f * sx, 108f * sy); lineTo(220f * sx, 114f * sy)
                        lineTo(290f * sx, 106f * sy); lineTo(350f * sx, 114f * sy)
                        lineTo(380f * sx, 112f * sy); lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF9A4A28)
                )
                // ближний край
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(0f, 124f * sy)
                        lineTo(60f * sx, 120f * sy); lineTo(140f * sx, 126f * sy)
                        lineTo(200f * sx, 122f * sy); lineTo(260f * sx, 128f * sy)
                        lineTo(320f * sx, 122f * sy); lineTo(380f * sx, 126f * sy)
                        lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF5A1F18)
                )
                // река
                drawPath(
                    path {
                        moveTo(0f, 110f * sy)
                        quadraticTo(80f * sx, 116f * sy, 140f * sx, 108f * sy)
                        quadraticTo(210f * sx, 100f * sy, 280f * sx, 110f * sy)
                        quadraticTo(330f * sx, 116f * sy, 380f * sx, 105f * sy)
                    },
                    Color(0xFF7A9EC0), alpha = 0.7f
                )
                // эрозионные линии
                val ero = Color(0xFF3A1208)
                listOf(
                    Triple(20f, 116f, 125f), Triple(55f, 114f, 122f),
                    Triple(92f, 118f, 127f), Triple(170f, 112f, 121f),
                    Triple(240f, 116f, 125f), Triple(310f, 110f, 120f),
                    Triple(345f, 115f, 124f)
                ).forEach { (lx, y1, y2) ->
                    drawLine(ero, Offset(lx * sx, y1 * sy), Offset(lx * sx, y2 * sy), strokeWidth = 0.5f * sx, alpha = 0.45f)
                }
            }
        }
    ),

    JourneyScene.VOLCANO to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFF3A4A68), 0.5f to Color(0xFF2A3650), 1f to Color(0xFF14182A)
        ),
        draw = {
            scaled { sx, sy ->
                // луна
                drawCircle(Color(0xFFFFE8C4), 13f * sx, Offset(80f * sx, 36f * sy), alpha = 0.85f)
                drawCircle(Color(0xFFFFF5D8), 7f * sx, Offset(80f * sx, 36f * sy))
                // звёзды
                listOf(
                    Triple(150f, 18f, 0.8f), Triple(190f, 12f, 0.9f),
                    Triple(240f, 22f, 0.7f), Triple(290f, 16f, 0.8f),
                    Triple(330f, 22f, 0.7f)
                ).forEach { (cx, cy, r) ->
                    drawCircle(Color.White, r * sx, Offset(cx * sx, cy * sy), alpha = 0.7f)
                }
                // дальний хребет
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(60f * sx, 98f * sy)
                        lineTo(120f * sx, 105f * sy); lineTo(380f * sx, 100f * sy)
                        lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF1A2032), alpha = 0.75f
                )
                // главный вулкан
                drawPath(
                    path {
                        moveTo(120f * sx, 132f * sy); lineTo(210f * sx, 40f * sy)
                        lineTo(290f * sx, 132f * sy); close()
                    },
                    Color(0xFF0A0A14)
                )
                drawPath(
                    path {
                        moveTo(200f * sx, 52f * sy); lineTo(210f * sx, 40f * sy)
                        lineTo(220f * sx, 52f * sy); lineTo(216f * sx, 58f * sy)
                        lineTo(210f * sx, 50f * sy); lineTo(204f * sx, 58f * sy); close()
                    },
                    Color(0xFF3A1814), alpha = 0.85f
                )
                // снежные полосы
                val snow = Color.White
                drawPath(
                    path {
                        moveTo(210f * sx, 40f * sy); lineTo(213f * sx, 60f * sy)
                        lineTo(208f * sx, 66f * sy); lineTo(205f * sx, 52f * sy); close()
                    },
                    snow, alpha = 0.85f
                )
                drawPath(
                    path {
                        moveTo(210f * sx, 40f * sy); lineTo(218f * sx, 66f * sy)
                        lineTo(214f * sx, 74f * sy); lineTo(208f * sx, 58f * sy); close()
                    },
                    snow, alpha = 0.85f
                )
                drawPath(
                    path {
                        moveTo(210f * sx, 40f * sy); lineTo(202f * sx, 60f * sy)
                        lineTo(208f * sx, 68f * sy); lineTo(213f * sx, 50f * sy); close()
                    },
                    snow, alpha = 0.85f
                )
                drawPath(
                    path {
                        moveTo(210f * sx, 40f * sy); lineTo(196f * sx, 76f * sy)
                        lineTo(204f * sx, 80f * sy); lineTo(208f * sx, 60f * sy); close()
                    },
                    snow, alpha = 0.85f
                )
                // тень вулкана
                drawPath(
                    path {
                        moveTo(210f * sx, 40f * sy); lineTo(260f * sx, 132f * sy)
                        lineTo(240f * sx, 132f * sy); close()
                    },
                    Color(0xFF1A1A26), alpha = 0.6f
                )
                // дымовой шлейф
                val plume = Color(0xFFDCDCE0)
                drawEllipse(210f * sx, 32f * sy, 11f * sx, 6f * sy, plume, alpha = 0.7f)
                drawEllipse(222f * sx, 22f * sy, 15f * sx, 7f * sy, plume, alpha = 0.7f)
                drawEllipse(240f * sx, 12f * sy, 20f * sx, 7f * sy, plume, alpha = 0.7f)
                drawEllipse(270f * sx, 6f * sy, 22f * sx, 6f * sy, plume, alpha = 0.7f)
                drawEllipse(226f * sx, 20f * sy, 8f * sx, 3f * sy, Color.White, alpha = 0.55f)
                drawEllipse(246f * sx, 10f * sy, 10f * sx, 3f * sy, Color.White, alpha = 0.55f)
                // второй вулкан
                drawPath(
                    path {
                        moveTo(295f * sx, 132f * sy); lineTo(325f * sx, 80f * sy)
                        lineTo(355f * sx, 132f * sy); close()
                    },
                    Color(0xFF0A0A14)
                )
                drawPath(
                    path {
                        moveTo(322f * sx, 85f * sy); lineTo(325f * sx, 80f * sy)
                        lineTo(328f * sx, 86f * sy); lineTo(325f * sx, 88f * sy); close()
                    },
                    snow, alpha = 0.75f
                )
                // тлеющая лава в кратере
                drawCircle(Color(0xFFFF6A2A), 2.4f * sx, Offset(210f * sx, 46f * sy), alpha = 0.85f)
            }
        }
    ),

    JourneyScene.MATTERHORN to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFF5A8EB8), 0.55f to Color(0xFF82B0CC), 1f to Color(0xFF2A4868)
        ),
        draw = {
            scaled { sx, sy ->
                // дальний хребет
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(0f, 92f * sy)
                        lineTo(60f * sx, 86f * sy); lineTo(120f * sx, 94f * sy)
                        lineTo(380f * sx, 88f * sy); lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF5A7494), alpha = 0.5f
                )
                // боковые пики
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(40f * sx, 78f * sy)
                        lineTo(80f * sx, 92f * sy); lineTo(120f * sx, 72f * sy)
                        lineTo(160f * sx, 95f * sy); lineTo(160f * sx, 132f * sy); close()
                    },
                    Color(0xFF2A3A54), alpha = 0.85f
                )
                drawPath(
                    path {
                        moveTo(240f * sx, 132f * sy); lineTo(260f * sx, 100f * sy)
                        lineTo(300f * sx, 80f * sy); lineTo(340f * sx, 95f * sy)
                        lineTo(380f * sx, 75f * sy); lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF2A3A54), alpha = 0.85f
                )
                // снег на боковых
                drawPath(
                    path {
                        moveTo(35f * sx, 84f * sy); lineTo(40f * sx, 78f * sy)
                        lineTo(46f * sx, 86f * sy); lineTo(42f * sx, 88f * sy)
                        lineTo(40f * sx, 82f * sy); lineTo(38f * sx, 88f * sy); close()
                    },
                    Color.White, alpha = 0.85f
                )
                drawPath(
                    path {
                        moveTo(115f * sx, 76f * sy); lineTo(120f * sx, 72f * sy)
                        lineTo(126f * sx, 80f * sy); lineTo(122f * sx, 82f * sy)
                        lineTo(120f * sx, 76f * sy); lineTo(118f * sx, 82f * sy); close()
                    },
                    Color.White, alpha = 0.85f
                )
                drawPath(
                    path {
                        moveTo(335f * sx, 80f * sy); lineTo(340f * sx, 75f * sy)
                        lineTo(344f * sx, 82f * sy); lineTo(340f * sx, 84f * sy); close()
                    },
                    Color.White, alpha = 0.85f
                )
                // пирамида Маттерхорн
                drawPath(
                    path {
                        moveTo(120f * sx, 132f * sy); lineTo(160f * sx, 130f * sy)
                        lineTo(200f * sx, 32f * sy); lineTo(240f * sx, 132f * sy)
                        lineTo(120f * sx, 132f * sy); close()
                    },
                    Color(0xFF3A4A64)
                )
                // тень
                drawPath(
                    path {
                        moveTo(200f * sx, 32f * sy); lineTo(240f * sx, 132f * sy)
                        lineTo(210f * sx, 132f * sy); close()
                    },
                    Color(0xFF1A2638)
                )
                // снежные пятна
                drawPath(
                    path {
                        moveTo(195f * sx, 45f * sy); lineTo(200f * sx, 32f * sy)
                        lineTo(210f * sx, 50f * sy); lineTo(205f * sx, 55f * sy)
                        lineTo(200f * sx, 45f * sy); close()
                    },
                    Color.White
                )
                drawPath(
                    path {
                        moveTo(190f * sx, 60f * sy); lineTo(196f * sx, 56f * sy)
                        lineTo(202f * sx, 70f * sy); lineTo(196f * sx, 76f * sy)
                        lineTo(188f * sx, 65f * sy); close()
                    },
                    Color.White, alpha = 0.95f
                )
                drawPath(
                    path {
                        moveTo(208f * sx, 55f * sy); lineTo(213f * sx, 50f * sy)
                        lineTo(218f * sx, 70f * sy); lineTo(213f * sx, 72f * sy); close()
                    },
                    Color.White, alpha = 0.85f
                )
                drawPath(
                    path {
                        moveTo(180f * sx, 85f * sy)
                        quadraticTo(200f * sx, 78f * sy, 220f * sx, 88f * sy)
                    },
                    Color.White
                )
                drawPath(
                    path {
                        moveTo(168f * sx, 100f * sy)
                        quadraticTo(190f * sx, 92f * sy, 215f * sx, 104f * sy)
                    },
                    Color.White
                )
                // шале
                val chalet = Color(0xFF3A2018)
                drawRect(chalet, Offset(80f * sx, 115f * sy), Size(20f * sx, 14f * sy))
                drawPath(
                    path {
                        moveTo(77f * sx, 115f * sy); lineTo(90f * sx, 105f * sy)
                        lineTo(103f * sx, 115f * sy); close()
                    },
                    chalet
                )
                drawRect(chalet, Offset(106f * sx, 118f * sy), Size(14f * sx, 11f * sy))
                drawPath(
                    path {
                        moveTo(104f * sx, 118f * sy); lineTo(113f * sx, 110f * sy)
                        lineTo(122f * sx, 118f * sy); close()
                    },
                    chalet
                )
                drawRect(chalet, Offset(280f * sx, 118f * sy), Size(14f * sx, 11f * sy))
                drawPath(
                    path {
                        moveTo(278f * sx, 118f * sy); lineTo(287f * sx, 110f * sy)
                        lineTo(296f * sx, 118f * sy); close()
                    },
                    chalet
                )
                drawRect(chalet, Offset(300f * sx, 115f * sy), Size(20f * sx, 14f * sy))
                drawPath(
                    path {
                        moveTo(297f * sx, 115f * sy); lineTo(310f * sx, 105f * sy)
                        lineTo(323f * sx, 115f * sy); close()
                    },
                    chalet
                )
                // окошки
                val win = Color(0xFFFFC878)
                listOf(86f, 93f, 305f, 313f).forEach { wx ->
                    drawRect(win, Offset(wx * sx, 120f * sy), Size(4f * sx, 4f * sy))
                }
                drawRect(win, Offset(110f * sx, 123f * sy), Size(3f * sx, 3f * sy))
                drawRect(win, Offset(284f * sx, 123f * sy), Size(3f * sx, 3f * sy))
                // снег внизу
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(0f, 128f * sy)
                        lineTo(380f * sx, 124f * sy); lineTo(380f * sx, 132f * sy); close()
                    },
                    Color.White, alpha = 0.65f
                )
            }
        }
    ),

    JourneyScene.BAIKAL to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFDCE8EC), 0.5f to Color(0xFF82B8C4), 1f to Color(0xFF2A5A78)
        ),
        draw = {
            scaled { sx, sy ->
                // дальние горы
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(0f, 70f * sy)
                        lineTo(40f * sx, 50f * sy); lineTo(80f * sx, 65f * sy)
                        lineTo(130f * sx, 45f * sy); lineTo(180f * sx, 60f * sy)
                        lineTo(240f * sx, 42f * sy); lineTo(290f * sx, 58f * sy)
                        lineTo(340f * sx, 48f * sy); lineTo(380f * sx, 60f * sy)
                        lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF4A6A8A), alpha = 0.7f
                )
                // снежные шапки
                listOf(
                    Triple(77f, 80f, 83f) to 67f,
                    Triple(127f, 130f, 133f) to 47f,
                    Triple(237f, 240f, 243f) to 44f,
                    Triple(337f, 340f, 343f) to 50f
                ).forEach { (t, peakY) ->
                    drawPath(
                        path {
                            moveTo(t.first * sx, (peakY + 2f) * sy)
                            lineTo(t.second * sx, peakY * sy)
                            lineTo(t.third * sx, (peakY + 3f) * sy)
                            close()
                        },
                        Color.White, alpha = 0.8f
                    )
                }
                // лёд
                drawRect(Color(0xFF7CB8C8), Offset(0f, 80f * sy), Size(size.width, 52f * sy), alpha = 0.95f)
                // лучи трещин из центра
                val crack = Color(0xFF1A3A52)
                listOf(
                    Pair(10f, 130f), Pair(80f, 132f), Pair(160f, 132f),
                    Pair(220f, 132f), Pair(300f, 132f), Pair(370f, 130f)
                ).forEach { (x2, y2) ->
                    drawLine(crack, Offset(190f * sx, 80f * sy), Offset(x2 * sx, y2 * sy), strokeWidth = 0.6f * sx, alpha = 0.6f)
                }
                // мелкие трещины
                listOf(
                    Pair(Pair(130f, 100f), Pair(80f, 110f)),
                    Pair(Pair(130f, 100f), Pair(100f, 130f)),
                    Pair(Pair(250f, 110f), Pair(300f, 130f)),
                    Pair(Pair(250f, 110f), Pair(290f, 105f)),
                    Pair(Pair(70f, 115f), Pair(120f, 122f)),
                    Pair(Pair(300f, 100f), Pair(340f, 95f))
                ).forEach { (start, end) ->
                    drawLine(
                        crack,
                        Offset(start.first * sx, start.second * sy),
                        Offset(end.first * sx, end.second * sy),
                        strokeWidth = 0.5f * sx,
                        alpha = 0.45f
                    )
                }
                // блики
                drawEllipse(100f * sx, 100f * sy, 50f * sx, 2f * sy, Color.White, alpha = 0.4f)
                drawEllipse(280f * sx, 115f * sy, 60f * sx, 2f * sy, Color.White, alpha = 0.35f)
                drawEllipse(190f * sx, 125f * sy, 80f * sx, 2f * sy, Color.White, alpha = 0.3f)
                // пузырьки во льду
                listOf(
                    Triple(80f, 95f, 1.6f), Triple(120f, 110f, 1.3f),
                    Triple(220f, 105f, 1.5f), Triple(280f, 125f, 1.6f),
                    Triple(340f, 115f, 1.3f), Triple(60f, 120f, 1.4f),
                    Triple(160f, 115f, 1.2f), Triple(320f, 92f, 1.3f)
                ).forEach { (cx, cy, r) ->
                    drawCircle(Color.White, r * sx, Offset(cx * sx, cy * sy), alpha = 0.75f)
                }
            }
        }
    ),

    JourneyScene.DUBAI to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFF5B478), 0.55f to Color(0xFFD87A48), 1f to Color(0xFF5A2438)
        ),
        draw = {
            scaled { sx, sy ->
                // солнце
                drawCircle(Color(0xFFFFE07A), 20f * sx, Offset(58f * sx, 50f * sy), alpha = 0.95f)
                drawCircle(Color(0xFFFFC25A), 12f * sx, Offset(58f * sx, 50f * sy))
                // дальние дюны
                drawPath(
                    path {
                        moveTo(0f, 132f * sy)
                        quadraticTo(80f * sx, 78f * sy, 180f * sx, 96f * sy)
                        quadraticTo(280f * sx, 110f * sy, 380f * sx, 80f * sy)
                        lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF8A3A2A), alpha = 0.55f
                )
                // кластер небоскрёбов
                val bldg = Color(0xFF1A1822)
                drawRect(bldg, Offset(98f * sx, 74f * sy), Size(14f * sx, 58f * sy), alpha = 0.95f)
                drawRect(bldg, Offset(114f * sx, 66f * sy), Size(10f * sx, 66f * sy), alpha = 0.95f)
                drawRect(bldg, Offset(128f * sx, 76f * sy), Size(14f * sx, 56f * sy), alpha = 0.95f)
                // Burj-Khalifa
                drawPath(
                    path {
                        moveTo(158f * sx, 132f * sy); lineTo(168f * sx, 28f * sy)
                        lineTo(170f * sx, 14f * sy); lineTo(172f * sx, 28f * sy)
                        lineTo(182f * sx, 132f * sy); close()
                    },
                    bldg, alpha = 0.95f
                )
                drawPath(
                    path {
                        moveTo(153f * sx, 132f * sy); lineTo(160f * sx, 60f * sy)
                        lineTo(164f * sx, 60f * sy); lineTo(160f * sx, 132f * sy); close()
                    },
                    bldg, alpha = 0.95f
                )
                drawPath(
                    path {
                        moveTo(178f * sx, 60f * sy); lineTo(182f * sx, 60f * sy)
                        lineTo(186f * sx, 132f * sy); lineTo(180f * sx, 132f * sy); close()
                    },
                    bldg, alpha = 0.95f
                )
                drawRect(bldg, Offset(169.2f * sx, 0f), Size(1.6f * sx, 14f * sy), alpha = 0.95f)
                // правый кластер
                drawRect(bldg, Offset(200f * sx, 70f * sy), Size(12f * sx, 62f * sy), alpha = 0.95f)
                drawRect(bldg, Offset(214f * sx, 62f * sy), Size(14f * sx, 70f * sy), alpha = 0.95f)
                drawPath(
                    path {
                        moveTo(232f * sx, 132f * sy); lineTo(234f * sx, 60f * sy)
                        lineTo(240f * sx, 60f * sy); lineTo(242f * sx, 132f * sy); close()
                    },
                    bldg, alpha = 0.95f
                )
                drawRect(bldg, Offset(246f * sx, 68f * sy), Size(14f * sx, 64f * sy), alpha = 0.95f)
                drawRect(bldg, Offset(264f * sx, 58f * sy), Size(12f * sx, 74f * sy), alpha = 0.95f)
                drawRect(bldg, Offset(278f * sx, 72f * sy), Size(10f * sx, 60f * sy), alpha = 0.95f)
                drawRect(bldg, Offset(292f * sx, 62f * sy), Size(14f * sx, 70f * sy), alpha = 0.95f)
                drawRect(bldg, Offset(308f * sx, 74f * sy), Size(10f * sx, 58f * sy), alpha = 0.95f)
                // светящиеся окна
                val win = Color(0xFFFFE898)
                listOf(
                    102f to 80f, 106f to 92f, 117f to 76f, 119f to 92f, 117f to 108f,
                    133f to 86f, 136f to 105f, 168f to 40f, 170f to 60f,
                    168f to 80f, 170f to 100f, 168f to 118f,
                    204f to 78f, 207f to 100f, 218f to 70f, 222f to 92f,
                    236f to 74f, 237f to 100f, 250f to 76f, 255f to 100f,
                    267f to 64f, 270f to 96f, 296f to 70f, 300f to 100f
                ).forEach { (wx, wy) ->
                    drawRect(win, Offset(wx * sx, wy * sy), Size(2f * sx, 2f * sy), alpha = 0.85f)
                }
                // передняя дюна
                drawPath(
                    path {
                        moveTo(0f, 132f * sy)
                        quadraticTo(60f * sx, 110f * sy, 130f * sx, 125f * sy)
                        quadraticTo(220f * sx, 130f * sy, 280f * sx, 122f * sy)
                        quadraticTo(340f * sx, 126f * sy, 380f * sx, 130f * sy)
                        lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF4A1418)
                )
                // верблюжонок
                val cam = Color(0xFF1A0408)
                drawPath(
                    path {
                        moveTo(40f * sx, 122f * sy); lineTo(42f * sx, 116f * sy)
                        lineTo(44f * sx, 116f * sy); lineTo(45f * sx, 119f * sy)
                        lineTo(48f * sx, 119f * sy); lineTo(49f * sx, 116f * sy)
                        lineTo(51f * sx, 116f * sy); lineTo(53f * sx, 122f * sy)
                        lineTo(51f * sx, 126f * sy); lineTo(51f * sx, 130f * sy)
                        lineTo(49f * sx, 130f * sy); lineTo(49f * sx, 126f * sy)
                        lineTo(44f * sx, 126f * sy); lineTo(43f * sx, 130f * sy)
                        lineTo(41f * sx, 130f * sy); lineTo(41f * sx, 126f * sy)
                        close()
                    },
                    cam
                )
            }
        }
    ),

    JourneyScene.RIO to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFFFB878), 0.5f to Color(0xFFFF7A8A), 1f to Color(0xFF5A2A78)
        ),
        draw = {
            scaled { sx, sy ->
                drawCircle(Color(0xFFFFE07A), 22f * sx, Offset(280f * sx, 50f * sy), alpha = 0.95f)
                drawCircle(Color(0xFFFFB84A), 14f * sx, Offset(280f * sx, 50f * sy))
                // дальние холмы
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(0f, 88f * sy)
                        lineTo(70f * sx, 65f * sy); lineTo(130f * sx, 80f * sy)
                        lineTo(180f * sx, 75f * sy); lineTo(210f * sx, 80f * sy)
                        lineTo(240f * sx, 70f * sy); lineTo(260f * sx, 85f * sy)
                        lineTo(380f * sx, 75f * sy); lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF3A2050), alpha = 0.65f
                )
                // Корковаду
                drawPath(
                    path {
                        moveTo(88f * sx, 132f * sy)
                        quadraticTo(130f * sx, 38f * sy, 175f * sx, 132f * sy)
                        close()
                    },
                    Color(0xFF1A0E2A)
                )
                // Спаситель
                drawRect(Color(0xFFF5E8C8), Offset(130f * sx, 40f * sy), Size(2f * sx, 14f * sy))
                drawRect(Color(0xFFF5E8C8), Offset(124f * sx, 44f * sy), Size(14f * sx, 2f * sy))
                drawCircle(Color(0xFFF5E8C8), 1.6f * sx, Offset(131f * sx, 38f * sy))
                // Сахарная Голова
                drawPath(
                    path {
                        moveTo(275f * sx, 132f * sy)
                        quadraticTo(295f * sx, 56f * sy, 320f * sx, 56f * sy)
                        quadraticTo(345f * sx, 56f * sy, 365f * sx, 132f * sy)
                        close()
                    },
                    Color(0xFF2A1838)
                )
                drawPath(
                    path {
                        moveTo(295f * sx, 56f * sy)
                        quadraticTo(297f * sx, 70f * sy, 300f * sx, 78f * sy)
                    },
                    Color(0xFF3A2848)
                )
                // вторичный холм
                drawPath(
                    path {
                        moveTo(200f * sx, 132f * sy)
                        quadraticTo(220f * sx, 90f * sy, 240f * sx, 132f * sy)
                        close()
                    },
                    Color(0xFF1A0E2A), alpha = 0.75f
                )
                // океан
                drawRect(Color(0xFF3A1A58), Offset(0f, 100f * sy), Size(size.width, 15f * sy))
                drawEllipse(190f * sx, 103f * sy, 180f * sx, 1.4f * sy, Color.White, alpha = 0.35f)
                drawEllipse(100f * sx, 112f * sy, 60f * sx, 1.2f * sy, Color.White, alpha = 0.3f)
                // пляж
                drawRect(Color(0xFFF5D878), Offset(0f, 115f * sy), Size(size.width, 17f * sy))
                drawEllipse(190f * sx, 115.5f * sy, 180f * sx, 1.2f * sy, Color.White, alpha = 0.5f)
                // зонтики
                drawRect(Color(0xFF3A1818), Offset(60f * sx, 122f * sy), Size(1.5f * sx, 8f * sy))
                drawPath(
                    path {
                        moveTo(54f * sx, 122f * sy); lineTo(68f * sx, 122f * sy)
                        lineTo(61f * sx, 116f * sy); close()
                    },
                    Color(0xFFC44A3A)
                )
                drawRect(Color(0xFF3A1818), Offset(180f * sx, 124f * sy), Size(1.5f * sx, 6f * sy))
                drawPath(
                    path {
                        moveTo(174f * sx, 124f * sy); lineTo(186f * sx, 124f * sy)
                        lineTo(180f * sx, 119f * sy); close()
                    },
                    Color(0xFF3A7AA8)
                )
                drawRect(Color(0xFF3A1818), Offset(220f * sx, 123f * sy), Size(1.5f * sx, 7f * sy))
                drawPath(
                    path {
                        moveTo(215f * sx, 123f * sy); lineTo(228f * sx, 123f * sy)
                        lineTo(221.5f * sx, 117f * sy); close()
                    },
                    Color(0xFFF5C25A)
                )
                drawRect(Color(0xFF3A1818), Offset(340f * sx, 124f * sy), Size(1.5f * sx, 6f * sy))
                drawPath(
                    path {
                        moveTo(334f * sx, 124f * sy); lineTo(346f * sx, 124f * sy)
                        lineTo(340f * sx, 119f * sy); close()
                    },
                    Color(0xFF3A8A5A)
                )
                // прометна (намёк)
                drawLine(Color(0xFF3A1818), Offset(0f, 128f * sy), Offset(size.width, 128f * sy), strokeWidth = 0.5f * sx, alpha = 0.5f)
            }
        }
    ),

    JourneyScene.HAWAII to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFC8EAF0), 0.5f to Color(0xFF7CD2DC), 1f to Color(0xFF2A8AA4)
        ),
        draw = {
            scaled { sx, sy ->
                drawCircle(Color(0xFFFFF5C2), 14f * sx, Offset(78f * sx, 34f * sy), alpha = 0.85f)
                // силуэт Diamond Head
                drawPath(
                    path {
                        moveTo(220f * sx, 132f * sy); lineTo(260f * sx, 55f * sy)
                        lineTo(300f * sx, 60f * sy); lineTo(340f * sx, 75f * sy)
                        lineTo(380f * sx, 70f * sy); lineTo(380f * sx, 132f * sy); close()
                    },
                    Color(0xFF3A5A4A), alpha = 0.9f
                )
                drawPath(
                    path {
                        moveTo(260f * sx, 55f * sy); lineTo(268f * sx, 65f * sy)
                        lineTo(280f * sx, 60f * sy); lineTo(294f * sx, 70f * sy)
                        lineTo(300f * sx, 60f * sy); lineTo(290f * sx, 50f * sy)
                        lineTo(275f * sx, 52f * sy); close()
                    },
                    Color(0xFF5A7A64), alpha = 0.55f
                )
                // вода
                drawRect(Color(0xFF2A8AA4), Offset(0f, 84f * sy), Size(size.width, 48f * sy))
                // волна
                drawPath(
                    path {
                        moveTo(0f, 100f * sy)
                        quadraticTo(50f * sx, 78f * sy, 100f * sx, 95f * sy)
                        quadraticTo(130f * sx, 110f * sy, 160f * sx, 100f * sy)
                        quadraticTo(200f * sx, 88f * sy, 220f * sx, 105f * sy)
                        lineTo(220f * sx, 132f * sy); lineTo(0f, 132f * sy); close()
                    },
                    Color(0xFF5AB8C8)
                )
                drawEllipse(50f * sx, 95f * sy, 40f * sx, 2f * sy, Color.White, alpha = 0.8f)
                drawEllipse(100f * sx, 100f * sy, 14f * sx, 3f * sy, Color.White, alpha = 0.9f)
                drawEllipse(160f * sx, 105f * sy, 25f * sx, 2f * sy, Color.White, alpha = 0.75f)
                // брызги
                listOf(
                    Triple(85f, 85f, 1f), Triple(92f, 80f, 1.2f), Triple(105f, 86f, 1f),
                    Triple(115f, 80f, 1f), Triple(76f, 82f, 1f), Triple(125f, 92f, 1.1f)
                ).forEach { (cx, cy, r) ->
                    drawCircle(Color.White, r * sx, Offset(cx * sx, cy * sy), alpha = 0.75f)
                }
                // парус
                drawPath(
                    path {
                        moveTo(180f * sx, 80f * sy); lineTo(185f * sx, 70f * sy)
                        lineTo(188f * sx, 80f * sy); close()
                    },
                    Color.White
                )
                drawPath(
                    path {
                        moveTo(178f * sx, 80f * sy); lineTo(190f * sx, 80f * sy)
                        lineTo(188f * sx, 84f * sy); lineTo(180f * sx, 84f * sy); close()
                    },
                    Color.White
                )
                // пальма
                val palm = Color(0xFF1A0E1A)
                drawPath(
                    path {
                        moveTo(30f * sx, 132f * sy); lineTo(34f * sx, 80f * sy)
                        lineTo(37f * sx, 80f * sy); lineTo(33f * sx, 132f * sy); close()
                    },
                    palm
                )
                listOf(
                    listOf(34f to 80f, 17f to 75f, 6f to 90f),
                    listOf(34f to 80f, 51f to 70f, 68f to 80f),
                    listOf(34f to 80f, 28f to 68f, 22f to 56f),
                    listOf(34f to 80f, 42f to 68f, 50f to 58f),
                    listOf(34f to 80f, 22f to 84f, 14f to 92f)
                ).forEach { pts ->
                    drawPath(
                        path {
                            moveTo(pts[0].first * sx, pts[0].second * sy)
                            quadraticTo(
                                pts[1].first * sx, pts[1].second * sy,
                                pts[2].first * sx, pts[2].second * sy
                            )
                            close()
                        },
                        palm
                    )
                }
                // сёрфер
                drawEllipse(202f * sx, 91f * sy, 11f * sx, 1.4f * sy, palm)
                drawCircle(palm, 2f * sx, Offset(202f * sx, 86f * sy))
                drawRect(palm, Offset(200f * sx, 87f * sy), Size(4f * sx, 6f * sy))
                drawPath(
                    path {
                        moveTo(198f * sx, 89f * sy); lineTo(196f * sx, 86f * sy)
                        lineTo(194f * sx, 88f * sy); close()
                    },
                    palm
                )
            }
        }
    ),

    JourneyScene.SEYCHELLES to SceneSpec(
        gradient = Brush.verticalGradient(
            0f to Color(0xFFFFE8C0), 0.5f to Color(0xFF7CD2C8), 1f to Color(0xFF1A7A8A)
        ),
        draw = {
            scaled { sx, sy ->
                drawCircle(Color(0xFFFFF5D0), 16f * sx, Offset(320f * sx, 32f * sy), alpha = 0.7f)
                // вода
                drawRect(Color(0xFF7CD8D0), Offset(0f, 58f * sy), Size(size.width, 50f * sy))
                drawEllipse(190f * sx, 64f * sy, 180f * sx, 1.4f * sy, Color.White, alpha = 0.4f)
                drawEllipse(200f * sx, 78f * sy, 160f * sx, 1.4f * sy, Color.White, alpha = 0.35f)
                drawEllipse(180f * sx, 94f * sy, 150f * sx, 1.4f * sy, Color.White, alpha = 0.3f)
                // дальний остров
                drawPath(
                    path {
                        moveTo(180f * sx, 76f * sy)
                        quadraticTo(210f * sx, 64f * sy, 240f * sx, 76f * sy)
                        close()
                    },
                    Color(0xFF3A5A4A), alpha = 0.5f
                )
                // песок
                drawPath(
                    path {
                        moveTo(0f, 132f * sy); lineTo(380f * sx, 132f * sy)
                        lineTo(380f * sx, 104f * sy)
                        quadraticTo(280f * sx, 99f * sy, 200f * sx, 108f * sy)
                        quadraticTo(95f * sx, 117f * sy, 0f, 104f * sy)
                        close()
                    },
                    Color(0xFFF5DCA8)
                )
                // гранитные валуны
                val boulder = Color(0xFF5A4838)
                drawEllipse(280f * sx, 105f * sy, 58f * sx, 34f * sy, boulder)
                drawEllipse(232f * sx, 110f * sy, 22f * sx, 14f * sy, boulder)
                drawEllipse(338f * sx, 112f * sy, 30f * sx, 20f * sy, boulder)
                drawEllipse(50f * sx, 115f * sy, 38f * sx, 22f * sy, boulder)
                drawEllipse(155f * sx, 115f * sy, 16f * sx, 10f * sy, boulder)
                // верхние блики
                val lit = Color(0xFF8A7460)
                drawEllipse(275f * sx, 90f * sy, 38f * sx, 8f * sy, lit, alpha = 0.65f)
                drawEllipse(45f * sx, 102f * sy, 24f * sx, 6f * sy, lit, alpha = 0.65f)
                drawEllipse(330f * sx, 100f * sy, 22f * sx, 6f * sy, lit, alpha = 0.65f)
                // трещины
                val crack = Color(0xFF2A1E14)
                drawPath(
                    path {
                        moveTo(260f * sx, 100f * sy)
                        quadraticTo(280f * sx, 115f * sy, 300f * sx, 105f * sy)
                    },
                    crack
                )
                drawPath(
                    path {
                        moveTo(30f * sx, 108f * sy)
                        quadraticTo(50f * sx, 122f * sy, 70f * sx, 110f * sy)
                    },
                    crack
                )
                // наклонившаяся пальма
                val palm = Color(0xFF1A0A08)
                drawPath(
                    path {
                        moveTo(100f * sx, 132f * sy)
                        quadraticTo(120f * sx, 80f * sy, 115f * sx, 50f * sy)
                        lineTo(118f * sx, 50f * sy)
                        quadraticTo(124f * sx, 80f * sy, 104f * sx, 132f * sy)
                        close()
                    },
                    palm
                )
                drawPath(
                    path {
                        moveTo(115f * sx, 50f * sy)
                        quadraticTo(100f * sx, 40f * sy, 84f * sx, 38f * sy)
                        quadraticTo(105f * sx, 42f * sy, 117f * sx, 50f * sy)
                        close()
                    },
                    palm
                )
                drawPath(
                    path {
                        moveTo(115f * sx, 50f * sy)
                        quadraticTo(130f * sx, 38f * sy, 148f * sx, 36f * sy)
                        quadraticTo(130f * sx, 42f * sy, 117f * sx, 50f * sy)
                        close()
                    },
                    palm
                )
                drawPath(
                    path {
                        moveTo(115f * sx, 50f * sy)
                        quadraticTo(108f * sx, 35f * sy, 100f * sx, 24f * sy)
                        quadraticTo(112f * sx, 40f * sy, 117f * sx, 50f * sy)
                        close()
                    },
                    palm
                )
                drawPath(
                    path {
                        moveTo(115f * sx, 50f * sy)
                        quadraticTo(124f * sx, 35f * sy, 134f * sx, 27f * sy)
                        quadraticTo(120f * sx, 42f * sy, 117f * sx, 50f * sy)
                        close()
                    },
                    palm
                )
                drawPath(
                    path {
                        moveTo(115f * sx, 50f * sy)
                        quadraticTo(105f * sx, 60f * sy, 96f * sx, 64f * sy)
                        quadraticTo(110f * sx, 56f * sy, 117f * sx, 50f * sy)
                        close()
                    },
                    palm
                )
                drawCircle(palm, 1.2f * sx, Offset(111f * sx, 52f * sy))
                drawCircle(palm, 1.1f * sx, Offset(114f * sx, 54f * sy))
                drawCircle(palm, 1.2f * sx, Offset(118f * sx, 54f * sy))
                // маленькая пальма справа
                drawPath(
                    path {
                        moveTo(360f * sx, 132f * sy)
                        quadraticTo(358f * sx, 105f * sy, 358f * sx, 90f * sy)
                        lineTo(361f * sx, 90f * sy)
                        quadraticTo(362f * sx, 105f * sy, 364f * sx, 132f * sy)
                        close()
                    },
                    palm
                )
                drawPath(
                    path {
                        moveTo(360f * sx, 90f * sy)
                        quadraticTo(348f * sx, 84f * sy, 340f * sx, 86f * sy)
                        quadraticTo(354f * sx, 88f * sy, 361f * sx, 90f * sy)
                        close()
                    },
                    palm
                )
                drawPath(
                    path {
                        moveTo(360f * sx, 90f * sy)
                        quadraticTo(372f * sx, 84f * sy, 380f * sx, 86f * sy)
                        quadraticTo(368f * sx, 88f * sy, 361f * sx, 90f * sy)
                        close()
                    },
                    palm
                )
                drawPath(
                    path {
                        moveTo(360f * sx, 90f * sy)
                        quadraticTo(354f * sx, 80f * sy, 350f * sx, 72f * sy)
                        quadraticTo(358f * sx, 84f * sy, 361f * sx, 90f * sy)
                        close()
                    },
                    palm
                )
            }
        }
    )
)

private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)
