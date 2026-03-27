package com.flightlog.app.data.calendar

import com.flightlog.app.data.AirportCoordinatesMap
import com.flightlog.app.data.AirportNameMap
import com.flightlog.app.data.AirportTimezoneMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of successfully parsing a calendar event title into flight details.
 *
 * [flightNumber] is empty string when the title contains a route but no airline code.
 * [departureCode] and [arrivalCode] are empty strings for multi-leg flights where
 * only the flight numbers are known and route resolution must happen via API.
 */
data class ParsedFlight(
    val flightNumber: String,        // e.g. "AA0011"; empty string when absent
    val departureCode: String,       // e.g. "ORD"; empty string when pending API lookup
    val arrivalCode: String          // e.g. "CMH"; empty string when pending API lookup
)

/**
 * Parses calendar event text fields to extract flight details.
 *
 * Five patterns are tried in priority order:
 *
 *  5. `"Southwest Flight 1946/3034 Columbus to Los Angeles"`
 *     -> [WN1946 (dep="",arr=""), WN3034 (dep="",arr="")]  (multi-leg)
 *     -> or single: `"Delta Flight 456 JFK-LAX"` -> [DL456 (dep=JFK,arr=LAX)]
 *  1. `"Flight AA0011 ORD-CMH"`          -> [flightNumber=AA0011, dep=ORD, arr=CMH]
 *  2. `"AA0011 ORD-CMH"`                 -> [flightNumber=AA0011, dep=ORD, arr=CMH]
 *  3. `"ORD to CMH"`                     -> [dep=ORD, arr=CMH, flightNumber=""]
 *  4. `"Booking Confirmation: ORD->CMH"` -> [dep=ORD, arr=CMH, flightNumber=""]
 *
 * Returns an empty list when none of the patterns match.
 */
@Singleton
class FlightEventParser @Inject constructor(
    private val airlineIataMap: AirlineIataMap
) {

    // Pattern 5: Airline name + optional "Flight" + slash-separated numbers + optional route.
    // Matches: "Southwest Flight 1946/3034 Columbus to Los Angeles"
    //          "Southwest 1946/3034 CMH-LAX"
    //          "Delta Flight 456 JFK-LAX"
    private val PATTERN_AIRLINE_NAME = Regex(
        """\b(\w[\w\s]*?)\s+(?:Flight\s+)?(\d{1,4}(?:/\d{1,4})+|\d{1,4})\b""",
        RegexOption.IGNORE_CASE
    )

    // Pattern 1: "Flight" keyword, then airline+number, then route.
    private val PATTERN_FLIGHT_KEYWORD = Regex(
        """\bFlight\s+([A-Z]{2}\d{2,4})\s+([A-Z]{3})\s*[-\u2013\u2192>]\s*([A-Z]{3})\b""",
        RegexOption.IGNORE_CASE
    )

    // Pattern 2: Airline code + number directly followed by a route, no keyword.
    private val PATTERN_CODE_ROUTE = Regex(
        """\b([A-Z]{2}\d{2,4})\s+([A-Z]{3})\s*[-\u2013\u2192>]\s*([A-Z]{3})\b""",
        RegexOption.IGNORE_CASE
    )

    // Pattern 3: Route-only with " to " separator.
    private val PATTERN_ROUTE_TO = Regex(
        """\b([A-Z]{3})\s+to\s+([A-Z]{3})\b""",
        RegexOption.IGNORE_CASE
    )

    // Pattern 4: Route with dash/arrow separators, no flight number prefix.
    private val PATTERN_ROUTE_ARROW = Regex(
        """\b([A-Z]{3})\s*[-\u2013\u2192>]\s*([A-Z]{3})\b""",
        RegexOption.IGNORE_CASE
    )

    // Standalone airline + flight number used to enrich a route-only match.
    private val PATTERN_FLIGHT_NUMBER = Regex(
        """\b([A-Z]{2}\d{2,4})\b"""
    )

    // Route embedded in text: 3-letter IATA codes separated by dash/arrow or " to ".
    private val PATTERN_ROUTE_EMBEDDED = Regex(
        """\b([A-Z]{3})\s*(?:[-\u2013\u2192>]|to)\s*([A-Z]{3})\b""",
        RegexOption.IGNORE_CASE
    )

    // Southwest-style description patterns:
    // "Departs: 03:50 PM CMH" or "Departs: CMH"
    private val PATTERN_DEPARTS = Regex(
        """Departs:\s+(?:\d{1,2}:\d{2}\s*[AP]M\s+)?([A-Z]{3})\b""",
        RegexOption.IGNORE_CASE
    )
    // "Arrives: 08:15 PM LAX" or "Arrives: LAX"
    private val PATTERN_ARRIVES = Regex(
        """Arrives:\s+(?:\d{1,2}:\d{2}\s*[AP]M\s+)?([A-Z]{3})\b""",
        RegexOption.IGNORE_CASE
    )
    // "Stop: Chicago (Midway), IL" or "Layover: Denver, CO"
    private val PATTERN_STOP = Regex(
        """(?:Stop|Layover|Connect(?:ion)?)\s*:\s*(.+?)(?:[,.]|\s*Change)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Attempts to parse flight info from a calendar event.
     *
     * All three text fields are concatenated for matching so that, for example,
     * a flight number in the description can complement a route in the title.
     *
     * @return list of [ParsedFlight] (one per leg); empty when nothing matches.
     */
    fun parse(title: String, description: String = "", location: String = ""): List<ParsedFlight> {
        val combined = "$title | $description | $location"

        parseAirlineName(combined).let { if (it.isNotEmpty()) return it }
        parseWithFlightKeyword(combined)?.let { return listOf(it) }
        parseCodeRoute(combined)?.let { return listOf(it) }
        parseRouteToSeparator(combined)?.let { return listOf(it) }
        parseRouteArrowOrDash(combined)?.let { return listOf(it) }

        return emptyList()
    }

    // -- private helpers --

    /**
     * Pattern 5 (highest priority): airline name with flight numbers.
     *
     * For multi-leg (slash-separated numbers), each leg gets its own [ParsedFlight]
     * with departure/arrival left empty for later API resolution.
     *
     * For a single flight number, route codes are populated if found in the text.
     */
    private fun parseAirlineName(text: String): List<ParsedFlight> {
        val m = PATTERN_AIRLINE_NAME.find(text) ?: return emptyList()
        val airlinePart = m.groupValues[1].trim()
        val numbersPart = m.groupValues[2]

        val iataCode = airlineIataMap.findIataCode(airlinePart) ?: return emptyList()

        val flightNumbers = numbersPart.split("/").map { it.trim() }

        if (flightNumbers.size > 1) {
            // Multi-leg: try to extract routes from description (Southwest-style).
            val legs = resolveMultiLegRoutes(text, flightNumbers.size)
            return flightNumbers.mapIndexed { index, num ->
                ParsedFlight(
                    flightNumber  = "$iataCode$num",
                    departureCode = legs.getOrNull(index)?.first.orEmpty(),
                    arrivalCode   = legs.getOrNull(index)?.second.orEmpty()
                )
            }
        }

        // Single flight number — try to extract route from the rest of the text.
        val flightNumber = "$iataCode${flightNumbers.first()}"
        val route = PATTERN_ROUTE_EMBEDDED.find(text)
        return if (route != null) {
            listOf(
                ParsedFlight(
                    flightNumber  = flightNumber,
                    departureCode = route.groupValues[1].uppercase(),
                    arrivalCode   = route.groupValues[2].uppercase()
                )
            )
        } else {
            listOf(
                ParsedFlight(
                    flightNumber  = flightNumber,
                    departureCode = "",
                    arrivalCode   = ""
                )
            )
        }
    }

    /**
     * Extracts per-leg departure/arrival codes from Southwest-style descriptions.
     *
     * Parses "Departs: 03:50 PM CMH", "Stop: Chicago (Midway), IL", "Arrives: 08:15 PM LAX"
     * into leg routes: [(CMH, MDW), (MDW, LAX)].
     *
     * Returns a list of (departure, arrival) pairs; empty pairs for legs that can't be resolved.
     */
    private fun resolveMultiLegRoutes(text: String, legCount: Int): List<Pair<String, String>> {
        val departureCode = PATTERN_DEPARTS.find(text)?.groupValues?.get(1)?.uppercase()
        val arrivalCode = PATTERN_ARRIVES.find(text)?.groupValues?.get(1)?.uppercase()
        val stopCode = PATTERN_STOP.find(text)?.groupValues?.get(1)?.trim()?.let { AirportNameMap.resolve(it) }

        // 2-leg flight with stop: Leg1 = departs→stop, Leg2 = stop→arrives
        if (legCount == 2 && departureCode != null && arrivalCode != null && stopCode != null) {
            return listOf(
                departureCode to stopCode,
                stopCode to arrivalCode
            )
        }

        // 2-leg flight without stop but with depart/arrive: leave intermediate empty for API
        if (legCount == 2 && departureCode != null && arrivalCode != null) {
            return listOf(
                departureCode to "",
                "" to arrivalCode
            )
        }

        // Fallback: empty routes, will be resolved via API
        return List(legCount) { "" to "" }
    }

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
        // Validate at least one code is a known airport to avoid false positives
        // like "Day to Add" being parsed as DAY → ADD
        if (!isKnownAirport(dep) && !isKnownAirport(arr)) return null
        val flightNumber = PATTERN_FLIGHT_NUMBER.find(text)
            ?.groupValues?.get(1)?.uppercase()
            .orEmpty()
        return ParsedFlight(flightNumber = flightNumber, departureCode = dep, arrivalCode = arr)
    }

    private fun parseRouteArrowOrDash(text: String): ParsedFlight? {
        val routeMatch = PATTERN_ROUTE_ARROW.find(text) ?: return null
        val dep = routeMatch.groupValues[1].uppercase()
        val arr = routeMatch.groupValues[2].uppercase()
        // Validate at least one code is a known airport
        if (!isKnownAirport(dep) && !isKnownAirport(arr)) return null
        val flightNumber = PATTERN_FLIGHT_NUMBER.find(text)
            ?.groupValues?.get(1)?.uppercase()
            .orEmpty()
        return ParsedFlight(flightNumber = flightNumber, departureCode = dep, arrivalCode = arr)
    }

    /** Checks if a 3-letter code is a known IATA airport code. */
    private fun isKnownAirport(code: String): Boolean =
        AirportCoordinatesMap.coordinatesFor(code) != null ||
        AirportTimezoneMap.timezoneFor(code) != null
}
