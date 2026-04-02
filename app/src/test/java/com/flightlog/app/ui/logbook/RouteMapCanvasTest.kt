package com.flightlog.app.ui.logbook

import com.flightlog.app.data.airport.AirportCoordinatesMap.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class RouteMapCanvasTest {

    private val EPSILON = 1e-6

    // ── greatCircleInterpolate ───────────────────────────────────────────────

    @Test
    fun `fraction 0 returns departure point`() {
        val dep = LatLng(40.6413, -73.7781)
        val arr = LatLng(51.4700, -0.4543)
        val result = greatCircleInterpolate(dep, arr, 0f)
        assertEquals(dep.lat, result.lat, EPSILON)
        assertEquals(dep.lng, result.lng, EPSILON)
    }

    @Test
    fun `fraction 1 returns arrival point`() {
        val dep = LatLng(40.6413, -73.7781)
        val arr = LatLng(51.4700, -0.4543)
        val result = greatCircleInterpolate(dep, arr, 1f)
        assertEquals(arr.lat, result.lat, EPSILON)
        assertEquals(arr.lng, result.lng, EPSILON)
    }

    @Test
    fun `fraction 0_5 returns midpoint on great circle`() {
        val dep = LatLng(40.6413, -73.7781)
        val arr = LatLng(51.4700, -0.4543)
        val mid = greatCircleInterpolate(dep, arr, 0.5f)
        // Midpoint should be north of both (great circle bows poleward for JFK-LHR)
        assertTrue("Midpoint lat should be >= departure lat", mid.lat >= dep.lat)
        // Midpoint should have valid coordinates
        assertTrue(mid.lat.isFinite())
        assertTrue(mid.lng.isFinite())
        assertTrue(mid.lat in -90.0..90.0)
    }

    @Test
    fun `same point guard returns from point`() {
        val point = LatLng(35.7647, 140.3864)
        val result = greatCircleInterpolate(point, point, 0.5f)
        assertEquals(point.lat, result.lat, EPSILON)
        assertEquals(point.lng, result.lng, EPSILON)
    }

    @Test
    fun `same point guard with very close points`() {
        val p1 = LatLng(35.7647, 140.3864)
        val p2 = LatLng(35.7647 + 1e-12, 140.3864 + 1e-12)
        val result = greatCircleInterpolate(p1, p2, 0.5f)
        assertEquals(p1.lat, result.lat, EPSILON)
        assertEquals(p1.lng, result.lng, EPSILON)
    }

    @Test
    fun `antimeridian crossing LAX to NRT`() {
        val lax = LatLng(33.9416, -118.4085)
        val nrt = LatLng(35.7647, 140.3864)
        val mid = greatCircleInterpolate(lax, nrt, 0.5f)
        assertTrue(mid.lat > 45.0)
        assertTrue(mid.lng > 150 || mid.lng < -150)
    }

    @Test
    fun `antimeridian crossing NRT to LAX reversed`() {
        val nrt = LatLng(35.7647, 140.3864)
        val lax = LatLng(33.9416, -118.4085)
        val mid = greatCircleInterpolate(nrt, lax, 0.5f)
        assertTrue(mid.lat > 45.0)
    }

    @Test
    fun `near-antipodal points produce valid result`() {
        val london = LatLng(51.5, -0.1)
        val antipode = LatLng(-51.0, 179.0)
        val mid = greatCircleInterpolate(london, antipode, 0.5f)
        assertTrue(mid.lat.isFinite())
        assertTrue(mid.lng.isFinite())
        assertTrue(mid.lat in -90.0..90.0)
    }

    @Test
    fun `interpolation produces monotonically changing distance`() {
        val dep = LatLng(40.6413, -73.7781)
        val arr = LatLng(51.4700, -0.4543)
        var prevDist = 0.0
        for (i in 1..10) {
            val frac = i / 10f
            val point = greatCircleInterpolate(dep, arr, frac)
            val dist = haversineDistance(dep, point)
            assertTrue(dist >= prevDist - EPSILON)
            prevDist = dist
        }
    }

    // ── Viewport computation ─────────────────────────────────────────────────

    @Test
    fun `viewport enforces minimum lat span of 10 degrees`() {
        val dep = LatLng(40.6413, -73.7781)
        val arr = LatLng(40.7769, -73.8740)
        val vp = computeViewport(dep, arr, emptyList())
        assertTrue(vp.maxLat - vp.minLat >= 10.0 - EPSILON)
    }

    @Test
    fun `viewport enforces minimum lon span of 15 degrees`() {
        val dep = LatLng(40.6413, -73.7781)
        val arr = LatLng(40.7769, -73.8740)
        val vp = computeViewport(dep, arr, emptyList())
        assertTrue(vp.maxLon - vp.minLon >= 15.0 - EPSILON)
    }

    @Test
    fun `viewport includes 20 percent padding`() {
        val dep = LatLng(10.0, 20.0)
        val arr = LatLng(50.0, 80.0)
        val vp = computeViewport(dep, arr, emptyList())
        assertTrue(dep.lat > vp.minLat)
        assertTrue(arr.lat < vp.maxLat)
    }

    @Test
    fun `viewport clamps latitude to -85 85`() {
        val dep = LatLng(80.0, 0.0)
        val arr = LatLng(-80.0, 50.0)
        val vp = computeViewport(dep, arr, emptyList())
        assertTrue(vp.maxLat <= 85.0)
        assertTrue(vp.minLat >= -85.0)
    }

    @Test
    fun `viewport clamps longitude to -180 180`() {
        val dep = LatLng(0.0, -175.0)
        val arr = LatLng(0.0, 175.0)
        val vp = computeViewport(dep, arr, emptyList())
        assertTrue(vp.maxLon <= 180.0)
        assertTrue(vp.minLon >= -180.0)
    }

    @Test
    fun `viewport includes arc points in bounds`() {
        val dep = LatLng(40.6413, -73.7781)
        val arr = LatLng(51.4700, -0.4543)
        val arcPoints = (0..60).map { greatCircleInterpolate(dep, arr, it / 60f) }
        val vp = computeViewport(dep, arr, arcPoints)
        for (point in arcPoints) {
            assertTrue(point.lat >= vp.minLat && point.lat <= vp.maxLat)
            assertTrue(point.lng >= vp.minLon && point.lng <= vp.maxLon)
        }
    }

    // ── Projection ───────────────────────────────────────────────────────────

    @Test
    fun `project maps bottom-left to correct corner`() {
        val vp = Viewport(minLat = 0.0, maxLat = 90.0, minLon = 0.0, maxLon = 180.0)
        val point = LatLng(0.0, 0.0)
        val offset = project(point, vp, 1000f, 500f)
        assertEquals(0f, offset.x, 0.1f)
        assertEquals(500f, offset.y, 0.1f)
    }

    @Test
    fun `project maps top-right to correct corner`() {
        val vp = Viewport(minLat = 0.0, maxLat = 90.0, minLon = 0.0, maxLon = 180.0)
        val point = LatLng(90.0, 180.0)
        val offset = project(point, vp, 1000f, 500f)
        assertEquals(1000f, offset.x, 0.1f)
        assertEquals(0f, offset.y, 0.1f)
    }

    @Test
    fun `project maps center to canvas center`() {
        val vp = Viewport(minLat = -10.0, maxLat = 10.0, minLon = -10.0, maxLon = 10.0)
        val point = LatLng(0.0, 0.0)
        val offset = project(point, vp, 400f, 400f)
        assertEquals(200f, offset.x, 0.1f)
        assertEquals(200f, offset.y, 0.1f)
    }

    // ── Route-specific edge cases ────────────────────────────────────────────

    @Test
    fun `equator route has minimal latitude curvature`() {
        val sin = LatLng(1.3644, 103.9915)
        val cgk = LatLng(-6.1256, 106.6559)
        val mid = greatCircleInterpolate(sin, cgk, 0.5f)
        val expectedMidLat = (sin.lat + cgk.lat) / 2.0
        assertTrue(abs(mid.lat - expectedMidLat) < 1.0)
    }

    @Test
    fun `polar route bows toward pole`() {
        val lhr = LatLng(51.4700, -0.4543)
        val anc = LatLng(61.1743, -149.9962)
        val mid = greatCircleInterpolate(lhr, anc, 0.5f)
        assertTrue(mid.lat > 70.0)
    }

    @Test
    fun `all 60 interpolated points have finite coordinates`() {
        val dep = LatLng(33.9416, -118.4085)
        val arr = LatLng(-33.9461, 151.1772)
        for (i in 0..60) {
            val point = greatCircleInterpolate(dep, arr, i / 60f)
            assertTrue(point.lat.isFinite())
            assertTrue(point.lng.isFinite())
            assertTrue(point.lat in -90.0..90.0)
            assertTrue(point.lng in -180.0..180.0)
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private fun haversineDistance(a: LatLng, b: LatLng): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lng - a.lng)
        val h = kotlin.math.sin(dLat / 2).pow(2) +
                kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * kotlin.math.sin(dLon / 2).pow(2)
        return 2 * kotlin.math.asin(kotlin.math.sqrt(h))
    }
}
