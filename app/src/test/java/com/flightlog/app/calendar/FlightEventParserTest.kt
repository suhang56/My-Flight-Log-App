package com.flightlog.app.calendar

import com.flightlog.app.data.calendar.AirlineIataMap
import com.flightlog.app.data.calendar.FlightEventParser
import com.flightlog.app.data.calendar.ParsedFlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FlightEventParserTest {

    private lateinit var parser: FlightEventParser

    @Before
    fun setUp() {
        parser = FlightEventParser(AirlineIataMap())
    }

    // ── Pattern 1: "Flight AA0011 ORD-CMH" ──────────────────────────────────

    @Test
    fun `pattern1 - Flight keyword with code and route`() {
        val result = parser.parse("Flight AA11 ORD-CMH")
        assertEquals(1, result.size)
        assertEquals(ParsedFlight("AA11", "ORD", "CMH"), result[0])
    }

    @Test
    fun `pattern1 - Flight keyword with en-dash separator`() {
        val result = parser.parse("Flight DL456 JFK\u2013LAX")
        assertEquals(1, result.size)
        assertEquals("DL456", result[0].flightNumber)
        assertEquals("JFK", result[0].departureCode)
        assertEquals("LAX", result[0].arrivalCode)
    }

    @Test
    fun `pattern1 - Flight keyword with arrow separator`() {
        val result = parser.parse("Flight UA789 SFO\u2192SEA")
        assertEquals(1, result.size)
        assertEquals("UA789", result[0].flightNumber)
        assertEquals("SFO", result[0].departureCode)
        assertEquals("SEA", result[0].arrivalCode)
    }

    @Test
    fun `pattern1 - Flight keyword with gt separator`() {
        val result = parser.parse("Flight AA11 ORD>CMH")
        assertEquals(1, result.size)
        assertEquals("ORD", result[0].departureCode)
        assertEquals("CMH", result[0].arrivalCode)
    }

    // ── Pattern 2: "AA0011 ORD-CMH" (no keyword) ────────────────────────────

    @Test
    fun `pattern2 - code and route without keyword`() {
        val result = parser.parse("AA11 ORD-CMH")
        assertEquals(1, result.size)
        assertEquals(ParsedFlight("AA11", "ORD", "CMH"), result[0])
    }

    @Test
    fun `pattern2 - four digit flight number`() {
        val result = parser.parse("DL1234 ATL-LAX")
        assertEquals(1, result.size)
        assertEquals("DL1234", result[0].flightNumber)
        assertEquals("ATL", result[0].departureCode)
        assertEquals("LAX", result[0].arrivalCode)
    }

    // ── Pattern 3: "ORD to CMH" (route with 'to') ───────────────────────────

    @Test
    fun `pattern3 - route with to separator`() {
        val result = parser.parse("ORD to CMH")
        assertEquals(1, result.size)
        assertEquals("ORD", result[0].departureCode)
        assertEquals("CMH", result[0].arrivalCode)
        assertEquals("", result[0].flightNumber)
    }

    @Test
    fun `pattern3 - route with to and standalone flight number`() {
        val result = parser.parse("AA11 ORD to CMH")
        assertEquals(1, result.size)
        assertEquals("ORD", result[0].departureCode)
        assertEquals("CMH", result[0].arrivalCode)
    }

    // ── Pattern 4: "ORD->CMH" / "ORD-CMH" (arrow/dash route) ────────────────

    @Test
    fun `pattern4 - route with dash separator`() {
        val result = parser.parse("Booking: ORD-CMH")
        assertEquals(1, result.size)
        assertEquals("ORD", result[0].departureCode)
        assertEquals("CMH", result[0].arrivalCode)
        assertEquals("", result[0].flightNumber)
    }

    @Test
    fun `pattern4 - route with gt separator`() {
        val result = parser.parse("Booking Confirmation: ORD>CMH")
        assertEquals(1, result.size)
        assertEquals("ORD", result[0].departureCode)
        assertEquals("CMH", result[0].arrivalCode)
    }

    // ── Pattern 5: Airline name with flight numbers ──────────────────────────

    @Test
    fun `pattern5 - single flight with airline name and route`() {
        val result = parser.parse("Delta Flight 456 JFK-LAX")
        assertEquals(1, result.size)
        assertEquals("DL456", result[0].flightNumber)
        assertEquals("JFK", result[0].departureCode)
        assertEquals("LAX", result[0].arrivalCode)
    }

    @Test
    fun `pattern5 - airline name without Flight keyword`() {
        val result = parser.parse("Southwest 1946 CMH-LAX")
        assertEquals(1, result.size)
        assertEquals("WN1946", result[0].flightNumber)
        assertEquals("CMH", result[0].departureCode)
        assertEquals("LAX", result[0].arrivalCode)
    }

    @Test
    fun `pattern5 - single flight with airline name, no route`() {
        val result = parser.parse("United Flight 321")
        assertEquals(1, result.size)
        assertEquals("UA321", result[0].flightNumber)
        assertEquals("", result[0].departureCode)
        assertEquals("", result[0].arrivalCode)
    }

    // ── Multi-leg Southwest style ────────────────────────────────────────────

    @Test
    fun `multi-leg - Southwest slash separated numbers`() {
        val result = parser.parse("Southwest Flight 1946/3034")
        assertEquals(2, result.size)
        assertEquals("WN1946", result[0].flightNumber)
        assertEquals("WN3034", result[1].flightNumber)
    }

    @Test
    fun `multi-leg - with Departs Arrives and Stop in description`() {
        val result = parser.parse(
            title = "Southwest Flight 1946/3034",
            description = "Departs: 03:50 PM CMH\nStop: Chicago (Midway), IL\nArrives: 08:15 PM LAX"
        )
        assertEquals(2, result.size)
        assertEquals("WN1946", result[0].flightNumber)
        assertEquals("CMH", result[0].departureCode)
        assertEquals("MDW", result[0].arrivalCode)
        assertEquals("WN3034", result[1].flightNumber)
        assertEquals("MDW", result[1].departureCode)
        assertEquals("LAX", result[1].arrivalCode)
    }

    @Test
    fun `multi-leg - with Departs and Arrives but no Stop`() {
        val result = parser.parse(
            title = "Southwest Flight 1946/3034",
            description = "Departs: 03:50 PM CMH\nArrives: 08:15 PM LAX"
        )
        assertEquals(2, result.size)
        assertEquals("CMH", result[0].departureCode)
        assertEquals("", result[0].arrivalCode)
        assertEquals("", result[1].departureCode)
        assertEquals("LAX", result[1].arrivalCode)
    }

    @Test
    fun `multi-leg - three legs`() {
        val result = parser.parse("American Flight 100/200/300")
        assertEquals(3, result.size)
        assertEquals("AA100", result[0].flightNumber)
        assertEquals("AA200", result[1].flightNumber)
        assertEquals("AA300", result[2].flightNumber)
    }

    // ── Unknown airline ──────────────────────────────────────────────────────

    @Test
    fun `unknown airline name falls through to other patterns`() {
        val result = parser.parse("Ryanair Flight 1234 DUB-STN")
        // "Ryanair" is not in AirlineIataMap, so pattern 5 fails.
        // Should fall through to pattern 1 or 2.
        assertEquals(1, result.size)
        assertEquals("DUB", result[0].departureCode)
        assertEquals("STN", result[0].arrivalCode)
    }

    @Test
    fun `unknown airline with no route returns empty`() {
        val result = parser.parse("Ryanair Flight 1234")
        assertTrue(result.isEmpty())
    }

    // ── No pattern match ─────────────────────────────────────────────────────

    @Test
    fun `no match - random text`() {
        val result = parser.parse("Team meeting at 3pm")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `no match - numbers but no flight pattern`() {
        val result = parser.parse("Invoice #12345 from vendor")
        assertTrue(result.isEmpty())
    }

    // ── Case insensitivity ───────────────────────────────────────────────────

    @Test
    fun `case insensitive - lowercase flight keyword`() {
        val result = parser.parse("flight AA11 ord-cmh")
        assertEquals(1, result.size)
        assertEquals("AA11", result[0].flightNumber)
        assertEquals("ORD", result[0].departureCode)
        assertEquals("CMH", result[0].arrivalCode)
    }

    @Test
    fun `case insensitive - mixed case airline name`() {
        val result = parser.parse("delta flight 456 jfk-lax")
        assertEquals(1, result.size)
        assertEquals("DL456", result[0].flightNumber)
        assertEquals("JFK", result[0].departureCode)
        assertEquals("LAX", result[0].arrivalCode)
    }

    @Test
    fun `case insensitive - lowercase route with to`() {
        val result = parser.parse("ord to cmh")
        assertEquals(1, result.size)
        assertEquals("ORD", result[0].departureCode)
        assertEquals("CMH", result[0].arrivalCode)
    }

    // ── Unicode separators ───────────────────────────────────────────────────

    @Test
    fun `unicode - en-dash in route`() {
        val result = parser.parse("ORD\u2013CMH")
        assertEquals(1, result.size)
        assertEquals("ORD", result[0].departureCode)
        assertEquals("CMH", result[0].arrivalCode)
    }

    @Test
    fun `unicode - right arrow in route`() {
        val result = parser.parse("ORD\u2192CMH")
        assertEquals(1, result.size)
        assertEquals("ORD", result[0].departureCode)
        assertEquals("CMH", result[0].arrivalCode)
    }

    // ── Blank and empty inputs ───────────────────────────────────────────────

    @Test
    fun `blank title returns empty`() {
        val result = parser.parse("   ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty title returns empty`() {
        val result = parser.parse("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `blank title with flight info in description`() {
        val result = parser.parse(title = "", description = "Flight AA11 ORD-CMH")
        assertEquals(1, result.size)
        assertEquals("AA11", result[0].flightNumber)
        assertEquals("ORD", result[0].departureCode)
        assertEquals("CMH", result[0].arrivalCode)
    }

    @Test
    fun `blank title with flight info in location`() {
        val result = parser.parse(title = "", description = "", location = "ORD-CMH")
        assertEquals(1, result.size)
        assertEquals("ORD", result[0].departureCode)
        assertEquals("CMH", result[0].arrivalCode)
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `description enriches title with route`() {
        val result = parser.parse(
            title = "Southwest Flight 1946",
            description = "Departs: CMH\nArrives: LAX"
        )
        assertEquals(1, result.size)
        assertEquals("WN1946", result[0].flightNumber)
    }

    @Test
    fun `flight number with leading zeros`() {
        val result = parser.parse("Flight AA0011 ORD-CMH")
        assertEquals(1, result.size)
        assertEquals("AA0011", result[0].flightNumber)
    }

    // ── False positive rejection ──────────────────────────────────────────────

    @Test
    fun `false positive - Day to Add rejected by pattern3`() {
        val result = parser.parse("Day to Add")
        assertTrue("'Day to Add' must not parse as a flight", result.isEmpty())
    }

    @Test
    fun `false positive - Day to Add in combined fields rejected`() {
        val result = parser.parse(
            title = "Day to Add",
            description = "Some calendar note",
            location = ""
        )
        assertTrue("'Day to Add' with description must not parse as a flight", result.isEmpty())
    }

    @Test
    fun `false positive - pattern1 rejects unknown airport codes`() {
        // "Flight XX12 FOO-BAR" — XX12 has flight code format but FOO/BAR are not airports
        val result = parser.parse("Flight XX12 FOO-BAR")
        assertTrue("Unknown airport codes in pattern1 must be rejected", result.isEmpty())
    }

    @Test
    fun `false positive - pattern2 rejects unknown airport codes`() {
        // "XX12 FOO-BAR" — same without Flight keyword
        val result = parser.parse("XX12 FOO-BAR")
        assertTrue("Unknown airport codes in pattern2 must be rejected", result.isEmpty())
    }

    @Test
    fun `pattern1 accepts when at least one airport is known`() {
        // ORD is known, ZZZ is not — should still parse
        val result = parser.parse("Flight AA11 ORD-ZZZ")
        assertEquals(1, result.size)
        assertEquals("AA11", result[0].flightNumber)
        assertEquals("ORD", result[0].departureCode)
        assertEquals("ZZZ", result[0].arrivalCode)
    }

    @Test
    fun `pattern2 accepts when at least one airport is known`() {
        val result = parser.parse("AA11 ZZZ-CMH")
        assertEquals(1, result.size)
        assertEquals("AA11", result[0].flightNumber)
        assertEquals("ZZZ", result[0].departureCode)
        assertEquals("CMH", result[0].arrivalCode)
    }
}
