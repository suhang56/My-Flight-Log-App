package com.flightlog.app.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.flightlog.app.data.AirportCoordinatesMap
import com.flightlog.app.ui.logbook.greatCircleInterpolate
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

private val ARC_COLOR = Color(0xFF9ECAFF)
private val BRIGHT_ARC_COLOR = Color(0xFF00E5FF)

/**
 * A single route segment to render on the map.
 * All data is pre-collected -- no Flows are accessed inside this composable.
 */
data class RouteSegment(
    val departureCode: String,
    val arrivalCode: String,
    val isHighlighted: Boolean = false
)

private fun createCircleMarkerBitmap(
    sizePx: Int,
    fillColor: Int,
    borderColor: Int,
    borderWidthPx: Float
): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f
    val radius = center - borderWidthPx

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = borderColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, center, borderPaint)

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, radius, fillPaint)

    return bitmap
}

@Composable
fun AllRoutesMapCanvas(
    routes: List<RouteSegment>,
    modifier: Modifier = Modifier
) {
    // Resolve routes to LatLng pairs
    val resolvedRoutes = remember(routes) {
        routes.mapNotNull { route ->
            val dep = AirportCoordinatesMap.coordinatesFor(route.departureCode)
            val arr = AirportCoordinatesMap.coordinatesFor(route.arrivalCode)
            if (dep != null && arr != null) {
                Triple(route, LatLng(dep.lat, dep.lng), LatLng(arr.lat, arr.lng))
            } else null
        }
    }

    // Compute great-circle arc points for polylines
    val arcData = remember(resolvedRoutes) {
        val segments = if (resolvedRoutes.size > 50) 20 else 40
        resolvedRoutes.map { (route, dep, arr) ->
            val depCoord = AirportCoordinatesMap.LatLng(dep.latitude, dep.longitude)
            val arrCoord = AirportCoordinatesMap.LatLng(arr.latitude, arr.longitude)
            val sameAirport = route.departureCode.equals(route.arrivalCode, ignoreCase = true)
            val arcPoints = if (sameAirport) listOf(dep)
            else (0..segments).map { i ->
                val p = greatCircleInterpolate(depCoord, arrCoord, i / segments.toFloat())
                LatLng(p.lat, p.lng)
            }
            Triple(route, arcPoints, listOf(dep, arr))
        }
    }

    // Pre-create marker bitmaps (~12dp diameter, 36px at ~3x density)
    val markerBitmapHighlighted = remember {
        BitmapDescriptorFactory.fromBitmap(
            createCircleMarkerBitmap(
                sizePx = 36,
                fillColor = ARC_COLOR.toArgb(),
                borderColor = android.graphics.Color.WHITE,
                borderWidthPx = 4f
            )
        )
    }
    val markerBitmapDimmed = remember {
        BitmapDescriptorFactory.fromBitmap(
            createCircleMarkerBitmap(
                sizePx = 36,
                fillColor = ARC_COLOR.copy(alpha = 0.6f).toArgb(),
                borderColor = android.graphics.Color.argb(153, 255, 255, 255),
                borderWidthPx = 4f
            )
        )
    }

    // Compute camera bounds
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(30.0, 0.0), 2f)
    }

    val bounds = remember(resolvedRoutes) {
        if (resolvedRoutes.isEmpty()) null
        else {
            val builder = LatLngBounds.builder()
            resolvedRoutes.forEach { (_, dep, arr) ->
                builder.include(dep)
                builder.include(arr)
            }
            builder.build()
        }
    }

    val mapProperties = remember {
        MapProperties(
            mapType = MapType.HYBRID,
            isBuildingEnabled = false,
            isIndoorEnabled = false
        )
    }

    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            tiltGesturesEnabled = false
        )
    }

    GoogleMap(
        modifier = modifier.semantics {
            contentDescription = "Flight routes map showing ${routes.size} routes"
        },
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = uiSettings,
        onMapLoaded = {
            bounds?.let { b ->
                cameraPositionState.move(
                    com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds(b, 80)
                )
            }
        }
    ) {
        // Draw arcs — dimmed first, then highlighted on top
        val sortedArcs = arcData.sortedBy { it.first.isHighlighted }

        for ((route, arcPoints, _) in sortedArcs) {
            if (arcPoints.size >= 2) {
                Polyline(
                    points = arcPoints,
                    color = if (route.isHighlighted) BRIGHT_ARC_COLOR else Color.White.copy(alpha = 0.35f),
                    width = if (route.isHighlighted) 8f else 3f,
                    geodesic = true
                )
            }
        }

        // Deduplicated airport markers — highlighted routes take priority
        val markerMap = mutableMapOf<String, Pair<LatLng, Boolean>>()
        for ((route, _, endpoints) in sortedArcs) {
            val codes = listOf(route.departureCode, route.arrivalCode)
            for ((i, ep) in endpoints.withIndex()) {
                val code = codes[i]
                val existing = markerMap[code]
                if (existing == null || route.isHighlighted) {
                    markerMap[code] = ep to route.isHighlighted
                }
            }
        }
        for ((code, pair) in markerMap) {
            val (position, highlighted) = pair
            Marker(
                state = MarkerState(position = position),
                title = code,
                icon = if (highlighted) markerBitmapHighlighted else markerBitmapDimmed,
                anchor = Offset(0.5f, 0.5f)
            )
        }
    }
}
