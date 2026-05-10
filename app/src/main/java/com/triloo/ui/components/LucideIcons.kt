package com.triloo.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Lucide-style stroke-иконки, перенесённые ровно по SVG-path'ам из мокапа
 * `App Shell.html`. Material `Icons.Rounded.*` для liquid-glass нав-бара
 * не подошли по форме (например, Material Explore — это компас со
 * стрелкой, а в дизайне — компас с ромбом-указателем).
 *
 * Все иконки рисуются stroke-only (без fill), `viewBox 24×24`, толщина
 * штриха 1.8, скругления как `linecap/linejoin: round` — те же значения,
 * что в `<Ic>` компоненте HTML-исходника.
 */

private const val STROKE_WIDTH = 1.8f
private val ICON_SIZE = 24.dp
private const val VIEW_PORT = 24f

private fun lucide(name: String, builder: (ImageVector.Builder) -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = ICON_SIZE,
        defaultHeight = ICON_SIZE,
        viewportWidth = VIEW_PORT,
        viewportHeight = VIEW_PORT
    ).also(builder).build()

private fun ImageVector.Builder.strokePath(block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) {
    addPath(
        pathData = androidx.compose.ui.graphics.vector.PathData(block),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero
    )
}

/**
 * Компас с ромбом-указателем (lucide `compass`).
 *
 * SVG в HTML:
 * ```
 * <circle cx="12" cy="12" r="9"/>
 * <path d="M14.6 9.4l-1.6 4.6-4.6 1.6 1.6-4.6 4.6-1.6Z"/>
 * ```
 */
val IconCompassLucide: ImageVector = lucide("CompassLucide") { builder ->
    builder.strokePath {
        // Круг радиуса 9 через 2 arc'а.
        moveTo(12f, 3f)
        arcToRelative(9f, 9f, 0f, true, false, 0f, 18f)
        arcToRelative(9f, 9f, 0f, true, false, 0f, -18f)
        close()
    }
    builder.strokePath {
        // Ромб-указатель.
        moveTo(14.6f, 9.4f)
        lineToRelative(-1.6f, 4.6f)
        lineToRelative(-4.6f, 1.6f)
        lineToRelative(1.6f, -4.6f)
        lineToRelative(4.6f, -1.6f)
        close()
    }
}

/**
 * Двое людей плечо к плечу (lucide `users`).
 *
 * SVG в HTML:
 * ```
 * <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/>
 * <circle cx="9" cy="7" r="4"/>
 * <path d="M22 21v-2a4 4 0 0 0-3-3.87"/>
 * <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
 * ```
 */
val IconUsersLucide: ImageVector = lucide("UsersLucide") { builder ->
    // Передний человек: тело-арка.
    builder.strokePath {
        moveTo(16f, 21f)
        verticalLineToRelative(-2f)
        arcToRelative(4f, 4f, 0f, false, false, -4f, -4f)
        horizontalLineTo(6f)
        arcToRelative(4f, 4f, 0f, false, false, -4f, 4f)
        verticalLineToRelative(2f)
    }
    // Передний человек: голова (круг радиуса 4 в (9, 7)).
    builder.strokePath {
        moveTo(9f, 3f)
        arcToRelative(4f, 4f, 0f, true, false, 0f, 8f)
        arcToRelative(4f, 4f, 0f, true, false, 0f, -8f)
        close()
    }
    // Задний человек: плечо.
    builder.strokePath {
        moveTo(22f, 21f)
        verticalLineToRelative(-2f)
        arcToRelative(4f, 4f, 0f, false, false, -3f, -3.87f)
    }
    // Задний человек: голова (полу-арка).
    builder.strokePath {
        moveTo(16f, 3.13f)
        arcToRelative(4f, 4f, 0f, false, true, 0f, 7.75f)
    }
}

/**
 * Кошелёк с прорезью под карту (lucide `wallet`).
 *
 * SVG в HTML:
 * ```
 * <path d="M3 7a2 2 0 0 1 2-2h13a2 2 0 0 1 2 2v2"/>
 * <path d="M3 7v10a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-3"/>
 * <path d="M22 12h-5a2 2 0 0 0 0 4h5"/>
 * ```
 */
val IconWalletLucide: ImageVector = lucide("WalletLucide") { builder ->
    builder.strokePath {
        moveTo(3f, 7f)
        arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
        horizontalLineToRelative(13f)
        arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
        verticalLineToRelative(2f)
    }
    builder.strokePath {
        moveTo(3f, 7f)
        verticalLineToRelative(10f)
        arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
        horizontalLineToRelative(14f)
        arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
        verticalLineToRelative(-3f)
    }
    builder.strokePath {
        moveTo(22f, 12f)
        horizontalLineToRelative(-5f)
        arcToRelative(2f, 2f, 0f, false, false, 0f, 4f)
        horizontalLineToRelative(5f)
    }
}

/**
 * Шестерня настроек (lucide `settings`).
 *
 * SVG в HTML — длинный path, повторяю один-в-один.
 */
/**
 * Простой `+` (lucide `plus`) — две скруглённые линии. Используется в
 * центральной FAB-слот liquid-glass нав-бара.
 */
val IconPlusLucide: ImageVector = lucide("PlusLucide") { builder ->
    builder.strokePath {
        // Горизонтальная.
        moveTo(5f, 12f)
        horizontalLineTo(19f)
    }
    builder.strokePath {
        // Вертикальная.
        moveTo(12f, 5f)
        verticalLineTo(19f)
    }
}

val IconGearLucide: ImageVector = lucide("GearLucide") { builder ->
    // Центральный круг радиуса 3.
    builder.strokePath {
        moveTo(12f, 9f)
        arcToRelative(3f, 3f, 0f, true, false, 0f, 6f)
        arcToRelative(3f, 3f, 0f, true, false, 0f, -6f)
        close()
    }
    // Зубцы шестерни — длинный собранный path из HTML.
    builder.strokePath {
        moveTo(19.4f, 15f)
        arcToRelative(1.65f, 1.65f, 0f, false, false, 0.33f, 1.82f)
        lineToRelative(0.06f, 0.06f)
        arcToRelative(2f, 2f, 0f, true, true, -2.83f, 2.83f)
        lineToRelative(-0.06f, -0.06f)
        arcToRelative(1.65f, 1.65f, 0f, false, false, -1.82f, -0.33f)
        arcToRelative(1.65f, 1.65f, 0f, false, false, -1f, 1.51f)
        verticalLineTo(21f)
        arcToRelative(2f, 2f, 0f, true, true, -4f, 0f)
        verticalLineToRelative(-0.09f)
        arcToRelative(1.65f, 1.65f, 0f, false, false, -1f, -1.51f)
        arcToRelative(1.65f, 1.65f, 0f, false, false, -1.82f, 0.33f)
        lineToRelative(-0.06f, 0.06f)
        arcToRelative(2f, 2f, 0f, true, true, -2.83f, -2.83f)
        lineToRelative(0.06f, -0.06f)
        arcToRelative(1.65f, 1.65f, 0f, false, false, 0.33f, -1.82f)
        arcToRelative(1.65f, 1.65f, 0f, false, false, -1.51f, -1f)
        horizontalLineTo(3f)
        arcToRelative(2f, 2f, 0f, true, true, 0f, -4f)
        horizontalLineToRelative(0.09f)
        arcToRelative(1.65f, 1.65f, 0f, false, false, 1.51f, -1f)
        arcToRelative(1.65f, 1.65f, 0f, false, false, -0.33f, -1.82f)
        lineToRelative(-0.06f, -0.06f)
        arcToRelative(2f, 2f, 0f, true, true, 2.83f, -2.83f)
        lineToRelative(0.06f, 0.06f)
        arcToRelative(1.65f, 1.65f, 0f, false, false, 1.82f, 0.33f)
        horizontalLineTo(9f)
        arcToRelative(1.65f, 1.65f, 0f, false, false, 1f, -1.51f)
        verticalLineTo(3f)
        arcToRelative(2f, 2f, 0f, true, true, 4f, 0f)
        verticalLineToRelative(0.09f)
        arcToRelative(1.65f, 1.65f, 0f, false, false, 1f, 1.51f)
        arcToRelative(1.65f, 1.65f, 0f, false, false, 1.82f, -0.33f)
        lineToRelative(0.06f, -0.06f)
        arcToRelative(2f, 2f, 0f, true, true, 2.83f, 2.83f)
        lineToRelative(-0.06f, 0.06f)
        arcToRelative(1.65f, 1.65f, 0f, false, false, -0.33f, 1.82f)
        verticalLineTo(9f)
        arcToRelative(1.65f, 1.65f, 0f, false, false, 1.51f, 1f)
        horizontalLineTo(21f)
        arcToRelative(2f, 2f, 0f, true, true, 0f, 4f)
        horizontalLineToRelative(-0.09f)
        arcToRelative(1.65f, 1.65f, 0f, false, false, -1.51f, 1f)
        close()
    }
}
