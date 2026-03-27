package com.flightlog.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.flightlog.app.ui.calendarflights.CalendarFlightsScreen

object Routes {
    const val CALENDAR_FLIGHTS = "calendar_flights"
}

@Composable
fun FlightNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.CALENDAR_FLIGHTS
    ) {
        composable(Routes.CALENDAR_FLIGHTS) {
            CalendarFlightsScreen()
        }
    }
}
