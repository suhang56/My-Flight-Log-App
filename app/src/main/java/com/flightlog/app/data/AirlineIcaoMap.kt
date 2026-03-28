package com.flightlog.app.data

/**
 * Static lookup of 2-letter IATA airline prefixes to 3-letter ICAO airline prefixes.
 * Used by FlightRouteServiceImpl to retry FlightAware AeroAPI lookups with ICAO
 * identifiers when IATA codes return empty results.
 *
 * FlightAware internally uses ICAO flight identifiers. Some IATA codes auto-map
 * (e.g., UA → UAL), but others do not (e.g., JL does NOT auto-map to JAL).
 *
 * Covers ~120 airlines by passenger volume.
 */
object AirlineIcaoMap {

    /**
     * Converts an IATA flight number (e.g., "JL5") to its ICAO equivalent ("JAL5").
     * Returns null if the airline prefix is not recognized or the input is invalid.
     */
    fun toIcaoFlightNumber(iataFlightNumber: String): String? {
        val trimmed = iataFlightNumber.trim().uppercase()
        if (trimmed.length < 3) return null // minimum: 2-char prefix + 1 digit

        // IATA airline codes are always exactly 2 characters (may contain digits, e.g., "6E", "3U")
        val prefix = trimmed.substring(0, 2)
        val numericPart = trimmed.substring(2)
        if (numericPart.isEmpty() || !numericPart.first().isDigit()) return null

        val icaoPrefix = MAP[prefix] ?: return null
        return "$icaoPrefix$numericPart"
    }

    /** Returns the 3-letter ICAO prefix for a 2-letter IATA code, or null if unknown. */
    fun icaoFor(iataPrefix: String): String? = MAP[iataPrefix.uppercase()]

    private val MAP: Map<String, String> = mapOf(
        // ── United States ──────────────────────────────────────────────────────
        "AA" to "AAL",   // American Airlines
        "DL" to "DAL",   // Delta Air Lines
        "UA" to "UAL",   // United Airlines
        "WN" to "SWA",   // Southwest Airlines
        "AS" to "ASA",   // Alaska Airlines
        "B6" to "JBU",   // JetBlue Airways
        "NK" to "NKS",   // Spirit Airlines
        "F9" to "FFT",   // Frontier Airlines
        "HA" to "HAL",   // Hawaiian Airlines
        "G4" to "AAY",   // Allegiant Air
        "SY" to "SCX",   // Sun Country Airlines

        // ── Canada ─────────────────────────────────────────────────────────────
        "AC" to "ACA",   // Air Canada
        "WS" to "WJA",   // WestJet
        "PD" to "POE",   // Porter Airlines
        "TS" to "TSC",   // Air Transat

        // ── Mexico / Central America / Caribbean ───────────────────────────────
        "AM" to "AMX",   // Aeromexico
        "4O" to "AIJ",   // Interjet (ABC Aerolineas)
        "Y4" to "VOI",   // Volaris
        "CM" to "CMP",   // Copa Airlines

        // ── South America ──────────────────────────────────────────────────────
        "LA" to "LAN",   // LATAM Airlines
        "G3" to "GLO",   // GOL Linhas Aereas
        "AD" to "AZU",   // Azul Brazilian Airlines
        "AR" to "ARG",   // Aerolineas Argentinas
        "AV" to "AVA",   // Avianca

        // ── United Kingdom / Ireland ───────────────────────────────────────────
        "BA" to "BAW",   // British Airways
        "VS" to "VIR",   // Virgin Atlantic
        "EI" to "EIN",   // Aer Lingus
        "U2" to "EZY",   // easyJet
        "FR" to "RYR",   // Ryanair

        // ── Western Europe ─────────────────────────────────────────────────────
        "AF" to "AFR",   // Air France
        "LH" to "DLH",   // Lufthansa
        "KL" to "KLM",   // KLM Royal Dutch Airlines
        "IB" to "IBE",   // Iberia
        "AZ" to "ITY",   // ITA Airways (formerly Alitalia)
        "LX" to "SWR",   // Swiss International Air Lines
        "OS" to "AUA",   // Austrian Airlines
        "SN" to "BEL",   // Brussels Airlines
        "TP" to "TAP",   // TAP Air Portugal
        "EW" to "EWG",   // Eurowings
        "VY" to "VLG",   // Vueling
        "W6" to "WZZ",   // Wizz Air

        // ── Northern Europe ────────────────────────────────────────────────────
        "SK" to "SAS",   // SAS Scandinavian Airlines
        "AY" to "FIN",   // Finnair
        "DY" to "NAX",   // Norwegian Air Shuttle
        "FI" to "ICE",   // Icelandair

        // ── Eastern Europe / Russia ────────────────────────────────────────────
        "LO" to "LOT",   // LOT Polish Airlines
        "OK" to "CSA",   // Czech Airlines
        "SU" to "AFL",   // Aeroflot
        "S7" to "SBI",   // S7 Airlines

        // ── Turkey ─────────────────────────────────────────────────────────────
        "TK" to "THY",   // Turkish Airlines
        "PC" to "PGT",   // Pegasus Airlines

        // ── Middle East ────────────────────────────────────────────────────────
        "EK" to "UAE",   // Emirates
        "QR" to "QTR",   // Qatar Airways
        "EY" to "ETD",   // Etihad Airways
        "GF" to "GFA",   // Gulf Air
        "SV" to "SVA",   // Saudia
        "WY" to "OMA",   // Oman Air
        "RJ" to "RJA",   // Royal Jordanian
        "LY" to "ELY",   // El Al Israel Airlines
        "MS" to "MSR",   // EgyptAir

        // ── Africa ─────────────────────────────────────────────────────────────
        "ET" to "ETH",   // Ethiopian Airlines
        "SA" to "SAA",   // South African Airways
        "KQ" to "KQA",   // Kenya Airways
        "AT" to "RAM",   // Royal Air Maroc
        "W3" to "ARA",   // Arik Air

        // ── South Asia ─────────────────────────────────────────────────────────
        "AI" to "AIC",   // Air India
        "6E" to "IGO",   // IndiGo
        "UK" to "VTI",   // Vistara
        "SG" to "SEJ",   // SpiceJet
        "UL" to "ALK",   // SriLankan Airlines
        "BG" to "BBC",   // Biman Bangladesh Airlines
        "PK" to "PIA",   // Pakistan International Airlines

        // ── Southeast Asia ─────────────────────────────────────────────────────
        "SQ" to "SIA",   // Singapore Airlines
        "TG" to "THA",   // Thai Airways
        "MH" to "MAS",   // Malaysia Airlines
        "AK" to "AXM",   // AirAsia
        "GA" to "GIA",   // Garuda Indonesia
        "PR" to "PAL",   // Philippine Airlines
        "VN" to "HVN",   // Vietnam Airlines
        "QZ" to "AWQ",   // AirAsia Indonesia
        "TR" to "TGW",   // Scoot
        "CZ" to "CSN",   // China Southern Airlines (also flies SEA routes)

        // ── East Asia — Japan ──────────────────────────────────────────────────
        "JL" to "JAL",   // Japan Airlines
        "NH" to "ANA",   // All Nippon Airways
        "BC" to "SKY",   // Skymark Airlines
        "MM" to "APJ",   // Peach Aviation
        "GK" to "JJP",   // Jetstar Japan

        // ── East Asia — South Korea ────────────────────────────────────────────
        "KE" to "KAL",   // Korean Air
        "OZ" to "AAR",   // Asiana Airlines
        "7C" to "JJA",   // Jeju Air
        "TW" to "TWB",   // T'way Air
        "LJ" to "JNA",   // Jin Air

        // ── East Asia — China ──────────────────────────────────────────────────
        "CA" to "CCA",   // Air China
        "MU" to "CES",   // China Eastern Airlines
        "HU" to "CHH",   // Hainan Airlines
        "ZH" to "CSZ",   // Shenzhen Airlines
        "3U" to "CSC",   // Sichuan Airlines
        "MF" to "CXA",   // Xiamen Airlines

        // ── East Asia — Hong Kong / Macau / Taiwan ─────────────────────────────
        "CX" to "CPA",   // Cathay Pacific
        "HX" to "CRK",   // Hong Kong Airlines
        "CI" to "CAL",   // China Airlines (Taiwan)
        "BR" to "EVA",   // EVA Air
        "NX" to "AMU",   // Air Macau

        // ── Oceania ────────────────────────────────────────────────────────────
        "QF" to "QFA",   // Qantas
        "NZ" to "ANZ",   // Air New Zealand
        "VA" to "VOZ",   // Virgin Australia
        "JQ" to "JST",   // Jetstar Airways
        "FJ" to "FJI"    // Fiji Airways
    )
}
