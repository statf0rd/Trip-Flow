package com.triloo.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.triloo.data.model.ExpenseCategory
import com.triloo.data.model.PlaceCategory

/**
 * Material-иконка для каждой категории места.
 */
val PlaceCategory.icon: ImageVector
    get() = when (this) {
        PlaceCategory.ATTRACTION -> Icons.Rounded.AccountBalance
        PlaceCategory.RESTAURANT -> Icons.Rounded.Restaurant
        PlaceCategory.CAFE -> Icons.Rounded.Coffee
        PlaceCategory.BAR -> Icons.Rounded.LocalBar
        PlaceCategory.MUSEUM -> Icons.Rounded.Palette
        PlaceCategory.PARK -> Icons.Rounded.Park
        PlaceCategory.BEACH -> Icons.Rounded.BeachAccess
        PlaceCategory.SHOPPING -> Icons.Rounded.ShoppingBag
        PlaceCategory.ENTERTAINMENT -> Icons.Rounded.TheaterComedy
        PlaceCategory.HOLIDAY -> Icons.Rounded.Celebration
        PlaceCategory.TRANSPORT -> Icons.Rounded.DirectionsTransit
        PlaceCategory.VIEWPOINT -> Icons.Rounded.PhotoCamera
        PlaceCategory.NATURE -> Icons.Rounded.Landscape
        PlaceCategory.NIGHTLIFE -> Icons.Rounded.NightlightRound
        PlaceCategory.OTHER -> Icons.Rounded.Place
    }

/**
 * Цвет категории для иконок и фона.
 */
val PlaceCategory.color: Color
    get() = when (this) {
        PlaceCategory.RESTAURANT, PlaceCategory.CAFE -> ExpenseFood
        PlaceCategory.BAR, PlaceCategory.NIGHTLIFE -> Color(0xFFEC4899)
        PlaceCategory.MUSEUM, PlaceCategory.ATTRACTION -> CoralPrimary
        PlaceCategory.PARK, PlaceCategory.NATURE, PlaceCategory.BEACH -> TealSecondary
        PlaceCategory.SHOPPING -> Color(0xFF14B8A6)
        PlaceCategory.ENTERTAINMENT -> Color(0xFF8B5CF6)
        PlaceCategory.HOLIDAY -> Color(0xFFF97316)
        PlaceCategory.TRANSPORT -> Color(0xFF6366F1)
        PlaceCategory.VIEWPOINT -> GoldenAccent
        PlaceCategory.OTHER -> Slate600
    }

/**
 * Material-иконка для каждой категории расхода.
 */
val ExpenseCategory.icon: ImageVector
    get() = when (this) {
        ExpenseCategory.FOOD -> Icons.Rounded.Restaurant
        ExpenseCategory.TRANSPORT -> Icons.Rounded.DirectionsTransit
        ExpenseCategory.ACCOMMODATION -> Icons.Rounded.Hotel
        ExpenseCategory.ENTERTAINMENT -> Icons.Rounded.TheaterComedy
        ExpenseCategory.SHOPPING -> Icons.Rounded.ShoppingBag
        ExpenseCategory.TICKETS -> Icons.Rounded.ConfirmationNumber
        ExpenseCategory.GROCERIES -> Icons.Rounded.ShoppingCart
        ExpenseCategory.COFFEE -> Icons.Rounded.Coffee
        ExpenseCategory.DRINKS -> Icons.Rounded.LocalBar
        ExpenseCategory.TIPS -> Icons.Rounded.Payments
        ExpenseCategory.HEALTH -> Icons.Rounded.MedicalServices
        ExpenseCategory.SOUVENIRS -> Icons.Rounded.CardGiftcard
        ExpenseCategory.OTHER -> Icons.Rounded.Receipt
    }
