package com.flightlog.app.ui.home

import com.flightlog.app.ui.calendarflights.PermissionState

data class HomeUiState(
    val upcomingItems: List<UnifiedFlightItem> = emptyList(),
    val pastItems: List<UnifiedFlightItem> = emptyList(),
    val isRefreshing: Boolean = false,
    val permissionState: PermissionState = PermissionState.NotRequested,
    val syncMessage: String? = null,
    val searchQuery: String = ""
) {
    val allRoutes: List<Pair<String, String>>
        get() = (upcomingItems + pastItems)
            .filter { it.departureCode.isNotBlank() && it.arrivalCode.isNotBlank() }
            .map { it.departureCode to it.arrivalCode }

    val nextUpcomingRoute: Pair<String, String>?
        get() = upcomingItems
            .sortedBy { it.sortKey }
            .firstOrNull { it.departureCode.isNotBlank() && it.arrivalCode.isNotBlank() }
            ?.let { it.departureCode to it.arrivalCode }
}
