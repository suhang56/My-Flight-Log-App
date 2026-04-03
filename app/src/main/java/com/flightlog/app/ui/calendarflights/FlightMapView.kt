package com.flightlog.app.ui.calendarflights

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.flightlog.app.R
import com.flightlog.app.data.airport.AirportCoordinatesMap
import com.flightlog.app.data.local.entity.CalendarFlight
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

// Golden amber routes (matches aviation map style)
private val ROUTE_COLOR_PRIMARY = Color(0xFFE8B634)
private val ROUTE_COLOR_UNSELECTED = Color(0xFFD4A12A).copy(alpha = 0.45f)
private val ROUTE_COLOR_DIMMED = Color(0xFFD4A12A).copy(alpha = 0.18f)
// Cyan/teal airport labels
private val MARKER_COLOR_LABEL = Color(0xFF00D4E0)
private val MARKER_COLOR_SELECTED = Color(0xFFFFFFFF)

internal data class FlightRoute(
    val flightId: Long,
    val depCode: String,
    val arrCode: String,
    val depLatLng: LatLng,
    val arrLatLng: LatLng,
    val arcPoints: List<LatLng>
)

@Composable
internal fun FlightMapView(
    allFlights: List<CalendarFlight>,
    selectedFlight: CalendarFlight?,
    onMapTapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val mapStyleOptions = remember {
        try {
            MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark)
        } catch (_: Exception) {
            null
        }
    }

    val routes = remember(allFlights) {
        allFlights.mapNotNull { flight ->
            val dep = AirportCoordinatesMap.getCoords(flight.departureCode) ?: return@mapNotNull null
            val arr = AirportCoordinatesMap.getCoords(flight.arrivalCode) ?: return@mapNotNull null
            val arcPoints = AirportCoordinatesMap.interpolateArc(dep, arr)
                .map { LatLng(it.first, it.second) }
            FlightRoute(
                flightId = flight.id,
                depCode = flight.departureCode,
                arrCode = flight.arrivalCode,
                depLatLng = LatLng(dep.first, dep.second),
                arrLatLng = LatLng(arr.first, arr.second),
                arcPoints = arcPoints
            )
        }
    }

    // Deduplicate airport markers
    val allAirports = remember(routes) {
        val map = mutableMapOf<String, LatLng>()
        routes.forEach { route ->
            map.putIfAbsent(route.depCode, route.depLatLng)
            map.putIfAbsent(route.arrCode, route.arrLatLng)
        }
        map
    }

    val selectedRoute = remember(routes, selectedFlight) {
        selectedFlight?.let { sf ->
            routes.find { it.flightId == sf.id }
        }
    }

    val selectedAirports = remember(selectedRoute) {
        selectedRoute?.let { setOf(it.depCode, it.arrCode) } ?: emptySet()
    }

    // Pre-create label bitmaps for each airport code
    val labelBitmaps = remember(allAirports, selectedAirports, density) {
        allAirports.mapValues { (code, _) ->
            val isSelected = code in selectedAirports
            createAirportLabelBitmap(
                code = code,
                textColor = if (isSelected) MARKER_COLOR_SELECTED.toArgb() else MARKER_COLOR_LABEL.toArgb(),
                density = density.density,
                isSelected = isSelected
            )
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)
    }

    // Camera: fit all routes on first load
    LaunchedEffect(routes) {
        if (routes.isEmpty()) return@LaunchedEffect
        val bounds = buildAllRoutesBounds(routes) ?: return@LaunchedEffect
        try {
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngBounds(bounds, with(density) { 60.dp.roundToPx() })
            )
        } catch (_: Exception) {
            // Map not yet laid out
        }
    }

    // Camera: animate on selection/deselection
    LaunchedEffect(selectedFlight?.id) {
        if (selectedRoute != null) {
            val bounds = buildRouteBounds(selectedRoute.depLatLng, selectedRoute.arrLatLng)
            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(bounds, with(density) { 80.dp.roundToPx() }),
                    durationMs = 600
                )
            } catch (_: Exception) { }
        } else if (routes.isNotEmpty()) {
            val bounds = buildAllRoutesBounds(routes) ?: return@LaunchedEffect
            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(bounds, with(density) { 60.dp.roundToPx() }),
                    durationMs = 600
                )
            } catch (_: Exception) { }
        }
    }

    val mapProperties = remember(mapStyleOptions) {
        MapProperties(
            mapType = MapType.HYBRID,
            isBuildingEnabled = false,
            isIndoorEnabled = false
        )
    }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = false
        )
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = mapUiSettings,
        onMapClick = { onMapTapped() }
    ) {
        // Draw polylines
        routes.forEach { route ->
            val isSelected = route.flightId == selectedFlight?.id
            val hasSelection = selectedFlight != null

            val color = when {
                isSelected -> ROUTE_COLOR_PRIMARY
                hasSelection -> ROUTE_COLOR_DIMMED
                else -> ROUTE_COLOR_UNSELECTED
            }
            val width = if (isSelected) 5f else 2.5f

            Polyline(
                points = route.arcPoints,
                color = color,
                width = with(density) { width.dp.toPx() },
                geodesic = true
            )
        }

        // Draw airport labels with IATA codes
        allAirports.forEach { (code, latLng) ->
            val bitmap = labelBitmaps[code] ?: return@forEach
            val icon = remember(bitmap) {
                BitmapDescriptorFactory.fromBitmap(bitmap)
            }
            Marker(
                state = MarkerState(position = latLng),
                icon = icon,
                title = code,
                anchor = Offset(0.5f, 0.5f),
                flat = true
            )
        }
    }
}

private fun buildAllRoutesBounds(routes: List<FlightRoute>): LatLngBounds? {
    if (routes.isEmpty()) return null
    val builder = LatLngBounds.builder()
    routes.forEach { route ->
        builder.include(route.depLatLng)
        builder.include(route.arrLatLng)
    }
    return builder.build()
}

private fun buildRouteBounds(dep: LatLng, arr: LatLng): LatLngBounds {
    val builder = LatLngBounds.builder()
    builder.include(dep)
    builder.include(arr)
    // Handle same-point bounds (departure == arrival)
    if (dep.latitude == arr.latitude && dep.longitude == arr.longitude) {
        builder.include(LatLng(dep.latitude + 0.5, dep.longitude + 0.5))
        builder.include(LatLng(dep.latitude - 0.5, dep.longitude - 0.5))
    }
    return builder.build()
}

private fun createAirportLabelBitmap(
    code: String,
    textColor: Int,
    density: Float,
    isSelected: Boolean
): Bitmap {
    val textSizeDp = if (isSelected) 12f else 10f
    val textSizePx = textSizeDp * density
    val paddingH = (6f * density).toInt()
    val paddingV = (3f * density).toInt()

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        this.textSize = textSizePx
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }

    val textWidth = textPaint.measureText(code).toInt()
    val fm = textPaint.fontMetrics
    val textHeight = (fm.descent - fm.ascent).toInt()

    // Small airplane icon size
    val iconSize = (textSizePx * 0.9f).toInt()
    val iconGap = (2f * density).toInt()

    val totalWidth = iconSize + iconGap + textWidth + paddingH * 2
    val totalHeight = textHeight + paddingV * 2

    val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Semi-transparent dark background
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x80000000.toInt()
        style = Paint.Style.FILL
    }
    val cornerRadius = 4f * density
    canvas.drawRoundRect(
        0f, 0f, totalWidth.toFloat(), totalHeight.toFloat(),
        cornerRadius, cornerRadius, bgPaint
    )

    // Draw small arrow (→) as airplane indicator
    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        this.textSize = textSizePx * 0.8f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
    }
    val arrowY = paddingV - fm.ascent
    canvas.drawText("✈", paddingH.toFloat(), arrowY, arrowPaint)

    // Draw IATA code text
    val textX = (paddingH + iconSize + iconGap).toFloat()
    val textY = paddingV - fm.ascent
    canvas.drawText(code, textX, textY, textPaint)

    return bitmap
}
