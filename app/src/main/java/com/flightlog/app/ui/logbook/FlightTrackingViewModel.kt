package com.flightlog.app.ui.logbook

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.local.entity.FlightStatus
import com.flightlog.app.data.network.FlightStatusEnum
import com.flightlog.app.data.repository.FlightStatusRepository
import com.flightlog.app.worker.FlightTrackingWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FlightTrackingViewModel @Inject constructor(
    private val flightStatusRepository: FlightStatusRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val flightId: Long = checkNotNull(savedStateHandle["flightId"])

    val flightStatus: StateFlow<FlightStatus?> = flightStatusRepository.getByFlightId(flightId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun startTracking(context: Context, flightNumber: String) {
        viewModelScope.launch {
            // Create initial status row
            val existing = flightStatusRepository.getByFlightIdOnce(flightId)
            if (existing == null) {
                flightStatusRepository.upsert(
                    FlightStatus(
                        logbookFlightId = flightId,
                        flightNumber = flightNumber,
                        statusEnum = FlightStatusEnum.SCHEDULED.name,
                        lastPolledAt = System.currentTimeMillis(),
                        trackingEnabled = true
                    )
                )
            } else if (!existing.trackingEnabled) {
                flightStatusRepository.upsert(existing.copy(trackingEnabled = true))
            }
            FlightTrackingWorker.enqueue(context, flightId, flightNumber)
        }
    }

    fun stopTracking(context: Context) {
        viewModelScope.launch {
            flightStatusRepository.disableTracking(flightId)
            FlightTrackingWorker.cancelWorker(context, flightId)
        }
    }
}
