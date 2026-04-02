package com.flightlog.app.data

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Airport lat/lon coordinates and great-circle distance calculator.
 *
 * Covers the same airports referenced in AirportNameMap and major international hubs.
 */
object AirportCoordinatesMap {

    /** Pair of (latitude, longitude) in degrees. */
    private val coords: Map<String, Pair<Double, Double>> = mapOf(
        // --- US domestic ---
        "ATL" to Pair(33.6407, -84.4277),
        "ANC" to Pair(61.1743, -149.9962),
        "AUS" to Pair(30.1975, -97.6664),
        "BNA" to Pair(36.1263, -86.6774),
        "BOS" to Pair(42.3656, -71.0096),
        "BWI" to Pair(39.1754, -76.6683),
        "CLE" to Pair(41.4117, -81.8498),
        "CLT" to Pair(35.2140, -80.9431),
        "CMH" to Pair(39.9980, -82.8919),
        "CVG" to Pair(39.0489, -84.6678),
        "DCA" to Pair(38.8512, -77.0402),
        "DEN" to Pair(39.8561, -104.6737),
        "DFW" to Pair(32.8998, -97.0403),
        "DTW" to Pair(42.2124, -83.3534),
        "EWR" to Pair(40.6895, -74.1745),
        "FLL" to Pair(26.0726, -80.1527),
        "HNL" to Pair(21.3187, -157.9224),
        "HOU" to Pair(29.6454, -95.2789),
        "IAD" to Pair(38.9445, -77.4558),
        "IAH" to Pair(29.9902, -95.3368),
        "IND" to Pair(39.7173, -86.2944),
        "JFK" to Pair(40.6413, -73.7781),
        "LAS" to Pair(36.0840, -115.1537),
        "LAX" to Pair(33.9416, -118.4085),
        "LGA" to Pair(40.7769, -73.8740),
        "MCI" to Pair(39.2976, -94.7139),
        "MCO" to Pair(28.4312, -81.3081),
        "MDW" to Pair(41.7868, -87.7522),
        "MIA" to Pair(25.7959, -80.2870),
        "MSP" to Pair(44.8848, -93.2223),
        "MSY" to Pair(29.9934, -90.2580),
        "OAK" to Pair(37.7213, -122.2208),
        "ORD" to Pair(41.9742, -87.9073),
        "PDX" to Pair(45.5898, -122.5951),
        "PHX" to Pair(33.4373, -112.0078),
        "PIT" to Pair(40.4915, -80.2329),
        "RDU" to Pair(35.8801, -78.7880),
        "RNO" to Pair(39.4991, -119.7681),
        "SAN" to Pair(32.7338, -117.1933),
        "SEA" to Pair(47.4502, -122.3088),
        "SFO" to Pair(37.6213, -122.3790),
        "SJC" to Pair(37.3639, -121.9290),
        "SLC" to Pair(40.7884, -111.9778),
        "STL" to Pair(38.7487, -90.3700),
        "TPA" to Pair(27.9756, -82.5333),

        // --- Major international hubs ---
        "LHR" to Pair(51.4700, -0.4543),
        "CDG" to Pair(49.0097, 2.5479),
        "FRA" to Pair(50.0379, 8.5622),
        "AMS" to Pair(52.3105, 4.7683),
        "MAD" to Pair(40.4983, -3.5676),
        "FCO" to Pair(41.8003, 12.2389),
        "IST" to Pair(41.2753, 28.7519),
        "DXB" to Pair(25.2532, 55.3657),
        "DOH" to Pair(25.2731, 51.6081),
        "SIN" to Pair(1.3644, 103.9915),
        "HKG" to Pair(22.3080, 113.9185),
        "NRT" to Pair(35.7647, 140.3864),
        "HND" to Pair(35.5494, 139.7798),
        "KIX" to Pair(34.4347, 135.2440),
        "ICN" to Pair(37.4602, 126.4407),
        "PEK" to Pair(40.0799, 116.6031),
        "PVG" to Pair(31.1443, 121.8083),
        "TPE" to Pair(25.0797, 121.2342),
        "BKK" to Pair(13.6900, 100.7501),
        "SYD" to Pair(-33.9461, 151.1772),
        "MEL" to Pair(-37.6733, 144.8433),
        "AKL" to Pair(-37.0082, 174.7850),
        "YYZ" to Pair(43.6777, -79.6248),
        "YVR" to Pair(49.1967, -123.1815),
        "MEX" to Pair(19.4363, -99.0721),
        "GRU" to Pair(-23.4356, -46.4731),
        "EZE" to Pair(-34.8222, -58.5358),
        "BOG" to Pair(4.7016, -74.1469),
        "LIM" to Pair(-12.0219, -77.1143),
        "SCL" to Pair(-33.3930, -70.7858),
        "JNB" to Pair(-26.1392, 28.2460),
        "CAI" to Pair(30.1219, 31.4056),
        "ADD" to Pair(8.9779, 38.7993),
        "DEL" to Pair(28.5562, 77.1000),
        "BOM" to Pair(19.0896, 72.8656),
        "KUL" to Pair(2.7456, 101.7099),
        "MNL" to Pair(14.5086, 121.0198),
        "CGK" to Pair(-6.1256, 106.6559),
        "CTS" to Pair(42.7752, 141.6925),
        "FUK" to Pair(33.5859, 130.4513),
        "NGO" to Pair(34.8584, 136.8049),
        "OKA" to Pair(26.1958, 127.6459),

        // --- Additional popular airports ---
        "SNA" to Pair(33.6757, -117.8681),
        "BUR" to Pair(34.2005, -118.3586),
        "DAL" to Pair(32.8471, -96.8518),
        "SAT" to Pair(29.5337, -98.4698),
        "RIC" to Pair(37.5052, -77.3197),
        "JAX" to Pair(30.4941, -81.6879),
        "ABQ" to Pair(35.0402, -106.6091),
        "OMA" to Pair(41.3032, -95.8941),
        "MKE" to Pair(42.9472, -87.8966),
        "BUF" to Pair(42.9405, -78.7322),
        "PBI" to Pair(26.6832, -80.0956),
        "RSW" to Pair(26.5362, -81.7552),
        "SMF" to Pair(38.6954, -121.5908),
        "MEM" to Pair(35.0424, -89.9767),
        "OKC" to Pair(35.3931, -97.6007),
        "TUL" to Pair(36.1984, -95.8881),
        "ONT" to Pair(34.0560, -117.6012),
        "SDF" to Pair(38.1741, -85.7360),
        "DSM" to Pair(41.5341, -93.6631),
        "LIT" to Pair(34.7294, -92.2243),
        "BHM" to Pair(33.5629, -86.7535),
        "ORF" to Pair(36.8946, -76.2012),
        "CHS" to Pair(32.8986, -80.0405),
        "SAV" to Pair(32.1276, -81.2021),
        "PHL" to Pair(39.8721, -75.2411),

        // --- Europe ---
        "MUC" to Pair(48.3538, 11.7861),
        "ZRH" to Pair(47.4647, 8.5492),
        "BCN" to Pair(41.2971, 2.0785),
        "LIS" to Pair(38.7742, -9.1342),
        "OSL" to Pair(60.1976, 11.1004),
        "CPH" to Pair(55.6180, 12.6508),
        "ARN" to Pair(59.6498, 17.9238),
        "HEL" to Pair(60.3172, 24.9633),
        "VIE" to Pair(48.1103, 16.5697),
        "DUB" to Pair(53.4264, -6.2499),
        "EDI" to Pair(55.9508, -3.3615),
        "MAN" to Pair(53.3537, -2.2750),
        "BRU" to Pair(50.9014, 4.4844),
        "WAW" to Pair(52.1657, 20.9671),
        "PRG" to Pair(50.1008, 14.2600),
        "ATH" to Pair(37.9364, 23.9445),
        "NCE" to Pair(43.6584, 7.2159),
        "GVA" to Pair(46.2381, 6.1089)
    )

    /** Returns (latitude, longitude) for an IATA code, or null if unknown. */
    fun getCoords(iataCode: String): Pair<Double, Double>? =
        coords[iataCode.uppercase()]

    /**
     * Generates intermediate points along the great-circle arc between two coordinates.
     * Uses spherical linear interpolation (slerp).
     *
     * @param dep (latitude, longitude) in degrees
     * @param arr (latitude, longitude) in degrees
     * @param numPoints number of intermediate points (excluding endpoints). Default 20.
     * @return List of (lat, lon) pairs including start and end. Size = numPoints + 2.
     */
    fun interpolateArc(
        dep: Pair<Double, Double>,
        arr: Pair<Double, Double>,
        numPoints: Int = 20
    ): List<Pair<Double, Double>> {
        val lat1 = dep.first.toRadians()
        val lon1 = dep.second.toRadians()
        val lat2 = arr.first.toRadians()
        val lon2 = arr.second.toRadians()

        val d = 2 * asin(
            sqrt(
                sin((lat2 - lat1) / 2).pow(2) +
                    cos(lat1) * cos(lat2) * sin((lon2 - lon1) / 2).pow(2)
            )
        )

        if (d < 1e-10) return listOf(dep, arr)

        val points = mutableListOf<Pair<Double, Double>>()
        val totalSteps = numPoints + 1
        for (i in 0..totalSteps) {
            val f = i.toDouble() / totalSteps
            val a = sin((1 - f) * d) / sin(d)
            val b = sin(f * d) / sin(d)
            val x = a * cos(lat1) * cos(lon1) + b * cos(lat2) * cos(lon2)
            val y = a * cos(lat1) * sin(lon1) + b * cos(lat2) * sin(lon2)
            val z = a * sin(lat1) + b * sin(lat2)
            val lat = atan2(z, sqrt(x * x + y * y))
            val lon = atan2(y, x)
            points.add(Pair(lat * 180.0 / PI, lon * 180.0 / PI))
        }
        return points
    }

    /** Returns great-circle distance in km between two airports, or null if either code is unknown. */
    fun greatCircleKm(depCode: String, arrCode: String): Int? {
        val (lat1, lon1) = coords[depCode.uppercase()] ?: return null
        val (lat2, lon2) = coords[arrCode.uppercase()] ?: return null
        return haversineKm(lat1, lon1, lat2, lon2)
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val r = 6_371.0 // Earth radius in km
        val dLat = (lat2 - lat1).toRadians()
        val dLon = (lon2 - lon1).toRadians()
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1.toRadians()) * cos(lat2.toRadians()) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * asin(sqrt(a))
        return (r * c).roundToInt()
    }

    private fun Double.toRadians(): Double = this * PI / 180.0
}
