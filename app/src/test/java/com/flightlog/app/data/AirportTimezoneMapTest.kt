package com.flightlog.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class AirportTimezoneMapTest {

    // ── Known airports ───────────────────────────────────────────────────────

    @Test
    fun `NRT returns Asia Tokyo`() {
        assertEquals("Asia/Tokyo", AirportTimezoneMap.timezoneFor("NRT"))
    }

    @Test
    fun `HND returns Asia Tokyo`() {
        assertEquals("Asia/Tokyo", AirportTimezoneMap.timezoneFor("HND"))
    }

    @Test
    fun `LAX returns America Los Angeles`() {
        assertEquals("America/Los_Angeles", AirportTimezoneMap.timezoneFor("LAX"))
    }

    @Test
    fun `LAS returns America Los Angeles`() {
        assertEquals("America/Los_Angeles", AirportTimezoneMap.timezoneFor("LAS"))
    }

    @Test
    fun `ORD returns America Chicago`() {
        assertEquals("America/Chicago", AirportTimezoneMap.timezoneFor("ORD"))
    }

    @Test
    fun `JFK returns America New York`() {
        assertEquals("America/New_York", AirportTimezoneMap.timezoneFor("JFK"))
    }

    @Test
    fun `LHR returns Europe London`() {
        assertEquals("Europe/London", AirportTimezoneMap.timezoneFor("LHR"))
    }

    @Test
    fun `DXB returns Asia Dubai`() {
        assertEquals("Asia/Dubai", AirportTimezoneMap.timezoneFor("DXB"))
    }

    @Test
    fun `SYD returns Australia Sydney`() {
        assertEquals("Australia/Sydney", AirportTimezoneMap.timezoneFor("SYD"))
    }

    @Test
    fun `ICN returns Asia Seoul`() {
        assertEquals("Asia/Seoul", AirportTimezoneMap.timezoneFor("ICN"))
    }

    @Test
    fun `SIN returns Asia Singapore`() {
        assertEquals("Asia/Singapore", AirportTimezoneMap.timezoneFor("SIN"))
    }

    @Test
    fun `GRU returns America Sao Paulo`() {
        assertEquals("America/Sao_Paulo", AirportTimezoneMap.timezoneFor("GRU"))
    }

    @Test
    fun `BER returns Europe Berlin`() {
        assertEquals("Europe/Berlin", AirportTimezoneMap.timezoneFor("BER"))
    }

    @Test
    fun `AEP returns America Argentina Buenos Aires`() {
        assertEquals("America/Argentina/Buenos_Aires", AirportTimezoneMap.timezoneFor("AEP"))
    }

    // ── Unknown codes ────────────────────────────────────────────────────────

    @Test
    fun `unknown airport code returns null`() {
        assertNull(AirportTimezoneMap.timezoneFor("XYZ"))
    }

    @Test
    fun `empty string returns null`() {
        assertNull(AirportTimezoneMap.timezoneFor(""))
    }

    @Test
    fun `two letter code returns null`() {
        assertNull(AirportTimezoneMap.timezoneFor("AB"))
    }

    // ── Case handling ────────────────────────────────────────────────────────

    @Test
    fun `lowercase input is resolved`() {
        assertEquals("Asia/Tokyo", AirportTimezoneMap.timezoneFor("nrt"))
    }

    @Test
    fun `mixed case input is resolved`() {
        assertEquals("America/Los_Angeles", AirportTimezoneMap.timezoneFor("Lax"))
    }

    // ── All IANA IDs are valid ZoneIds ────────────────────────────────────────

    @Test
    fun `all timezone values are valid IANA ZoneIds`() {
        // Use reflection-free approach: try every known airport in the map
        val knownCodes = listOf(
            "ATL", "BOS", "BWI", "CLT", "CMH", "DTW", "EWR", "FLL", "IAD", "JFK",
            "LGA", "MCO", "MIA", "PBI", "PHL", "PIT", "RDU", "RSW", "TPA",
            "AUS", "DAL", "DFW", "HOU", "IAH", "IND", "MCI", "MDW", "MEM", "MSP",
            "MSY", "ORD", "SAT", "STL", "BNA",
            "ABQ", "COS", "DEN", "PHX", "SLC",
            "LAS", "LAX", "OAK", "ONT", "PDX", "SAN", "SEA", "SFO", "SJC", "SMF",
            "ANC", "HNL", "OGG",
            "YEG", "YOW", "YUL", "YVR", "YWG", "YYC", "YYZ",
            "CUN", "GDL", "MEX", "SJD", "TIJ",
            "PTY", "SJO", "SAL", "MBJ", "NAS", "SXM", "PUJ", "SDQ", "SJU", "HAV",
            "BOG", "BSB", "AEP", "EZE", "GIG", "GRU", "LIM", "SCL", "UIO", "VVI",
            "AMS", "BCN", "BRU", "CDG", "DUB", "DUS", "EDI", "FCO", "FRA", "GVA",
            "HAM", "LGW", "LHR", "LIS", "MAD", "MAN", "MRS", "MUC", "MXP", "NCE",
            "ORY", "OSL", "PMI", "STN", "BER", "VCE", "VIE", "ZRH",
            "ARN", "CPH", "HEL", "KEF",
            "BUD", "OTP", "PRG", "WAW",
            "ATH", "IST", "SAW", "SOF",
            "AUH", "BAH", "DOH", "DXB", "JED", "KWI", "MCT", "RUH", "TLV",
            "ADD", "CAI", "CMN", "CPT", "JNB", "LOS", "NBO",
            "BLR", "BOM", "CCU", "CMB", "DAC", "DEL", "HYD", "ISB", "KHI", "MAA",
            "BKK", "CGK", "DPS", "HAN", "KUL", "MNL", "RGN", "SGN", "SIN",
            "CTS", "FUK", "HND", "ITM", "KIX", "NGO", "NRT", "OKA",
            "GMP", "ICN", "PUS",
            "CAN", "CKG", "CTU", "HGH", "KMG", "NKG", "PEK", "PKX", "PVG", "SHA", "SZX", "WUH", "XIY",
            "HKG", "MFM", "TPE",
            "AKL", "BNE", "CHC", "MEL", "PER", "SYD", "WLG",
            "DME", "LED", "SVO", "VVO", "ALA", "NQZ", "TAS"
        )

        val failures = mutableListOf<String>()
        for (code in knownCodes) {
            val tz = AirportTimezoneMap.timezoneFor(code)
            assertNotNull("Missing mapping for $code", tz)
            try {
                ZoneId.of(tz!!)
            } catch (e: Exception) {
                failures.add("$code -> '$tz' is not a valid ZoneId: ${e.message}")
            }
        }
        if (failures.isNotEmpty()) {
            throw AssertionError("Invalid IANA timezone IDs:\n${failures.joinToString("\n")}")
        }
    }
}
