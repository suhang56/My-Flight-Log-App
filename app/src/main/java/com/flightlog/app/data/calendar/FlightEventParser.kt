package com.flightlog.app.data.calendar

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

    // Common US airport name → IATA code mapping for resolving stops
    private val AIRPORT_NAME_MAP = mapOf(
        "midway" to "MDW", "chicago midway" to "MDW", "chicago (midway)" to "MDW",
        "o'hare" to "ORD", "chicago o'hare" to "ORD", "chicago (o'hare)" to "ORD",
        "los angeles" to "LAX", "lax" to "LAX",
        "denver" to "DEN",
        "dallas" to "DFW", "dallas/fort worth" to "DFW", "dallas fort worth" to "DFW",
        "atlanta" to "ATL", "hartsfield" to "ATL",
        "san francisco" to "SFO",
        "seattle" to "SEA", "seattle-tacoma" to "SEA",
        "phoenix" to "PHX",
        "las vegas" to "LAS",
        "orlando" to "MCO",
        "houston hobby" to "HOU", "houston (hobby)" to "HOU",
        "houston intercontinental" to "IAH", "houston (intercontinental)" to "IAH", "houston bush" to "IAH",
        "baltimore" to "BWI", "baltimore/washington" to "BWI",
        "nashville" to "BNA",
        "austin" to "AUS",
        "san diego" to "SAN",
        "kansas city" to "MCI",
        "st. louis" to "STL", "st louis" to "STL", "saint louis" to "STL",
        "tampa" to "TPA",
        "fort lauderdale" to "FLL",
        "new york (jfk)" to "JFK", "jfk" to "JFK",
        "new york (laguardia)" to "LGA", "laguardia" to "LGA",
        "newark" to "EWR",
        "boston" to "BOS",
        "minneapolis" to "MSP",
        "detroit" to "DTW",
        "columbus" to "CMH",
        "oakland" to "OAK",
        "reno" to "RNO",
        "portland" to "PDX",
        "salt lake city" to "SLC",
        "indianapolis" to "IND",
        "pittsburgh" to "PIT",
        "cleveland" to "CLE",
        "cincinnati" to "CVG",
        "new orleans" to "MSY",
        "miami" to "MIA",
        "charlotte" to "CLT",
        "raleigh" to "RDU", "raleigh-durham" to "RDU",
        "washington dulles" to "IAD", "dulles" to "IAD",
        "washington reagan" to "DCA", "reagan" to "DCA",
        "san jose" to "SJC",
        "honolulu" to "HNL",
        "anchorage" to "ANC"
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
        val stopCode = PATTERN_STOP.find(text)?.groupValues?.get(1)?.trim()?.let { resolveAirportName(it) }

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

    /**
     * Resolves an airport/city name to an IATA code.
     * Tries exact match first, then substring match on known airport names.
     */
    private fun resolveAirportName(name: String): String? {
        val lower = name.lowercase().trim()

        // Direct match
        AIRPORT_NAME_MAP[lower]?.let { return it }

        // Substring match: check if any known name appears in the input
        for ((key, code) in AIRPORT_NAME_MAP) {
            if (lower.contains(key)) return code
        }

        // Check if it's already a 3-letter IATA code
        if (lower.length == 3 && lower.all { it.isLetter() }) {
            return lower.uppercase()
        }

        return null
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
