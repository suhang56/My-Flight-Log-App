package com.flightlog.app.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Static lookup of IATA airport codes to latitude/longitude coordinates.
 * Used to compute great-circle distance between departure and arrival airports.
 *
 * Covers ~200 of the world's busiest airports by passenger volume.
 */
object AirportCoordinatesMap {

    data class LatLng(val lat: Double, val lng: Double)

    /** Returns coordinates for the given IATA code, or null if unknown. */
    fun coordinatesFor(iataCode: String): LatLng? = MAP[iataCode.uppercase()]

    /**
     * Computes the great-circle distance in nautical miles between two airports.
     * Returns null if either airport code is unknown.
     */
    fun distanceNm(departureIata: String, arrivalIata: String): Int? {
        val from = coordinatesFor(departureIata) ?: return null
        val to = coordinatesFor(arrivalIata) ?: return null
        return haversineNm(from.lat, from.lng, to.lat, to.lng)
    }

    /**
     * Haversine formula returning distance in nautical miles.
     * Earth radius = 3440.065 NM.
     */
    private fun haversineNm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
        val earthRadiusNm = 3440.065
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * asin(sqrt(a))
        return (earthRadiusNm * c).toInt()
    }

    @Suppress("MagicNumber")
    private val MAP: Map<String, LatLng> = mapOf(
        // ── North America ───────────────────────────────────────────────────
        // United States — Eastern
        "ATL" to LatLng(33.6407, -84.4277),
        "BOS" to LatLng(42.3656, -71.0096),
        "BWI" to LatLng(39.1754, -76.6684),
        "CLT" to LatLng(35.2140, -80.9431),
        "CMH" to LatLng(39.9980, -82.8919),
        "DTW" to LatLng(42.2124, -83.3534),
        "EWR" to LatLng(40.6895, -74.1745),
        "FLL" to LatLng(26.0726, -80.1527),
        "IAD" to LatLng(38.9445, -77.4558),
        "JFK" to LatLng(40.6413, -73.7781),
        "LGA" to LatLng(40.7769, -73.8740),
        "MCO" to LatLng(28.4312, -81.3081),
        "MIA" to LatLng(25.7959, -80.2870),
        "PBI" to LatLng(26.6832, -80.0956),
        "PHL" to LatLng(39.8721, -75.2411),
        "PIT" to LatLng(40.4915, -80.2329),
        "RDU" to LatLng(35.8776, -78.7875),
        "TPA" to LatLng(27.9755, -82.5332),
        "DCA" to LatLng(38.8512, -77.0402),

        // United States — Central
        "AUS" to LatLng(30.1975, -97.6664),
        "BNA" to LatLng(36.1263, -86.6774),
        "DFW" to LatLng(32.8998, -97.0403),
        "HOU" to LatLng(29.6454, -95.2789),
        "IAH" to LatLng(29.9902, -95.3368),
        "IND" to LatLng(39.7173, -86.2944),
        "MCI" to LatLng(39.2976, -94.7139),
        "MDW" to LatLng(41.7868, -87.7522),
        "MKE" to LatLng(42.9472, -87.8966),
        "MSP" to LatLng(44.8820, -93.2218),
        "MSY" to LatLng(29.9934, -90.2580),
        "ORD" to LatLng(41.9742, -87.9073),
        "SAT" to LatLng(29.5337, -98.4698),
        "STL" to LatLng(38.7487, -90.3700),

        // United States — Mountain
        "ABQ" to LatLng(35.0402, -106.6090),
        "DEN" to LatLng(39.8561, -104.6737),
        "PHX" to LatLng(33.4373, -112.0078),
        "SLC" to LatLng(40.7884, -111.9778),

        // United States — Pacific
        "LAX" to LatLng(33.9416, -118.4085),
        "LAS" to LatLng(36.0840, -115.1537),
        "OAK" to LatLng(37.7213, -122.2208),
        "PDX" to LatLng(45.5898, -122.5951),
        "SAN" to LatLng(32.7338, -117.1933),
        "SEA" to LatLng(47.4502, -122.3088),
        "SFO" to LatLng(37.6213, -122.3790),
        "SJC" to LatLng(37.3626, -121.9290),
        "SMF" to LatLng(38.6954, -121.5908),

        // United States — Hawaii / Alaska
        "HNL" to LatLng(21.3187, -157.9225),
        "ANC" to LatLng(61.1743, -149.9962),

        // Canada
        "YYZ" to LatLng(43.6777, -79.6248),
        "YVR" to LatLng(49.1967, -123.1815),
        "YUL" to LatLng(45.4707, -73.7408),
        "YOW" to LatLng(45.3225, -75.6692),
        "YYC" to LatLng(51.1215, -114.0076),
        "YEG" to LatLng(53.3097, -113.5800),

        // Mexico
        "MEX" to LatLng(19.4363, -99.0721),
        "CUN" to LatLng(21.0365, -86.8771),
        "GDL" to LatLng(20.5218, -103.3111),

        // ── Europe ──────────────────────────────────────────────────────────
        "LHR" to LatLng(51.4700, -0.4543),
        "LGW" to LatLng(51.1537, -0.1821),
        "STN" to LatLng(51.8860, 0.2389),
        "CDG" to LatLng(49.0097, 2.5479),
        "ORY" to LatLng(48.7233, 2.3794),
        "AMS" to LatLng(52.3105, 4.7683),
        "FRA" to LatLng(50.0379, 8.5622),
        "MUC" to LatLng(48.3538, 11.7861),
        "BER" to LatLng(52.3667, 13.5033),
        "MAD" to LatLng(40.4983, -3.5676),
        "BCN" to LatLng(41.2974, 2.0833),
        "FCO" to LatLng(41.8003, 12.2389),
        "MXP" to LatLng(45.6306, 8.7281),
        "ZRH" to LatLng(47.4647, 8.5492),
        "VIE" to LatLng(48.1103, 16.5697),
        "CPH" to LatLng(55.6180, 12.6508),
        "OSL" to LatLng(60.1939, 11.1004),
        "ARN" to LatLng(59.6519, 17.9186),
        "HEL" to LatLng(60.3172, 24.9633),
        "DUB" to LatLng(53.4264, -6.2499),
        "LIS" to LatLng(38.7742, -9.1342),
        "ATH" to LatLng(37.9364, 23.9445),
        "IST" to LatLng(41.2753, 28.7519),
        "WAW" to LatLng(52.1657, 20.9671),
        "PRG" to LatLng(50.1008, 14.2600),
        "BUD" to LatLng(47.4369, 19.2556),
        "OTP" to LatLng(44.5711, 26.0850),
        "SVO" to LatLng(55.9726, 37.4146),
        "LED" to LatLng(59.8003, 30.2625),
        "EDI" to LatLng(55.9500, -3.3725),
        "MAN" to LatLng(53.3537, -2.2750),
        "BRU" to LatLng(50.9010, 4.4844),

        // ── Asia-Pacific ────────────────────────────────────────────────────
        "NRT" to LatLng(35.7647, 140.3864),
        "HND" to LatLng(35.5494, 139.7798),
        "KIX" to LatLng(34.4347, 135.2441),
        "ICN" to LatLng(37.4602, 126.4407),
        "GMP" to LatLng(37.5583, 126.7906),
        "PEK" to LatLng(40.0799, 116.6031),
        "PVG" to LatLng(31.1443, 121.8083),
        "CAN" to LatLng(23.3924, 113.2988),
        "HKG" to LatLng(22.3080, 113.9185),
        "TPE" to LatLng(25.0797, 121.2342),
        "SIN" to LatLng(1.3644, 103.9915),
        "BKK" to LatLng(13.6900, 100.7501),
        "KUL" to LatLng(2.7456, 101.7099),
        "CGK" to LatLng(-6.1256, 106.6559),
        "MNL" to LatLng(14.5086, 121.0198),
        "DEL" to LatLng(28.5562, 77.1000),
        "BOM" to LatLng(19.0896, 72.8656),
        "BLR" to LatLng(13.1986, 77.7066),
        "MAA" to LatLng(12.9941, 80.1709),
        "CCU" to LatLng(22.6547, 88.4467),

        // ── Middle East ─────────────────────────────────────────────────────
        "DXB" to LatLng(25.2532, 55.3657),
        "AUH" to LatLng(24.4330, 54.6511),
        "DOH" to LatLng(25.2731, 51.6081),
        "JED" to LatLng(21.6796, 39.1565),
        "RUH" to LatLng(24.9576, 46.6988),
        "TLV" to LatLng(32.0055, 34.8854),

        // ── Oceania ─────────────────────────────────────────────────────────
        "SYD" to LatLng(-33.9461, 151.1772),
        "MEL" to LatLng(-37.6690, 144.8410),
        "BNE" to LatLng(-27.3842, 153.1175),
        "AKL" to LatLng(-37.0082, 174.7850),

        // ── Africa ──────────────────────────────────────────────────────────
        "JNB" to LatLng(-26.1367, 28.2411),
        "CPT" to LatLng(-33.9715, 18.6021),
        "CAI" to LatLng(30.1219, 31.4056),
        "ADD" to LatLng(8.9779, 38.7993),
        "NBO" to LatLng(-1.3192, 36.9278),
        "LOS" to LatLng(6.5774, 3.3213),
        "CMN" to LatLng(33.3675, -7.5898),

        // ── South America ───────────────────────────────────────────────────
        "GRU" to LatLng(-23.4356, -46.4731),
        "GIG" to LatLng(-22.8100, -43.2505),
        "EZE" to LatLng(-34.8222, -58.5358),
        "AEP" to LatLng(-34.5592, -58.4156),
        "SCL" to LatLng(-33.3930, -70.7858),
        "BOG" to LatLng(4.7016, -74.1469),
        "LIM" to LatLng(-12.0219, -77.1143),
        "PTY" to LatLng(9.0714, -79.3835)
    )
}
