package com.flightlog.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.flightlog.app.ui.calendarflights.CalendarFlightsScreen
import com.flightlog.app.ui.logbook.AddEditLogbookFlightScreen
import com.flightlog.app.ui.logbook.FlightDetailScreen
import com.flightlog.app.ui.logbook.LogbookScreen
import com.flightlog.app.ui.settings.SettingsScreen
import com.flightlog.app.ui.statistics.StatisticsScreen

object Routes {
    const val CALENDAR_FLIGHTS = "calendar_flights"
    const val LOGBOOK = "logbook"
    const val STATISTICS = "stats"
    const val LOGBOOK_ADD = "logbook/add"
    const val LOGBOOK_EDIT = "logbook/edit/{flightId}"
    const val LOGBOOK_DETAIL = "logbook/detail/{flightId}"
    const val SETTINGS = "settings"

    fun logbookEdit(flightId: Long) = "logbook/edit/$flightId"
    fun logbookDetail(flightId: Long) = "logbook/detail/$flightId"
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
private val hideBottomBarRoutes = setOf(Routes.LOGBOOK_ADD, Routes.LOGBOOK_EDIT, Routes.LOGBOOK_DETAIL, Routes.SETTINGS)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightNavGraph(
    navController: NavHostController,
    navBadgeViewModel: NavBadgeViewModel = hiltViewModel()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route !in hideBottomBarRoutes
    val hasUnseenAchievements by navBadgeViewModel.hasUnseenAchievements.collectAsState()

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        val showBadge = item.route == Routes.STATISTICS && hasUnseenAchievements
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
                                if (showBadge) {
                                    BadgedBox(badge = { Badge(modifier = Modifier.size(8.dp)) }) {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                }
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
                    onEditFlight = { id -> navController.navigate(Routes.logbookEdit(id)) },
                    onViewFlight = { id -> navController.navigate(Routes.logbookDetail(id)) },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
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
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.LOGBOOK_DETAIL,
                arguments = listOf(navArgument("flightId") { type = NavType.LongType })
            ) {
                FlightDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { id -> navController.navigate(Routes.logbookEdit(id)) }
                )
            }
        }
    }
}
