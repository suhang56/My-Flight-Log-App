package com.flightlog.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.auth.AuthRepository
import com.flightlog.app.data.auth.AuthUser
import com.flightlog.app.data.backup.BackupMetadata
import com.flightlog.app.data.backup.BackupMetadataStore
import com.flightlog.app.data.backup.BackupResult
import com.flightlog.app.data.backup.DriveBackupService
import com.flightlog.app.data.backup.RestoreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val authUser: AuthUser? = null,
    val backupMetadata: BackupMetadata? = null,
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val snackbarMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val driveBackupService: DriveBackupService,
    private val metadataStore: BackupMetadataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.update {
                    it.copy(
                        authUser = user,
                        backupMetadata = if (user != null) metadataStore.get() else null
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            metadataStore.clear()
            _uiState.update { it.copy(authUser = null, backupMetadata = null) }
        }
    }

    fun backupNow() {
        val user = _uiState.value.authUser ?: return
        if (!user.isGoogleProvider) return
        _uiState.update { it.copy(isBackingUp = true) }

        viewModelScope.launch {
            when (val result = driveBackupService.backup(user)) {
                is BackupResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBackingUp = false,
                            backupMetadata = metadataStore.get(),
                            snackbarMessage = "Backed up ${result.flightCount} flights"
                        )
                    }
                }
                is BackupResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isBackingUp = false,
                            snackbarMessage = "Backup failed: ${result.reason}"
                        )
                    }
                }
            }
        }
    }

    fun restore() {
        val user = _uiState.value.authUser ?: return
        if (!user.isGoogleProvider) return
        _uiState.update { it.copy(isRestoring = true) }

        viewModelScope.launch {
            when (val result = driveBackupService.restore(user)) {
                is RestoreResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            snackbarMessage = "Imported ${result.imported} flights (${result.skipped} skipped)"
                        )
                    }
                }
                is RestoreResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            snackbarMessage = "Restore failed: ${result.reason}"
                        )
                    }
                }
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
