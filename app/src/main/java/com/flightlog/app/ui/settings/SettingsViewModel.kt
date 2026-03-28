package com.flightlog.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.backup.AutoBackupWorker
import com.flightlog.app.data.backup.BackupMetadata
import com.flightlog.app.data.backup.BackupMetadataStore
import com.flightlog.app.data.backup.BackupResult
import com.flightlog.app.data.backup.DriveBackupService
import com.flightlog.app.data.backup.RestoreResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val account: GoogleSignInAccount? = null,
    val backupMetadata: BackupMetadata? = null,
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val isSigningIn: Boolean = false,
    val snackbarMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveBackupService: DriveBackupService,
    private val metadataStore: BackupMetadataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        _uiState.update {
            it.copy(
                account = account,
                backupMetadata = metadataStore.get()
            )
        }
    }

    fun getSignInIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    fun onSignInResult(account: GoogleSignInAccount?) {
        _uiState.update {
            it.copy(
                account = account,
                isSigningIn = false
            )
        }
    }

    fun signOut() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        GoogleSignIn.getClient(context, gso).signOut().addOnCompleteListener {
            _uiState.update {
                it.copy(account = null, backupMetadata = null)
            }
            metadataStore.clear()
        }
    }

    fun backupNow() {
        val account = _uiState.value.account ?: return
        _uiState.update { it.copy(isBackingUp = true) }

        viewModelScope.launch {
            when (val result = driveBackupService.backup(account)) {
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
        val account = _uiState.value.account ?: return
        _uiState.update { it.copy(isRestoring = true) }

        viewModelScope.launch {
            when (val result = driveBackupService.restore(account)) {
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
