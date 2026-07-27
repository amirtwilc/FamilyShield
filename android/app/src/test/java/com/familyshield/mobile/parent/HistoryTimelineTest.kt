package com.familyshield.mobile.parent

import com.familyshield.mobile.net.HistoryPoint
import com.familyshield.mobile.net.FrequentRoute
import com.familyshield.mobile.net.Geo
import com.familyshield.mobile.net.RoutePoint
import com.familyshield.mobile.net.RouteTrip
import com.familyshield.mobile.net.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HistoryTimelineTest {

    @Test
    fun `adjacent nearby history points are grouped into one stay`() {
        val stays = groupHistoryStays(
            points = listOf(
                point(32.00000, 34.00000, "2026-07-26T08:00:00Z"),
                point(32.00015, 34.00010, "2026-07-26T09:30:00Z"),
                point(32.00020, 34.00020, "2026-07-26T12:00:00Z"),
            ),
            zones = listOf(Zone("zone-home", "Home", 32.0001, 34.0001, radiusM = 100)),
        )

        assertEquals(1, stays.size)
        assertEquals("2026-07-26T08:00:00Z", stays.single().startAt)
        assertEquals("2026-07-26T12:00:00Z", stays.single().endAt)
        assertEquals(3, stays.single().pointCount)
        assertEquals("Home", stays.single().zoneName)
    }

    @Test
    fun `only adjacent similar locations are grouped`() {
        val stays = groupHistoryStays(
            points = listOf(
                point(32.0000, 34.0000, "2026-07-26T08:00:00Z"),
                point(32.0100, 34.0100, "2026-07-26T09:00:00Z"),
                point(32.0001, 34.0001, "2026-07-26T10:00:00Z"),
            ),
            zones = emptyList(),
        )

        assertEquals(3, stays.size)
        assertEquals("2026-07-26T10:00:00Z", stays[0].startAt)
        assertEquals("2026-07-26T09:00:00Z", stays[1].startAt)
        assertEquals("2026-07-26T08:00:00Z", stays[2].startAt)
        assertNull(stays[0].zoneName)
        assertNull(stays[1].zoneName)
        assertNull(stays[2].zoneName)
    }

    @Test
    fun `the similar location radius can be tuned`() {
        val points = listOf(
            point(32.0000, 34.0000, "2026-07-26T08:00:00Z"),
            point(32.0007, 34.0000, "2026-07-26T09:00:00Z"),
        )

        assertEquals(2, groupHistoryStays(points, emptyList(), similarLocationRadiusM = 50.0).size)
        assertEquals(1, groupHistoryStays(points, emptyList(), similarLocationRadiusM = 100.0).size)
    }

    @Test
    fun `route filter shows only matching route activities`() {
        val route = FrequentRoute(Geo(32.0, 34.0), Geo(32.02, 34.02), count = 2,
            lastAt = "2026-07-26T09:20:00Z", avgMinutes = 20.0, avgKm = 2.8)
        val activities = buildHistoryActivities(
            points = listOf(
                point(32.0, 34.0, "2026-07-26T08:00:00Z"),
                point(32.02, 34.02, "2026-07-26T09:20:00Z"),
            ),
            trips = listOf(
                trip(Geo(32.02, 34.02), Geo(32.0, 34.0), "2026-07-26T09:00:00Z"),
                trip(Geo(33.0, 35.0), Geo(33.02, 35.02), "2026-07-26T10:00:00Z"),
            ),
            zones = emptyList(),
            selectedDate = "2026-07-26",
            selectedRoute = route,
        )

        assertEquals(1, activities.size)
        assertEquals("2026-07-26T09:00:00Z", activities.single().startAt)
    }

    @Test
    fun `history switcher includes last seven days and older data within the fourteen day cap`() {
        val days = historySwitcherDays(
            today = LocalDate.of(2026, 7, 27),
            historyDays = listOf("2026-07-17", "2026-07-10"),
        )

        assertEquals(LocalDate.of(2026, 7, 17), days.first())
        assertEquals(LocalDate.of(2026, 7, 27), days.last())
        assertEquals(11, days.size)
    }

    @Test
    fun `route occurrence days match either direction`() {
        val route = FrequentRoute(Geo(32.0, 34.0), Geo(32.02, 34.02), count = 2,
            lastAt = "2026-07-26T09:20:00Z", avgMinutes = 20.0, avgKm = 2.8)

        assertEquals(setOf("2026-07-26"), routeOccurrenceDays(listOf(trip(Geo(32.02, 34.02), Geo(32.0, 34.0), "2026-07-26T09:00:00Z")), route))
    }

    private fun point(lat: Double, lng: Double, recordedAt: String) =
        HistoryPoint(lat = lat, lng = lng, recordedAt = recordedAt)

    private fun trip(from: Geo, to: Geo, departAt: String): RouteTrip {
        val arriveAt = departAt.replace("09:00", "09:20").replace("10:00", "10:20")
        return RouteTrip(
            from = from,
            to = to,
            departAt = departAt,
            arriveAt = arriveAt,
            durationMin = 20.0,
            distanceKm = 2.8,
            points = listOf(RoutePoint(from.lat, from.lng, departAt), RoutePoint(to.lat, to.lng, arriveAt)),
        )
    }
}
