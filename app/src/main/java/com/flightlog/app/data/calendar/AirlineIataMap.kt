package com.flightlog.app.data.calendar

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps common airline names to their 2-letter IATA codes.
 *
 * Lookup is case-insensitive and supports partial matching: the input text is
 * checked against every known alias, and the first alias that appears as a
 * substring wins.  Longer aliases are tried first so that "American Airlines"
 * matches before "American" when both are present in the input.
 */
@Singleton
class AirlineIataMap @Inject constructor() {

    /**
     * Each entry maps an IATA code to its known name variants.
     * Variants are stored lowercase; longer names come first so the
     * substring search prefers the most specific match.
     */
    private val airlineAliases: List<Pair<String, String>> = buildList {
        fun add(iata: String, vararg names: String) {
            for (name in names.sortedByDescending { it.length }) {
                add(name.lowercase() to iata)
            }
        }

        add("WN", "Southwest Airlines", "Southwest")
        add("AA", "American Airlines", "American")
        add("DL", "Delta Air Lines", "Delta Airlines", "Delta")
        add("UA", "United Airlines", "United")
        add("B6", "JetBlue Airways", "JetBlue")
        add("AS", "Alaska Airlines", "Alaska")
        add("NK", "Spirit Airlines", "Spirit")
        add("F9", "Frontier Airlines", "Frontier")
        add("HA", "Hawaiian Airlines", "Hawaiian")
        add("SY", "Sun Country Airlines", "Sun Country")
        add("G4", "Allegiant Air", "Allegiant")
    }

    /**
     * Reverse lookup: maps IATA codes to their canonical full names.
     * Used by the Statistics screen to display full airline names.
     */
    private val canonicalNames: Map<String, String> = mapOf(
        "WN" to "Southwest Airlines",
        "AA" to "American Airlines",
        "DL" to "Delta Air Lines",
        "UA" to "United Airlines",
        "B6" to "JetBlue Airways",
        "AS" to "Alaska Airlines",
        "NK" to "Spirit Airlines",
        "F9" to "Frontier Airlines",
        "HA" to "Hawaiian Airlines",
        "SY" to "Sun Country Airlines",
        "G4" to "Allegiant Air",
        "NH" to "ANA",
        "JL" to "Japan Airlines",
        "BA" to "British Airways",
        "LH" to "Lufthansa",
        "AF" to "Air France",
        "EK" to "Emirates",
        "SQ" to "Singapore Airlines",
        "CX" to "Cathay Pacific",
        "QF" to "Qantas",
        "AC" to "Air Canada",
        "KE" to "Korean Air",
        "OZ" to "Asiana Airlines",
        "TK" to "Turkish Airlines",
        "QR" to "Qatar Airways"
    )

    /**
     * Returns the full airline name for a given IATA code.
     * Falls back to the uppercase IATA code itself if unknown.
     */
    fun getFullName(iataCode: String): String =
        canonicalNames[iataCode.uppercase()] ?: iataCode.uppercase()

    /**
     * Attempts to find an IATA code for an airline mentioned in [text].
     *
     * @return the 2-letter IATA code, or null when no known airline is found.
     */
    fun findIataCode(text: String): String? {
        val lower = text.lowercase()
        for ((alias, iata) in airlineAliases) {
            if (lower.contains(alias)) return iata
        }
        return null
    }
}
