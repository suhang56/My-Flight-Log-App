package com.flightlog.app.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AircraftTypePhotoProviderTest {

    // -- Null and blank inputs --

    @Test
    fun `getPhotoForType with null returns null`() {
        assertNull(AircraftTypePhotoProvider.getPhotoForType(null))
    }

    @Test
    fun `getPhotoForType with empty string returns null`() {
        assertNull(AircraftTypePhotoProvider.getPhotoForType(""))
    }

    @Test
    fun `getPhotoForType with blank string returns null`() {
        assertNull(AircraftTypePhotoProvider.getPhotoForType("   "))
    }

    @Test
    fun `getPhotoForType with tab only returns null`() {
        assertNull(AircraftTypePhotoProvider.getPhotoForType("\t"))
    }

    // -- Known type codes --

    @Test
    fun `getPhotoForType returns photo for B738`() {
        val result = AircraftTypePhotoProvider.getPhotoForType("B738")
        assertNotNull(result)
        assertNotNull(result?.photoUrl)
        assertNotNull(result?.photographer)
        assertEquals("Boeing 737-800", result?.aircraftName)
    }

    @Test
    fun `getPhotoForType returns photo for A320`() {
        val result = AircraftTypePhotoProvider.getPhotoForType("A320")
        assertNotNull(result)
        assertEquals("Airbus A320", result?.aircraftName)
    }

    @Test
    fun `getPhotoForType returns photo for A388`() {
        val result = AircraftTypePhotoProvider.getPhotoForType("A388")
        assertNotNull(result)
        assertEquals("Airbus A380-800", result?.aircraftName)
    }

    @Test
    fun `getPhotoForType returns photo for B77W`() {
        val result = AircraftTypePhotoProvider.getPhotoForType("B77W")
        assertNotNull(result)
        assertEquals("Boeing 777-300ER", result?.aircraftName)
    }

    @Test
    fun `getPhotoForType returns photo for B789`() {
        val result = AircraftTypePhotoProvider.getPhotoForType("B789")
        assertNotNull(result)
        assertEquals("Boeing 787-9 Dreamliner", result?.aircraftName)
    }

    @Test
    fun `getPhotoForType returns photo for C919`() {
        val result = AircraftTypePhotoProvider.getPhotoForType("C919")
        assertNotNull(result)
        assertEquals("COMAC C919", result?.aircraftName)
    }

    @Test
    fun `getPhotoForType returns photo for ARJ2`() {
        val result = AircraftTypePhotoProvider.getPhotoForType("ARJ2")
        assertNotNull(result)
        assertEquals("COMAC ARJ21", result?.aircraftName)
    }

    // -- Case insensitivity --

    @Test
    fun `getPhotoForType is case insensitive lowercase`() {
        val result = AircraftTypePhotoProvider.getPhotoForType("b738")
        assertNotNull(result)
        assertEquals("Boeing 737-800", result?.aircraftName)
    }

    @Test
    fun `getPhotoForType is case insensitive mixed case`() {
        val result = AircraftTypePhotoProvider.getPhotoForType("a320")
        assertNotNull(result)
        assertEquals("Airbus A320", result?.aircraftName)
    }

    @Test
    fun `getPhotoForType is case insensitive for aliases`() {
        val result = AircraftTypePhotoProvider.getPhotoForType("b73h")
        assertNotNull(result)
        assertEquals("Boeing 737-800", result?.aircraftName)
    }

    // -- Whitespace handling --

    @Test
    fun `getPhotoForType trims leading whitespace`() {
        val result = AircraftTypePhotoProvider.getPhotoForType("  B738")
        assertNotNull(result)
    }

    @Test
    fun `getPhotoForType trims trailing whitespace`() {
        val result = AircraftTypePhotoProvider.getPhotoForType("B738  ")
        assertNotNull(result)
    }

    @Test
    fun `getPhotoForType trims both sides`() {
        val result = AircraftTypePhotoProvider.getPhotoForType("  A320  ")
        assertNotNull(result)
    }

    // -- Alias resolution --

    @Test
    fun `B73H resolves to B738 Boeing 737-800`() {
        val aliased = AircraftTypePhotoProvider.getPhotoForType("B73H")
        val direct = AircraftTypePhotoProvider.getPhotoForType("B738")
        assertEquals(direct, aliased)
    }

    @Test
    fun `A20N resolves to A320`() {
        val aliased = AircraftTypePhotoProvider.getPhotoForType("A20N")
        val direct = AircraftTypePhotoProvider.getPhotoForType("A320")
        assertEquals(direct, aliased)
    }

    @Test
    fun `A21N resolves to A321`() {
        val aliased = AircraftTypePhotoProvider.getPhotoForType("A21N")
        val direct = AircraftTypePhotoProvider.getPhotoForType("A321")
        assertEquals(direct, aliased)
    }

    @Test
    fun `B772 resolves to B77W`() {
        val aliased = AircraftTypePhotoProvider.getPhotoForType("B772")
        val direct = AircraftTypePhotoProvider.getPhotoForType("B77W")
        assertEquals(direct, aliased)
    }

    @Test
    fun `B78X resolves to B789`() {
        val aliased = AircraftTypePhotoProvider.getPhotoForType("B78X")
        val direct = AircraftTypePhotoProvider.getPhotoForType("B789")
        assertEquals(direct, aliased)
    }

    @Test
    fun `B38M resolves to 737 MAX 8`() {
        val result = AircraftTypePhotoProvider.getPhotoForType("B38M")
        assertNotNull(result)
        assertEquals("Boeing 737 MAX 8", result?.aircraftName)
    }

    @Test
    fun `B39M resolves to 737 MAX 8 photo`() {
        val aliased = AircraftTypePhotoProvider.getPhotoForType("B39M")
        val direct = AircraftTypePhotoProvider.getPhotoForType("B38M")
        assertEquals(direct, aliased)
    }

    @Test
    fun `A332 resolves to A333`() {
        val aliased = AircraftTypePhotoProvider.getPhotoForType("A332")
        val direct = AircraftTypePhotoProvider.getPhotoForType("A333")
        assertEquals(direct, aliased)
    }

    @Test
    fun `A359 resolves to A35K`() {
        val aliased = AircraftTypePhotoProvider.getPhotoForType("A359")
        val direct = AircraftTypePhotoProvider.getPhotoForType("A35K")
        assertEquals(direct, aliased)
    }

    @Test
    fun `CRJ7 resolves to CRJ9`() {
        val aliased = AircraftTypePhotoProvider.getPhotoForType("CRJ7")
        val direct = AircraftTypePhotoProvider.getPhotoForType("CRJ9")
        assertEquals(direct, aliased)
    }

    @Test
    fun `E170 resolves to E190`() {
        val aliased = AircraftTypePhotoProvider.getPhotoForType("E170")
        val direct = AircraftTypePhotoProvider.getPhotoForType("E190")
        assertEquals(direct, aliased)
    }

    @Test
    fun `DH8A resolves to DH8D`() {
        val aliased = AircraftTypePhotoProvider.getPhotoForType("DH8A")
        val direct = AircraftTypePhotoProvider.getPhotoForType("DH8D")
        assertEquals(direct, aliased)
    }

    @Test
    fun `AT72 resolves to AT76`() {
        val aliased = AircraftTypePhotoProvider.getPhotoForType("AT72")
        val direct = AircraftTypePhotoProvider.getPhotoForType("AT76")
        assertEquals(direct, aliased)
    }

    // -- Unknown types --

    @Test
    fun `getPhotoForType with unknown type returns null`() {
        assertNull(AircraftTypePhotoProvider.getPhotoForType("ZZZZ"))
    }

    @Test
    fun `getPhotoForType with random string returns null`() {
        assertNull(AircraftTypePhotoProvider.getPhotoForType("NOTAPLANE"))
    }

    @Test
    fun `getPhotoForType with numeric string returns null`() {
        assertNull(AircraftTypePhotoProvider.getPhotoForType("1234"))
    }

    @Test
    fun `getPhotoForType with special characters returns null`() {
        assertNull(AircraftTypePhotoProvider.getPhotoForType("@#$%"))
    }

    // -- Photo URL validity --

    @Test
    fun `all photo URLs start with https`() {
        val knownTypes = listOf(
            "B738", "B38M", "B744", "B752", "B762", "B77W", "B789",
            "A319", "A320", "A321", "A333", "A343", "A35K", "A388",
            "E190", "E195", "CRJ9", "AT76", "DH8D", "C919", "ARJ2"
        )
        for (type in knownTypes) {
            val info = AircraftTypePhotoProvider.getPhotoForType(type)
            assertNotNull("Expected photo info for $type", info)
            assertTrue(
                "Photo URL for $type should start with https: ${info?.photoUrl}",
                info?.photoUrl?.startsWith("https://") == true
            )
        }
    }

    @Test
    fun `all photo entries have non-blank photographer`() {
        val knownTypes = listOf(
            "B738", "B38M", "B744", "B752", "B762", "B77W", "B789",
            "A319", "A320", "A321", "A333", "A343", "A35K", "A388",
            "E190", "E195", "CRJ9", "AT76", "DH8D", "C919", "ARJ2"
        )
        for (type in knownTypes) {
            val info = AircraftTypePhotoProvider.getPhotoForType(type)
            assertNotNull("Expected photo info for $type", info)
            assertTrue(
                "Photographer for $type should not be blank",
                info?.photographer?.isNotBlank() == true
            )
        }
    }

    @Test
    fun `all photo entries have non-blank aircraft name`() {
        val knownTypes = listOf(
            "B738", "B38M", "B744", "B752", "B762", "B77W", "B789",
            "A319", "A320", "A321", "A333", "A343", "A35K", "A388",
            "E190", "E195", "CRJ9", "AT76", "DH8D", "C919", "ARJ2"
        )
        for (type in knownTypes) {
            val info = AircraftTypePhotoProvider.getPhotoForType(type)
            assertNotNull("Expected photo info for $type", info)
            assertTrue(
                "Aircraft name for $type should not be blank",
                info?.aircraftName?.isNotBlank() == true
            )
        }
    }
}
