package com.flightlog.app.data

import com.flightlog.app.data.calendar.AirlineIcaoMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AirlineIcaoMapTest {

    // =========================================================================
    // toIcaoFlightNumber — happy path
    // =========================================================================

    @Test
    fun `JL5 converts to JAL5`() {
        assertEquals("JAL5", AirlineIcaoMap.toIcaoFlightNumber("JL5"))
    }

    @Test
    fun `NH847 converts to ANA847`() {
        assertEquals("ANA847", AirlineIcaoMap.toIcaoFlightNumber("NH847"))
    }

    @Test
    fun `UA882 converts to UAL882`() {
        assertEquals("UAL882", AirlineIcaoMap.toIcaoFlightNumber("UA882"))
    }

    @Test
    fun `BA115 converts to BAW115`() {
        assertEquals("BAW115", AirlineIcaoMap.toIcaoFlightNumber("BA115"))
    }

    @Test
    fun `EK381 converts to UAE381`() {
        assertEquals("UAE381", AirlineIcaoMap.toIcaoFlightNumber("EK381"))
    }

    @Test
    fun `CX520 converts to CPA520`() {
        assertEquals("CPA520", AirlineIcaoMap.toIcaoFlightNumber("CX520"))
    }

    @Test
    fun `QF1 converts to QFA1 — single digit flight number`() {
        assertEquals("QFA1", AirlineIcaoMap.toIcaoFlightNumber("QF1"))
    }

    @Test
    fun `AA1234 converts to AAL1234 — four digit flight number`() {
        assertEquals("AAL1234", AirlineIcaoMap.toIcaoFlightNumber("AA1234"))
    }

    // =========================================================================
    // toIcaoFlightNumber — case insensitivity
    // =========================================================================

    @Test
    fun `lowercase jl5 converts to JAL5`() {
        assertEquals("JAL5", AirlineIcaoMap.toIcaoFlightNumber("jl5"))
    }

    @Test
    fun `mixed case Nh847 converts to ANA847`() {
        assertEquals("ANA847", AirlineIcaoMap.toIcaoFlightNumber("Nh847"))
    }

    // =========================================================================
    // toIcaoFlightNumber — edge cases returning null
    // =========================================================================

    @Test
    fun `empty string returns null`() {
        assertNull(AirlineIcaoMap.toIcaoFlightNumber(""))
    }

    @Test
    fun `blank string returns null`() {
        assertNull(AirlineIcaoMap.toIcaoFlightNumber("   "))
    }

    @Test
    fun `single character returns null`() {
        assertNull(AirlineIcaoMap.toIcaoFlightNumber("A"))
    }

    @Test
    fun `two characters no digits returns null`() {
        assertNull(AirlineIcaoMap.toIcaoFlightNumber("JL"))
    }

    @Test
    fun `three-letter ICAO prefix returns null — not a 2-letter IATA prefix`() {
        assertNull(AirlineIcaoMap.toIcaoFlightNumber("JAL5"))
    }

    @Test
    fun `single letter prefix returns null`() {
        assertNull(AirlineIcaoMap.toIcaoFlightNumber("A123"))
    }

    @Test
    fun `unknown airline prefix returns null`() {
        assertNull(AirlineIcaoMap.toIcaoFlightNumber("ZZ999"))
    }

    @Test
    fun `digits only returns null`() {
        assertNull(AirlineIcaoMap.toIcaoFlightNumber("123"))
    }

    @Test
    fun `whitespace around input is trimmed`() {
        assertEquals("JAL5", AirlineIcaoMap.toIcaoFlightNumber("  JL5  "))
    }

    // =========================================================================
    // toIcaoFlightNumber — preserves numeric part exactly
    // =========================================================================

    @Test
    fun `leading zero in flight number is preserved`() {
        assertEquals("JAL007", AirlineIcaoMap.toIcaoFlightNumber("JL007"))
    }

    // =========================================================================
    // icaoFor — direct prefix lookup
    // =========================================================================

    @Test
    fun `icaoFor JL returns JAL`() {
        assertEquals("JAL", AirlineIcaoMap.icaoFor("JL"))
    }

    @Test
    fun `icaoFor unknown returns null`() {
        assertNull(AirlineIcaoMap.icaoFor("ZZ"))
    }

    @Test
    fun `icaoFor is case insensitive`() {
        assertEquals("JAL", AirlineIcaoMap.icaoFor("jl"))
    }

    // =========================================================================
    // Coverage of regional airlines
    // =========================================================================

    @Test
    fun `Korean Air KE converts to KAL`() {
        assertEquals("KAL", AirlineIcaoMap.icaoFor("KE"))
    }

    @Test
    fun `LATAM LA converts to LAN`() {
        assertEquals("LAN", AirlineIcaoMap.icaoFor("LA"))
    }

    @Test
    fun `Ethiopian ET converts to ETH`() {
        assertEquals("ETH", AirlineIcaoMap.icaoFor("ET"))
    }

    @Test
    fun `IndiGo 6E converts to IGO`() {
        assertEquals("IGO", AirlineIcaoMap.icaoFor("6E"))
    }

    @Test
    fun `6E123 converts to IGO123 — digit in IATA prefix`() {
        assertEquals("IGO123", AirlineIcaoMap.toIcaoFlightNumber("6E123"))
    }

    @Test
    fun `3U8501 converts to CSC8501 — Sichuan Airlines`() {
        assertEquals("CSC8501", AirlineIcaoMap.toIcaoFlightNumber("3U8501"))
    }

    @Test
    fun `S7500 converts to SBI500 — S7 Airlines`() {
        assertEquals("SBI500", AirlineIcaoMap.toIcaoFlightNumber("S7500"))
    }

    // =========================================================================
    // toIcaoFlightNumber — rejects non-flight-number inputs
    // =========================================================================

    @Test
    fun `letters after prefix returns null — not a flight number`() {
        assertNull(AirlineIcaoMap.toIcaoFlightNumber("JLABC"))
    }
}
