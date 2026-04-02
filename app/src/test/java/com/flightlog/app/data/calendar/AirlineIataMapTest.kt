package com.flightlog.app.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for AirlineIataMap reverse lookup (Improvement #3).
 */
class AirlineIataMapTest {

    private val map = AirlineIataMap()

    @Test
    fun `getAirlineName returns full name for known code`() {
        assertEquals("All Nippon Airways", map.getAirlineName("NH"))
        assertEquals("Japan Airlines", map.getAirlineName("JL"))
        assertEquals("Delta Air Lines", map.getAirlineName("DL"))
        assertEquals("American Airlines", map.getAirlineName("AA"))
    }

    @Test
    fun `getAirlineName is case insensitive`() {
        assertEquals("All Nippon Airways", map.getAirlineName("nh"))
        assertEquals("All Nippon Airways", map.getAirlineName("Nh"))
    }

    @Test
    fun `getAirlineName returns null for unknown code`() {
        assertNull(map.getAirlineName("ZZ"))
        assertNull(map.getAirlineName("XX"))
    }

    @Test
    fun `findIataCode still works for forward lookup`() {
        assertEquals("NH", map.findIataCode("All Nippon Airways flight"))
        assertEquals("DL", map.findIataCode("I flew Delta Airlines"))
    }

    @Test
    fun `airline display format is correct`() {
        val code = "NH"
        val name = map.getAirlineName(code)
        val display = if (name != null) "$code \u2014 $name" else code
        assertEquals("NH \u2014 All Nippon Airways", display)
    }

    @Test
    fun `unknown code falls back to just code`() {
        val code = "ZZ"
        val name = map.getAirlineName(code)
        val display = if (name != null) "$code \u2014 $name" else code
        assertEquals("ZZ", display)
    }
}
