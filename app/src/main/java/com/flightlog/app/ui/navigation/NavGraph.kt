package com.flightlog.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.flightlog.app.ui.calendarflights.CalendarFlightsScreen
import com.flightlog.app.ui.logbook.AddEditLogbookFlightScreen
import com.flightlog.app.ui.logbook.LogbookScreen
import com.flightlog.app.ui.statistics.StatisticsScreen

object Routes {
    const val CALENDAR_FLIGHTS = "calendar_flights"
    const val LOGBOOK = "logbook"
    const val LOGBOOK_ADD = "logbook/add"
    const val LOGBOOK_EDIT = "logbook/edit/{id}"
    const val STATISTICS = "statistics"

    fun logbookEdit(id: Long) = "logbook/edit/$id"
}

@Composable
fun FlightNavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Routes.CALENDAR_FLIGHTS,
        modifier = modifier
    ) {
        composable(Routes.CALENDAR_FLIGHTS) {
            CalendarFlightsScreen()
        }
        composable(Routes.LOGBOOK) {
            LogbookScreen(
                onNavigateToAdd = { navController.navigate(Routes.LOGBOOK_ADD) },
                onNavigateToEdit = { id -> navController.navigate(Routes.logbookEdit(id)) }
            )
        }
        composable(Routes.LOGBOOK_ADD) {
            AddEditLogbookFlightScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.LOGBOOK_EDIT,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) {
            AddEditLogbookFlightScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.STATISTICS) {
            StatisticsScreen()
        }
    }
}
