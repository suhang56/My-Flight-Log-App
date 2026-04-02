package com.flightlog.app.ui.logbook

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.export.ExportService
import com.flightlog.app.data.repository.LogbookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ExportState {
    data object Idle : ExportState()
    data object Loading : ExportState()
    data class Ready(val uri: Uri, val mimeType: String) : ExportState()
    data class Error(val message: String) : ExportState()
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val repository: LogbookRepository,
    private val exportService: ExportService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    fun exportCsv() {
        if (_exportState.value is ExportState.Loading) return
        _exportState.value = ExportState.Loading
        viewModelScope.launch {
            try {
                val flights = repository.getAllOnce()
                val file = exportService.exportToCsv(flights)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                _exportState.value = ExportState.Ready(uri, "text/csv")
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _exportState.value = ExportState.Error("Export failed. Please try again.")
            }
        }
    }

    fun exportJson() {
        if (_exportState.value is ExportState.Loading) return
        _exportState.value = ExportState.Loading
        viewModelScope.launch {
            try {
                val flights = repository.getAllOnce()
                val file = exportService.exportToJson(flights)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                _exportState.value = ExportState.Ready(uri, "application/json")
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _exportState.value = ExportState.Error("Export failed. Please try again.")
            }
        }
    }

    fun clearExportState() {
        _exportState.value = ExportState.Idle
    }
}
