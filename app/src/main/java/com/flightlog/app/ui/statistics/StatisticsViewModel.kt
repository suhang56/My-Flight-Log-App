package com.flightlog.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.local.model.StatsData
import com.flightlog.app.data.repository.LogbookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    repository: LogbookRepository
) : ViewModel() {

    val stats: StateFlow<StatsData> = combine(
        repository.getCount(),
        repository.getTotalDistanceNm(),
        repository.getTotalFlightTimeMinutes(),
        repository.getUniqueAirportCount(),
        repository.getMonthlyFlightCounts()
    ) { count, distance, flightTime, airports, monthly ->
        StatsData(
            flightCount = count,
            totalDistanceNm = distance,
            totalFlightTimeMinutes = flightTime,
            uniqueAirportCount = airports,
            monthlyFlightCounts = monthly
        )
    }.combine(
        combine(
            repository.getTopAirports(),
            repository.getTopAirlines(),
            repository.getSeatClassDistribution(),
            repository.getAircraftTypeDistribution(),
            repository.getLongestFlight()
        ) { topAirports, topAirlines, seatClass, aircraft, longest ->
            DetailStats(topAirports, topAirlines, seatClass, aircraft, longest)
        }
    ) { base, detail ->
        base.copy(
            topAirports = detail.topAirports,
            topAirlines = detail.topAirlines,
            seatClassDistribution = detail.seatClassDistribution,
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
    val topAirports: List<com.flightlog.app.data.local.model.AirportCount>,
    val topAirlines: List<com.flightlog.app.data.local.model.AirlineCount>,
    val seatClassDistribution: List<com.flightlog.app.data.local.model.LabelCount>,
    val aircraftTypeDistribution: List<com.flightlog.app.data.local.model.LabelCount>,
    val longestFlight: com.flightlog.app.data.local.entity.LogbookFlight?
)
