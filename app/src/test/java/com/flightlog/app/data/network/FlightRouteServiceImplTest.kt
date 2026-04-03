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

    private val service = FlightRouteServiceImpl(FakeFlightAwareApi(), FakeAviationStackApi())

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

    // -- selectApi routing tests --

    @Test
    fun `selectApi returns FLIGHTAWARE for today`() {
        val today = LocalDate.now(ZoneOffset.UTC)
        assertEquals(FlightRouteServiceImpl.ApiSource.FLIGHTAWARE, service.selectApi(today))
    }

    @Test
    fun `selectApi returns FLIGHTAWARE for today plus 7`() {
        val date = LocalDate.now(ZoneOffset.UTC).plusDays(7)
        assertEquals(FlightRouteServiceImpl.ApiSource.FLIGHTAWARE, service.selectApi(date))
    }

    @Test
    fun `selectApi returns AVIATION_STACK for today plus 8`() {
        val date = LocalDate.now(ZoneOffset.UTC).plusDays(8)
        assertEquals(FlightRouteServiceImpl.ApiSource.AVIATION_STACK, service.selectApi(date))
    }

    @Test
    fun `selectApi returns FLIGHTAWARE for past dates`() {
        val date = LocalDate.now(ZoneOffset.UTC).minusDays(30)
        assertEquals(FlightRouteServiceImpl.ApiSource.FLIGHTAWARE, service.selectApi(date))
    }

    // -- FlightAware integration tests --

    @Test
    fun `lookupAllRoutes returns results for future date via undated fallback`() = runTest {
        val targetDate = LocalDate.now(ZoneOffset.UTC).plusDays(3)

        val fakeApi = FakeFlightAwareApi()
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

        val svc = FlightRouteServiceImpl(fakeApi, FakeAviationStackApi())
        val routes = svc.lookupAllRoutes("UA882", targetDate)
        assertEquals(1, routes.size)
        assertEquals("SFO", routes[0].departureIata)
        assertEquals("PVG", routes[0].arrivalIata)
    }

    @Test
    fun `lookupAllRoutes uses dated query for old flights`() = runTest {
        val targetDate = LocalDate.now(ZoneOffset.UTC).minusDays(30)

        val fakeApi = FakeFlightAwareApi()
        fakeApi.onGetFlights = { _, start, _, _ ->
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

        val svc = FlightRouteServiceImpl(fakeApi, FakeAviationStackApi())
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

        val svc = FlightRouteServiceImpl(fakeApi, FakeAviationStackApi())
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

        val svc = FlightRouteServiceImpl(fakeApi, FakeAviationStackApi())
        val routes = svc.lookupAllRoutes("UA882", targetDate)
        assertEquals(1, routes.size)
    }

    // -- AviationStack integration tests --

    @Test
    fun `lookupAllRoutes uses AviationStack for date beyond 7 days`() = runTest {
        val targetDate = LocalDate.now(ZoneOffset.UTC).plusDays(15)

        val fakeAvStack = FakeAviationStackApi()
        fakeAvStack.onGetScheduledFlights = { _, _, _, _, _ ->
            Response.success(
                AviationStackResponse(
                    data = listOf(
                        aviationStackFlight(
                            flightIata = "NH211",
                            depIata = "NRT",
                            arrIata = "SFO",
                            depScheduled = targetDate.atStartOfDay()
                                .atOffset(ZoneOffset.UTC).toString(),
                            arrScheduled = targetDate.atTime(10, 0)
                                .atOffset(ZoneOffset.UTC).toString()
                        )
                    )
                )
            )
        }

        val svc = FlightRouteServiceImpl(FakeFlightAwareApi(), fakeAvStack)
        val routes = svc.lookupAllRoutes("NH211", targetDate, "NRT")
        assertEquals(1, routes.size)
        assertEquals("NRT", routes[0].departureIata)
        assertEquals("SFO", routes[0].arrivalIata)
    }

    @Test
    fun `lookupAllRoutes skips AviationStack when departure airport is null`() = runTest {
        val targetDate = LocalDate.now(ZoneOffset.UTC).plusDays(15)

        val fakeAvStack = FakeAviationStackApi()
        var aviationStackCalled = false
        fakeAvStack.onGetScheduledFlights = { _, _, _, _, _ ->
            aviationStackCalled = true
            Response.success(AviationStackResponse(data = emptyList()))
        }

        val svc = FlightRouteServiceImpl(FakeFlightAwareApi(), fakeAvStack)
        val routes = svc.lookupAllRoutes("NH211", targetDate, null)
        assertTrue(routes.isEmpty())
        // AviationStack was the primary, but skipped due to null departure — then fallback to FlightAware
        // The fake AviationStack should not have been called
        assertTrue(!aviationStackCalled)
    }

    @Test
    fun `lookupAllRoutes handles AviationStack 429 rate limit gracefully`() = runTest {
        val targetDate = LocalDate.now(ZoneOffset.UTC).plusDays(15)

        val fakeAvStack = FakeAviationStackApi()
        fakeAvStack.onGetScheduledFlights = { _, _, _, _, _ ->
            Response.error(429, "Rate Limited".toResponseBody("text/plain".toMediaType()))
        }

        val svc = FlightRouteServiceImpl(FakeFlightAwareApi(), fakeAvStack)
        val routes = svc.lookupAllRoutes("NH211", targetDate, "NRT")
        assertTrue(routes.isEmpty())
    }

    @Test
    fun `lookupAllRoutes handles AviationStack 402 exhausted quota gracefully`() = runTest {
        val targetDate = LocalDate.now(ZoneOffset.UTC).plusDays(15)

        val fakeAvStack = FakeAviationStackApi()
        fakeAvStack.onGetScheduledFlights = { _, _, _, _, _ ->
            Response.error(402, "Payment Required".toResponseBody("text/plain".toMediaType()))
        }

        val svc = FlightRouteServiceImpl(FakeFlightAwareApi(), fakeAvStack)
        val routes = svc.lookupAllRoutes("NH211", targetDate, "NRT")
        assertTrue(routes.isEmpty())
    }

    @Test
    fun `AviationStack fallback to FlightAware when AviationStack returns empty`() = runTest {
        val targetDate = LocalDate.now(ZoneOffset.UTC).plusDays(15)

        val fakeAvStack = FakeAviationStackApi()
        fakeAvStack.onGetScheduledFlights = { _, _, _, _, _ ->
            Response.success(AviationStackResponse(data = emptyList()))
        }

        // FlightAware returns a flight via undated endpoint
        val fakeFlightAware = FakeFlightAwareApi()
        fakeFlightAware.onGetFlights = { _, start, _, _ ->
            if (start == null) {
                Response.success(
                    FlightAwareFlightsResponse(
                        flights = listOf(
                            flightAwareFlight(
                                originIata = "NRT",
                                destIata = "SFO",
                                scheduledOut = targetDate.atStartOfDay()
                                    .atOffset(ZoneOffset.UTC).toString()
                            )
                        )
                    )
                )
            } else {
                Response.error(400, "Bad Request".toResponseBody("text/plain".toMediaType()))
            }
        }

        val svc = FlightRouteServiceImpl(fakeFlightAware, fakeAvStack)
        val routes = svc.lookupAllRoutes("NH211", targetDate, "NRT")
        assertEquals(1, routes.size)
        assertEquals("NRT", routes[0].departureIata)
    }

    @Test
    fun `both APIs fail returns empty list`() = runTest {
        val targetDate = LocalDate.now(ZoneOffset.UTC).plusDays(15)

        val fakeFlightAware = FakeFlightAwareApi()
        fakeFlightAware.onGetFlights = { _, _, _, _ ->
            Response.error(400, "Bad Request".toResponseBody("text/plain".toMediaType()))
        }

        val fakeAvStack = FakeAviationStackApi()
        fakeAvStack.onGetScheduledFlights = { _, _, _, _, _ ->
            Response.error(500, "Server Error".toResponseBody("text/plain".toMediaType()))
        }

        val svc = FlightRouteServiceImpl(fakeFlightAware, fakeAvStack)
        val routes = svc.lookupAllRoutes("NH211", targetDate, "NRT")
        assertTrue(routes.isEmpty())
    }

    @Test
    fun `mapAviationStackResponse skips flights with null departure iata`() {
        val response = Response.success(
            AviationStackResponse(
                data = listOf(
                    aviationStackFlight(flightIata = "NH211", depIata = null, arrIata = "SFO"),
                    aviationStackFlight(flightIata = "NH211", depIata = "NRT", arrIata = "SFO")
                )
            )
        )
        val routes = service.mapAviationStackResponse(response, "NH211")
        assertEquals(1, routes.size)
        assertEquals("NRT", routes[0].departureIata)
    }

    @Test
    fun `mapAviationStackResponse skips flights with null arrival iata`() {
        val response = Response.success(
            AviationStackResponse(
                data = listOf(
                    aviationStackFlight(flightIata = "NH211", depIata = "NRT", arrIata = null)
                )
            )
        )
        val routes = service.mapAviationStackResponse(response, "NH211")
        assertTrue(routes.isEmpty())
    }

    @Test
    fun `mapAviationStackResponse returns empty for null data`() {
        val response = Response.success(AviationStackResponse(data = null))
        val routes = service.mapAviationStackResponse(response, "NH211")
        assertTrue(routes.isEmpty())
    }

    @Test
    fun `mapAviationStackResponse returns empty for empty data list`() {
        val response = Response.success(AviationStackResponse(data = emptyList()))
        val routes = service.mapAviationStackResponse(response, "NH211")
        assertTrue(routes.isEmpty())
    }

    @Test
    fun `AviationStack response registration is always null`() {
        val response = Response.success(
            AviationStackResponse(
                data = listOf(
                    aviationStackFlight(flightIata = "NH211", depIata = "NRT", arrIata = "SFO")
                )
            )
        )
        val routes = service.mapAviationStackResponse(response, "NH211")
        assertEquals(1, routes.size)
        assertEquals(null, routes[0].registration)
    }

    // -- parseIsoToUtc tests --

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

    private fun aviationStackFlight(
        flightIata: String?,
        depIata: String?,
        arrIata: String?,
        depScheduled: String? = null,
        arrScheduled: String? = null
    ) = AviationStackFlight(
        flight = AviationStackFlightCode(iata = flightIata, icao = null),
        departure = AviationStackAirport(iata = depIata, timezone = "Asia/Tokyo", scheduled = depScheduled),
        arrival = AviationStackAirport(iata = arrIata, timezone = "America/Los_Angeles", scheduled = arrScheduled),
        airline = AviationStackAirline(name = "All Nippon Airways", iata = "NH"),
        aircraft = AviationStackAircraft(modelCode = "B789", modelText = "Boeing 787-9")
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

/**
 * Fake implementation of [AviationStackApi] for testing.
 * Configurable via [onGetScheduledFlights] lambda.
 */
class FakeAviationStackApi : AviationStackApi {

    var onGetScheduledFlights: (
        accessKey: String, iataCode: String, type: String, date: String, flightIata: String?
    ) -> Response<AviationStackResponse> = { _, _, _, _, _ ->
        Response.success(AviationStackResponse(data = emptyList()))
    }

    override suspend fun getScheduledFlights(
        accessKey: String,
        iataCode: String,
        type: String,
        date: String,
        flightIata: String?
    ): Response<AviationStackResponse> {
        return onGetScheduledFlights(accessKey, iataCode, type, date, flightIata)
    }
}
