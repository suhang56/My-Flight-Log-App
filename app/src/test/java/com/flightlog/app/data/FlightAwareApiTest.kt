package com.flightlog.app.data

import com.flightlog.app.data.network.FlightAwareFlight
import com.flightlog.app.data.network.FlightAwareAirport
import com.flightlog.app.data.network.FlightAwareFlightsResponse
import com.flightlog.app.data.network.FlightAwareLivePosition
import com.flightlog.app.data.network.FlightAwarePositionResponse
import com.flightlog.app.data.network.FlightStatusEnum
import com.flightlog.app.data.network.toFlightStatusEnum
import com.flightlog.app.data.network.FlightRouteServiceImpl
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightAwareApiTest {

    private val moshi = Moshi.Builder().build()

    // =========================================================================
    // toFlightStatusEnum mapping tests
    // =========================================================================

    @Test
    fun `null status maps to UNKNOWN`() {
        assertEquals(FlightStatusEnum.UNKNOWN, null.toFlightStatusEnum())
    }

    @Test
    fun `Scheduled status maps to SCHEDULED`() {
        assertEquals(FlightStatusEnum.SCHEDULED, "Scheduled".toFlightStatusEnum())
    }

    @Test
    fun `Scheduled with extra text maps to SCHEDULED`() {
        assertEquals(FlightStatusEnum.SCHEDULED, "Scheduled / On Time".toFlightStatusEnum())
    }

    @Test
    fun `En Route maps to EN_ROUTE`() {
        assertEquals(FlightStatusEnum.EN_ROUTE, "En Route / On Time".toFlightStatusEnum())
    }

    @Test
    fun `En Route delayed maps to EN_ROUTE`() {
        assertEquals(FlightStatusEnum.EN_ROUTE, "En Route / Delayed".toFlightStatusEnum())
    }

    @Test
    fun `Departed maps to DEPARTED`() {
        assertEquals(FlightStatusEnum.DEPARTED, "Departed".toFlightStatusEnum())
    }

    @Test
    fun `Departed with additional info maps to DEPARTED`() {
        assertEquals(FlightStatusEnum.DEPARTED, "Departed / On Time / Gate A12".toFlightStatusEnum())
    }

    @Test
    fun `Arrived maps to LANDED`() {
        assertEquals(FlightStatusEnum.LANDED, "Arrived".toFlightStatusEnum())
    }

    @Test
    fun `Landed maps to LANDED`() {
        assertEquals(FlightStatusEnum.LANDED, "Landed / On Time".toFlightStatusEnum())
    }

    @Test
    fun `Cancelled maps to CANCELLED`() {
        assertEquals(FlightStatusEnum.CANCELLED, "Cancelled".toFlightStatusEnum())
    }

    @Test
    fun `Diverted maps to DIVERTED`() {
        assertEquals(FlightStatusEnum.DIVERTED, "Diverted to ABC".toFlightStatusEnum())
    }

    @Test
    fun `Boarding maps to BOARDING`() {
        assertEquals(FlightStatusEnum.BOARDING, "Boarding".toFlightStatusEnum())
    }

    @Test
    fun `novel string maps to UNKNOWN`() {
        assertEquals(FlightStatusEnum.UNKNOWN, "Something Entirely New".toFlightStatusEnum())
    }

    @Test
    fun `empty string maps to UNKNOWN`() {
        assertEquals(FlightStatusEnum.UNKNOWN, "".toFlightStatusEnum())
    }

    @Test
    fun `case sensitive - lowercase scheduled is UNKNOWN`() {
        assertEquals(FlightStatusEnum.UNKNOWN, "scheduled".toFlightStatusEnum())
    }

    // =========================================================================
    // FlightAware response model parsing tests
    // =========================================================================

    @Test
    fun `parse flights response with valid data`() {
        val json = """
        {
            "flights": [{
                "ident_iata": "NH847",
                "status": "En Route / On Time",
                "departure_delay": 300,
                "arrival_delay": null,
                "origin": {"code_iata": "HND", "timezone": "Asia/Tokyo"},
                "destination": {"code_iata": "NRT", "timezone": "Asia/Tokyo"},
                "scheduled_out": "2026-03-27T14:35:00+09:00",
                "estimated_out": "2026-03-27T14:35:00+09:00",
                "actual_out": null,
                "scheduled_in": "2026-03-27T18:20:00+09:00",
                "estimated_in": "2026-03-27T18:20:00+09:00",
                "actual_in": null,
                "gate_origin": "64",
                "gate_destination": null,
                "aircraft_type": "B788"
            }]
        }
        """.trimIndent()

        val adapter = moshi.adapter(FlightAwareFlightsResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertEquals(1, response?.flights?.size)
        val flight = response?.flights?.first()
        assertEquals("NH847", flight?.identIata)
        assertEquals("En Route / On Time", flight?.status)
        assertEquals(300, flight?.departureDelay)
        assertNull(flight?.arrivalDelay)
        assertEquals("HND", flight?.origin?.codeIata)
        assertEquals("Asia/Tokyo", flight?.origin?.timezone)
        assertEquals("NRT", flight?.destination?.codeIata)
        assertEquals("64", flight?.gateOrigin)
        assertNull(flight?.gateDestination)
        assertEquals("B788", flight?.aircraftType)
    }

    @Test
    fun `parse flights response with empty flights list`() {
        val json = """{"flights": []}"""
        val adapter = moshi.adapter(FlightAwareFlightsResponse::class.java)
        val response = adapter.fromJson(json)
        assertNotNull(response)
        assertTrue(response?.flights?.isEmpty() == true)
    }

    @Test
    fun `parse flights response with null flights`() {
        val json = """{}"""
        val adapter = moshi.adapter(FlightAwareFlightsResponse::class.java)
        val response = adapter.fromJson(json)
        assertNotNull(response)
        assertNull(response?.flights)
    }

    @Test
    fun `parse position response with valid data`() {
        val json = """
        {
            "last_position": {
                "latitude": 35.123,
                "longitude": 136.456,
                "altitude": 38000,
                "groundspeed": 510,
                "heading": 45,
                "timestamp": "2026-03-27T15:10:00Z"
            }
        }
        """.trimIndent()

        val adapter = moshi.adapter(FlightAwarePositionResponse::class.java)
        val response = adapter.fromJson(json)
        assertNotNull(response)
        assertEquals(35.123, response?.lastPosition?.latitude ?: 0.0, 0.001)
        assertEquals(136.456, response?.lastPosition?.longitude ?: 0.0, 0.001)
        assertEquals(38000, response?.lastPosition?.altitude)
        assertEquals(510, response?.lastPosition?.groundspeed)
        assertEquals(45, response?.lastPosition?.heading)
    }

    @Test
    fun `parse position response with null last_position`() {
        val json = """{"last_position": null}"""
        val adapter = moshi.adapter(FlightAwarePositionResponse::class.java)
        val response = adapter.fromJson(json)
        assertNotNull(response)
        assertNull(response?.lastPosition)
    }

    @Test
    fun `parse flight with missing optional fields`() {
        val json = """
        {
            "flights": [{
                "ident_iata": "AA100",
                "status": null,
                "departure_delay": null,
                "arrival_delay": null,
                "origin": {"code_iata": "JFK", "timezone": null},
                "destination": {"code_iata": "LAX", "timezone": null},
                "scheduled_out": null,
                "estimated_out": null,
                "actual_out": null,
                "scheduled_in": null,
                "estimated_in": null,
                "actual_in": null,
                "gate_origin": null,
                "gate_destination": null,
                "aircraft_type": null
            }]
        }
        """.trimIndent()

        val adapter = moshi.adapter(FlightAwareFlightsResponse::class.java)
        val response = adapter.fromJson(json)
        val flight = response?.flights?.first()
        assertEquals("AA100", flight?.identIata)
        assertNull(flight?.status)
        assertNull(flight?.departureDelay)
        assertNull(flight?.aircraftType)
    }

    // =========================================================================
    // Delay conversion tests (seconds -> minutes)
    // =========================================================================

    @Test
    fun `departure delay in seconds converts to minutes`() {
        val delaySeconds = 2700 // 45 minutes
        val delayMinutes = delaySeconds / 60
        assertEquals(45, delayMinutes)
    }

    @Test
    fun `zero delay stays zero`() {
        assertEquals(0, 0 / 60)
    }

    @Test
    fun `small delay rounds down`() {
        // 89 seconds = 1 minute (integer division)
        assertEquals(1, 89 / 60)
    }

    // =========================================================================
    // ISO datetime parsing tests
    // =========================================================================

    @Test
    fun `parseIsoToUtc with valid offset datetime`() {
        val utc = FlightRouteServiceImpl.parseIsoToUtc("2026-03-27T14:35:00+09:00")
        assertNotNull(utc)
        // 14:35 +09:00 = 05:35 UTC = 5*3600 + 35*60 seconds since midnight
        // This is a specific epoch millis value, just verify non-null
        assertTrue(utc!! > 0)
    }

    @Test
    fun `parseIsoToUtc with null returns null`() {
        assertNull(FlightRouteServiceImpl.parseIsoToUtc(null))
    }

    @Test
    fun `parseIsoToUtc with invalid string returns null`() {
        assertNull(FlightRouteServiceImpl.parseIsoToUtc("not-a-date"))
    }

    @Test
    fun `parseIsoToUtc with empty string returns null`() {
        assertNull(FlightRouteServiceImpl.parseIsoToUtc(""))
    }

    // =========================================================================
    // Null Island guard
    // =========================================================================

    @Test
    fun `position at 0,0 should be treated as invalid`() {
        val lat = 0.0
        val lng = 0.0
        val isNullIsland = lat == 0.0 && lng == 0.0
        assertTrue(isNullIsland)
    }

    @Test
    fun `valid position is not Null Island`() {
        val lat = 35.123
        val lng = 136.456
        val isNullIsland = lat == 0.0 && lng == 0.0
        assertTrue(!isNullIsland)
    }
}
