package com.flightlog.app.data.repository

import com.flightlog.app.data.local.dao.AirportDao
import com.flightlog.app.data.local.entity.Airport
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AirportRepositoryTest {

    private lateinit var dao: AirportDao
    private lateinit var repository: AirportRepository

    private val nrt = Airport(
        iata = "NRT", icao = "RJAA", name = "Narita International Airport",
        city = "Tokyo", country = "JP", lat = 35.7647, lng = 140.3864,
        timezone = "Asia/Tokyo"
    )

    private val lax = Airport(
        iata = "LAX", icao = "KLAX", name = "Los Angeles International Airport",
        city = "Los Angeles", country = "US", lat = 33.9425, lng = -118.4081,
        timezone = "America/Los_Angeles"
    )

    @Before
    fun setup() {
        dao = mockk()
        repository = AirportRepository(dao)
    }

    // -- getByIata --

    @Test
    fun `getByIata normalizes lowercase to uppercase`() = runTest {
        coEvery { dao.getByIata("NRT") } returns nrt
        val result = repository.getByIata("nrt")
        assertNotNull(result)
        assertEquals("NRT", result?.iata)
    }

    @Test
    fun `getByIata normalizes mixed case to uppercase`() = runTest {
        coEvery { dao.getByIata("NRT") } returns nrt
        val result = repository.getByIata("Nrt")
        assertNotNull(result)
        assertEquals("NRT", result?.iata)
    }

    @Test
    fun `getByIata returns null for empty string`() = runTest {
        val result = repository.getByIata("")
        assertNull(result)
    }

    @Test
    fun `getByIata returns null for whitespace only`() = runTest {
        val result = repository.getByIata("   ")
        assertNull(result)
    }

    @Test
    fun `getByIata trims whitespace`() = runTest {
        coEvery { dao.getByIata("NRT") } returns nrt
        val result = repository.getByIata(" NRT ")
        assertNotNull(result)
    }

    @Test
    fun `getByIata falls back to static map when not in DB`() = runTest {
        // JFK is in the static AirportCoordinatesMap but we mock DAO to return null
        coEvery { dao.getByIata("JFK") } returns null
        val result = repository.getByIata("JFK")
        // Static fallback should produce a synthetic Airport
        assertNotNull(result)
        assertEquals("JFK", result?.iata)
    }

    @Test
    fun `getByIata returns null for completely unknown code`() = runTest {
        coEvery { dao.getByIata("ZZZ") } returns null
        val result = repository.getByIata("ZZZ")
        assertNull(result)
    }

    // -- search --

    @Test
    fun `search returns empty list for empty query`() = runTest {
        val result = repository.search("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `search returns empty list for single character query`() = runTest {
        val result = repository.search("A")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `search returns empty list for whitespace-only query under 2 chars`() = runTest {
        val result = repository.search(" ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `search calls DAO for queries of 2 or more characters`() = runTest {
        coEvery { dao.search("To") } returns listOf(nrt)
        val result = repository.search("To")
        assertEquals(1, result.size)
        assertEquals("NRT", result[0].iata)
    }

    @Test
    fun `search trims whitespace before length check`() = runTest {
        // " A " trims to "A" which is 1 char, should return empty
        val result = repository.search(" A ")
        assertTrue(result.isEmpty())
    }

    // -- distanceNm --

    @Test
    fun `distanceNm returns null when departure unknown`() = runTest {
        coEvery { dao.getByIata("ZZZ") } returns null
        coEvery { dao.getByIata("NRT") } returns nrt
        val result = repository.distanceNm("ZZZ", "NRT")
        assertNull(result)
    }

    @Test
    fun `distanceNm returns null when arrival unknown`() = runTest {
        coEvery { dao.getByIata("NRT") } returns nrt
        coEvery { dao.getByIata("ZZZ") } returns null
        val result = repository.distanceNm("NRT", "ZZZ")
        assertNull(result)
    }

    @Test
    fun `distanceNm computes reasonable distance between NRT and LAX`() = runTest {
        coEvery { dao.getByIata("NRT") } returns nrt
        coEvery { dao.getByIata("LAX") } returns lax
        val distance = repository.distanceNm("NRT", "LAX")
        assertNotNull(distance)
        // NRT-LAX is approximately 4730 NM
        assertTrue("Distance should be between 4500 and 5000 NM, was $distance",
            distance!! in 4500..5000)
    }

    @Test
    fun `distanceNm returns 0 for same airport`() = runTest {
        coEvery { dao.getByIata("NRT") } returns nrt
        val distance = repository.distanceNm("NRT", "NRT")
        assertNotNull(distance)
        assertEquals(0, distance)
    }

    // -- isKnownAirport --

    @Test
    fun `isKnownAirport returns true for known airport`() = runTest {
        coEvery { dao.getByIata("NRT") } returns nrt
        assertTrue(repository.isKnownAirport("NRT"))
    }

    @Test
    fun `isKnownAirport returns false for unknown airport`() = runTest {
        coEvery { dao.getByIata("ZZZ") } returns null
        assertTrue(!repository.isKnownAirport("ZZZ"))
    }

    // -- resolveCity --

    @Test
    fun `resolveCity returns null for empty string`() = runTest {
        val result = repository.resolveCity("")
        assertNull(result)
    }

    @Test
    fun `resolveCity returns null for whitespace only`() = runTest {
        val result = repository.resolveCity("   ")
        assertNull(result)
    }

    @Test
    fun `resolveCity returns IATA from DB search`() = runTest {
        coEvery { dao.search("Tokyo") } returns listOf(nrt)
        val result = repository.resolveCity("Tokyo")
        assertEquals("NRT", result)
    }

    // -- Airport with null timezone --

    @Test
    fun `getByIata returns airport with null timezone`() = runTest {
        val noTz = nrt.copy(timezone = null)
        coEvery { dao.getByIata("NRT") } returns noTz
        val result = repository.getByIata("NRT")
        assertNotNull(result)
        assertNull(result?.timezone)
    }
}
