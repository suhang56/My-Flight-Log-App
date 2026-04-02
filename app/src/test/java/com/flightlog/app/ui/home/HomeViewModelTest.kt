package com.flightlog.app.ui.home

import android.Manifest
import android.app.Application
import android.content.ContentResolver
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.repository.CalendarRepository
import com.flightlog.app.data.repository.LogbookRepository
import com.flightlog.app.data.repository.SyncResult
import com.flightlog.app.ui.calendarflights.PermissionState
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var application: Application
    private lateinit var contentResolver: ContentResolver
    private lateinit var calendarRepository: CalendarRepository
    private lateinit var logbookRepository: LogbookRepository

    private val calendarFlow = MutableStateFlow<List<CalendarFlight>>(emptyList())
    private val logbookFlow = MutableStateFlow<List<LogbookFlight>>(emptyList())

    private var currentTime = 1_000_000_000_000L
    private val clock: () -> Long = { currentTime }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        every { application.contentResolver } returns contentResolver

        calendarRepository = mockk(relaxed = true)
        logbookRepository = mockk(relaxed = true)

        every { calendarRepository.getAllVisible() } returns calendarFlow
        every { logbookRepository.getAll() } returns logbookFlow

        // Default: permission NOT granted (no sync on init)
        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(application, Manifest.permission.READ_CALENDAR)
        } returns PackageManager.PERMISSION_DENIED

        // Mock CalendarSyncWorker to avoid WorkManager init in tests
        mockkObject(CalendarSyncWorker)
        every { CalendarSyncWorker.enqueuePeriodicSync(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(ContextCompat::class)
        unmockkObject(CalendarSyncWorker)
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            application = application,
            calendarRepository = calendarRepository,
            logbookRepository = logbookRepository
        ).also { it.clock = clock }
    }

    // ── Helper factories ─────────────────────────────────────────────────────

    private fun calendarFlight(
        id: Long = 1,
        calendarEventId: Long = 100,
        legIndex: Int = 0,
        flightNumber: String = "NH847",
        depCode: String = "HND",
        arrCode: String = "LHR",
        scheduledTime: Long = 1_700_000_000_000L
    ) = CalendarFlight(
        id = id,
        calendarEventId = calendarEventId,
        legIndex = legIndex,
        flightNumber = flightNumber,
        departureCode = depCode,
        arrivalCode = arrCode,
        rawTitle = "$flightNumber $depCode-$arrCode",
        scheduledTime = scheduledTime
    )

    private fun logbookFlight(
        id: Long = 1,
        sourceCalendarEventId: Long? = null,
        sourceLegIndex: Int? = null,
        flightNumber: String = "NH847",
        depCode: String = "HND",
        arrCode: String = "LHR",
        departureTimeUtc: Long = 1_700_000_000_000L
    ) = LogbookFlight(
        id = id,
        sourceCalendarEventId = sourceCalendarEventId,
        sourceLegIndex = sourceLegIndex,
        flightNumber = flightNumber,
        departureCode = depCode,
        arrivalCode = arrCode,
        departureTimeUtc = departureTimeUtc
    )

    // ═══════════════════════════════════════════════════════════════════════
    // 1. combine() emission: one repo empty, other has items
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `empty calendar and logbook with items emits only logbook flights`() = runTest {
        val vm = createViewModel()
        // Keep a subscriber alive so WhileSubscribed upstream stays active
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        logbookFlow.value = listOf(
            logbookFlight(id = 1, departureTimeUtc = currentTime + 1000),
            logbookFlight(id = 2, flightNumber = "JL5", departureTimeUtc = currentTime + 2000)
        )
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.upcomingItems.size)
        assertTrue(state.upcomingItems.all { it is UnifiedFlightItem.FromLogbook })
        assertTrue(state.pastItems.isEmpty())

        job.cancel()
    }

    @Test
    fun `calendar with items and empty logbook emits only calendar flights`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        calendarFlow.value = listOf(
            calendarFlight(id = 1, calendarEventId = 100, scheduledTime = currentTime + 5000),
            calendarFlight(id = 2, calendarEventId = 200, scheduledTime = currentTime + 6000)
        )
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.upcomingItems.size)
        assertTrue(state.upcomingItems.all { it is UnifiedFlightItem.FromCalendar })
        assertTrue(state.pastItems.isEmpty())

        job.cancel()
    }

    @Test
    fun `both repos emit flights and merge de-duplicates`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        calendarFlow.value = listOf(
            calendarFlight(id = 1, calendarEventId = 100, legIndex = 0, scheduledTime = currentTime + 1000)
        )
        logbookFlow.value = listOf(
            logbookFlight(id = 10, sourceCalendarEventId = 100, sourceLegIndex = 0, departureTimeUtc = currentTime + 1000)
        )
        advanceUntilIdle()

        val state = vm.uiState.value
        val allItems = state.upcomingItems + state.pastItems
        assertEquals(1, allItems.size)
        assertTrue(allItems[0] is UnifiedFlightItem.FromLogbook)

        job.cancel()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. Search filtering
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `search filters by flight number case-insensitively`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        logbookFlow.value = listOf(
            logbookFlight(id = 1, flightNumber = "NH847", departureTimeUtc = currentTime + 1000),
            logbookFlight(id = 2, flightNumber = "JL5", depCode = "NRT", arrCode = "SFO", departureTimeUtc = currentTime + 2000)
        )
        advanceUntilIdle()

        vm.updateSearchQuery("nh")
        advanceUntilIdle()

        val state = vm.uiState.value
        val allItems = state.upcomingItems + state.pastItems
        assertEquals(1, allItems.size)
        assertEquals("NH847", allItems[0].flightNumber)

        job.cancel()
    }

    @Test
    fun `search filters by departure code`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        logbookFlow.value = listOf(
            logbookFlight(id = 1, depCode = "NRT", arrCode = "LHR", departureTimeUtc = currentTime + 1000),
            logbookFlight(id = 2, depCode = "HND", arrCode = "SFO", departureTimeUtc = currentTime + 2000)
        )
        advanceUntilIdle()

        vm.updateSearchQuery("NRT")
        advanceUntilIdle()

        val state = vm.uiState.value
        val allItems = state.upcomingItems + state.pastItems
        assertEquals(1, allItems.size)
        assertEquals("NRT", allItems[0].departureCode)

        job.cancel()
    }

    @Test
    fun `search filters by arrival code`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        logbookFlow.value = listOf(
            logbookFlight(id = 1, depCode = "HND", arrCode = "LHR", departureTimeUtc = currentTime + 1000),
            logbookFlight(id = 2, depCode = "HND", arrCode = "SFO", departureTimeUtc = currentTime + 2000)
        )
        advanceUntilIdle()

        vm.updateSearchQuery("sfo")
        advanceUntilIdle()

        val state = vm.uiState.value
        val allItems = state.upcomingItems + state.pastItems
        assertEquals(1, allItems.size)
        assertEquals("SFO", allItems[0].arrivalCode)

        job.cancel()
    }

    @Test
    fun `blank search query returns all items`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        logbookFlow.value = listOf(
            logbookFlight(id = 1, departureTimeUtc = currentTime + 1000),
            logbookFlight(id = 2, flightNumber = "JL5", departureTimeUtc = currentTime + 2000)
        )
        advanceUntilIdle()

        vm.updateSearchQuery("NH")
        advanceUntilIdle()
        assertEquals(1, (vm.uiState.value.upcomingItems + vm.uiState.value.pastItems).size)

        vm.updateSearchQuery("")
        advanceUntilIdle()
        assertEquals(2, (vm.uiState.value.upcomingItems + vm.uiState.value.pastItems).size)

        job.cancel()
    }

    @Test
    fun `search with whitespace-only query returns all items`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        logbookFlow.value = listOf(
            logbookFlight(id = 1, departureTimeUtc = currentTime + 1000),
            logbookFlight(id = 2, flightNumber = "JL5", departureTimeUtc = currentTime + 2000)
        )
        advanceUntilIdle()

        vm.updateSearchQuery("   ")
        advanceUntilIdle()

        val allItems = vm.uiState.value.upcomingItems + vm.uiState.value.pastItems
        assertEquals(2, allItems.size)

        job.cancel()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. Time boundary: flight exactly at current time
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `flight exactly at current time is classified as upcoming`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        logbookFlow.value = listOf(
            logbookFlight(id = 1, departureTimeUtc = currentTime)
        )
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.upcomingItems.size)
        assertTrue(state.pastItems.isEmpty())

        job.cancel()
    }

    @Test
    fun `flight one millisecond before current time is past`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        logbookFlow.value = listOf(
            logbookFlight(id = 1, departureTimeUtc = currentTime - 1)
        )
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.upcomingItems.isEmpty())
        assertEquals(1, state.pastItems.size)

        job.cancel()
    }

    @Test
    fun `flight one millisecond after current time is upcoming`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        logbookFlow.value = listOf(
            logbookFlight(id = 1, departureTimeUtc = currentTime + 1)
        )
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.upcomingItems.size)
        assertTrue(state.pastItems.isEmpty())

        job.cancel()
    }

    @Test
    fun `upcoming items sorted ascending, past items sorted descending`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        logbookFlow.value = listOf(
            logbookFlight(id = 1, departureTimeUtc = currentTime + 3000),
            logbookFlight(id = 2, flightNumber = "JL1", departureTimeUtc = currentTime + 1000),
            logbookFlight(id = 3, flightNumber = "JL2", departureTimeUtc = currentTime - 1000),
            logbookFlight(id = 4, flightNumber = "JL3", departureTimeUtc = currentTime - 3000)
        )
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.upcomingItems.size)
        assertTrue(state.upcomingItems[0].sortKey < state.upcomingItems[1].sortKey)
        assertEquals(2, state.pastItems.size)
        assertTrue(state.pastItems[0].sortKey > state.pastItems[1].sortKey)

        job.cancel()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. Concurrent sync: multiple refreshes don't duplicate items
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `multiple rapid refreshes do not duplicate items`() = runTest {
        every {
            ContextCompat.checkSelfPermission(application, Manifest.permission.READ_CALENDAR)
        } returns PackageManager.PERMISSION_GRANTED

        coEvery { calendarRepository.syncFromCalendar(any()) } returns SyncResult.Success(2, 0)

        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onRefresh(contentResolver)
        vm.onRefresh(contentResolver)
        vm.onRefresh(contentResolver)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(false, state.isRefreshing)

        job.cancel()
    }

    @Test
    fun `sync updates isRefreshing state correctly`() = runTest {
        every {
            ContextCompat.checkSelfPermission(application, Manifest.permission.READ_CALENDAR)
        } returns PackageManager.PERMISSION_GRANTED

        coEvery { calendarRepository.syncFromCalendar(any()) } returns SyncResult.Success(1, 0)

        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isRefreshing)

        job.cancel()
    }

    @Test
    fun `sync error sets sync message and resets refreshing`() = runTest {
        every {
            ContextCompat.checkSelfPermission(application, Manifest.permission.READ_CALENDAR)
        } returns PackageManager.PERMISSION_GRANTED

        coEvery { calendarRepository.syncFromCalendar(any()) } returns SyncResult.Error("Network error")

        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals("Network error", vm.syncMessage.value)
        assertEquals(false, vm.uiState.value.isRefreshing)

        job.cancel()
    }

    @Test
    fun `sync SecurityException sets permission to Denied`() = runTest {
        every {
            ContextCompat.checkSelfPermission(application, Manifest.permission.READ_CALENDAR)
        } returns PackageManager.PERMISSION_GRANTED

        coEvery {
            calendarRepository.syncFromCalendar(any())
        } returns SyncResult.Error("Permission revoked", SecurityException("revoked"))

        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.permissionState is PermissionState.Denied)

        job.cancel()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. Permission state: init with granted vs not-granted
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `init with permission NOT granted sets NotRequested and skips sync`() = runTest {
        every {
            ContextCompat.checkSelfPermission(application, Manifest.permission.READ_CALENDAR)
        } returns PackageManager.PERMISSION_DENIED

        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.permissionState is PermissionState.NotRequested)

        job.cancel()
    }

    @Test
    fun `init with permission granted sets Granted and triggers sync`() = runTest {
        every {
            ContextCompat.checkSelfPermission(application, Manifest.permission.READ_CALENDAR)
        } returns PackageManager.PERMISSION_GRANTED

        coEvery { calendarRepository.syncFromCalendar(any()) } returns SyncResult.Success(3, 0)

        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.permissionState is PermissionState.Granted)
        assertEquals("Synced 3 flights", vm.syncMessage.value)

        job.cancel()
    }

    @Test
    fun `onPermissionResult granted sets Granted state`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        coEvery { calendarRepository.syncFromCalendar(any()) } returns SyncResult.Success(0, 0)

        vm.onPermissionResult(granted = true, shouldShowRationale = false)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.permissionState is PermissionState.Granted)

        job.cancel()
    }

    @Test
    fun `onPermissionResult denied with rationale sets Denied`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onPermissionResult(granted = false, shouldShowRationale = true)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.permissionState is PermissionState.Denied)

        job.cancel()
    }

    @Test
    fun `onPermissionResult denied without rationale sets PermanentlyDenied`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onPermissionResult(granted = false, shouldShowRationale = false)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.permissionState is PermissionState.PermanentlyDenied)

        job.cancel()
    }

    @Test
    fun `onRefresh without permission does nothing`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onRefresh(contentResolver)
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isRefreshing)

        job.cancel()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 6. Edge cases
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `clearSyncMessage sets value to null`() = runTest {
        every {
            ContextCompat.checkSelfPermission(application, Manifest.permission.READ_CALENDAR)
        } returns PackageManager.PERMISSION_GRANTED

        coEvery { calendarRepository.syncFromCalendar(any()) } returns SyncResult.Success(1, 0)

        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals("Synced 1 flights", vm.syncMessage.value)
        vm.clearSyncMessage()
        assertEquals(null, vm.syncMessage.value)

        job.cancel()
    }

    @Test
    fun `sync with zero synced and zero removed shows No flights found`() = runTest {
        every {
            ContextCompat.checkSelfPermission(application, Manifest.permission.READ_CALENDAR)
        } returns PackageManager.PERMISSION_GRANTED

        coEvery { calendarRepository.syncFromCalendar(any()) } returns SyncResult.Success(0, 0)

        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals("No flights found", vm.syncMessage.value)

        job.cancel()
    }

    @Test
    fun `sync with removals shows synced and removed count`() = runTest {
        every {
            ContextCompat.checkSelfPermission(application, Manifest.permission.READ_CALENDAR)
        } returns PackageManager.PERMISSION_GRANTED

        coEvery { calendarRepository.syncFromCalendar(any()) } returns SyncResult.Success(5, 2)

        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals("Synced 5 flights, removed 2", vm.syncMessage.value)

        job.cancel()
    }

    @Test
    fun `routeSegments computed in uiState`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        logbookFlow.value = listOf(
            logbookFlight(id = 1, depCode = "HND", arrCode = "LHR", departureTimeUtc = currentTime + 1000)
        )
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.routeSegments.size)
        assertEquals("HND", state.routeSegments[0].departureCode)
        assertEquals("LHR", state.routeSegments[0].arrivalCode)

        job.cancel()
    }

    @Test
    fun `updateSearchQuery updates the query in state`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.updateSearchQuery("test query")
        advanceUntilIdle()

        assertEquals("test query", vm.uiState.value.searchQuery)

        job.cancel()
    }
}
