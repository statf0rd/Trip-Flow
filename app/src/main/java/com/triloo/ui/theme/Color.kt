package com.triloo.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Цветовая палитра дизайн-системы Triloo.
 *
 * Визуальная идея: тёплое путешествие с чистой современной базой.
 * Ассоциации: закатный горизонт, морская глубина и золотой час в дороге.
 */

// PRIMARY — коралловый для основных действий.
val CoralPrimary = Color(0xFFFF6B5B)          // Основной акцентный цвет действий.
val CoralLight = Color(0xFFFF9A8F)            // Осветлённый коралловый для фонов и hover.
val CoralDark = Color(0xFFE04E40)             // Затемнённый коралловый для pressed-состояний.
val CoralSubtle = CoralPrimary.copy(alpha = 0.12f)

// SECONDARY — бирюзовый для вторичных действий.
val TealSecondary = Color(0xFF2DD4BF)         // Вторичные действия и успешные состояния.
val TealLight = Color(0xFF6EE7D8)             // Осветлённый бирюзовый.
val TealDark = Color(0xFF14B8A6)              // Затемнённый бирюзовый.
val TealSubtle = TealSecondary.copy(alpha = 0.12f)

// ACCENT — золотой для акцентов.
val GoldenAccent = Color(0xFFFBBF24)          // Валюта, премиальные акценты и выделение.
val GoldenLight = Color(0xFFFCD34D)           // Осветлённый золотой.
val GoldenDark = Color(0xFFF59E0B)            // Затемнённый золотой.
val GoldenSubtle = GoldenAccent.copy(alpha = 0.12f)

// NEUTRAL — сланцевые нейтрали для текста, фонов и границ.
val Slate950 = Color(0xFF0F172A)              // Основной текст в светлой теме.
val Slate900 = Color(0xFF1E293B)              // Заголовки.
val Slate800 = Color(0xFF334155)              // Вторичный текст.
val Slate700 = Color(0xFF475569)              // Третичный текст.
val Slate600 = Color(0xFF64748B)              // Плейсхолдеры.
val Slate500 = Color(0xFF94A3B8)              // Неактивные элементы.
val Slate400 = Color(0xFFCBD5E1)              // Границы.
val Slate300 = Color(0xFFE2E8F0)              // Разделители.
val Slate200 = Color(0xFFF1F5F9)              // Фоны карточек.
val Slate100 = Color(0xFFF8FAFC)              // Фон страниц.
val Slate50 = Color(0xFFFAFBFC)               // Едва заметные подложки.

// SEMANTIC — статусные цвета, собранные вокруг основной палитры.
val Success = TealSecondary                   // Успех и позитивные состояния.
val SuccessLight = TealSecondary.copy(alpha = 0.16f)
val Warning = GoldenAccent                    // Состояния, требующие внимания.
val WarningLight = GoldenAccent.copy(alpha = 0.16f)
val Error = Color(0xFFEF4444)                 // Ошибки и destructive-действия.
val ErrorLight = Error.copy(alpha = 0.16f)
val Info = CoralPrimary                       // Информационные подсказки.
val InfoLight = CoralPrimary.copy(alpha = 0.16f)

// EXPENSE CATEGORIES — цвета категорий расходов без лишнего расширения палитры.
val ExpenseFood = CoralPrimary
val ExpenseTransport = TealSecondary
val ExpenseAccommodation = GoldenAccent
val ExpenseEntertainment = CoralPrimary
val ExpenseShopping = TealSecondary
val ExpenseOther = Slate600

// MAP MARKERS — цвета маркеров карты в той же базовой палитре.
val MarkerHotel = GoldenAccent
val MarkerFood = CoralPrimary
val MarkerAttraction = CoralPrimary
val MarkerNature = TealSecondary
val MarkerFriend = TealSecondary
val MarkerParticipant = Color(0xFF2563EB)     // Синий маркер участника группы.
val MarkerDestination = TealSecondary         // Маркер места назначения.
val MarkerRecommendation = GoldenAccent       // Маркер AI-рекомендации.

// DARK MODE — специальные цвета тёмной темы.
val DarkBackground = Color(0xFF0D1117)        // Глубокий тёмный фон.
val DarkSurface = Color(0xFF161B22)           // Поднятые поверхности.
val DarkSurfaceVariant = Color(0xFF21262D)    // Карточки.
val DarkBorder = Color(0xFF30363D)            // Границы.
val DarkTextPrimary = Color(0xFFF0F6FC)       // Основной текст.
val DarkTextSecondary = Color(0xFF8B949E)     // Вторичный текст.

// DARK MODE CONTAINERS — приглушённые тёмные оттенки палитры с гарантированным
// контрастом для светлого текста. Заменяют использование CoralDark/TealDark/GoldenDark
// в *Container ролях, на которых alpha-12% «*Subtle» текст был нечитаем.
val CoralContainerDark = Color(0xFF5C2A22)    // Контейнер primary в тёмной теме.
val TealContainerDark = Color(0xFF0F4640)     // Контейнер secondary в тёмной теме.
val GoldenContainerDark = Color(0xFF5A4416)   // Контейнер tertiary в тёмной теме.
val ErrorContainerDark = Color(0xFF5C1F1F)    // Контейнер error в тёмной теме.
val ErrorOnContainerDark = Color(0xFFFEE2E2)  // Светлый текст на ErrorContainerDark.
