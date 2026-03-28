package com.flightlog.app.ui.logbook

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flightlog.app.data.AirportCoordinatesMap.LatLng
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LivePosition(val lat: Double, val lng: Double, val heading: Int?)

@Composable
fun RouteMapCanvas(
    departure: LatLng?,
    arrival: LatLng?,
    departureIata: String,
    arrivalIata: String,
    livePosition: LivePosition? = null,
    modifier: Modifier = Modifier
) {
    if (departure == null || arrival == null) {
        FallbackState(
            departureIata = departureIata,
            arrivalIata = arrivalIata,
            departureKnown = departure != null,
            arrivalKnown = arrival != null,
            modifier = modifier
        )
        return
    }

    val sameAirport = departureIata.equals(arrivalIata, ignoreCase = true) ||
            (abs(departure.lat - arrival.lat) < 1e-10 && abs(departure.lng - arrival.lng) < 1e-10)

    val primaryColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val density = LocalDensity.current

    val labelPaint = remember(textColor) {
        Paint().apply {
            color = textColor.toArgb()
            textSize = with(density) { 10.sp.toPx() }
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }
    }

    val arcPoints = remember(departure, arrival) {
        if (sameAirport) emptyList()
        else interpolateArc(departure, arrival, 60)
    }

    val viewport = remember(departure, arrival, arcPoints) {
        computeViewport(departure, arrival, arcPoints)
    }

    val semanticsDesc = "Route map from $departureIata to $arrivalIata"

    Canvas(
        modifier = modifier.semantics { contentDescription = semanticsDesc }
    ) {
        drawRect(color = bgColor)

        val strokeWidthPx = with(density) { 2.dp.toPx() }
        val dotRadiusPx = with(density) { 5.dp.toPx() }
        val labelOffsetPx = with(density) { 8.dp.toPx() }

        if (sameAirport) {
            val point = project(departure, viewport, size.width, size.height)
            drawCircle(color = primaryColor, radius = dotRadiusPx, center = point)
            drawLabel(this, labelPaint, departureIata.uppercase(), point, labelOffsetPx, size.height)
        } else {
            if (arcPoints.isNotEmpty()) {
                val path = Path()
                val firstPoint = project(arcPoints[0], viewport, size.width, size.height)
                path.moveTo(firstPoint.x, firstPoint.y)
                for (i in 1 until arcPoints.size) {
                    val p = project(arcPoints[i], viewport, size.width, size.height)
                    path.lineTo(p.x, p.y)
                }
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }

            val depPoint = project(departure, viewport, size.width, size.height)
            val arrPoint = project(arrival, viewport, size.width, size.height)
            drawCircle(color = primaryColor, radius = dotRadiusPx, center = depPoint)
            drawCircle(color = primaryColor, radius = dotRadiusPx, center = arrPoint)

            drawLabel(this, labelPaint, departureIata.uppercase(), depPoint, labelOffsetPx, size.height)
            drawLabel(this, labelPaint, arrivalIata.uppercase(), arrPoint, labelOffsetPx, size.height)
        }

        // Draw live position airplane icon
        if (livePosition != null) {
            val planeLatLng = LatLng(livePosition.lat, livePosition.lng)
            val planePoint = project(planeLatLng, viewport, size.width, size.height)
            val planeSize = with(density) { 12.dp.toPx() }
            val borderSize = with(density) { 14.dp.toPx() }
            val headingDeg = (livePosition.heading ?: 0).toFloat()

            // White border circle
            drawCircle(color = Color.White, radius = borderSize, center = planePoint)
            // Primary fill circle
            drawCircle(color = primaryColor, radius = planeSize, center = planePoint)

            // Draw a small airplane triangle rotated to heading
            rotate(degrees = headingDeg, pivot = planePoint) {
                val trianglePath = Path().apply {
                    moveTo(planePoint.x, planePoint.y - planeSize * 0.7f)
                    lineTo(planePoint.x - planeSize * 0.4f, planePoint.y + planeSize * 0.4f)
                    lineTo(planePoint.x + planeSize * 0.4f, planePoint.y + planeSize * 0.4f)
                    close()
                }
                drawPath(trianglePath, color = Color.White)
            }
        }
    }
}

@Composable
private fun FallbackState(
    departureIata: String,
    arrivalIata: String,
    departureKnown: Boolean,
    arrivalKnown: Boolean,
    modifier: Modifier = Modifier
) {
    val fallbackText = when {
        !departureKnown && !arrivalKnown -> "Route map unavailable"
        !departureKnown -> "Route map unavailable \u2014 $departureIata not found"
        else -> "Route map unavailable \u2014 $arrivalIata not found"
    }

    Box(
        modifier = modifier.background(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = fallbackText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// ── Projection & viewport ────────────────────────────────────────────────────

internal data class Viewport(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)

internal fun computeViewport(
    departure: LatLng,
    arrival: LatLng,
    arcPoints: List<LatLng>
): Viewport {
    val allPoints = listOf(departure, arrival) + arcPoints

    var minLat = allPoints.minOf { it.lat }
    var maxLat = allPoints.maxOf { it.lat }
    var minLon = allPoints.minOf { it.lng }
    var maxLon = allPoints.maxOf { it.lng }

    val latPad = (maxLat - minLat) * 0.2
    val lonPad = (maxLon - minLon) * 0.2
    minLat -= latPad
    maxLat += latPad
    minLon -= lonPad
    maxLon += lonPad

    if (maxLat - minLat < 10.0) {
        val center = (maxLat + minLat) / 2.0
        minLat = center - 5.0
        maxLat = center + 5.0
    }
    if (maxLon - minLon < 15.0) {
        val center = (maxLon + minLon) / 2.0
        minLon = center - 7.5
        maxLon = center + 7.5
    }

    minLat = max(minLat, -85.0)
    maxLat = min(maxLat, 85.0)
    minLon = max(minLon, -180.0)
    maxLon = min(maxLon, 180.0)

    return Viewport(minLat, maxLat, minLon, maxLon)
}

internal fun project(
    point: LatLng,
    viewport: Viewport,
    canvasWidth: Float,
    canvasHeight: Float
): Offset {
    val x = ((point.lng - viewport.minLon) / (viewport.maxLon - viewport.minLon) * canvasWidth).toFloat()
    val y = ((viewport.maxLat - point.lat) / (viewport.maxLat - viewport.minLat) * canvasHeight).toFloat()
    return Offset(x, y)
}

// ── Great-circle interpolation ───────────────────────────────────────────────

internal fun greatCircleInterpolate(from: LatLng, to: LatLng, fraction: Float): LatLng {
    val lat1 = Math.toRadians(from.lat)
    val lon1 = Math.toRadians(from.lng)
    val lat2 = Math.toRadians(to.lat)
    var lon2 = Math.toRadians(to.lng)

    if (lon2 - lon1 > Math.PI) lon2 -= 2 * Math.PI
    if (lon2 - lon1 < -Math.PI) lon2 += 2 * Math.PI

    val d = 2 * asin(
        sqrt(
            sin((lat2 - lat1) / 2).pow(2) +
                    cos(lat1) * cos(lat2) * sin((lon2 - lon1) / 2).pow(2)
        )
    )

    if (d < 1e-10) return from

    val a = sin((1 - fraction) * d) / sin(d)
    val b = sin(fraction * d) / sin(d)

    val x = a * cos(lat1) * cos(lon1) + b * cos(lat2) * cos(lon2)
    val y = a * cos(lat1) * sin(lon1) + b * cos(lat2) * sin(lon2)
    val z = a * sin(lat1) + b * sin(lat2)

    val lat = atan2(z, sqrt(x * x + y * y))
    val lon = atan2(y, x)

    return LatLng(Math.toDegrees(lat), Math.toDegrees(lon))
}

private fun interpolateArc(from: LatLng, to: LatLng, segments: Int): List<LatLng> {
    return (0..segments).map { i ->
        greatCircleInterpolate(from, to, i / segments.toFloat())
    }
}

// ── Label drawing ────────────────────────────────────────────────────────────

private fun drawLabel(
    canvas: DrawScope,
    paint: Paint,
    text: String,
    dotCenter: Offset,
    offsetY: Float,
    canvasHeight: Float
) {
    val textWidth = paint.measureText(text)
    val textX = dotCenter.x - textWidth / 2f

    val nearTopThreshold = canvasHeight * 0.15f
    val nearBottomThreshold = canvasHeight * 0.85f

    val textY = when {
        dotCenter.y < nearTopThreshold -> dotCenter.y + offsetY + paint.textSize
        dotCenter.y > nearBottomThreshold -> dotCenter.y - offsetY
        else -> dotCenter.y - offsetY
    }

    canvas.drawIntoCanvas {
        it.nativeCanvas.drawText(text, textX, textY, paint)
    }
}
