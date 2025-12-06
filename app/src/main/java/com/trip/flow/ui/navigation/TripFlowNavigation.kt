package com.trip.flow.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trip.flow.ui.tripdetails.AddPlaceScreen
import com.trip.flow.ui.tripdetails.TripDetailsScreen
import com.trip.flow.ui.trips.CreateTripScreen
import com.trip.flow.ui.trips.TripListScreen

/**
 * Navigation routes for Trip Flow
 */
sealed class Screen(val route: String) {
    object TripList : Screen("trips")
    object CreateTrip : Screen("trips/create")
    object TripDetails : Screen("trips/{tripId}") {
        fun createRoute(tripId: String) = "trips/$tripId"
    }
    object AddPlace : Screen("trips/{tripId}/days/{dayId}/add-place") {
        fun createRoute(tripId: String, dayId: String) = "trips/$tripId/days/$dayId/add-place"
    }
    object AddExpense : Screen("trips/{tripId}/add-expense") {
        fun createRoute(tripId: String) = "trips/$tripId/add-expense"
    }
}

@Composable
fun TripFlowNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.TripList.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 3 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 3 },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        // Trip List (Home)
        composable(Screen.TripList.route) {
            TripListScreen(
                onNavigateToTrip = { tripId ->
                    navController.navigate(Screen.TripDetails.createRoute(tripId))
                },
                onNavigateToCreateTrip = {
                    navController.navigate(Screen.CreateTrip.route)
                }
            )
        }
        
        // Create Trip
        composable(
            route = Screen.CreateTrip.route,
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(350)
                ) + fadeIn()
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(350)
                ) + fadeOut()
            },
            popEnterTransition = { fadeIn() },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(350)
                ) + fadeOut()
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
        
        // Trip Details
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
                }
            )
        }
        
        // Add Place (placeholder)
        composable(
            route = Screen.AddPlace.route,
            arguments = listOf(
                navArgument("tripId") { type = NavType.StringType },
                navArgument("dayId") { type = NavType.StringType }
            ),
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(350)
                ) + fadeIn()
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(350)
                ) + fadeOut()
            }
        ) {
            val tripId = it.arguments?.getString("tripId") ?: return@composable
            val dayId = it.arguments?.getString("dayId") ?: return@composable

            AddPlaceScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Add Expense (placeholder)
        composable(
            route = Screen.AddExpense.route,
            arguments = listOf(
                navArgument("tripId") { type = NavType.StringType }
            ),
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(350)
                ) + fadeIn()
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(350)
                ) + fadeOut()
            }
        ) {
            // TODO: Implement AddExpenseScreen
        }
    }
}
