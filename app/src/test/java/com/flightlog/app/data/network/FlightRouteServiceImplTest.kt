package com.flightlog.app.data.network

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class FlightRouteServiceImplTest {

    // -- filterByDate tests (pure logic, no API) --

    private val service = FlightRouteServiceImpl(FakeFlightAwareApi())

    private fun routeOnDate(date: LocalDate): FlightRoute {
        val millis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        return FlightRoute(
            flightNumber = "UA882",
            departureIata = "SFO",
            arrivalIata = "PVG",
            departureScheduledUtc = millis
        )
    }

    @Test
    fun `filterByDate returns exact date match`() {
        val target = LocalDate.of(2026, 4, 8)
        val routes = listOf(routeOnDate(target))
        val result = service.filterByDate(routes, target)
        assertEquals(1, result.size)
    }

    @Test
    fun `filterByDate returns route within plus 1 day tolerance`() {
        val target = LocalDate.of(2026, 4, 8)
        val routes = listOf(routeOnDate(target.plusDays(1)))
        val result = service.filterByDate(routes, target)
        assertEquals(1, result.size)
    }

    @Test
    fun `filterByDate returns route within minus 1 day tolerance`() {
        val target = LocalDate.of(2026, 4, 8)
        val routes = listOf(routeOnDate(target.minusDays(1)))
        val result = service.filterByDate(routes, target)
        assertEquals(1, result.size)
    }

    @Test
    fun `filterByDate excludes route beyond tolerance`() {
        val target = LocalDate.of(2026, 4, 8)
        val routes = listOf(routeOnDate(target.plusDays(2)))
        val result = service.filterByDate(routes, target)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filterByDate excludes route with null departure time`() {
        val target = LocalDate.of(2026, 4, 8)
        val routes = listOf(
            FlightRoute(
                flightNumber = "UA882",
                departureIata = "SFO",
                arrivalIata = "PVG",
                departureScheduledUtc = null
            )
        )
        val result = service.filterByDate(routes, target)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filterByDate with empty list returns empty`() {
        val result = service.filterByDate(emptyList(), LocalDate.of(2026, 4, 8))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filterByDate selects only matching routes from mixed list`() {
        val target = LocalDate.of(2026, 4, 8)
        val routes = listOf(
            routeOnDate(target),
            routeOnDate(target.plusDays(5)),
            routeOnDate(target.minusDays(1)),
            routeOnDate(target.minusDays(3))
        )
        val result = service.filterByDate(routes, target)
        assertEquals(2, result.size)
    }

    // -- API integration tests with FakeFlightAwareApi --

    @Test
    fun `lookupAllRoutes returns results for future date via undated fallback`() = runTest {
        val targetDate = LocalDate.now(ZoneOffset.UTC).plusDays(10)
        val depMillis = targetDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

        val fakeApi = FakeFlightAwareApi()
        // Dated calls return 400, undated calls return a flight
        fakeApi.onGetFlights = { _, start, _, _ ->
            if (start != null) {
                Response.error(400, "Bad Request".toResponseBody("text/plain".toMediaType()))
            } else {
                Response.success(
                    FlightAwareFlightsResponse(
                        flights = listOf(
                            flightAwareFlight(
                                originIata = "SFO",
                                destIata = "PVG",
                                scheduledOut = targetDate.atStartOfDay()
                                    .atOffset(ZoneOffset.UTC).toString()
                            )
                        )
                    )
                )
            }
        }

        val svc = FlightRouteServiceImpl(fakeApi)
        val routes = svc.lookupAllRoutes("UA882", targetDate)
        assertEquals(1, routes.size)
        assertEquals("SFO", routes[0].departureIata)
        assertEquals("PVG", routes[0].arrivalIata)
    }

    @Test
    fun `lookupAllRoutes uses dated query for old flights`() = runTest {
        val targetDate = LocalDate.now(ZoneOffset.UTC).minusDays(30)
        val depMillis = targetDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

        val fakeApi = FakeFlightAwareApi()
        fakeApi.onGetFlights = { _, start, end, _ ->
            if (start != null) {
                Response.success(
                    FlightAwareFlightsResponse(
                        flights = listOf(
                            flightAwareFlight(
                                originIata = "NRT",
                                destIata = "LAX",
                                scheduledOut = targetDate.atStartOfDay()
                                    .atOffset(ZoneOffset.UTC).toString()
                            )
                        )
                    )
                )
            } else {
                Response.success(FlightAwareFlightsResponse(flights = emptyList()))
            }
        }

        val svc = FlightRouteServiceImpl(fakeApi)
        val routes = svc.lookupAllRoutes("JL62", targetDate)
        assertEquals(1, routes.size)
        assertEquals("NRT", routes[0].departureIata)
    }

    @Test
    fun `lookupAllRoutes returns empty when all strategies fail`() = runTest {
        val fakeApi = FakeFlightAwareApi()
        fakeApi.onGetFlights = { _, _, _, _ ->
            Response.error(400, "Bad Request".toResponseBody("text/plain".toMediaType()))
        }

        val svc = FlightRouteServiceImpl(fakeApi)
        val routes = svc.lookupAllRoutes("XX999", LocalDate.of(2026, 12, 25))
        assertTrue(routes.isEmpty())
    }

    @Test
    fun `lookupAllRoutes filters undated results to target date`() = runTest {
        val targetDate = LocalDate.now(ZoneOffset.UTC).plusDays(3)
        val wrongDate = targetDate.plusDays(10)

        val fakeApi = FakeFlightAwareApi()
        fakeApi.onGetFlights = { _, start, _, _ ->
            if (start != null) {
                Response.error(400, "Bad Request".toResponseBody("text/plain".toMediaType()))
            } else {
                // Return flights for both target and wrong dates
                Response.success(
                    FlightAwareFlightsResponse(
                        flights = listOf(
                            flightAwareFlight(
                                originIata = "SFO",
                                destIata = "PVG",
                                scheduledOut = targetDate.atStartOfDay()
                                    .atOffset(ZoneOffset.UTC).toString()
                            ),
                            flightAwareFlight(
                                originIata = "SFO",
                                destIata = "PVG",
                                scheduledOut = wrongDate.atStartOfDay()
                                    .atOffset(ZoneOffset.UTC).toString()
                            )
                        )
                    )
                )
            }
        }

        val svc = FlightRouteServiceImpl(fakeApi)
        val routes = svc.lookupAllRoutes("UA882", targetDate)
        assertEquals(1, routes.size)
    }

    @Test
    fun `parseIsoToUtc parses valid ISO string`() {
        val millis = FlightRouteServiceImpl.parseIsoToUtc("2026-04-08T10:30:00+00:00")
        assertTrue(millis != null && millis > 0)
    }

    @Test
    fun `parseIsoToUtc returns null for null input`() {
        assertEquals(null, FlightRouteServiceImpl.parseIsoToUtc(null))
    }

    @Test
    fun `parseIsoToUtc returns null for invalid string`() {
        assertEquals(null, FlightRouteServiceImpl.parseIsoToUtc("not-a-date"))
    }

    // -- Helpers --

    private fun flightAwareFlight(
        originIata: String,
        destIata: String,
        scheduledOut: String? = null,
        scheduledIn: String? = null
    ) = FlightAwareFlight(
        identIata = null,
        status = null,
        departureDelay = null,
        arrivalDelay = null,
        origin = FlightAwareAirport(codeIata = originIata, timezone = "America/Los_Angeles"),
        destination = FlightAwareAirport(codeIata = destIata, timezone = "Asia/Shanghai"),
        scheduledOut = scheduledOut,
        estimatedOut = null,
        actualOut = null,
        scheduledIn = scheduledIn,
        estimatedIn = null,
        actualIn = null,
        gateOrigin = null,
        gateDestination = null,
        aircraftType = "B77W",
        registration = "N12345"
    )
}

/**
 * Fake implementation of [FlightAwareApi] for testing.
 * Configurable via [onGetFlights] lambda.
 */
class FakeFlightAwareApi : FlightAwareApi {

    var onGetFlights: (
        ident: String, start: String?, end: String?, maxPages: Int
    ) -> Response<FlightAwareFlightsResponse> = { _, _, _, _ ->
        Response.success(FlightAwareFlightsResponse(flights = emptyList()))
    }

    override suspend fun getFlights(
        ident: String,
        start: String?,
        end: String?,
        maxPages: Int
    ): Response<FlightAwareFlightsResponse> {
        return onGetFlights(ident, start, end, maxPages)
    }

    override suspend fun getPosition(ident: String): Response<FlightAwarePositionResponse> {
        return Response.success(FlightAwarePositionResponse(lastPosition = null))
    }
}
