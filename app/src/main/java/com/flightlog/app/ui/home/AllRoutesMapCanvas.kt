package com.flightlog.app.ui.home

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import kotlin.math.max
import kotlin.math.min

private val DARK_BG = Color(0xFF0D1B2A)
private val DIM_ARC_COLOR = Color(0xFF2A4A6B)
private val DIM_DOT_COLOR = Color(0xFF3A5A7B)
private val HIGHLIGHT_ARC_COLOR = Color(0xFF42A5F5)
private val HIGHLIGHT_DOT_COLOR = Color(0xFF90CAF9)
private val LABEL_COLOR = Color(0xFF8EAFC0)

data class RouteData(
    val departureCode: String,
    val arrivalCode: String,
    val isHighlighted: Boolean = false
)

@Composable
fun AllRoutesMapCanvas(
    routes: List<RouteData>,
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

    // Compute the world viewport encompassing all routes
    val viewport = remember(resolvedRoutes) {
        if (resolvedRoutes.isEmpty()) {
            // Default world view
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

    // Pre-compute arcs
    val arcs = remember(resolvedRoutes) {
        resolvedRoutes.map { (route, dep, arr) ->
            val arcPoints = (0..40).map { i ->
                greatCircleInterpolate(dep, arr, i / 40f)
            }
            Triple(route, arcPoints, listOf(dep, arr))
        }
    }

    Canvas(
        modifier = modifier.semantics {
            contentDescription = "Flight routes map showing ${routes.size} routes"
        }
    ) {
        // Dark background
        drawRect(color = DARK_BG)

        val thinStroke = with(density) { 1.2.dp.toPx() }
        val thickStroke = with(density) { 2.5.dp.toPx() }
        val smallDot = with(density) { 3.dp.toPx() }
        val largeDot = with(density) { 5.dp.toPx() }

        val allAirportCodes = mutableSetOf<String>()

        // Draw dimmed routes first, highlighted on top
        val sortedArcs = arcs.sortedBy { it.first.isHighlighted }

        for ((route, arcPoints, endpoints) in sortedArcs) {
            val isHighlighted = route.isHighlighted
            val arcColor = if (isHighlighted) HIGHLIGHT_ARC_COLOR else DIM_ARC_COLOR
            val dotColor = if (isHighlighted) HIGHLIGHT_DOT_COLOR else DIM_DOT_COLOR
            val strokeWidth = if (isHighlighted) thickStroke else thinStroke
            val dotRadius = if (isHighlighted) largeDot else smallDot

            // Draw arc
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

            // Draw airport dots
            for (ep in endpoints) {
                val p = project(ep, viewport, size.width, size.height)
                drawCircle(color = dotColor, radius = dotRadius, center = p)
            }

            allAirportCodes.add(route.departureCode)
            allAirportCodes.add(route.arrivalCode)
        }

        // Draw labels for highlighted route airports
        val highlightedCodes = arcs
            .filter { it.first.isHighlighted }
            .flatMap { listOf(it.first.departureCode, it.first.arrivalCode) }
            .toSet()

        for (code in highlightedCodes) {
            val coord = AirportCoordinatesMap.coordinatesFor(code) ?: continue
            val point = project(coord, viewport, size.width, size.height)
            val textWidth = labelPaint.measureText(code)
            val textX = point.x - textWidth / 2f
            val textY = point.y - with(density) { 6.dp.toPx() }

            drawIntoCanvas {
                it.nativeCanvas.drawText(code, textX, textY, labelPaint)
            }
        }
    }
}
