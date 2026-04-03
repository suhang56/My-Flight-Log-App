package com.flightlog.app.ui.logbook

import com.flightlog.app.data.local.entity.LogbookFlight
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightDetailShareTextTest {

    private fun baseFlight(rating: Int? = null) = LogbookFlight(
        id = 1L,
        flightNumber = "AA100",
        departureCode = "JFK",
        arrivalCode = "LAX",
        departureDateEpochDay = 20000L,
        departureTimeMillis = 1700000000000L,
        rating = rating
    )

    @Test
    fun `share text includes star rating when rated`() {
        val text = buildShareText(baseFlight(rating = 4))
        assertTrue(text.contains("Rating:"))
        assertTrue(text.contains("\u2605\u2605\u2605\u2605\u2606"))
    }

    @Test
    fun `share text excludes rating when null`() {
        val text = buildShareText(baseFlight(rating = null))
        assertFalse(text.contains("Rating:"))
    }

    @Test
    fun `share text shows all 5 stars for max rating`() {
        val text = buildShareText(baseFlight(rating = 5))
        assertTrue(text.contains("\u2605\u2605\u2605\u2605\u2605"))
        assertFalse(text.contains("\u2606"))
    }

    @Test
    fun `share text shows 1 filled and 4 empty for min rating`() {
        val text = buildShareText(baseFlight(rating = 1))
        assertTrue(text.contains("\u2605\u2606\u2606\u2606\u2606"))
    }
}
