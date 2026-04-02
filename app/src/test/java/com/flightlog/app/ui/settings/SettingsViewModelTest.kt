package com.flightlog.app.ui.settings

import com.flightlog.app.data.auth.AuthRepository
import com.flightlog.app.data.auth.AuthUser
import com.flightlog.app.data.backup.BackupMetadata
import com.flightlog.app.data.backup.BackupMetadataStore
import com.flightlog.app.data.backup.BackupResult
import com.flightlog.app.data.backup.DriveBackupService
import com.flightlog.app.data.backup.RestoreResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var driveBackupService: DriveBackupService
    private lateinit var metadataStore: BackupMetadataStore
    private val currentUserFlow = MutableStateFlow<AuthUser?>(null)

    private val googleUser = AuthUser(uid = "g1", email = "a@gmail.com", displayName = "Test", isGoogleProvider = true)
    private val emailUser = AuthUser(uid = "e1", email = "a@test.com", displayName = "Email", isGoogleProvider = false)
    private val sampleMetadata = BackupMetadata(lastBackupAt = 1_700_000_000_000L, flightCount = 42, fileSizeBytes = 12345L)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        driveBackupService = mockk(relaxed = true)
        metadataStore = mockk(relaxed = true)
        every { authRepository.currentUser } returns currentUserFlow
        every { metadataStore.get() } returns null
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun createViewModel() = SettingsViewModel(authRepository, driveBackupService, metadataStore)

    @Test
    fun `initial state with no user`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertNull(vm.uiState.value.authUser)
        assertNull(vm.uiState.value.backupMetadata)
        assertFalse(vm.uiState.value.isBackingUp)
        assertFalse(vm.uiState.value.isRestoring)
        assertNull(vm.uiState.value.snackbarMessage)
        job.cancel()
    }

    @Test
    fun `user login updates state with metadata`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        every { metadataStore.get() } returns sampleMetadata
        currentUserFlow.value = googleUser
        advanceUntilIdle()
        assertEquals(googleUser, vm.uiState.value.authUser)
        assertEquals(sampleMetadata, vm.uiState.value.backupMetadata)
        job.cancel()
    }

    @Test
    fun `signOut clears user and metadata`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        currentUserFlow.value = googleUser
        advanceUntilIdle()
        vm.signOut()
        advanceUntilIdle()
        assertNull(vm.uiState.value.authUser)
        assertNull(vm.uiState.value.backupMetadata)
        coVerify { authRepository.signOut() }
        verify { metadataStore.clear() }
        job.cancel()
    }

    @Test
    fun `backupNow with no user does nothing`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.backupNow()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isBackingUp)
        assertNull(vm.uiState.value.snackbarMessage)
        job.cancel()
    }

    @Test
    fun `backupNow with email user does nothing`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        currentUserFlow.value = emailUser
        advanceUntilIdle()
        vm.backupNow()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isBackingUp)
        assertNull(vm.uiState.value.snackbarMessage)
        job.cancel()
    }

    @Test
    fun `backupNow success shows message`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        currentUserFlow.value = googleUser
        advanceUntilIdle()
        every { metadataStore.get() } returns sampleMetadata
        coEvery { driveBackupService.backup() } returns BackupResult.Success(42, 12345L)
        vm.backupNow()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isBackingUp)
        assertEquals("Backed up 42 flights", vm.uiState.value.snackbarMessage)
        job.cancel()
    }

    @Test
    fun `backupNow failure shows error`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        currentUserFlow.value = googleUser
        advanceUntilIdle()
        coEvery { driveBackupService.backup() } returns BackupResult.Failure("Network error")
        vm.backupNow()
        advanceUntilIdle()
        assertEquals("Backup failed: Network error", vm.uiState.value.snackbarMessage)
        job.cancel()
    }

    @Test
    fun `restore success shows counts`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        currentUserFlow.value = googleUser
        advanceUntilIdle()
        coEvery { driveBackupService.restore() } returns RestoreResult.Success(30, 5)
        vm.restore()
        advanceUntilIdle()
        assertEquals("Imported 30 flights (5 skipped)", vm.uiState.value.snackbarMessage)
        job.cancel()
    }

    @Test
    fun `restore failure shows error`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        currentUserFlow.value = googleUser
        advanceUntilIdle()
        coEvery { driveBackupService.restore() } returns RestoreResult.Failure("No backup found")
        vm.restore()
        advanceUntilIdle()
        assertEquals("Restore failed: No backup found", vm.uiState.value.snackbarMessage)
        job.cancel()
    }

    @Test
    fun `clearSnackbar sets null`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        currentUserFlow.value = googleUser
        advanceUntilIdle()
        coEvery { driveBackupService.backup() } returns BackupResult.Success(1, 100L)
        every { metadataStore.get() } returns sampleMetadata
        vm.backupNow()
        advanceUntilIdle()
        vm.clearSnackbar()
        advanceUntilIdle()
        assertNull(vm.uiState.value.snackbarMessage)
        job.cancel()
    }
}
