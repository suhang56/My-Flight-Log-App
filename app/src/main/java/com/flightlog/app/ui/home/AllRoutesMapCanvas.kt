package com.flightlog.app.ui.home

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flightlog.app.data.AirportCoordinatesMap
import com.flightlog.app.data.AirportCoordinatesMap.LatLng
import com.flightlog.app.ui.logbook.Viewport
import com.flightlog.app.ui.logbook.greatCircleInterpolate
import com.flightlog.app.ui.logbook.project
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Forced dark background regardless of system theme
private val DARK_BG = Color(0xFF1A1C1E)
private val ARC_COLOR = Color(0xFF9ECAFF)
private val LABEL_COLOR = Color(0xFFB0C4DE)

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
    val density = LocalDensity.current

    val labelPaint = remember {
        Paint().apply {
            color = LABEL_COLOR.toArgb()
            textSize = with(density) { 8.sp.toPx() }
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }
    }

    // Pre-compute resolved routes with coordinates
    val resolvedRoutes = remember(routes) {
        routes.mapNotNull { route ->
            val dep = AirportCoordinatesMap.coordinatesFor(route.departureCode)
            val arr = AirportCoordinatesMap.coordinatesFor(route.arrivalCode)
            if (dep != null && arr != null) {
                Triple(route, dep, arr)
            } else null
        }
    }

    val viewport = remember(resolvedRoutes) {
        if (resolvedRoutes.isEmpty()) {
            Viewport(minLat = -60.0, maxLat = 70.0, minLon = -170.0, maxLon = 170.0)
        } else {
            val allPoints = resolvedRoutes.flatMap { (_, dep, arr) -> listOf(dep, arr) }
            var minLat = allPoints.minOf { it.lat }
            var maxLat = allPoints.maxOf { it.lat }
            var minLon = allPoints.minOf { it.lng }
            var maxLon = allPoints.maxOf { it.lng }

            val latPad = (maxLat - minLat) * 0.15
            val lonPad = (maxLon - minLon) * 0.15
            minLat -= latPad
            maxLat += latPad
            minLon -= lonPad
            maxLon += lonPad

            if (maxLat - minLat < 20.0) {
                val center = (maxLat + minLat) / 2.0
                minLat = center - 10.0
                maxLat = center + 10.0
            }
            if (maxLon - minLon < 30.0) {
                val center = (maxLon + minLon) / 2.0
                minLon = center - 15.0
                maxLon = center + 15.0
            }

            Viewport(
                minLat = max(minLat, -85.0),
                maxLat = min(maxLat, 85.0),
                minLon = max(minLon, -180.0),
                maxLon = min(maxLon, 180.0)
            )
        }
    }

    // Pre-compute arcs with reduced segments for large route sets
    val arcs = remember(resolvedRoutes) {
        val segments = if (resolvedRoutes.size > 50) 20 else 40
        resolvedRoutes.map { (route, dep, arr) ->
            val sameAirport = route.departureCode.equals(route.arrivalCode, ignoreCase = true) ||
                (abs(dep.lat - arr.lat) < 1e-10 && abs(dep.lng - arr.lng) < 1e-10)
            val arcPoints = if (sameAirport) emptyList()
            else (0..segments).map { i -> greatCircleInterpolate(dep, arr, i / segments.toFloat()) }
            Triple(route, arcPoints, listOf(dep, arr))
        }
    }

    Canvas(
        modifier = modifier.semantics {
            contentDescription = "Flight routes map showing ${routes.size} routes"
        }
    ) {
        drawRect(color = DARK_BG)

        val thinStroke = with(density) { 1.2.dp.toPx() }
        val thickStroke = with(density) { 2.5.dp.toPx() }
        val dotRadius = with(density) { 5.dp.toPx() }
        val borderRadius = with(density) { 6.dp.toPx() }

        // Draw dimmed (past) routes first, then highlighted on top
        val sortedArcs = arcs.sortedBy { it.first.isHighlighted }

        for ((route, arcPoints, endpoints) in sortedArcs) {
            val isHighlighted = route.isHighlighted
            val arcColor = if (isHighlighted) ARC_COLOR else ARC_COLOR.copy(alpha = 0.6f)
            val strokeWidth = if (isHighlighted) thickStroke else thinStroke

            // Draw arc (skip for same-airport routes)
            if (arcPoints.size >= 2) {
                val path = Path()
                val first = project(arcPoints[0], viewport, size.width, size.height)
                path.moveTo(first.x, first.y)
                for (i in 1 until arcPoints.size) {
                    val p = project(arcPoints[i], viewport, size.width, size.height)
                    path.lineTo(p.x, p.y)
                }
                drawPath(
                    path = path,
                    color = arcColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Draw 5dp white-bordered airport dots
            for (ep in endpoints) {
                val p = project(ep, viewport, size.width, size.height)
                drawCircle(color = Color.White, radius = borderRadius, center = p)
                drawCircle(color = ARC_COLOR, radius = dotRadius, center = p)
            }
        }

        // Draw labels for highlighted route airports only
        val highlightedCodes = arcs
            .filter { it.first.isHighlighted }
            .flatMap { listOf(it.first.departureCode, it.first.arrivalCode) }
            .toSet()

        for (code in highlightedCodes) {
            val coord = AirportCoordinatesMap.coordinatesFor(code) ?: continue
            val point = project(coord, viewport, size.width, size.height)
            val textWidth = labelPaint.measureText(code)
            val textX = point.x - textWidth / 2f
            val textY = point.y - with(density) { 8.dp.toPx() }

            drawIntoCanvas {
                it.nativeCanvas.drawText(code, textX, textY, labelPaint)
            }
        }
    }
}
