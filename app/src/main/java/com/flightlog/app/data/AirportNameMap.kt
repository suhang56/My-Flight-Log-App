package com.flightlog.app.data

/**
 * Maps common airport/city names to their 3-letter IATA codes.
 *
 * Used by [com.flightlog.app.data.calendar.FlightEventParser] to resolve
 * stop/layover city names (e.g. "Chicago (Midway)" -> "MDW") found in
 * Southwest-style calendar event descriptions.
 *
 * Covers major US airports. Lookup is case-insensitive with substring matching.
 */
object AirportNameMap {

    private val MAP = mapOf(
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
     * Resolves an airport/city name to an IATA code.
     * Tries exact match first, then substring match on known airport names.
     * If the input is already a 3-letter code, returns it uppercased.
     *
     * @return the IATA code, or null when no match is found.
     */
    fun resolve(name: String): String? {
        val lower = name.lowercase().trim()

        // Direct match
        MAP[lower]?.let { return it }

        // Substring match: check if any known name appears in the input
        for ((key, code) in MAP) {
            if (lower.contains(key)) return code
        }

        // Check if it's already a 3-letter IATA code
        if (lower.length == 3 && lower.all { it.isLetter() }) {
            return lower.uppercase()
        }

        return null
    }
}
