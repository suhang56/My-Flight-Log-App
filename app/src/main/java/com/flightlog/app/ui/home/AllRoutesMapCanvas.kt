package com.flightlog.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

private val ARC_COLOR = Color(0xFF9ECAFF)

// Dark map style JSON — hides labels, roads, transit; shows water/land in dark tones
private val DARK_MAP_STYLE = """
[
  {"elementType":"geometry","stylers":[{"color":"#1a1c1e"}]},
  {"elementType":"labels","stylers":[{"visibility":"off"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0e1621"}]},
  {"featureType":"landscape","elementType":"geometry","stylers":[{"color":"#1a1c1e"}]},
  {"featureType":"administrative","elementType":"geometry.stroke","stylers":[{"color":"#2c2f33"},{"weight":0.5}]},
  {"featureType":"administrative.country","elementType":"geometry.stroke","stylers":[{"color":"#3a3d42"},{"visibility":"on"}]},
  {"featureType":"road","stylers":[{"visibility":"off"}]},
  {"featureType":"transit","stylers":[{"visibility":"off"}]},
  {"featureType":"poi","stylers":[{"visibility":"off"}]}
]
""".trimIndent()

/**
 * A single route segment to render on the map.
 * All data is pre-collected -- no Flows are accessed inside this composable.
 */
data class RouteSegment(
    val departureCode: String,
    val arrivalCode: String,
    val isHighlighted: Boolean = false
)

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
            mapStyleOptions = MapStyleOptions(DARK_MAP_STYLE),
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

        for ((route, arcPoints, endpoints) in sortedArcs) {
            if (arcPoints.size >= 2) {
                Polyline(
                    points = arcPoints,
                    color = if (route.isHighlighted) ARC_COLOR else ARC_COLOR.copy(alpha = 0.4f),
                    width = if (route.isHighlighted) 6f else 3f,
                    geodesic = true
                )
            }

            // Airport markers
            for ((i, ep) in endpoints.withIndex()) {
                val code = if (i == 0) route.departureCode else route.arrivalCode
                Marker(
                    state = MarkerState(position = ep),
                    title = code,
                    icon = BitmapDescriptorFactory.defaultMarker(
                        if (route.isHighlighted) BitmapDescriptorFactory.HUE_AZURE
                        else BitmapDescriptorFactory.HUE_BLUE
                    ),
                    alpha = if (route.isHighlighted) 1f else 0.6f
                )
            }
        }
    }
}
