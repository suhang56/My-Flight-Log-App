package com.flightlog.app.ui.statistics

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flightlog.app.data.calendar.AirlineIataMap
import com.flightlog.app.data.local.dao.AirlineCount
import com.flightlog.app.data.local.dao.MonthlyCount
import com.flightlog.app.data.local.dao.RouteCount
import com.flightlog.app.data.local.dao.SeatClassCount
import com.flightlog.app.data.local.entity.LogbookFlight
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Date
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
        if (stats.flightCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        text = "Log some flights to see your statistics.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 40.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Hero stats row
                HeroStatsRow(stats)

                // First flight milestone card (#7)
                stats.firstFlight?.let { flight ->
                    FirstFlightCard(flight)
                }

                // Monthly bar chart (#2, #6)
                if (stats.monthlyFlightCounts.isNotEmpty()) {
                    MonthlyBarChartSection(stats.monthlyFlightCounts)
                }

                // Seat class breakdown (#1)
                if (stats.seatClassCounts.isNotEmpty()) {
                    SeatClassBreakdown(stats.seatClassCounts, stats.flightCount)
                }

                // Airlines top list (#3)
                if (stats.topAirlines.isNotEmpty()) {
                    AirlinesTopList(stats.topAirlines)
                }

                // Longest flight by distance
                stats.longestByDistance?.let { flight ->
                    LongestFlightCard(
                        title = "Longest Flight (Distance)",
                        flight = flight,
                        icon = Icons.Default.Route
                    )
                }

                // Longest flight by duration (#4)
                stats.longestByDuration?.let { flight ->
                    LongestFlightByDurationCard(flight)
                }

                // Top routes (#5)
                if (stats.topRoutes.isNotEmpty()) {
                    TopRoutesSection(stats.topRoutes)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HeroStatsRow(stats: StatsData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        HeroStatItem(label = "Flights", value = "${stats.flightCount}")
        HeroStatItem(
            label = "Hours",
            value = String.format(Locale.US, "%.1f", stats.totalDurationMinutes / 60.0)
        )
        HeroStatItem(
            label = "Distance",
            value = if (stats.totalDistanceKm >= 1000) {
                String.format(Locale.US, "%.1fk km", stats.totalDistanceKm / 1000.0)
            } else {
                "${stats.totalDistanceKm} km"
            }
        )
        HeroStatItem(label = "Airports", value = "${stats.uniqueAirportCount}")
    }
}

@Composable
private fun HeroStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --- #7: First Flight Milestone Card ---

@Composable
private fun FirstFlightCard(flight: LogbookFlight) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "First Flight",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${flight.departureCode} \u2192 ${flight.arrivalCode}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = dateFormat.format(Date(flight.departureTimeMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --- #2 & #6: Monthly Bar Chart with Toggle ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthlyBarChartSection(monthlyCounts: List<MonthlyCount>) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val options = listOf("Last 12 Months", "All Time")

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Flights per Month",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size
                        )
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val displayData = if (selectedIndex == 0) {
                // Last 12 months: fill in missing months with 0
                val now = YearMonth.now()
                val last12 = (11 downTo 0).map { now.minusMonths(it.toLong()) }
                val countMap = monthlyCounts.associate { it.month to it.count }
                last12.map { ym ->
                    val key = ym.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                    MonthlyCount(key, countMap[key] ?: 0)
                }
            } else {
                monthlyCounts
            }

            if (selectedIndex == 1 && displayData.size > 12) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    MonthlyBarChart(
                        data = displayData,
                        modifier = Modifier
                            .width((displayData.size * 40).dp)
                            .height(180.dp)
                    )
                }
            } else {
                MonthlyBarChart(
                    data = displayData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }
        }
    }
}

@Composable
private fun MonthlyBarChart(data: List<MonthlyCount>, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas

        val maxCount = data.maxOf { it.count }.coerceAtLeast(1)
        val barSpacing = 4.dp.toPx()
        val bottomPadding = 24.dp.toPx()
        val topPadding = 20.dp.toPx()
        val chartHeight = size.height - bottomPadding - topPadding
        val barWidth = (size.width - barSpacing * (data.size + 1)) / data.size

        // Label paint for count labels above bars
        val countLabelPaint = Paint().apply {
            color = labelColor.toArgb()
            textSize = 10.dp.toPx()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        // Month label paint
        val monthLabelPaint = Paint().apply {
            color = outlineColor.toArgb()
            textSize = 8.dp.toPx()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        data.forEachIndexed { index, item ->
            val x = barSpacing + index * (barWidth + barSpacing)
            val barHeight = if (maxCount > 0) (item.count.toFloat() / maxCount) * chartHeight else 0f
            val barTop = topPadding + chartHeight - barHeight

            // Draw bar
            if (item.count > 0) {
                drawRect(
                    color = primaryColor,
                    topLeft = Offset(x, barTop),
                    size = Size(barWidth, barHeight)
                )

                // Draw count label above bar (#2)
                drawContext.canvas.nativeCanvas.drawText(
                    "${item.count}",
                    x + barWidth / 2,
                    barTop - 4.dp.toPx(),
                    countLabelPaint
                )
            }

            // Draw month label below
            val monthLabel = if (item.month.length >= 7) {
                item.month.substring(5, 7)
            } else {
                item.month
            }
            drawContext.canvas.nativeCanvas.drawText(
                monthLabel,
                x + barWidth / 2,
                size.height - 4.dp.toPx(),
                monthLabelPaint
            )
        }
    }
}

// --- #1: Seat Class Breakdown ---

@Composable
private fun SeatClassBreakdown(seatClassCounts: List<SeatClassCount>, totalFlights: Int) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Seat Class",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val totalWithClass = seatClassCounts.sumOf { it.count }

            seatClassCounts.forEach { seatClass ->
                val percentage = (seatClass.count.toFloat() / totalWithClass * 100)
                val percentText = if (percentage > 0 && percentage < 1) {
                    "<1%"
                } else {
                    "${percentage.toInt()}%"
                }
                val displayName = seatClassDisplayName(seatClass.seatClass)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "$percentText (${seatClass.count})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun seatClassDisplayName(seatClass: String): String = when (seatClass) {
    "economy" -> "Economy"
    "premium_economy" -> "Premium Economy"
    "business" -> "Business"
    "first" -> "First"
    else -> seatClass.replaceFirstChar { it.uppercase() }
}

// --- #3: Airlines Top List ---

@Composable
private fun AirlinesTopList(airlines: List<AirlineCount>) {
    val airlineIataMap = remember { AirlineIataMap() }

    TopListSection(
        title = "Top Airlines",
        icon = Icons.Default.FlightTakeoff,
        items = airlines.map { airline ->
            val fullName = airlineIataMap.getAirlineName(airline.airlineCode)
            val display = if (fullName != null) {
                "${airline.airlineCode} \u2014 $fullName"
            } else {
                airline.airlineCode
            }
            display to airline.count
        }
    )
}

// --- #4: Longest Flight by Duration ---

@Composable
private fun LongestFlightByDurationCard(flight: LogbookFlight) {
    LongestFlightCard(
        title = "Longest Flight (Duration)",
        flight = flight,
        icon = Icons.Default.Schedule
    )
}

@Composable
private fun LongestFlightCard(
    title: String,
    flight: LogbookFlight,
    icon: ImageVector
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${flight.departureCode} \u2192 ${flight.arrivalCode}",
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (flight.distanceKm != null) {
                    Text(
                        text = "${flight.distanceKm} km",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (flight.durationMinutes != null) {
                    val h = flight.durationMinutes / 60
                    val m = flight.durationMinutes % 60
                    Text(
                        text = "${h}h ${m}m",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = dateFormat.format(Date(flight.departureTimeMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (flight.flightNumber.isNotBlank()) {
                Text(
                    text = flight.flightNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

// --- #5: Top Routes ---

@Composable
private fun TopRoutesSection(routes: List<RouteCount>) {
    TopListSection(
        title = "Top Routes",
        icon = Icons.Default.SyncAlt,
        items = routes.map { it.label to it.count }
    )
}

// --- Shared TopListSection ---

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
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            items.forEach { (label, count) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$label ($count)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
