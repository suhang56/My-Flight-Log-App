package com.flightlog.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.flightlog.app.ui.calendarflights.CalendarFlightsScreen
import com.flightlog.app.ui.logbook.AddEditLogbookFlightScreen
import com.flightlog.app.ui.logbook.LogbookScreen
import com.flightlog.app.ui.statistics.StatisticsScreen

object Routes {
    const val CALENDAR_FLIGHTS = "calendar_flights"
    const val LOGBOOK = "logbook"
    const val STATISTICS = "statistics"
    const val LOGBOOK_ADD = "logbook/add"
    const val LOGBOOK_EDIT = "logbook/edit/{flightId}"

    fun logbookEdit(flightId: Long) = "logbook/edit/$flightId"
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(
        route = Routes.CALENDAR_FLIGHTS,
        label = "Calendar",
        selectedIcon = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth
    ),
    BottomNavItem(
        route = Routes.LOGBOOK,
        label = "Logbook",
        selectedIcon = Icons.Filled.Book,
        unselectedIcon = Icons.Outlined.Book
    ),
    BottomNavItem(
        route = Routes.STATISTICS,
        label = "Stats",
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart
    )
)

/** Routes that should hide the bottom navigation bar. */
private val hideBottomBarRoutes = setOf(Routes.LOGBOOK_ADD, Routes.LOGBOOK_EDIT)

@Composable
fun FlightNavGraph(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route !in hideBottomBarRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CALENDAR_FLIGHTS,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.CALENDAR_FLIGHTS) {
                CalendarFlightsScreen()
            }
            composable(Routes.LOGBOOK) {
                LogbookScreen(
                    onAddFlight = { navController.navigate(Routes.LOGBOOK_ADD) },
                    onEditFlight = { id -> navController.navigate(Routes.logbookEdit(id)) }
                )
            }
            composable(Routes.STATISTICS) {
                StatisticsScreen()
            }
            composable(Routes.LOGBOOK_ADD) {
                AddEditLogbookFlightScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.LOGBOOK_EDIT,
                arguments = listOf(navArgument("flightId") { type = NavType.LongType })
            ) {
                AddEditLogbookFlightScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
