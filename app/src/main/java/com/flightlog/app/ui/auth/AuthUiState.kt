package com.flightlog.app.ui.auth

import com.flightlog.app.data.auth.AuthUser

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data class Success(val user: AuthUser) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}
