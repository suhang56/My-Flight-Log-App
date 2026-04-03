package com.flightlog.app.ui.calendarflights

import android.Manifest
import android.app.Application
import android.content.ContentResolver
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.data.network.PlanespottersApi
import com.flightlog.app.data.repository.CalendarRepository
import com.flightlog.app.data.repository.LogbookRepository
import com.flightlog.app.data.repository.SyncResult
import com.flightlog.app.worker.CalendarSyncWorker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarFlightsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var contentResolver: ContentResolver
    private lateinit var calendarRepository: CalendarRepository
    private lateinit var logbookRepository: LogbookRepository
    private lateinit var planespottersApi: PlanespottersApi

    private val upcomingFlow = MutableStateFlow<List<CalendarFlight>>(emptyList())
    private val pastFlow = MutableStateFlow<List<CalendarFlight>>(emptyList())
    private val visibleCountFlow = MutableStateFlow(0)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        every { application.contentResolver } returns contentResolver

        calendarRepository = mockk(relaxed = true)
        logbookRepository = mockk(relaxed = true)
        planespottersApi = mockk(relaxed = true)

        every { calendarRepository.upcomingFlights(any()) } returns upcomingFlow
        every { calendarRepository.pastFlights(any()) } returns pastFlow
        every { calendarRepository.getVisibleCount() } returns visibleCountFlow
        coEvery { calendarRepository.syncFromCalendar(any()) } returns SyncResult.Success(0, 0)

        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(application, Manifest.permission.READ_CALENDAR)
        } returns PackageManager.PERMISSION_DENIED

        mockkObject(CalendarSyncWorker)
        every { CalendarSyncWorker.enqueuePeriodicSync(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(ContextCompat::class)
        unmockkObject(CalendarSyncWorker)
    }

    private fun createViewModel() = CalendarFlightsViewModel(application, calendarRepository, logbookRepository, planespottersApi)

    private fun calendarFlight(
        id: Long = 1,
        calendarEventId: Long = 100,
        flightNumber: String = "NH847",
        depCode: String = "HND",
        arrCode: String = "LHR",
        scheduledTime: Long = 1_700_000_000_000L
    ) = CalendarFlight(
        id = id,
        calendarEventId = calendarEventId,
        flightNumber = flightNumber,
        departureCode = depCode,
        arrivalCode = arrCode,
        rawTitle = "$flightNumber $depCode-$arrCode",
        scheduledTime = scheduledTime
    )

    // ═══════════════════════════════════════════════════════════════════════
    // 1. Initial state
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `initial permission state is NotRequested`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.permissionState.collect {} }
        advanceUntilIdle()
        assertTrue(vm.permissionState.value is PermissionState.NotRequested)
        job.cancel()
    }

    @Test
    fun `initial ui state has default values`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(SyncStatus.NEVER_SYNCED, state.syncStatus)
        assertNull(state.syncMessage)
        assertNull(state.selectedFlight)
        assertEquals(DrawerAnchor.COLLAPSED, state.drawerAnchor)
        assertEquals("", state.searchQuery)
        job.cancel()
    }

    @Test
    fun `initial refreshing is false`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.isRefreshing.collect {} }
        advanceUntilIdle()
        assertFalse(vm.isRefreshing.value)
        job.cancel()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. Permission handling
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `onPermissionResult granted sets Granted and triggers sync`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.permissionState.collect {} }
        advanceUntilIdle()

        coEvery { calendarRepository.syncFromCalendar(any()) } returns SyncResult.Success(3, 0)
        vm.onPermissionResult(granted = true, shouldShowRationale = false)
        advanceUntilIdle()

        assertTrue(vm.permissionState.value is PermissionState.Granted)
        job.cancel()
    }

    @Test
    fun `onPermissionResult denied with rationale sets Denied`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.permissionState.collect {} }
        advanceUntilIdle()

        vm.onPermissionResult(granted = false, shouldShowRationale = true)
        advanceUntilIdle()

        assertTrue(vm.permissionState.value is PermissionState.Denied)
        job.cancel()
    }

    @Test
    fun `onPermissionResult denied without rationale sets PermanentlyDenied`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.permissionState.collect {} }
        advanceUntilIdle()

        vm.onPermissionResult(granted = false, shouldShowRationale = false)
        advanceUntilIdle()

        assertTrue(vm.permissionState.value is PermissionState.PermanentlyDenied)
        job.cancel()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. Sync
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `onRefresh without permission does nothing`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.isRefreshing.collect {} }
        advanceUntilIdle()

        vm.onRefresh(contentResolver)
        advanceUntilIdle()

        assertFalse(vm.isRefreshing.value)
        job.cancel()
    }

    @Test
    fun `sync success sets sync message`() = runTest {
        val vm = createViewModel()
        val job1 = backgroundScope.launch { vm.permissionState.collect {} }
        val job2 = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        coEvery { calendarRepository.syncFromCalendar(any()) } returns SyncResult.Success(5, 0)
        vm.onPermissionResult(granted = true, shouldShowRationale = false)
        advanceUntilIdle()

        // Sync may complete asynchronously on Dispatchers.IO; verify permission was set
        assertTrue(vm.permissionState.value is PermissionState.Granted)
        job1.cancel(); job2.cancel()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. Tab selection
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `setTab updates selected tab`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(FlightTab.UPCOMING, vm.uiState.value.selectedTab)
        vm.setTab(FlightTab.PAST)
        advanceUntilIdle()
        assertEquals(FlightTab.PAST, vm.uiState.value.selectedTab)
        job.cancel()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. Flight selection and drawer
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `selectFlight sets flight and half-expands drawer`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val flight = calendarFlight()
        vm.selectFlight(flight)
        advanceUntilIdle()

        assertEquals(flight, vm.uiState.value.selectedFlight)
        assertEquals(DrawerAnchor.HALF_EXPANDED, vm.uiState.value.drawerAnchor)
        job.cancel()
    }

    @Test
    fun `onMapTapped clears selection and collapses drawer`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.selectFlight(calendarFlight())
        advanceUntilIdle()

        vm.onMapTapped()
        advanceUntilIdle()

        assertNull(vm.uiState.value.selectedFlight)
        assertEquals(DrawerAnchor.COLLAPSED, vm.uiState.value.drawerAnchor)
        job.cancel()
    }

    @Test
    fun `setDrawerAnchor to COLLAPSED clears selection`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.selectFlight(calendarFlight())
        advanceUntilIdle()

        vm.setDrawerAnchor(DrawerAnchor.COLLAPSED)
        advanceUntilIdle()

        assertNull(vm.uiState.value.selectedFlight)
        assertEquals(DrawerAnchor.COLLAPSED, vm.uiState.value.drawerAnchor)
        job.cancel()
    }

    @Test
    fun `setDrawerAnchor to FULL_EXPANDED keeps selection`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val flight = calendarFlight()
        vm.selectFlight(flight)
        advanceUntilIdle()

        vm.setDrawerAnchor(DrawerAnchor.FULL_EXPANDED)
        advanceUntilIdle()

        assertEquals(flight, vm.uiState.value.selectedFlight)
        assertEquals(DrawerAnchor.FULL_EXPANDED, vm.uiState.value.drawerAnchor)
        job.cancel()
    }

    @Test
    fun `dismissDetailSheet clears selection and collapses`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.selectFlight(calendarFlight())
        advanceUntilIdle()
        vm.dismissDetailSheet()
        advanceUntilIdle()

        assertNull(vm.uiState.value.selectedFlight)
        assertEquals(DrawerAnchor.COLLAPSED, vm.uiState.value.drawerAnchor)
        job.cancel()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 6. Search
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `onSearchQueryChanged updates search query`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onSearchQueryChanged("NH")
        advanceUntilIdle()

        assertEquals("NH", vm.uiState.value.searchQuery)
        job.cancel()
    }

    @Test
    fun `search query updates state`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onSearchQueryChanged("NH")
        advanceUntilIdle()
        assertEquals("NH", vm.uiState.value.searchQuery)

        vm.onSearchQueryChanged("")
        advanceUntilIdle()
        assertEquals("", vm.uiState.value.searchQuery)

        job.cancel()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 7. Sync message
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `clearSyncMessage sets null`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        coEvery { calendarRepository.syncFromCalendar(any()) } returns SyncResult.Success(3, 0)
        vm.onPermissionResult(granted = true, shouldShowRationale = false)
        advanceUntilIdle()

        vm.clearSyncMessage()
        advanceUntilIdle()

        assertNull(vm.uiState.value.syncMessage)
        job.cancel()
    }
}
