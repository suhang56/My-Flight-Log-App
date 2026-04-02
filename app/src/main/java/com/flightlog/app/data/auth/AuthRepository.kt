package com.flightlog.app.data.auth

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val currentUser: StateFlow<AuthUser?>
    suspend fun signInWithGoogle(idToken: String): Result<AuthUser>
    suspend fun signInWithGitHub(activity: Activity): Result<AuthUser>
    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser>
    suspend fun createAccountWithEmail(email: String, password: String): Result<AuthUser>
    suspend fun signOut()
    fun isSignedIn(): Boolean
}
