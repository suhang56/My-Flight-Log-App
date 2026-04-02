package com.flightlog.app.data.calendar

/**
 * Maps common US airport/city names to their 3-letter IATA codes.
 *
 * Used by [FlightEventParser] to resolve stop/layover names (e.g. "Chicago (Midway)" -> "MDW")
 * from Southwest-style calendar event descriptions.
 *
 * Lookup is case-insensitive: callers should lower-case before querying.
 */
val AIRPORT_NAME_MAP: Map<String, String> = mapOf(
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
