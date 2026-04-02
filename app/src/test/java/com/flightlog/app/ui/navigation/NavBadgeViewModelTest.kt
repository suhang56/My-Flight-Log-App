package com.flightlog.app.ui.navigation

import com.flightlog.app.data.local.entity.Achievement
import com.flightlog.app.data.repository.AchievementRepository
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavBadgeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var achievementRepository: AchievementRepository
    private val achievementsFlow = MutableStateFlow<List<Achievement>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        achievementRepository = mockk(relaxed = true)
        every { achievementRepository.getAll() } returns achievementsFlow
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun createViewModel() = NavBadgeViewModel(achievementRepository)

    @Test
    fun `initial state is false`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.hasUnseenAchievements.collect {} }
        advanceUntilIdle()
        assertEquals(false, vm.hasUnseenAchievements.value)
        job.cancel()
    }

    @Test
    fun `empty list returns false`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.hasUnseenAchievements.collect {} }
        advanceUntilIdle()
        achievementsFlow.value = emptyList()
        advanceUntilIdle()
        assertEquals(false, vm.hasUnseenAchievements.value)
        job.cancel()
    }

    @Test
    fun `all locked returns false`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.hasUnseenAchievements.collect {} }
        advanceUntilIdle()
        achievementsFlow.value = listOf(
            Achievement(id = "first_flight", unlockedAt = null, seenByUser = false)
        )
        advanceUntilIdle()
        assertEquals(false, vm.hasUnseenAchievements.value)
        job.cancel()
    }

    @Test
    fun `unlocked unseen returns true`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.hasUnseenAchievements.collect {} }
        advanceUntilIdle()
        achievementsFlow.value = listOf(
            Achievement(id = "first_flight", unlockedAt = 1_700_000_000_000L, seenByUser = false)
        )
        advanceUntilIdle()
        assertEquals(true, vm.hasUnseenAchievements.value)
        job.cancel()
    }

    @Test
    fun `all seen returns false`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.hasUnseenAchievements.collect {} }
        advanceUntilIdle()
        achievementsFlow.value = listOf(
            Achievement(id = "first_flight", unlockedAt = 1_700_000_000_000L, seenByUser = true)
        )
        advanceUntilIdle()
        assertEquals(false, vm.hasUnseenAchievements.value)
        job.cancel()
    }

    @Test
    fun `mix of seen and unseen returns true`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.hasUnseenAchievements.collect {} }
        advanceUntilIdle()
        achievementsFlow.value = listOf(
            Achievement(id = "a", unlockedAt = 1L, seenByUser = true),
            Achievement(id = "b", unlockedAt = 2L, seenByUser = false)
        )
        advanceUntilIdle()
        assertEquals(true, vm.hasUnseenAchievements.value)
        job.cancel()
    }

    @Test
    fun `badge updates reactively on unlock`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.hasUnseenAchievements.collect {} }
        advanceUntilIdle()
        achievementsFlow.value = listOf(Achievement(id = "a", unlockedAt = null))
        advanceUntilIdle()
        assertEquals(false, vm.hasUnseenAchievements.value)
        achievementsFlow.value = listOf(Achievement(id = "a", unlockedAt = 1L, seenByUser = false))
        advanceUntilIdle()
        assertEquals(true, vm.hasUnseenAchievements.value)
        job.cancel()
    }

    @Test
    fun `badge updates reactively when user sees achievement`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.hasUnseenAchievements.collect {} }
        advanceUntilIdle()
        achievementsFlow.value = listOf(Achievement(id = "a", unlockedAt = 1L, seenByUser = false))
        advanceUntilIdle()
        assertEquals(true, vm.hasUnseenAchievements.value)
        achievementsFlow.value = listOf(Achievement(id = "a", unlockedAt = 1L, seenByUser = true))
        advanceUntilIdle()
        assertEquals(false, vm.hasUnseenAchievements.value)
        job.cancel()
    }

    @Test
    fun `seenByUser true but unlockedAt null returns false`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.hasUnseenAchievements.collect {} }
        advanceUntilIdle()
        achievementsFlow.value = listOf(Achievement(id = "a", unlockedAt = null, seenByUser = true))
        advanceUntilIdle()
        assertEquals(false, vm.hasUnseenAchievements.value)
        job.cancel()
    }
}
