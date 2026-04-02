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
        add("NH", "All Nippon Airways")
        add("JL", "Japan Airlines")
        add("CX", "Cathay Pacific")
        add("SQ", "Singapore Airlines")
        add("QF", "Qantas")
        add("EK", "Emirates")
        add("LH", "Lufthansa")
        add("BA", "British Airways")
        add("AF", "Air France")
        add("KL", "KLM Royal Dutch Airlines")
        add("QR", "Qatar Airways")
        add("TK", "Turkish Airlines")
        add("AC", "Air Canada")
    }

    /**
     * Maps IATA code to the primary (first / longest) airline name.
     */
    private val codeToName: Map<String, String> = buildMap {
        fun add(iata: String, primaryName: String) {
            put(iata, primaryName)
        }
        add("WN", "Southwest Airlines")
        add("AA", "American Airlines")
        add("DL", "Delta Air Lines")
        add("UA", "United Airlines")
        add("B6", "JetBlue Airways")
        add("AS", "Alaska Airlines")
        add("NK", "Spirit Airlines")
        add("F9", "Frontier Airlines")
        add("HA", "Hawaiian Airlines")
        add("SY", "Sun Country Airlines")
        add("G4", "Allegiant Air")
        add("NH", "All Nippon Airways")
        add("JL", "Japan Airlines")
        add("CX", "Cathay Pacific")
        add("SQ", "Singapore Airlines")
        add("QF", "Qantas")
        add("EK", "Emirates")
        add("LH", "Lufthansa")
        add("BA", "British Airways")
        add("AF", "Air France")
        add("KL", "KLM Royal Dutch Airlines")
        add("QR", "Qatar Airways")
        add("TK", "Turkish Airlines")
        add("AC", "Air Canada")
        add("CA", "Air China")
        add("MU", "China Eastern Airlines")
        add("CZ", "China Southern Airlines")
        add("KE", "Korean Air")
        add("OZ", "Asiana Airlines")
        add("TG", "Thai Airways")
        add("AI", "Air India")
        add("ET", "Ethiopian Airlines")
        add("MS", "EgyptAir")
        add("SK", "SAS Scandinavian Airlines")
        add("AY", "Finnair")
        add("IB", "Iberia")
        add("TP", "TAP Air Portugal")
        add("LX", "Swiss International Air Lines")
        add("OS", "Austrian Airlines")
        add("LO", "LOT Polish Airlines")
        add("VS", "Virgin Atlantic")
        add("EY", "Etihad Airways")
        add("GA", "Garuda Indonesia")
        add("MH", "Malaysia Airlines")
        add("PR", "Philippine Airlines")
        add("VN", "Vietnam Airlines")
        add("CI", "China Airlines")
        add("BR", "EVA Air")
        add("JQ", "Jetstar Airways")
        add("MM", "Peach Aviation")
        add("7C", "Jeju Air")
        add("TW", "T'way Air")
        add("BC", "Skymark Airlines")
    }

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

    /**
     * Returns the full airline name for a given 2-letter IATA [code], or null if unknown.
     */
    fun getAirlineName(code: String): String? = codeToName[code.uppercase()]
}
