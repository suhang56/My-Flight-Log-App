package com.flightlog.app.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.local.model.LabelCount
import com.flightlog.app.data.local.model.MonthlyCount
import com.flightlog.app.data.local.model.StatsData
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Statistics") })
        }
    ) { padding ->
        if (stats.isEmpty) {
            StatisticsEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(padding)
            ) {
                item(key = "hero") {
                    HeroStatsRow(stats)
                }
                if (stats.monthlyFlightCounts.isNotEmpty()) {
                    item(key = "chart") {
                        MonthlyBarChart(stats.monthlyFlightCounts)
                    }
                }
                if (stats.topAirports.size >= 2) {
                    item(key = "airports") {
                        TopListSection(
                            title = "Top Airports",
                            icon = Icons.Default.LocationOn,
                            items = stats.topAirports.take(5).map { it.code to it.count }
                        )
                    }
                }
                if (stats.topAirlines.size >= 2) {
                    item(key = "airlines") {
                        TopListSection(
                            title = "Top Airlines",
                            icon = Icons.Default.AirplanemodeActive,
                            items = stats.topAirlines.take(5).map { it.code to it.count }
                        )
                    }
                }
                if (stats.longestFlight?.distanceNm != null) {
                    item(key = "longest") {
                        LongestFlightCard(stats.longestFlight!!)
                    }
                }
                if (stats.seatClassBreakdown.isNotEmpty()) {
                    item(key = "seats") {
                        SeatClassBreakdown(stats.seatClassBreakdown)
                    }
                }
                if (stats.aircraftTypeDistribution.size >= 2) {
                    item(key = "aircraft") {
                        TopListSection(
                            title = "Aircraft Types",
                            icon = Icons.Default.Flight,
                            items = stats.aircraftTypeDistribution.take(5).map { it.label to it.count }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

// ── Hero stats row ──────────────────────────────────────────────────────────────

@Composable
private fun HeroStatsRow(stats: StatsData) {
    val hours = stats.totalDurationMinutes / 60
    val minutes = stats.totalDurationMinutes % 60
    val timeLabel = when {
        stats.totalDurationMinutes == 0L -> "\u2014"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        HeroStatItem(
            icon = Icons.Default.FlightTakeoff,
            value = "${stats.flightCount}",
            label = if (stats.flightCount == 1) "Flight" else "Flights"
        )
        HeroStatItem(
            icon = Icons.Default.Route,
            value = if (stats.totalDistanceNm > 0) "%,d".format(stats.totalDistanceNm) else "\u2014",
            label = "NM"
        )
        HeroStatItem(
            icon = Icons.Default.Schedule,
            value = timeLabel,
            label = "In the Air"
        )
        HeroStatItem(
            icon = Icons.Default.LocationOn,
            value = if (stats.uniqueAirportCount > 0) "${stats.uniqueAirportCount}" else "\u2014",
            label = if (stats.uniqueAirportCount == 1) "Airport" else "Airports"
        )
    }
}

@Composable
private fun HeroStatItem(
    icon: ImageVector,
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Monthly bar chart ───────────────────────────────────────────────────────────

private val MONTH_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())

@Composable
private fun MonthlyBarChart(data: List<MonthlyCount>) {
    // Zero-fill the last 12 months
    val filledData = remember(data) {
        val now = YearMonth.now()
        val dataMap = data.associate { it.yearMonth to it.count }
        (11 downTo 0).map { offset ->
            val ym = now.minusMonths(offset.toLong())
            val key = ym.toString() // "YYYY-MM"
            MonthlyCount(key, dataMap[key] ?: 0)
        }
    }

    val currentYearMonth = remember { YearMonth.now().toString() }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Flights per Month",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            val maxCount = filledData.maxOf { it.count }.coerceAtLeast(1)
            val barColor = MaterialTheme.colorScheme.primaryContainer
            val highlightColor = MaterialTheme.colorScheme.primary

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val barCount = filledData.size
                val barSpacing = 4.dp.toPx()
                val totalSpacing = barSpacing * (barCount - 1)
                val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(4f)
                val chartHeight = size.height

                filledData.forEachIndexed { index, item ->
                    val barHeight = if (item.count > 0)
                        (item.count.toFloat() / maxCount) * chartHeight
                    else
                        0f
                    val x = index * (barWidth + barSpacing)
                    val isCurrentMonth = item.yearMonth == currentYearMonth
                    val color = if (isCurrentMonth) highlightColor else barColor

                    if (barHeight > 0) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x, chartHeight - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(2.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Month labels — show every 3rd month to avoid crowding
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                filledData.forEachIndexed { index, item ->
                    if (index % 3 == 0 || index == filledData.lastIndex) {
                        val ym = runCatching { YearMonth.parse(item.yearMonth) }.getOrNull()
                        val label = ym?.format(MONTH_LABEL_FORMATTER) ?: item.yearMonth.takeLast(2)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                }
            }
        }
    }
}

// ── Top list section ────────────────────────────────────────────────────────────

@Composable
private fun TopListSection(
    title: String,
    icon: ImageVector,
    items: List<Pair<String, Int>>
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            val maxCount = items.maxOfOrNull { it.second } ?: 1
            val barColor = MaterialTheme.colorScheme.primaryContainer

            items.forEach { (label, count) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(48.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        val fraction = count.toFloat() / maxCount
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(20.dp)
                        ) {
                            drawRoundRect(
                                color = barColor,
                                size = size,
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

// ── Longest flight card ─────────────────────────────────────────────────────────

@Composable
private fun LongestFlightCard(flight: LogbookFlight) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Longest Flight",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = flight.departureCode,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.Flight,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(90f),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = flight.arrivalCode,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            flight.distanceNm?.let { nm ->
                Text(
                    text = "%,d NM".format(nm),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (flight.flightNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = flight.flightNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Seat class breakdown ────────────────────────────────────────────────────────

@Composable
private fun SeatClassBreakdown(data: List<LabelCount>) {
    val total = data.sumOf { it.count }.coerceAtLeast(1)
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Seat Class",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Stacked horizontal bar
            val barColors = data.mapIndexed { index, _ -> colors[index % colors.size] }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                var xOffset = 0f
                data.forEachIndexed { index, item ->
                    val segmentWidth = (item.count.toFloat() / total) * size.width
                    drawRect(
                        color = barColors[index],
                        topLeft = Offset(xOffset, 0f),
                        size = Size(segmentWidth, size.height)
                    )
                    xOffset += segmentWidth
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            data.forEachIndexed { index, item ->
                val pct = (item.count * 100) / total
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawRect(color = barColors[index], size = size)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${item.count} ($pct%)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Empty state ─────────────────────────────────────────────────────────────────

@Composable
private fun StatisticsEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.BarChart,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No statistics yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Add flights to your logbook to see your statistics here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }
    }
}
