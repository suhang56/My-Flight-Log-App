package com.flightlog.app.repository

import com.flightlog.app.data.local.dao.LogbookFlightDao
import com.flightlog.app.data.repository.LogbookRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class LogbookRepositoryRatingTest {

    private lateinit var dao: LogbookFlightDao
    private lateinit var repository: LogbookRepository

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        repository = LogbookRepository(dao)
    }

    @Test
    fun `setRating passes valid rating to DAO`() = runTest {
        coEvery { dao.updateRating(any(), any()) } returns Unit

        repository.setRating(1L, 3)

        coVerify { dao.updateRating(1L, 3) }
    }

    @Test
    fun `setRating with null clears rating`() = runTest {
        coEvery { dao.updateRating(any(), any()) } returns Unit

        repository.setRating(1L, null)

        coVerify { dao.updateRating(1L, null) }
    }

    @Test
    fun `setRating clamps value below 1 to 1`() = runTest {
        coEvery { dao.updateRating(any(), any()) } returns Unit

        repository.setRating(1L, 0)

        coVerify { dao.updateRating(1L, 1) }
    }

    @Test
    fun `setRating clamps negative value to 1`() = runTest {
        coEvery { dao.updateRating(any(), any()) } returns Unit

        repository.setRating(1L, -5)

        coVerify { dao.updateRating(1L, 1) }
    }

    @Test
    fun `setRating clamps value above 5 to 5`() = runTest {
        coEvery { dao.updateRating(any(), any()) } returns Unit

        repository.setRating(1L, 10)

        coVerify { dao.updateRating(1L, 5) }
    }

    @Test
    fun `setRating accepts boundary value 1`() = runTest {
        coEvery { dao.updateRating(any(), any()) } returns Unit

        repository.setRating(1L, 1)

        coVerify { dao.updateRating(1L, 1) }
    }

    @Test
    fun `setRating accepts boundary value 5`() = runTest {
        coEvery { dao.updateRating(any(), any()) } returns Unit

        repository.setRating(1L, 5)

        coVerify { dao.updateRating(1L, 5) }
    }
}
