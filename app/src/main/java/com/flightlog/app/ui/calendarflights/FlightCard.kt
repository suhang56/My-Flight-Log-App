package com.flightlog.app.ui.calendarflights

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.util.toRelativeTimeLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Badge colors (exact spec values with light/dark variants) ──────────────────

internal object BadgeColors {
    // Light mode
    val todayBgLight = Color(0xFF1565C0)
    val todayTextLight = Color.White
    val upcomingBgLight = Color(0xFF2E7D32)
    val upcomingTextLight = Color.White
    val pastBgLight = Color(0xFFE0E0E0)
    val pastTextLight = Color(0xFF616161)

    // Dark mode
    val todayBgDark = Color(0xFF42A5F5)
    val todayTextDark = Color(0xFF0D1B2A)
    val upcomingBgDark = Color(0xFF66BB6A)
    val upcomingTextDark = Color(0xFF0D1B2A)
    val pastBgDark = Color(0xFF424242)
    val pastTextDark = Color(0xFFBDBDBD)
}

// ── Flight card ────────────────────────────────────────────────────────────────

@Composable
internal fun FlightCard(
    flight: CalendarFlight,
    onClick: () -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val isUpcoming = flight.scheduledTime >= now
    val relativeLabel = flight.scheduledTime.toRelativeTimeLabel(now)
    val isToday = relativeLabel == "Today"

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault()) }

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (flight.flightNumber.isNotBlank()) {
                    Text(
                        text = flight.flightNumber,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                val routeText = if (flight.departureCode.isBlank() && flight.arrivalCode.isBlank()) {
                    "Route pending"
                } else {
                    "${flight.departureCode}  \u2192  ${flight.arrivalCode}"
                }
                Text(
                    text = routeText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = if (flight.departureCode.isBlank() && flight.arrivalCode.isBlank())
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        Color.Unspecified
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = dateFormat.format(Date(flight.scheduledTime)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            RelativeTimeBadge(
                label = relativeLabel,
                isUpcoming = isUpcoming,
                isToday = isToday
            )
        }
    }
}

// ── Relative time badge with exact spec colors (light + dark) ──────────────────

@Composable
internal fun RelativeTimeBadge(
    label: String,
    isUpcoming: Boolean,
    isToday: Boolean
) {
    val isDark = isSystemInDarkTheme()

    val containerColor = when {
        isToday -> if (isDark) BadgeColors.todayBgDark else BadgeColors.todayBgLight
        isUpcoming -> if (isDark) BadgeColors.upcomingBgDark else BadgeColors.upcomingBgLight
        else -> if (isDark) BadgeColors.pastBgDark else BadgeColors.pastBgLight
    }
    val contentColor = when {
        isToday -> if (isDark) BadgeColors.todayTextDark else BadgeColors.todayTextLight
        isUpcoming -> if (isDark) BadgeColors.upcomingTextDark else BadgeColors.upcomingTextLight
        else -> if (isDark) BadgeColors.pastTextDark else BadgeColors.pastTextLight
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
