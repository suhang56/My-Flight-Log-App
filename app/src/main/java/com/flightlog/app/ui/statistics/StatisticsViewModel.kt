package com.flightlog.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.calendar.AirlineIataMap
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.local.model.AirlineCount
import com.flightlog.app.data.local.model.AirportCount
import com.flightlog.app.data.local.model.LabelCount
import com.flightlog.app.data.local.model.MonthlyCount
import com.flightlog.app.data.local.model.RouteCount
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
    repository: LogbookRepository,
    private val airlineIataMap: AirlineIataMap
) : ViewModel() {

    val stats: StateFlow<StatsData> = run {
        // Block A: basic counts
        val blockA = combine(
            repository.getCount(),
            repository.getTotalDistanceNm(),
            repository.getTotalDurationMinutes().map { it ?: 0L }
        ) { count, distance, duration ->
            Triple(count, distance, duration)
        }

        // Block B: airports, monthly, first flight
        val blockB = combine(
            repository.getDistinctAirportCodes().map { it.size },
            repository.getFlightsPerMonth(),
            repository.getFirstFlight()
        ) { airportCount, monthly, firstFlight ->
            Triple(airportCount, monthly, firstFlight)
        }

        // Block C: top airports, airlines, seat class
        val blockC = combine(
            repository.getTopAirports(),
            repository.getDistinctAirlinePrefixes(),
            repository.getSeatClassBreakdown()
        ) { topAirports, topAirlines, seatClass ->
            Triple(topAirports, topAirlines, seatClass)
        }

        // Block D: aircraft, longest by distance, longest by duration
        val blockD = combine(
            repository.getAircraftTypeDistribution(),
            repository.getLongestFlightByDistance(),
            repository.getLongestFlightByDuration()
        ) { aircraft, longestDist, longestDur ->
            Triple(aircraft, longestDist, longestDur)
        }

        // Block E: top routes
        val blockE = repository.getTopRoutes()

        blockA.combine(blockB) { a, b ->
            StatsData(
                flightCount = a.first,
                totalDistanceNm = a.second,
                totalDurationMinutes = a.third,
                uniqueAirportCount = b.first,
                monthlyFlightCounts = b.second,
                firstFlight = b.third
            )
        }.combine(blockC) { ab, c ->
            ab.copy(
                topAirports = c.first,
                topAirlines = c.second.map { it.copy(airline = airlineIataMap.getFullName(it.airline)) },
                seatClassBreakdown = c.third
            )
        }.combine(blockD) { abc, d ->
            abc.copy(
                aircraftTypeDistribution = d.first,
                longestFlight = d.second,
                longestFlightByDuration = d.third
            )
        }.combine(blockE) { abcd, e ->
            abcd.copy(topRoutes = e)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            StatsData()
        )
    }
}
