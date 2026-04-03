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
     *
     * URLs use the Special:FilePath redirect format for stability:
     * https://commons.wikimedia.org/wiki/Special:FilePath/Filename.jpg?width=1280
     * This avoids breakage from thumbnail path changes on Wikimedia servers.
     */
    private val AIRCRAFT_PHOTOS: Map<String, AircraftPhotoInfo> = mapOf(
        "B738" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Gear_Actuation-Boeing-737-800_EL-AL_approaching_VIE-DSC_3259w.jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "Boeing 737-800"
        ),
        "B38M" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Berlin_Brandenburg_Airport_Icelandair_Boeing_737-8_MAX_TF-ICY_(DSC07832).jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "Boeing 737 MAX 8"
        ),
        "B744" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Thai_Airways_International_Boeing_747-4D7_HS-TGP_MUC_2015_03.jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "Boeing 747-400"
        ),
        "B752" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Delta_Boeing_757-200_N699DL_BWI_MD1.jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "Boeing 757-200"
        ),
        "B762" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Ba_b767-300_g-bnwa_planform_arp.jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "Boeing 767"
        ),
        "B77W" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Qatar_Boeing_777-300ER_A7-BEP_IAD_VA1.jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "Boeing 777-300ER"
        ),
        "B789" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Etihad_Boeing_787-9_A6-BNI_IAD_VA2.jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "Boeing 787-9 Dreamliner"
        ),
        "A319" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Finnair_Airbus_A319-112_OH-LVL_snowfall.jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "Airbus A319"
        ),
        "A320" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Lufthansa_Airbus_A320-211_D-AIQT_01.jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "Airbus A320"
        ),
        "A321" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/American_Airbus_A321_N913US_BWI_MD1.jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "Airbus A321"
        ),
        "A333" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/China_Eastern_Airlines_A330-300_B-6097_SVO_2011-6-17.png?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "Airbus A330-300"
        ),
        "A343" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/South_African_Airways_Airbus_A340-313_ZS-SXE_MUC_2015_02.jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "Airbus A340-300"
        ),
        "A35K" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Airbus_A350-941_F-WWCF_MSN002_ILA_Berlin_2016_17.jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "Airbus A350"
        ),
        "A388" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Emirates_Airbus_A380-861_A6-EER_MUC_2015_01.jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "Airbus A380-800"
        ),
        "E190" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Hannover_Airport_Helvetic_Airways_Embraer_E190-E2_HB-AZE_(DSC09329).jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "Embraer E190"
        ),
        "E195" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Embraer_E195-E2,_Air_Show_2019,_Le_Bourget_(SIAE0894).jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "Embraer E195"
        ),
        "CRJ9" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Bombardier_CRJ-900_by_Lufthansa_CityLine.jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "Bombardier CRJ-900"
        ),
        "AT76" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Cebgo_(Cebu_Pacific)_ATR_72-600.jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "ATR 72-600"
        ),
        "DH8D" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Austrian_Airlines_Bombardier_Dash_8_Q400_(flight_683)_boarding_at_Vienna_Airport,_Austria_(DSC_0003).jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "De Havilland Dash 8-400"
        ),
        "C919" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/COMAC_B-001A_May_2017.jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "COMAC C919"
        ),
        "ARJ2" to AircraftPhotoInfo(
            photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Comac_ARJ21-700.jpg?width=1280",
            photographer = "Wikimedia Commons",
            aircraftName = "COMAC ARJ21"
        )
    )
}
