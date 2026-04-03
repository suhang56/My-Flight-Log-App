package com.flightlog.app.data.network

/**
 * Provides photo URLs for aircraft by ICAO type code.
 *
 * Since the Planespotters API only supports registration-based lookup and we only have
 * ICAO type codes (e.g., "B738", "A320"), this provider maps type codes to curated
 * Wikimedia Commons photo URLs covering the most common commercial aircraft.
 *
 * Type codes that share a family (e.g., B738/B73H → Boeing 737-800) are normalized
 * via [FAMILY_ALIASES] before lookup.
 */
object AircraftTypePhotoProvider {

    data class AircraftPhotoInfo(
        val photoUrl: String,
        val photographer: String,
        val aircraftName: String
    )

    /**
     * Returns photo info for the given ICAO type code, or null if unknown.
     * Lookup is case-insensitive and tries family alias normalization.
     */
    fun getPhotoForType(icaoTypeCode: String?): AircraftPhotoInfo? {
        if (icaoTypeCode.isNullOrBlank()) return null
        val normalized = icaoTypeCode.trim().uppercase()
        val key = FAMILY_ALIASES[normalized] ?: normalized
        return AIRCRAFT_PHOTOS[key]
    }

    /**
     * Maps variant type codes to their canonical family code.
     * E.g., B73H (737-800 with winglets) → B738, A20N (A320neo) → A320.
     */
    private val FAMILY_ALIASES: Map<String, String> = mapOf(
        // Boeing 737 family
        "B73H" to "B738",
        "B737" to "B738",
        "B739" to "B738",
        "B38M" to "B38M",  // 737 MAX 8 has its own entry
        "B39M" to "B38M",  // 737 MAX 9 → MAX 8 photo
        "B3XM" to "B38M",  // 737 MAX 10 → MAX 8 photo
        // Boeing 747 family
        "B74S" to "B744",
        "B748" to "B744",
        // Boeing 757 family
        "B753" to "B752",
        // Boeing 767 family
        "B763" to "B762",
        "B764" to "B762",
        // Boeing 777 family
        "B772" to "B77W",
        "B773" to "B77W",
        "B778" to "B77W",
        "B779" to "B77W",
        // Boeing 787 family
        "B78X" to "B789",
        "B788" to "B789",
        // Airbus A319
        "A319" to "A319",
        // Airbus A320 family
        "A20N" to "A320",
        // Airbus A321 family
        "A321" to "A321",
        "A21N" to "A321",
        // Airbus A330 family
        "A332" to "A333",
        "A338" to "A333",
        "A339" to "A333",
        // Airbus A340 family
        "A342" to "A343",
        "A345" to "A343",
        "A346" to "A343",
        // Airbus A350 family
        "A359" to "A35K",
        // Airbus A380
        "A38F" to "A388",
        // Embraer family
        "E170" to "E190",
        "E75L" to "E190",
        "E75S" to "E190",
        // CRJ family
        "CRJ2" to "CRJ9",
        "CRJ7" to "CRJ9",
        "CRJX" to "CRJ9",
        // ATR family
        "AT72" to "AT76",
        "AT75" to "AT76",
        // Dash 8
        "DH8A" to "DH8D",
        "DH8B" to "DH8D",
        "DH8C" to "DH8D"
    )

    /**
     * Curated aircraft photos from Wikimedia Commons (freely licensed).
     * Each entry maps a canonical ICAO type code to a photo URL, photographer credit,
     * and human-readable aircraft name.
     */
    private val AIRCRAFT_PHOTOS: Map<String, AircraftPhotoInfo> = mapOf(
        "B738" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6e/Ryanair_B737-8AS_EI-DWO.jpg/1280px-Ryanair_B737-8AS_EI-DWO.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "Boeing 737-800"
        ),
        "B38M" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f9/Southwest_Airlines_Boeing_737_MAX_8_N8713M_approaching_Washington_Reagan_Airport.jpg/1280px-Southwest_Airlines_Boeing_737_MAX_8_N8713M_approaching_Washington_Reagan_Airport.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "Boeing 737 MAX 8"
        ),
        "B744" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a0/British_Airways_Boeing_747-400_G-BNLY.jpg/1280px-British_Airways_Boeing_747-400_G-BNLY.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "Boeing 747-400"
        ),
        "B752" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5e/Delta_Air_Lines_Boeing_757-232_N665DN.jpg/1280px-Delta_Air_Lines_Boeing_757-232_N665DN.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "Boeing 757-200"
        ),
        "B762" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c1/Delta_Air_Lines_Boeing_767-332_N130DL.jpg/1280px-Delta_Air_Lines_Boeing_767-332_N130DL.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "Boeing 767"
        ),
        "B77W" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b5/Emirates_Boeing_777-300ER_A6-EGZ_at_Schiphol_%28cropped%29.jpg/1280px-Emirates_Boeing_777-300ER_A6-EGZ_at_Schiphol_%28cropped%29.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "Boeing 777-300ER"
        ),
        "B789" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7f/All_Nippon_Airways_Boeing_787-9_JA882A_approaching_Runway_16R_at_Narita_Airport.jpg/1280px-All_Nippon_Airways_Boeing_787-9_JA882A_approaching_Runway_16R_at_Narita_Airport.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "Boeing 787-9 Dreamliner"
        ),
        "A319" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9b/Airbus_A319-112%2C_American_Airlines_AN2334797.jpg/1280px-Airbus_A319-112%2C_American_Airlines_AN2334797.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "Airbus A319"
        ),
        "A320" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/0/09/A320-214_-_EasyJet_-_G-EZWB_-_LEMD.jpg/1280px-A320-214_-_EasyJet_-_G-EZWB_-_LEMD.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "Airbus A320"
        ),
        "A321" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fb/Airbus_A321-231%2C_Air_France_AN1990552.jpg/1280px-Airbus_A321-231%2C_Air_France_AN1990552.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "Airbus A321"
        ),
        "A333" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/99/Airbus_A330-302%2C_Cathay_Pacific_Airways_JP7309806.jpg/1280px-Airbus_A330-302%2C_Cathay_Pacific_Airways_JP7309806.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "Airbus A330-300"
        ),
        "A343" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/ce/Lufthansa_A340-313X_D-AIGP.jpg/1280px-Lufthansa_A340-313X_D-AIGP.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "Airbus A340-300"
        ),
        "A35K" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/bf/Singapore_Airlines_Airbus_A350-941_%289V-SMF%29.jpg/1280px-Singapore_Airlines_Airbus_A350-941_%289V-SMF%29.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "Airbus A350"
        ),
        "A388" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/0/09/A6-EDY_A380_Emirates_31_jan_2013_jfk_%288442269364%29_%28cropped%29.jpg/1280px-A6-EDY_A380_Emirates_31_jan_2013_jfk_%288442269364%29_%28cropped%29.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "Airbus A380-800"
        ),
        "E190" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3f/JetBlue_Airways_Embraer_ERJ-190_N192JB.jpg/1280px-JetBlue_Airways_Embraer_ERJ-190_N192JB.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "Embraer E190"
        ),
        "E195" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1b/Azul_Brazilian_Airlines_Embraer_195_PR-AXH.jpg/1280px-Azul_Brazilian_Airlines_Embraer_195_PR-AXH.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "Embraer E195"
        ),
        "CRJ9" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7e/Bombardier_CRJ-900_Lufthansa_CityLine_D-ACNR.jpg/1280px-Bombardier_CRJ-900_Lufthansa_CityLine_D-ACNR.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "Bombardier CRJ-900"
        ),
        "AT76" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/93/ATR_72-600_HOP%21_%28F-HOPX%29.jpg/1280px-ATR_72-600_HOP%21_%28F-HOPX%29.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "ATR 72-600"
        ),
        "DH8D" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b0/Bombardier_Dash_8_Q400_%28All_Nippon_Airways%29.jpg/1280px-Bombardier_Dash_8_Q400_%28All_Nippon_Airways%29.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "De Havilland Dash 8-400"
        ),
        "C919" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e7/B-919A_at_PKX_%2820220724162907%29.jpg/1280px-B-919A_at_PKX_%2820220724162907%29.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "COMAC C919"
        ),
        "ARJ2" to AircraftPhotoInfo(
            photoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2d/Chengdu_Airlines_ARJ21-700_at_CTU_%28cropped%29.jpg/1280px-Chengdu_Airlines_ARJ21-700_at_CTU_%28cropped%29.jpg",
            photographer = "Wikimedia Commons",
            aircraftName = "COMAC ARJ21"
        )
    )
}
