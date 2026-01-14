package com.triloo.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Triloo Design System — Colors
 * 
 * Aesthetic: Warm adventure meets modern minimalism
 * Inspired by: Sunset horizons, ocean depths, golden hour travel moments
 */

// PRIMARY — Coral (Core actions)
val CoralPrimary = Color(0xFFFF6B5B)          // Main action color
val CoralLight = CoralPrimary
val CoralDark = CoralPrimary
val CoralSubtle = CoralPrimary.copy(alpha = 0.12f)

// SECONDARY — Teal (Secondary actions)
val TealSecondary = Color(0xFF2DD4BF)         // Secondary actions, success
val TealLight = TealSecondary
val TealDark = TealSecondary
val TealSubtle = TealSecondary.copy(alpha = 0.12f)

// ACCENT — Gold (Highlights)
val GoldenAccent = Color(0xFFFBBF24)          // Currency, premium features
val GoldenLight = GoldenAccent
val GoldenDark = GoldenAccent
val GoldenSubtle = GoldenAccent.copy(alpha = 0.12f)

// NEUTRAL — Slate (Text, Backgrounds, Borders)
val Slate950 = Color(0xFF0F172A)              // Primary text (light mode)
val Slate900 = Color(0xFF1E293B)              // Headings
val Slate800 = Color(0xFF334155)              // Secondary text
val Slate700 = Color(0xFF475569)              // Tertiary text
val Slate600 = Color(0xFF64748B)              // Placeholder
val Slate500 = Color(0xFF94A3B8)              // Disabled
val Slate400 = Color(0xFFCBD5E1)              // Borders
val Slate300 = Color(0xFFE2E8F0)              // Dividers
val Slate200 = Color(0xFFF1F5F9)              // Card backgrounds
val Slate100 = Color(0xFFF8FAFC)              // Page backgrounds
val Slate50 = Color(0xFFFAFBFC)               // Subtle backgrounds

// SEMANTIC — Status Colors (aligned to 3-4 hues)
val Success = TealSecondary                   // Completed, Positive
val SuccessLight = TealSecondary.copy(alpha = 0.16f)
val Warning = GoldenAccent                    // Attention needed
val WarningLight = GoldenAccent.copy(alpha = 0.16f)
val Error = Color(0xFFEF4444)                 // Errors, Delete
val ErrorLight = Error.copy(alpha = 0.16f)
val Info = CoralPrimary                       // Information
val InfoLight = CoralPrimary.copy(alpha = 0.16f)

// EXPENSE CATEGORIES — Reduced to the core palette
val ExpenseFood = CoralPrimary
val ExpenseTransport = TealSecondary
val ExpenseAccommodation = GoldenAccent
val ExpenseEntertainment = CoralPrimary
val ExpenseShopping = TealSecondary
val ExpenseOther = Slate600

// MAP MARKERS — Reduced to the core palette
val MarkerHotel = GoldenAccent
val MarkerFood = CoralPrimary
val MarkerAttraction = CoralPrimary
val MarkerNature = TealSecondary
val MarkerFriend = TealSecondary

// DARK MODE — Specific Dark Theme Colors
val DarkBackground = Color(0xFF0D1117)        // GitHub-like dark
val DarkSurface = Color(0xFF161B22)           // Elevated surfaces
val DarkSurfaceVariant = Color(0xFF21262D)    // Cards
val DarkBorder = Color(0xFF30363D)            // Borders
val DarkTextPrimary = Color(0xFFF0F6FC)       // Primary text
val DarkTextSecondary = Color(0xFF8B949E)     // Secondary text
