package com.flightlog.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.local.dao.AirlineCount
import com.flightlog.app.data.local.dao.MonthlyCount
import com.flightlog.app.data.local.dao.RouteCount
import com.flightlog.app.data.local.dao.SeatClassCount
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.repository.LogbookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StatsData(
    val flightCount: Int = 0,
    val totalDurationMinutes: Long = 0,
    val totalDistanceKm: Long = 0,
    val uniqueAirportCount: Int = 0,
    val seatClassCounts: List<SeatClassCount> = emptyList(),
    val topAirlines: List<AirlineCount> = emptyList(),
    val longestByDistance: LogbookFlight? = null,
    val longestByDuration: LogbookFlight? = null,
    val topRoutes: List<RouteCount> = emptyList(),
    val firstFlight: LogbookFlight? = null,
    val monthlyFlightCounts: List<MonthlyCount> = emptyList()
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    repository: LogbookRepository
) : ViewModel() {

    val stats: StateFlow<StatsData> = combine(
        repository.getFlightCount(),
        repository.getTotalDurationMinutes(),
        repository.getTotalDistanceKm(),
        repository.getUniqueAirportCount(),
        repository.getSeatClassCounts()
    ) { count, duration, distance, airports, seatClasses ->
        StatsPartial1(count, duration ?: 0L, distance ?: 0L, airports, seatClasses)
    }.combine(
        combine(
            repository.getTopAirlines(),
            repository.getLongestFlightByDistance(),
            repository.getLongestFlightByDuration(),
            repository.getTopRoutes(),
            repository.getFirstFlight()
        ) { airlines, longestDist, longestDur, routes, first ->
            StatsPartial2(airlines, longestDist, longestDur, routes, first)
        }
    ) { p1, p2 ->
        StatsPartial12(p1, p2)
    }.combine(
        repository.getMonthlyFlightCounts()
    ) { p12, monthly ->
        StatsData(
            flightCount = p12.p1.count,
            totalDurationMinutes = p12.p1.duration,
            totalDistanceKm = p12.p1.distance,
            uniqueAirportCount = p12.p1.airports,
            seatClassCounts = p12.p1.seatClasses,
            topAirlines = p12.p2.airlines,
            longestByDistance = p12.p2.longestDist,
            longestByDuration = p12.p2.longestDur,
            topRoutes = p12.p2.routes,
            firstFlight = p12.p2.first,
            monthlyFlightCounts = monthly
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsData())
}

private data class StatsPartial1(
    val count: Int,
    val duration: Long,
    val distance: Long,
    val airports: Int,
    val seatClasses: List<SeatClassCount>
)

private data class StatsPartial2(
    val airlines: List<AirlineCount>,
    val longestDist: LogbookFlight?,
    val longestDur: LogbookFlight?,
    val routes: List<RouteCount>,
    val first: LogbookFlight?
)

private data class StatsPartial12(
    val p1: StatsPartial1,
    val p2: StatsPartial2
)
