package com.triloo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Радиус-токены дизайн-системы Triloo.
 *
 * Сводят разнобой 8/12/14/16/18/20/24/28 dp в три уровня:
 *  • Sm — компактные элементы: чипы, текстовые поля, маленькие кнопки.
 *  • Md — карточки, табы, модалки, баннеры.
 *  • Lg — крупные акцентные карточки и листы.
 *
 * Для конкретных компонентов есть алиасы (`button`, `card`, `chip`, ...) — их и
 * стоит использовать в коде, чтобы при следующем тюнинге достаточно было
 * поменять одно значение здесь.
 */
object TrilooShapes {
    val Sm = RoundedCornerShape(12.dp)
    val Md = RoundedCornerShape(20.dp)
    val Lg = RoundedCornerShape(28.dp)

    val button = Sm
    val chip = Sm
    val textField = Sm
    val card = Md
    val sheet = Md
    val banner = Md
    val featureCard = Lg
    val pill = RoundedCornerShape(999.dp)
}
