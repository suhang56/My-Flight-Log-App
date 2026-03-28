package com.flightlog.app.data.achievements

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalAirport
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class Tier(val color: Color) {
    BRONZE(Color(0xFFCD7F32)),
    SILVER(Color(0xFFA8A9AD)),
    GOLD(Color(0xFFFFD700)),
    PLATINUM(Color(0xFFE5E4E2))
}

data class AchievementDefinition(
    val id: String,
    val name: String,
    val description: String,
    val tier: Tier,
    val icon: ImageVector
)

object AchievementDefinitions {

    val ALL: List<AchievementDefinition> = listOf(
        // Bronze
        AchievementDefinition(
            id = "first_flight",
            name = "First Takeoff",
            description = "Log your first flight",
            tier = Tier.BRONZE,
            icon = Icons.Filled.FlightTakeoff
        ),
        AchievementDefinition(
            id = "first_manual_add",
            name = "Flight Historian",
            description = "Manually add a flight",
            tier = Tier.BRONZE,
            icon = Icons.Filled.Edit
        ),
        AchievementDefinition(
            id = "ten_flights",
            name = "Frequent Flyer",
            description = "Log 10 flights",
            tier = Tier.BRONZE,
            icon = Icons.Filled.Flight
        ),
        AchievementDefinition(
            id = "five_airports",
            name = "Airport Hopper",
            description = "Visit 5 unique airports",
            tier = Tier.BRONZE,
            icon = Icons.Filled.LocalAirport
        ),
        AchievementDefinition(
            id = "five_airlines",
            name = "Multi-Carrier",
            description = "Fly 5 unique airlines",
            tier = Tier.BRONZE,
            icon = Icons.Filled.AirplanemodeActive
        ),

        // Silver
        AchievementDefinition(
            id = "fifty_flights",
            name = "Road Warrior",
            description = "Log 50 flights",
            tier = Tier.SILVER,
            icon = Icons.Filled.Route
        ),
        AchievementDefinition(
            id = "twenty_airports",
            name = "Globe Trotter",
            description = "Visit 20 unique airports",
            tier = Tier.SILVER,
            icon = Icons.Filled.Explore
        ),
        AchievementDefinition(
            id = "three_seat_classes",
            name = "Class Act",
            description = "Fly in 3 different seat classes",
            tier = Tier.SILVER,
            icon = Icons.Filled.Star
        ),
        AchievementDefinition(
            id = "distance_10k",
            name = "10K Club",
            description = "Fly 10,000 nautical miles total",
            tier = Tier.SILVER,
            icon = Icons.Filled.NearMe
        ),
        AchievementDefinition(
            id = "short_hop",
            name = "Short Hop",
            description = "Log 5 flights under 300 nm each",
            tier = Tier.SILVER,
            icon = Icons.Filled.Straighten
        ),

        // Gold
        AchievementDefinition(
            id = "century_club",
            name = "Century Club",
            description = "Log 100 flights",
            tier = Tier.GOLD,
            icon = Icons.Filled.EmojiEvents
        ),
        AchievementDefinition(
            id = "fifty_airports",
            name = "World Wanderer",
            description = "Visit 50 unique airports",
            tier = Tier.GOLD,
            icon = Icons.Filled.Public
        ),
        AchievementDefinition(
            id = "long_hauler",
            name = "Long Hauler",
            description = "Fly a single flight of 5,000+ nm",
            tier = Tier.GOLD,
            icon = Icons.Filled.Language
        ),
        AchievementDefinition(
            id = "distance_100k",
            name = "Around the World",
            description = "Fly 100,000 nautical miles total",
            tier = Tier.GOLD,
            icon = Icons.Filled.Public
        ),
        AchievementDefinition(
            id = "night_owl",
            name = "Night Owl",
            description = "3 flights departing 00:00-04:59 local",
            tier = Tier.GOLD,
            icon = Icons.Filled.Bedtime
        ),

        // Platinum
        AchievementDefinition(
            id = "five_hundred_flights",
            name = "Elite Traveler",
            description = "Log 500 flights",
            tier = Tier.PLATINUM,
            icon = Icons.Filled.WorkspacePremium
        ),
        AchievementDefinition(
            id = "ultra_long_haul",
            name = "Ultra Marathon",
            description = "Fly a single flight of 8,000+ nm",
            tier = Tier.PLATINUM,
            icon = Icons.Filled.MilitaryTech
        ),
        AchievementDefinition(
            id = "distance_500k",
            name = "Circumnavigator",
            description = "Fly 500,000 nautical miles total",
            tier = Tier.PLATINUM,
            icon = Icons.Filled.AutoAwesome
        )
    )

    private val byId: Map<String, AchievementDefinition> = ALL.associateBy { it.id }

    fun get(id: String): AchievementDefinition? = byId[id]
}
