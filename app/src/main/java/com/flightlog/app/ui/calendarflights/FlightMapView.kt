package com.flightlog.app.ui.calendarflights

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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

private val ROUTE_COLOR_PRIMARY = Color(0xFF9ECAFF)
private val ROUTE_COLOR_UNSELECTED = Color.White.copy(alpha = 0.25f)
private val ROUTE_COLOR_DIMMED = Color.White.copy(alpha = 0.15f)
private val MARKER_COLOR_WHITE = Color.White.copy(alpha = 0.8f)

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

    // Pre-create raw bitmaps outside GoogleMap; wrap with BitmapDescriptorFactory
    // inside the map content where the Maps SDK is guaranteed to be initialized.
    val unselectedMarkerBitmap = remember(density) {
        createCircleBitmap(
            radiusDp = 4f,
            color = MARKER_COLOR_WHITE.toArgb(),
            density = density.density
        )
    }

    val selectedMarkerBitmap = remember(density) {
        createCircleBitmap(
            radiusDp = 5f,
            color = ROUTE_COLOR_PRIMARY.toArgb(),
            density = density.density,
            strokeColor = Color.White.toArgb(),
            strokeWidth = 1f
        )
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
        // BitmapDescriptorFactory is only safe inside GoogleMap content
        val unselectedMarkerIcon = remember(unselectedMarkerBitmap) {
            BitmapDescriptorFactory.fromBitmap(unselectedMarkerBitmap)
        }
        val selectedMarkerIcon = remember(selectedMarkerBitmap) {
            BitmapDescriptorFactory.fromBitmap(selectedMarkerBitmap)
        }

        // Draw polylines
        routes.forEach { route ->
            val isSelected = route.flightId == selectedFlight?.id
            val hasSelection = selectedFlight != null

            val color = when {
                isSelected -> ROUTE_COLOR_PRIMARY
                hasSelection -> ROUTE_COLOR_DIMMED
                else -> ROUTE_COLOR_UNSELECTED
            }
            val width = if (isSelected) 6f else 3f

            Polyline(
                points = route.arcPoints,
                color = color,
                width = with(density) { width.dp.toPx() }
            )
        }

        // Draw airport markers
        allAirports.forEach { (code, latLng) ->
            val isSelected = code in selectedAirports
            Marker(
                state = MarkerState(position = latLng),
                icon = if (isSelected) selectedMarkerIcon else unselectedMarkerIcon,
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

private fun createCircleBitmap(
    radiusDp: Float,
    color: Int,
    density: Float,
    strokeColor: Int? = null,
    strokeWidth: Float = 0f
): Bitmap {
    val radiusPx = (radiusDp * density).toInt().coerceAtLeast(1)
    val totalStroke = if (strokeColor != null) (strokeWidth * density).toInt() else 0
    val size = (radiusPx + totalStroke) * 2
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    if (strokeColor != null && totalStroke > 0) {
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = strokeColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radiusPx.toFloat() + totalStroke, strokePaint)
    }

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, radiusPx.toFloat(), fillPaint)

    return bitmap
}
