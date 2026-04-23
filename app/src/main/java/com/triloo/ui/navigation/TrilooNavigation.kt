package com.triloo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.triloo.ui.auth.AuthFlowScreen
import com.triloo.ui.grouptrips.GroupTripsScreen
import com.triloo.ui.invite.InviteScreen
import com.triloo.ui.qr.QrScannerScreen
import com.triloo.ui.relay.RelayScreen
import com.triloo.ui.settings.SettingsScreen
import com.triloo.ui.settings.PrivacyPolicyScreen
import com.triloo.ui.tripdetails.AddExpenseScreen
import com.triloo.ui.tripdetails.AddPlaceScreen
import com.triloo.ui.tripdetails.PlaceDetailsScreen
import com.triloo.ui.tripdetails.TripDetailsScreen
import com.triloo.ui.trips.CreateTripScreen
import com.triloo.ui.trips.TripListScreen
import com.triloo.ui.components.TrilooButton
import com.triloo.ui.theme.TrilooMotion
import com.triloo.ui.theme.TrilooTheme

/**
 * Навигационные маршруты приложения Triloo.
 */
sealed class Screen(val route: String) {
    object TripList : Screen("trips")
    object Auth : Screen("auth")
    object PrivacyPolicy : Screen("privacy-policy")
    object QrScanner : Screen("qr-scan?mode={mode}") {
        fun createRoute(mode: String) = "qr-scan?mode=$mode"
    }
    object CreateTrip : Screen("trips/create?isGroupTrip={isGroupTrip}") {
        fun createRoute(isGroupTrip: Boolean) = "trips/create?isGroupTrip=$isGroupTrip"
    }
    object EditTrip : Screen("trips/{tripId}/edit") {
        fun createRoute(tripId: String) = "trips/$tripId/edit"
    }
    object Settings : Screen("settings")
    object GroupTrips : Screen("group-trips")
    object Invite : Screen("trips/{tripId}/invite") {
        fun createRoute(tripId: String) = "trips/$tripId/invite"
    }
    object Relay : Screen("trips/{tripId}/relay") {
        fun createRoute(tripId: String) = "trips/$tripId/relay"
    }
    
    object TripDetails : Screen("trips/{tripId}") {
        fun createRoute(tripId: String) = "trips/$tripId"
    }
    object AddPlace : Screen("trips/{tripId}/days/{dayId}/add-place") {
        fun createRoute(tripId: String, dayId: String) = "trips/$tripId/days/$dayId/add-place"
    }
    object EditPlace : Screen("places/{placeId}/edit") {
        fun createRoute(placeId: String) = "places/$placeId/edit"
    }
    object PlaceDetails : Screen("places/{placeId}") {
        fun createRoute(placeId: String) = "places/$placeId"
    }
    object AddExpense : Screen("trips/{tripId}/add-expense") {
        fun createRoute(tripId: String) = "trips/$tripId/add-expense"
    }
    object EditExpense : Screen("trips/{tripId}/expenses/{expenseId}/edit") {
        fun createRoute(tripId: String, expenseId: String) = "trips/$tripId/expenses/$expenseId/edit"
    }
}

private enum class ScreenPresentation {
    PUSH,
    OVERLAY
}

private val overlayRoutes = setOf(
    Screen.CreateTrip.route,
    Screen.EditTrip.route,
    Screen.AddPlace.route,
    Screen.EditPlace.route,
    Screen.AddExpense.route,
    Screen.EditExpense.route
)

private fun String?.presentation(): ScreenPresentation {
    return if (this != null && this in overlayRoutes) {
        ScreenPresentation.OVERLAY
    } else {
        ScreenPresentation.PUSH
    }
}

@Composable
fun TrilooNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.TripList.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            val initialPresentation = initialState.destination.route.presentation()
            val targetPresentation = targetState.destination.route.presentation()
            when {
                targetPresentation == ScreenPresentation.OVERLAY -> TrilooMotion.enterBottomSheet()
                initialPresentation == ScreenPresentation.OVERLAY -> TrilooMotion.enterNavFromOverlay()
                else -> TrilooMotion.enterNavForward()
            }
        },
        exitTransition = {
            val initialPresentation = initialState.destination.route.presentation()
            val targetPresentation = targetState.destination.route.presentation()
            when {
                targetPresentation == ScreenPresentation.OVERLAY -> TrilooMotion.exitNavForOverlay()
                initialPresentation == ScreenPresentation.OVERLAY -> TrilooMotion.exitBottomSheet()
                else -> TrilooMotion.exitNavForward()
            }
        },
        popEnterTransition = {
            val initialPresentation = initialState.destination.route.presentation()
            val targetPresentation = targetState.destination.route.presentation()
            when {
                initialPresentation == ScreenPresentation.OVERLAY -> TrilooMotion.enterNavUnderOverlay()
                targetPresentation == ScreenPresentation.OVERLAY -> TrilooMotion.enterBottomSheet()
                else -> TrilooMotion.enterNavBack()
            }
        },
        popExitTransition = {
            val initialPresentation = initialState.destination.route.presentation()
            val targetPresentation = targetState.destination.route.presentation()
            when {
                initialPresentation == ScreenPresentation.OVERLAY -> TrilooMotion.exitBottomSheet()
                targetPresentation == ScreenPresentation.OVERLAY -> TrilooMotion.exitNavForOverlay()
                else -> TrilooMotion.exitNavBack()
            }
        }
    ) {
        // Список поездок на домашнем экране.
        composable(Screen.TripList.route) {
            TripListScreen(
                onNavigateToTrip = { tripId ->
                    navController.navigate(Screen.TripDetails.createRoute(tripId))
                },
                onNavigateToCreateTrip = { isGroupTrip ->
                    navController.navigate(Screen.CreateTrip.createRoute(isGroupTrip))
                },
                onNavigateToGroupTrips = {
                    navController.navigate(Screen.GroupTrips.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        // Настройки.
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToGroupTrips = {
                    navController.navigate(Screen.GroupTrips.route)
                },
                onNavigateToAuth = {
                    navController.navigate(Screen.Auth.route)
                },
                onNavigateToPrivacyPolicy = {
                    navController.navigate(Screen.PrivacyPolicy.route)
                }
            )
        }

        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Авторизация.
        composable(Screen.Auth.route) {
            AuthFlowScreen(
                onNavigateBack = { navController.popBackStack() },
                onAuthComplete = { navController.popBackStack() }
            )
        }

        // Групповые поездки.
        composable(Screen.GroupTrips.route) {
            val qrResultFlow = navController.currentBackStackEntry
                ?.savedStateHandle
                ?.getStateFlow("qr_result", null)
            val qrResult = qrResultFlow?.collectAsStateWithLifecycle()?.value

            GroupTripsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTrip = { tripId ->
                    navController.navigate(Screen.TripDetails.createRoute(tripId))
                },
                onScanInvite = {
                    navController.navigate(Screen.QrScanner.createRoute("invite"))
                },
                qrResult = qrResult,
                onConsumeQrResult = {
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("qr_result", null)
                }
            )
        }
        
        // Создание поездки.
        composable(
            route = Screen.CreateTrip.route,
            arguments = listOf(
                navArgument("isGroupTrip") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            ),
            enterTransition = {
                TrilooMotion.enterBottomSheet()
            },
            exitTransition = {
                TrilooMotion.exitBottomSheet()
            },
            popEnterTransition = { TrilooMotion.enterBottomSheet() },
            popExitTransition = {
                TrilooMotion.exitBottomSheet()
            }
        ) {
            CreateTripScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onTripCreated = { tripId ->
                    navController.navigate(Screen.TripDetails.createRoute(tripId)) {
                        popUpTo(Screen.TripList.route)
                    }
                }
            )
        }
        
        // Детали поездки.
        composable(
            route = Screen.TripDetails.route,
            arguments = listOf(
                navArgument("tripId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: return@composable
            
            TripDetailsScreen(
                tripId = tripId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToAddPlace = { id, dayId ->
                    navController.navigate(Screen.AddPlace.createRoute(id, dayId))
                },
                onNavigateToAddExpense = { id ->
                    navController.navigate(Screen.AddExpense.createRoute(id))
                },
                onNavigateToInvite = { id ->
                    navController.navigate(Screen.Invite.createRoute(id))
                },
                onNavigateToRelay = { id ->
                    navController.navigate(Screen.Relay.createRoute(id))
                },
                onNavigateToEditTrip = { id ->
                    navController.navigate(Screen.EditTrip.createRoute(id))
                },
                onNavigateToPlaceDetails = { placeId ->
                    navController.navigate(Screen.PlaceDetails.createRoute(placeId))
                },
                onNavigateToEditExpense = { id, expenseId ->
                    navController.navigate(Screen.EditExpense.createRoute(id, expenseId))
                },
                onNavigateToEditPlace = { placeId ->
                    navController.navigate(Screen.EditPlace.createRoute(placeId))
                }
            )
        }

        // Приглашение.
        composable(
            route = Screen.Invite.route,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
        ) {
            InviteScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Relay sync.
        composable(
            route = Screen.Relay.route,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
        ) {
            RelayScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // QR-сканер.
        composable(
            route = Screen.QrScanner.route,
            arguments = listOf(
                navArgument("mode") {
                    type = NavType.StringType
                    defaultValue = "relay"
                }
            )
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "relay"
            val title = when (mode) {
                "invite" -> "Сканировать приглашение"
                else -> "Сканировать QR"
            }

            QrScannerScreen(
                title = title,
                onNavigateBack = { navController.popBackStack() },
                onResult = { result ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("qr_result", result)
                    navController.popBackStack()
                }
            )
        }
        
        // Добавление места.
        composable(
            route = Screen.AddPlace.route,
            arguments = listOf(
                navArgument("tripId") { type = NavType.StringType },
                navArgument("dayId") { type = NavType.StringType }
            ),
            enterTransition = {
                TrilooMotion.enterBottomSheet()
            },
            exitTransition = { TrilooMotion.exitBottomSheet() },
            popExitTransition = {
                TrilooMotion.exitBottomSheet()
            }
        ) {
            AddPlaceScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Редактирование места.
        composable(
            route = Screen.EditPlace.route,
            arguments = listOf(
                navArgument("placeId") { type = NavType.StringType }
            ),
            enterTransition = {
                TrilooMotion.enterBottomSheet()
            },
            exitTransition = { TrilooMotion.exitBottomSheet() },
            popExitTransition = {
                TrilooMotion.exitBottomSheet()
            }
        ) {
            AddPlaceScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Редактирование поездки.
        composable(
            route = Screen.EditTrip.route,
            arguments = listOf(
                navArgument("tripId") { type = NavType.StringType }
            ),
            enterTransition = {
                TrilooMotion.enterBottomSheet()
            },
            exitTransition = { TrilooMotion.exitBottomSheet() },
            popExitTransition = {
                TrilooMotion.exitBottomSheet()
            }
        ) {
            CreateTripScreen(
                onNavigateBack = { navController.popBackStack() },
                onTripCreated = { navController.popBackStack() }
            )
        }
        
        // Детали места.
        composable(
            route = Screen.PlaceDetails.route,
            arguments = listOf(
                navArgument("placeId") { type = NavType.StringType }
            )
        ) {
            PlaceDetailsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { placeId ->
                    navController.navigate(Screen.EditPlace.createRoute(placeId))
                }
            )
        }
        
        // Добавление расхода.
        composable(
            route = Screen.AddExpense.route,
            arguments = listOf(
                navArgument("tripId") { type = NavType.StringType }
            ),
            enterTransition = {
                TrilooMotion.enterBottomSheet()
            },
            exitTransition = { TrilooMotion.exitBottomSheet() },
            popExitTransition = {
                TrilooMotion.exitBottomSheet()
            }
        ) {
            AddExpenseScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Редактирование расхода.
        composable(
            route = Screen.EditExpense.route,
            arguments = listOf(
                navArgument("tripId") { type = NavType.StringType },
                navArgument("expenseId") { type = NavType.StringType }
            ),
            enterTransition = {
                TrilooMotion.enterBottomSheet()
            },
            exitTransition = { TrilooMotion.exitBottomSheet() },
            popExitTransition = {
                TrilooMotion.exitBottomSheet()
            }
        ) {
            AddExpenseScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TrilooNavHostPreview() {
    TrilooTheme {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = "preview"
        ) {
            composable("preview") {
                Surface(modifier = Modifier.padding(20.dp)) {
                    Column {
                        Text(
                            text = "Навигация Triloo",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TrilooButton(
                            text = "Открыть второй экран",
                            onClick = { navController.navigate("second") }
                        )
                    }
                }
            }
            composable("second") {
                Surface(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Второй экран превью",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
