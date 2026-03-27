package com.flightlog.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.flightlog.app.ui.flights.FlightDetailScreen
import com.flightlog.app.ui.flights.FlightsScreen

object Routes {
    const val FLIGHTS = "flights"
    const val FLIGHT_DETAIL = "flight/{flightId}"

    fun flightDetail(flightId: Long) = "flight/$flightId"
}

@Composable
fun FlightNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.FLIGHTS
    ) {
        composable(Routes.FLIGHTS) {
            FlightsScreen(
                onFlightClick = { flightId ->
                    navController.navigate(Routes.flightDetail(flightId))
                }
            )
        }

        composable(
            route = Routes.FLIGHT_DETAIL,
            arguments = listOf(
                navArgument("flightId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val flightId = backStackEntry.arguments?.getLong("flightId") ?: return@composable
            FlightDetailScreen(
                flightId = flightId,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
