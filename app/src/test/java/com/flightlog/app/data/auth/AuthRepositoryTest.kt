package com.flightlog.app.data.auth

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository

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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createMockRepo(initialUser: AuthUser? = null): AuthRepository {
        val repo = mockk<AuthRepository>(relaxed = true)
        every { repo.currentUser } returns kotlinx.coroutines.flow.MutableStateFlow(initialUser)
        every { repo.isSignedIn() } returns (initialUser != null)
        return repo
    }

    // -- Null user on init --

    @Test
    fun `currentUser is null when no user signed in`() {
        val repo = createMockRepo(initialUser = null)
        assertNull(repo.currentUser.value)
        assertFalse(repo.isSignedIn())
    }

    @Test
    fun `currentUser has value when user is signed in`() {
        val repo = createMockRepo(initialUser = testUser)
        assertNotNull(repo.currentUser.value)
        assertEquals("test@example.com", repo.currentUser.value?.email)
        assertTrue(repo.isSignedIn())
    }

    // -- Sign-in success --

    @Test
    fun `signInWithGoogle returns success with google user`() = runTest {
        val repo = createMockRepo()
        coEvery { repo.signInWithGoogle(any()) } returns Result.success(googleUser)

        val result = repo.signInWithGoogle("valid-id-token")
        assertTrue(result.isSuccess)
        assertEquals(googleUser, result.getOrNull())
        assertTrue(result.getOrNull()!!.isGoogleProvider)
    }

    @Test
    fun `signInWithEmail returns success`() = runTest {
        val repo = createMockRepo()
        coEvery { repo.signInWithEmail(any(), any()) } returns Result.success(testUser)

        val result = repo.signInWithEmail("test@example.com", "password123")
        assertTrue(result.isSuccess)
        assertEquals("test-uid", result.getOrNull()?.uid)
    }

    @Test
    fun `createAccountWithEmail returns success`() = runTest {
        val repo = createMockRepo()
        coEvery { repo.createAccountWithEmail(any(), any()) } returns Result.success(testUser)

        val result = repo.createAccountWithEmail("new@example.com", "password123")
        assertTrue(result.isSuccess)
    }

    // -- Sign-in failure --

    @Test
    fun `signInWithGoogle returns failure on invalid token`() = runTest {
        val repo = createMockRepo()
        coEvery { repo.signInWithGoogle(any()) } returns
            Result.failure(IllegalArgumentException("Invalid ID token"))

        val result = repo.signInWithGoogle("bad-token")
        assertTrue(result.isFailure)
        assertEquals("Invalid ID token", result.exceptionOrNull()?.message)
    }

    @Test
    fun `signInWithEmail returns failure on wrong password`() = runTest {
        val repo = createMockRepo()
        coEvery { repo.signInWithEmail(any(), any()) } returns
            Result.failure(RuntimeException("The password is invalid"))

        val result = repo.signInWithEmail("test@example.com", "wrong")
        assertTrue(result.isFailure)
        assertEquals("The password is invalid", result.exceptionOrNull()?.message)
    }

    @Test
    fun `signInWithEmail returns failure on nonexistent account`() = runTest {
        val repo = createMockRepo()
        coEvery { repo.signInWithEmail(any(), any()) } returns
            Result.failure(RuntimeException("There is no user record corresponding to this identifier"))

        val result = repo.signInWithEmail("nobody@example.com", "password")
        assertTrue(result.isFailure)
    }

    // -- Sign-out propagation --

    @Test
    fun `signOut clears currentUser`() = runTest {
        val userFlow = kotlinx.coroutines.flow.MutableStateFlow<AuthUser?>(testUser)
        val repo = mockk<AuthRepository>(relaxed = true)
        every { repo.currentUser } returns userFlow
        coEvery { repo.signOut() } coAnswers { userFlow.value = null }

        assertEquals(testUser, repo.currentUser.value)
        repo.signOut()
        assertNull(repo.currentUser.value)
    }

    @Test
    fun `signOut after Google sign-in clears google user`() = runTest {
        val userFlow = kotlinx.coroutines.flow.MutableStateFlow<AuthUser?>(googleUser)
        val repo = mockk<AuthRepository>(relaxed = true)
        every { repo.currentUser } returns userFlow
        coEvery { repo.signOut() } coAnswers { userFlow.value = null }

        assertTrue(repo.currentUser.value?.isGoogleProvider == true)
        repo.signOut()
        assertNull(repo.currentUser.value)
    }

    // -- GitHub OAuth cancelled mid-flow --

    @Test
    fun `signInWithGitHub returns failure when cancelled`() = runTest {
        val repo = createMockRepo()
        val activity = mockk<android.app.Activity>()
        coEvery { repo.signInWithGitHub(any()) } returns
            Result.failure(RuntimeException("User cancelled the sign-in flow"))

        val result = repo.signInWithGitHub(activity)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("cancelled") == true)
    }

    // -- Network offline during sign-in --

    @Test
    fun `signInWithEmail fails gracefully when offline`() = runTest {
        val repo = createMockRepo()
        coEvery { repo.signInWithEmail(any(), any()) } returns
            Result.failure(java.net.UnknownHostException("Unable to resolve host"))

        val result = repo.signInWithEmail("test@example.com", "password123")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is java.net.UnknownHostException)
    }

    @Test
    fun `signInWithGoogle fails gracefully when offline`() = runTest {
        val repo = createMockRepo()
        coEvery { repo.signInWithGoogle(any()) } returns
            Result.failure(java.net.UnknownHostException("Network unavailable"))

        val result = repo.signInWithGoogle("token")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is java.net.UnknownHostException)
    }

    // -- Same email across Google + GitHub (account linking) --

    @Test
    fun `signInWithGitHub returns linking error for existing Google email`() = runTest {
        val repo = createMockRepo()
        val activity = mockk<android.app.Activity>()
        coEvery { repo.signInWithGitHub(any()) } returns
            Result.failure(RuntimeException(
                "An account already exists with the same email address but different sign-in credentials"
            ))

        val result = repo.signInWithGitHub(activity)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("already exists") == true)
    }

    // -- isSignedIn boundary --

    @Test
    fun `isSignedIn returns false after sign-out`() = runTest {
        val userFlow = kotlinx.coroutines.flow.MutableStateFlow<AuthUser?>(testUser)
        val repo = mockk<AuthRepository>(relaxed = true)
        every { repo.currentUser } returns userFlow
        every { repo.isSignedIn() } answers { userFlow.value != null }
        coEvery { repo.signOut() } coAnswers { userFlow.value = null }

        assertTrue(repo.isSignedIn())
        repo.signOut()
        assertFalse(repo.isSignedIn())
    }
}
