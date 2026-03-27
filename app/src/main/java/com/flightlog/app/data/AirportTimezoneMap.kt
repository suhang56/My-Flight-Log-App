package com.flightlog.app.data

/**
 * Static lookup of IATA airport codes to IANA timezone identifiers.
 * Used as an offline fallback when the AviationStack API does not return timezone info.
 *
 * Covers ~200 of the world's busiest airports by passenger volume.
 */
object AirportTimezoneMap {

    /** Returns the IANA timezone for the given IATA code, or null if unknown. */
    fun timezoneFor(iataCode: String): String? = MAP[iataCode.uppercase()]

    private val MAP: Map<String, String> = mapOf(
        // ── North America ───────────────────────────────────────────────────
        // United States — Eastern
        "ATL" to "America/New_York",
        "BOS" to "America/New_York",
        "BWI" to "America/New_York",
        "CLT" to "America/New_York",
        "CMH" to "America/New_York",
        "DTW" to "America/New_York",
        "EWR" to "America/New_York",
        "FLL" to "America/New_York",
        "IAD" to "America/New_York",
        "JFK" to "America/New_York",
        "LGA" to "America/New_York",
        "MCO" to "America/New_York",
        "MIA" to "America/New_York",
        "PBI" to "America/New_York",
        "PHL" to "America/New_York",
        "PIT" to "America/New_York",
        "RDU" to "America/New_York",
        "RSW" to "America/New_York",
        "TPA" to "America/New_York",
        // United States — Central
        "AUS" to "America/Chicago",
        "DAL" to "America/Chicago",
        "DFW" to "America/Chicago",
        "HOU" to "America/Chicago",
        "IAH" to "America/Chicago",
        "IND" to "America/Indiana/Indianapolis",
        "MCI" to "America/Chicago",
        "MDW" to "America/Chicago",
        "MEM" to "America/Chicago",
        "MSP" to "America/Chicago",
        "MSY" to "America/Chicago",
        "ORD" to "America/Chicago",
        "SAT" to "America/Chicago",
        "STL" to "America/Chicago",
        "BNA" to "America/Chicago",
        // United States — Mountain
        "ABQ" to "America/Denver",
        "COS" to "America/Denver",
        "DEN" to "America/Denver",
        "PHX" to "America/Phoenix",
        "SLC" to "America/Denver",
        // United States — Pacific
        "LAS" to "America/Los_Angeles",
        "LAX" to "America/Los_Angeles",
        "OAK" to "America/Los_Angeles",
        "ONT" to "America/Los_Angeles",
        "PDX" to "America/Los_Angeles",
        "SAN" to "America/Los_Angeles",
        "SEA" to "America/Los_Angeles",
        "SFO" to "America/Los_Angeles",
        "SJC" to "America/Los_Angeles",
        "SMF" to "America/Los_Angeles",
        // United States — Other
        "ANC" to "America/Anchorage",
        "HNL" to "Pacific/Honolulu",
        "OGG" to "Pacific/Honolulu",
        // Canada
        "YEG" to "America/Edmonton",
        "YOW" to "America/Toronto",
        "YUL" to "America/Toronto",
        "YVR" to "America/Vancouver",
        "YWG" to "America/Winnipeg",
        "YYC" to "America/Edmonton",
        "YYZ" to "America/Toronto",
        // Mexico
        "CUN" to "America/Cancun",
        "GDL" to "America/Mexico_City",
        "MEX" to "America/Mexico_City",
        "SJD" to "America/Mazatlan",
        "TIJ" to "America/Tijuana",

        // ── Central America & Caribbean ─────────────────────────────────────
        "PTY" to "America/Panama",
        "SJO" to "America/Costa_Rica",
        "SAL" to "America/El_Salvador",
        "MBJ" to "America/Jamaica",
        "NAS" to "America/Nassau",
        "SXM" to "America/Lower_Princes",
        "PUJ" to "America/Santo_Domingo",
        "SDQ" to "America/Santo_Domingo",
        "SJU" to "America/Puerto_Rico",
        "HAV" to "America/Havana",

        // ── South America ───────────────────────────────────────────────────
        "BOG" to "America/Bogota",
        "BSB" to "America/Sao_Paulo",
        "AEP" to "America/Argentina/Buenos_Aires",
        "EZE" to "America/Argentina/Buenos_Aires",
        "GIG" to "America/Sao_Paulo",
        "GRU" to "America/Sao_Paulo",
        "LIM" to "America/Lima",
        "SCL" to "America/Santiago",
        "UIO" to "America/Guayaquil",
        "VVI" to "America/La_Paz",

        // ── Europe ──────────────────────────────────────────────────────────
        // Western Europe
        "AMS" to "Europe/Amsterdam",
        "BCN" to "Europe/Madrid",
        "BRU" to "Europe/Brussels",
        "CDG" to "Europe/Paris",
        "DUB" to "Europe/Dublin",
        "DUS" to "Europe/Berlin",
        "EDI" to "Europe/London",
        "FCO" to "Europe/Rome",
        "FRA" to "Europe/Berlin",
        "GVA" to "Europe/Zurich",
        "HAM" to "Europe/Berlin",
        "LGW" to "Europe/London",
        "LHR" to "Europe/London",
        "LIS" to "Europe/Lisbon",
        "MAD" to "Europe/Madrid",
        "MAN" to "Europe/London",
        "MRS" to "Europe/Paris",
        "MUC" to "Europe/Berlin",
        "MXP" to "Europe/Rome",
        "NCE" to "Europe/Paris",
        "ORY" to "Europe/Paris",
        "OSL" to "Europe/Oslo",
        "PMI" to "Europe/Madrid",
        "STN" to "Europe/London",
        "BER" to "Europe/Berlin",
        "VCE" to "Europe/Rome",
        "VIE" to "Europe/Vienna",
        "ZRH" to "Europe/Zurich",
        // Northern Europe
        "ARN" to "Europe/Stockholm",
        "CPH" to "Europe/Copenhagen",
        "HEL" to "Europe/Helsinki",
        "KEF" to "Atlantic/Reykjavik",
        // Eastern Europe
        "BUD" to "Europe/Budapest",
        "OTP" to "Europe/Bucharest",
        "PRG" to "Europe/Prague",
        "WAW" to "Europe/Warsaw",
        // Southeastern Europe / Turkey
        "ATH" to "Europe/Athens",
        "IST" to "Europe/Istanbul",
        "SAW" to "Europe/Istanbul",
        "SOF" to "Europe/Sofia",

        // ── Middle East ─────────────────────────────────────────────────────
        "AUH" to "Asia/Dubai",
        "BAH" to "Asia/Bahrain",
        "DOH" to "Asia/Qatar",
        "DXB" to "Asia/Dubai",
        "JED" to "Asia/Riyadh",
        "KWI" to "Asia/Kuwait",
        "MCT" to "Asia/Muscat",
        "RUH" to "Asia/Riyadh",
        "TLV" to "Asia/Jerusalem",

        // ── Africa ──────────────────────────────────────────────────────────
        "ADD" to "Africa/Addis_Ababa",
        "CAI" to "Africa/Cairo",
        "CMN" to "Africa/Casablanca",
        "CPT" to "Africa/Johannesburg",
        "JNB" to "Africa/Johannesburg",
        "LOS" to "Africa/Lagos",
        "NBO" to "Africa/Nairobi",

        // ── South Asia ──────────────────────────────────────────────────────
        "BLR" to "Asia/Kolkata",
        "BOM" to "Asia/Kolkata",
        "CCU" to "Asia/Kolkata",
        "CMB" to "Asia/Colombo",
        "DAC" to "Asia/Dhaka",
        "DEL" to "Asia/Kolkata",
        "HYD" to "Asia/Kolkata",
        "ISB" to "Asia/Karachi",
        "KHI" to "Asia/Karachi",
        "MAA" to "Asia/Kolkata",

        // ── Southeast Asia ──────────────────────────────────────────────────
        "BKK" to "Asia/Bangkok",
        "CGK" to "Asia/Jakarta",
        "DPS" to "Asia/Makassar",
        "HAN" to "Asia/Ho_Chi_Minh",
        "KUL" to "Asia/Kuala_Lumpur",
        "MNL" to "Asia/Manila",
        "RGN" to "Asia/Yangon",
        "SGN" to "Asia/Ho_Chi_Minh",
        "SIN" to "Asia/Singapore",

        // ── East Asia ───────────────────────────────────────────────────────
        // Japan
        "CTS" to "Asia/Tokyo",
        "FUK" to "Asia/Tokyo",
        "HND" to "Asia/Tokyo",
        "ITM" to "Asia/Tokyo",
        "KIX" to "Asia/Tokyo",
        "NGO" to "Asia/Tokyo",
        "NRT" to "Asia/Tokyo",
        "OKA" to "Asia/Tokyo",
        // South Korea
        "GMP" to "Asia/Seoul",
        "ICN" to "Asia/Seoul",
        "PUS" to "Asia/Seoul",
        // China
        "CAN" to "Asia/Shanghai",
        "CKG" to "Asia/Shanghai",
        "CTU" to "Asia/Shanghai",
        "HGH" to "Asia/Shanghai",
        "KMG" to "Asia/Shanghai",
        "NKG" to "Asia/Shanghai",
        "PEK" to "Asia/Shanghai",
        "PKX" to "Asia/Shanghai",
        "PVG" to "Asia/Shanghai",
        "SHA" to "Asia/Shanghai",
        "SZX" to "Asia/Shanghai",
        "WUH" to "Asia/Shanghai",
        "XIY" to "Asia/Shanghai",
        // Hong Kong / Macau / Taiwan
        "HKG" to "Asia/Hong_Kong",
        "MFM" to "Asia/Macau",
        "TPE" to "Asia/Taipei",

        // ── Oceania ─────────────────────────────────────────────────────────
        "AKL" to "Pacific/Auckland",
        "BNE" to "Australia/Brisbane",
        "CHC" to "Pacific/Auckland",
        "MEL" to "Australia/Melbourne",
        "PER" to "Australia/Perth",
        "SYD" to "Australia/Sydney",
        "WLG" to "Pacific/Auckland",

        // ── Russia / Central Asia ───────────────────────────────────────────
        "DME" to "Europe/Moscow",
        "LED" to "Europe/Moscow",
        "SVO" to "Europe/Moscow",
        "VVO" to "Asia/Vladivostok",
        "ALA" to "Asia/Almaty",
        "NQZ" to "Asia/Almaty",
        "TAS" to "Asia/Tashkent"
    )
}
