package com.flightlog.app.ui.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val authState: AuthUiState = AuthUiState.Idle,
    val email: String = "",
    val password: String = "",
    val isCreateAccount: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun toggleCreateAccount() {
        _uiState.update {
            it.copy(
                isCreateAccount = !it.isCreateAccount,
                authState = AuthUiState.Idle
            )
        }
    }

    fun signInWithEmail() {
        val state = _uiState.value
        val trimmedEmail = state.email.trim()

        if (trimmedEmail.isBlank()) {
            _uiState.update { it.copy(authState = AuthUiState.Error("Email is required")) }
            return
        }
        if (state.password.length < 6) {
            _uiState.update { it.copy(authState = AuthUiState.Error("Password must be at least 6 characters")) }
            return
        }

        _uiState.update { it.copy(authState = AuthUiState.Loading) }

        viewModelScope.launch {
            val result = if (state.isCreateAccount) {
                authRepository.createAccountWithEmail(trimmedEmail, state.password)
            } else {
                authRepository.signInWithEmail(trimmedEmail, state.password)
            }
            result.fold(
                onSuccess = { user ->
                    _uiState.update { it.copy(authState = AuthUiState.Success(user)) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(authState = AuthUiState.Error(e.message ?: "Authentication failed"))
                    }
                }
            )
        }
    }

    fun signInWithGoogle(idToken: String) {
        _uiState.update { it.copy(authState = AuthUiState.Loading) }

        viewModelScope.launch {
            authRepository.signInWithGoogle(idToken).fold(
                onSuccess = { user ->
                    _uiState.update { it.copy(authState = AuthUiState.Success(user)) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(authState = AuthUiState.Error(e.message ?: "Google sign-in failed"))
                    }
                }
            )
        }
    }

    fun signInWithGitHub(activity: Activity) {
        _uiState.update { it.copy(authState = AuthUiState.Loading) }

        viewModelScope.launch {
            authRepository.signInWithGitHub(activity).fold(
                onSuccess = { user ->
                    _uiState.update { it.copy(authState = AuthUiState.Success(user)) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(authState = AuthUiState.Error(e.message ?: "GitHub sign-in failed"))
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(authState = AuthUiState.Idle) }
    }
}
