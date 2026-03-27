package com.flightlog.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.local.model.AirlineCount
import com.flightlog.app.data.local.model.AirportCount
import com.flightlog.app.data.local.model.LabelCount
import com.flightlog.app.data.local.model.StatsData
import com.flightlog.app.data.repository.LogbookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    repository: LogbookRepository
) : ViewModel() {

    val stats: StateFlow<StatsData> = combine(
        repository.getCount(),
        repository.getTotalDistanceNm(),
        repository.getTotalDurationMinutes().map { it ?: 0L },
        repository.getDistinctAirportCodes().map { it.size },
        repository.getFlightsPerMonth()
    ) { count, distance, duration, airportCount, monthly ->
        StatsData(
            flightCount = count,
            totalDistanceNm = distance,
            totalDurationMinutes = duration,
            uniqueAirportCount = airportCount,
            monthlyFlightCounts = monthly
        )
    }.combine(
        combine(
            repository.getTopAirports(),
            repository.getDistinctAirlinePrefixes(),
            repository.getSeatClassBreakdown(),
            repository.getAircraftTypeDistribution(),
            repository.getLongestFlightByDistance()
        ) { topAirports, topAirlines, seatClass, aircraft, longest ->
            DetailStats(topAirports, topAirlines, seatClass, aircraft, longest)
        }
    ) { base, detail ->
        base.copy(
            topAirports = detail.topAirports,
            topAirlines = detail.topAirlines,
            seatClassBreakdown = detail.seatClassBreakdown,
            aircraftTypeDistribution = detail.aircraftTypeDistribution,
            longestFlight = detail.longestFlight
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StatsData()
    )
}

/** Intermediate holder to work around combine's 5-parameter limit. */
private data class DetailStats(
    val topAirports: List<AirportCount>,
    val topAirlines: List<AirlineCount>,
    val seatClassBreakdown: List<LabelCount>,
    val aircraftTypeDistribution: List<LabelCount>,
    val longestFlight: LogbookFlight?
)
