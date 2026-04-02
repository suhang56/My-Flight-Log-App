package com.flightlog.app.ui.home

import com.flightlog.app.ui.calendarflights.PermissionState

data class HomeUiState(
    val upcomingItems: List<UnifiedFlightItem> = emptyList(),
    val pastItems: List<UnifiedFlightItem> = emptyList(),
    val isRefreshing: Boolean = false,
    val permissionState: PermissionState = PermissionState.NotRequested,
    val searchQuery: String = ""
) {
    val routeSegments: List<RouteSegment>
        get() {
            val nextRoute = upcomingItems
                .sortedBy { it.sortKey }
                .firstOrNull { it.departureCode.isNotBlank() && it.arrivalCode.isNotBlank() }

            return (upcomingItems + pastItems)
                .filter { it.departureCode.isNotBlank() && it.arrivalCode.isNotBlank() }
                .map { it.departureCode to it.arrivalCode }
                .distinct()
                .map { (dep, arr) ->
                    RouteSegment(
                        departureCode = dep,
                        arrivalCode = arr,
                        isHighlighted = nextRoute != null &&
                            dep == nextRoute.departureCode && arr == nextRoute.arrivalCode
                    )
                }
        }
}
