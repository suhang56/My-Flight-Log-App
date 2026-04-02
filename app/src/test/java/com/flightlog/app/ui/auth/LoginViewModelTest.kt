package com.flightlog.app.ui.auth

import android.app.Activity
import com.flightlog.app.data.auth.AuthRepository
import com.flightlog.app.data.auth.AuthUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: LoginViewModel

    private val testUser = AuthUser(
        uid = "test-uid",
        email = "test@example.com",
        displayName = "Test User",
        isGoogleProvider = false
    )

    private val googleUser = AuthUser(
        uid = "google-uid",
        email = "google@gmail.com",
        displayName = "Google User",
        isGoogleProvider = true
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        every { authRepository.currentUser } returns MutableStateFlow(null)
        viewModel = LoginViewModel(authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -- Email validation edge cases --

    @Test
    fun `empty email shows error`() = runTest {
        viewModel.onEmailChange("")
        viewModel.onPasswordChange("password123")
        viewModel.signInWithEmail()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.authState is AuthUiState.Error)
        assertEquals("Email is required", (state.authState as AuthUiState.Error).message)
    }

    @Test
    fun `whitespace-only email shows error`() = runTest {
        viewModel.onEmailChange("   ")
        viewModel.onPasswordChange("password123")
        viewModel.signInWithEmail()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.authState is AuthUiState.Error)
        assertEquals("Email is required", (state.authState as AuthUiState.Error).message)
    }

    @Test
    fun `email is trimmed before submission`() = runTest {
        coEvery { authRepository.signInWithEmail(any(), any()) } returns Result.success(testUser)

        viewModel.onEmailChange("  test@example.com  ")
        viewModel.onPasswordChange("password123")
        viewModel.signInWithEmail()
        advanceUntilIdle()

        coVerify { authRepository.signInWithEmail("test@example.com", "password123") }
    }

    // -- Password validation edge cases --

    @Test
    fun `empty password shows error`() = runTest {
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("")
        viewModel.signInWithEmail()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.authState is AuthUiState.Error)
        assertEquals("Password must be at least 6 characters", (state.authState as AuthUiState.Error).message)
    }

    @Test
    fun `password with 5 characters shows error`() = runTest {
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("12345")
        viewModel.signInWithEmail()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.authState is AuthUiState.Error)
        assertEquals("Password must be at least 6 characters", (state.authState as AuthUiState.Error).message)
    }

    @Test
    fun `password with exactly 6 characters passes validation`() = runTest {
        coEvery { authRepository.signInWithEmail(any(), any()) } returns Result.success(testUser)

        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("123456")
        viewModel.signInWithEmail()
        advanceUntilIdle()

        coVerify { authRepository.signInWithEmail("test@example.com", "123456") }
    }

    // -- Email sign-in success/failure --

    @Test
    fun `successful email sign-in emits Success state`() = runTest {
        coEvery { authRepository.signInWithEmail(any(), any()) } returns Result.success(testUser)

        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.signInWithEmail()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.authState is AuthUiState.Success)
        assertEquals(testUser, (state.authState as AuthUiState.Success).user)
    }

    @Test
    fun `failed email sign-in emits Error state`() = runTest {
        coEvery { authRepository.signInWithEmail(any(), any()) } returns
            Result.failure(RuntimeException("Invalid credentials"))

        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.signInWithEmail()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.authState is AuthUiState.Error)
        assertEquals("Invalid credentials", (state.authState as AuthUiState.Error).message)
    }

    @Test
    fun `failed sign-in with null message uses fallback`() = runTest {
        coEvery { authRepository.signInWithEmail(any(), any()) } returns
            Result.failure(RuntimeException())

        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.signInWithEmail()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.authState is AuthUiState.Error)
        assertEquals("Authentication failed", (state.authState as AuthUiState.Error).message)
    }

    // -- Create account mode --

    @Test
    fun `create account mode calls createAccountWithEmail`() = runTest {
        coEvery { authRepository.createAccountWithEmail(any(), any()) } returns Result.success(testUser)

        viewModel.toggleCreateAccount()
        viewModel.onEmailChange("new@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.signInWithEmail()
        advanceUntilIdle()

        coVerify { authRepository.createAccountWithEmail("new@example.com", "password123") }
    }

    @Test
    fun `toggle create account switches mode`() {
        assertFalse(viewModel.uiState.value.isCreateAccount)
        viewModel.toggleCreateAccount()
        assertTrue(viewModel.uiState.value.isCreateAccount)
        viewModel.toggleCreateAccount()
        assertFalse(viewModel.uiState.value.isCreateAccount)
    }

    @Test
    fun `toggle create account clears error state`() = runTest {
        viewModel.onEmailChange("")
        viewModel.signInWithEmail()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.authState is AuthUiState.Error)

        viewModel.toggleCreateAccount()
        assertTrue(viewModel.uiState.value.authState is AuthUiState.Idle)
    }

    // -- Google sign-in --

    @Test
    fun `successful Google sign-in emits Success`() = runTest {
        coEvery { authRepository.signInWithGoogle(any()) } returns Result.success(googleUser)

        viewModel.signInWithGoogle("id-token-123")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.authState is AuthUiState.Success)
        assertEquals(googleUser, (state.authState as AuthUiState.Success).user)
    }

    @Test
    fun `failed Google sign-in emits Error`() = runTest {
        coEvery { authRepository.signInWithGoogle(any()) } returns
            Result.failure(RuntimeException("Token expired"))

        viewModel.signInWithGoogle("expired-token")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.authState is AuthUiState.Error)
        assertEquals("Token expired", (state.authState as AuthUiState.Error).message)
    }

    @Test
    fun `Google sign-in with null error message uses fallback`() = runTest {
        coEvery { authRepository.signInWithGoogle(any()) } returns
            Result.failure(RuntimeException())

        viewModel.signInWithGoogle("token")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.authState is AuthUiState.Error)
        assertEquals("Google sign-in failed", (state.authState as AuthUiState.Error).message)
    }

    // -- GitHub sign-in --

    @Test
    fun `failed GitHub sign-in emits Error`() = runTest {
        val activity = mockk<Activity>()
        coEvery { authRepository.signInWithGitHub(any()) } returns
            Result.failure(RuntimeException("GitHub auth cancelled"))

        viewModel.signInWithGitHub(activity)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.authState is AuthUiState.Error)
        assertEquals("GitHub auth cancelled", (state.authState as AuthUiState.Error).message)
    }

    @Test
    fun `GitHub sign-in with null error message uses fallback`() = runTest {
        val activity = mockk<Activity>()
        coEvery { authRepository.signInWithGitHub(any()) } returns
            Result.failure(RuntimeException())

        viewModel.signInWithGitHub(activity)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.authState is AuthUiState.Error)
        assertEquals("GitHub sign-in failed", (state.authState as AuthUiState.Error).message)
    }

    // -- clearError --

    @Test
    fun `clearError resets to Idle`() = runTest {
        viewModel.onEmailChange("")
        viewModel.signInWithEmail()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.authState is AuthUiState.Error)

        viewModel.clearError()
        assertTrue(viewModel.uiState.value.authState is AuthUiState.Idle)
    }

    // -- GitHub OAuth cancelled mid-flow --

    @Test
    fun `GitHub cancelled returns clean error, not stuck loading`() = runTest {
        val activity = mockk<Activity>()
        coEvery { authRepository.signInWithGitHub(any()) } returns
            Result.failure(RuntimeException("User cancelled the sign-in flow"))

        viewModel.signInWithGitHub(activity)

        // Should be Loading immediately
        assertTrue(viewModel.uiState.value.authState is AuthUiState.Loading)

        advanceUntilIdle()

        // Should transition to Error, not remain Loading
        val state = viewModel.uiState.value
        assertTrue(state.authState is AuthUiState.Error)
        assertTrue((state.authState as AuthUiState.Error).message.contains("cancelled"))
    }

    // -- Network offline --

    @Test
    fun `network offline during email sign-in shows error`() = runTest {
        coEvery { authRepository.signInWithEmail(any(), any()) } returns
            Result.failure(java.net.UnknownHostException("Unable to resolve host"))

        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.signInWithEmail()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.authState is AuthUiState.Error)
        assertTrue((state.authState as AuthUiState.Error).message.contains("resolve host"))
    }

    @Test
    fun `network offline during Google sign-in shows error`() = runTest {
        coEvery { authRepository.signInWithGoogle(any()) } returns
            Result.failure(java.net.UnknownHostException("Network unavailable"))

        viewModel.signInWithGoogle("token")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.authState is AuthUiState.Error)
        assertTrue((state.authState as AuthUiState.Error).message.contains("Network unavailable"))
    }

    // -- Account linking conflict --

    @Test
    fun `same email Google and GitHub shows linking error`() = runTest {
        val activity = mockk<Activity>()
        coEvery { authRepository.signInWithGitHub(any()) } returns
            Result.failure(RuntimeException(
                "An account already exists with the same email address but different sign-in credentials"
            ))

        viewModel.signInWithGitHub(activity)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.authState is AuthUiState.Error)
        assertTrue((state.authState as AuthUiState.Error).message.contains("already exists"))
    }

    // -- Loading state transitions --

    @Test
    fun `sign-in sets Loading then transitions to final state`() = runTest {
        coEvery { authRepository.signInWithEmail(any(), any()) } returns Result.success(testUser)

        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.signInWithEmail()

        // Should be Loading before coroutine completes
        assertTrue(viewModel.uiState.value.authState is AuthUiState.Loading)

        advanceUntilIdle()

        // Should be Success after completion
        assertTrue(viewModel.uiState.value.authState is AuthUiState.Success)
    }
}
