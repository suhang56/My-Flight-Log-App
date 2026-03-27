package com.flightlog.app.data.calendar

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of successfully parsing a calendar event title into flight details.
 *
 * [flightNumber] is empty string when the title contains a route but no airline code.
 */
data class ParsedFlight(
    val flightNumber: String,        // e.g. "AA0011"; empty string when absent
    val departureCode: String,       // e.g. "ORD"
    val arrivalCode: String          // e.g. "CMH"
)

/**
 * Parses calendar event text fields to extract flight details.
 *
 * Four patterns are tried in priority order:
 *
 *  1. `"Flight AA0011 ORD-CMH"`          → flightNumber=AA0011, dep=ORD, arr=CMH
 *  2. `"AA0011 ORD-CMH"`                 → flightNumber=AA0011, dep=ORD, arr=CMH
 *  3. `"ORD to CMH"`                     → dep=ORD, arr=CMH, flightNumber=""
 *  4. `"Booking Confirmation: ORD→CMH"`  → dep=ORD, arr=CMH, flightNumber=""
 *
 * Returns null when none of the patterns match.
 */
@Singleton
class FlightEventParser @Inject constructor() {

    // Pattern 1: Optional "Flight" keyword, then airline+number, then route.
    // Matches: "Flight AA0011 ORD-CMH", "Flight UA 123 JFK→LAX"
    private val PATTERN_FLIGHT_KEYWORD = Regex(
        """\bFlight\s+([A-Z]{2}\d{2,4})\s+([A-Z]{3})\s*[-–→>]\s*([A-Z]{3})\b""",
        RegexOption.IGNORE_CASE
    )

    // Pattern 2: Airline code + number directly followed by a route, no keyword.
    // Matches: "AA0011 ORD-CMH", "DL 456 ATL→BOS"
    private val PATTERN_CODE_ROUTE = Regex(
        """\b([A-Z]{2}\d{2,4})\s+([A-Z]{3})\s*[-–→>]\s*([A-Z]{3})\b""",
        RegexOption.IGNORE_CASE
    )

    // Pattern 3: Route-only with " to " separator.
    // Matches: "ORD to CMH", "jfk to lax"
    private val PATTERN_ROUTE_TO = Regex(
        """\b([A-Z]{3})\s+to\s+([A-Z]{3})\b""",
        RegexOption.IGNORE_CASE
    )

    // Pattern 4: Route with dash/arrow separators, no flight number prefix.
    // Used as the fallback for booking confirmations like "Booking Confirmation: ORD→CMH".
    // Also catches plain "ORD-CMH" that survived patterns 1–3.
    private val PATTERN_ROUTE_ARROW = Regex(
        """\b([A-Z]{3})\s*[-–→>]\s*([A-Z]{3})\b""",
        RegexOption.IGNORE_CASE
    )

    // Standalone airline + flight number used to enrich a route-only match.
    private val PATTERN_FLIGHT_NUMBER = Regex(
        """\b([A-Z]{2}\d{2,4})\b"""
    )

    /**
     * Attempts to parse flight info from a calendar event.
     *
     * All three text fields are concatenated for matching so that, for example,
     * a flight number in the description can complement a route in the title.
     *
     * @return [ParsedFlight] when at least a departure and arrival code are found,
     *         null otherwise.
     */
    fun parse(title: String, description: String = "", location: String = ""): ParsedFlight? {
        // Concatenate with a neutral separator that won't accidentally form new tokens.
        val combined = "$title | $description | $location"

        parseWithFlightKeyword(combined)?.let { return it }
        parseCodeRoute(combined)?.let { return it }
        parseRouteToSeparator(combined)?.let { return it }
        parseRouteArrowOrDash(combined)?.let { return it }

        return null
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private fun parseWithFlightKeyword(text: String): ParsedFlight? {
        val m = PATTERN_FLIGHT_KEYWORD.find(text) ?: return null
        return ParsedFlight(
            flightNumber  = m.groupValues[1].uppercase(),
            departureCode = m.groupValues[2].uppercase(),
            arrivalCode   = m.groupValues[3].uppercase()
        )
    }

    private fun parseCodeRoute(text: String): ParsedFlight? {
        val m = PATTERN_CODE_ROUTE.find(text) ?: return null
        return ParsedFlight(
            flightNumber  = m.groupValues[1].uppercase(),
            departureCode = m.groupValues[2].uppercase(),
            arrivalCode   = m.groupValues[3].uppercase()
        )
    }

    private fun parseRouteToSeparator(text: String): ParsedFlight? {
        val routeMatch = PATTERN_ROUTE_TO.find(text) ?: return null
        val dep = routeMatch.groupValues[1].uppercase()
        val arr = routeMatch.groupValues[2].uppercase()
        // Opportunistically grab a flight number appearing anywhere else in the text.
        val flightNumber = PATTERN_FLIGHT_NUMBER.find(text)
            ?.groupValues?.get(1)?.uppercase()
            .orEmpty()
        return ParsedFlight(flightNumber = flightNumber, departureCode = dep, arrivalCode = arr)
    }

    private fun parseRouteArrowOrDash(text: String): ParsedFlight? {
        val routeMatch = PATTERN_ROUTE_ARROW.find(text) ?: return null
        val dep = routeMatch.groupValues[1].uppercase()
        val arr = routeMatch.groupValues[2].uppercase()
        val flightNumber = PATTERN_FLIGHT_NUMBER.find(text)
            ?.groupValues?.get(1)?.uppercase()
            .orEmpty()
        return ParsedFlight(flightNumber = flightNumber, departureCode = dep, arrivalCode = arr)
    }
}
