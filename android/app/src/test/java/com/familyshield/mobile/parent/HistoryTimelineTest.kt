package com.familyshield.mobile.parent

import com.familyshield.mobile.net.HistoryPoint
import com.familyshield.mobile.net.FrequentRoute
import com.familyshield.mobile.net.Geo
import com.familyshield.mobile.net.RoutePoint
import com.familyshield.mobile.net.RouteTrip
import com.familyshield.mobile.net.Stop
import com.familyshield.mobile.net.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

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
        assertEquals(listOf(Geo(32.0, 34.0), Geo(32.00015, 34.0001), Geo(32.0002, 34.0002)), stays.single().nameCandidates)
    }

    @Test
    fun `only adjacent similar locations are grouped`() {
        val stays = groupHistoryStays(
            points = listOf(
                point(32.0000, 34.0000, "2026-07-26T08:00:00Z"),
                point(32.0100, 34.0100, "2026-07-26T09:00:00Z"),
                point(32.0101, 34.0101, "2026-07-26T09:06:00Z"),
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
    fun `isolated noisy or invalid points do not split a long stay`() {
        val gym = Geo(50.934953, 6.974577)
        val activities = buildHistoryActivities(
            points = listOf(
                point(32.0000, 34.0000, "2026-07-27T17:00:00Z"),
                point(32.0000, 34.0000, "2026-07-27T17:10:00Z"),
                point(gym.lat, gym.lng, "2026-07-27T17:24:00Z"),
                point(gym.lat + 0.002, gym.lng, "2026-07-27T17:38:00Z"),
                point(gym.lat, gym.lng, "2026-07-27T17:52:00Z"),
                point(gym.lat, gym.lng, "2026-07-27T18:02:00Z"),
                point(1.135e45, gym.lng, "2026-07-27T18:05:00Z"),
                point(gym.lat, gym.lng, "2026-07-27T18:14:00Z"),
                point(32.0000, 34.0000, "2026-07-27T18:30:00Z"),
                point(32.0000, 34.0000, "2026-07-27T18:40:00Z"),
            ),
            trips = emptyList(),
            zones = emptyList(),
            selectedDate = "2026-07-27",
        )

        val gymStay = activities
            .filterIsInstance<HistoryStay>()
            .single { abs(it.lat - gym.lat) < 0.001 }

        assertEquals("2026-07-27T17:24:00Z", gymStay.startAt)
        assertEquals("2026-07-27T18:14:00Z", gymStay.endAt)
        assertTrue(activities.filterIsInstance<HistoryMovementActivity>().all { movement ->
            movement.points.none { it.lat > 90.0 }
        })
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
    fun `route filter uses exact backend occurrences instead of nearby partial trips`() {
        val direct = trip(Geo(32.0, 34.0), Geo(32.02, 34.02), "2026-07-26T09:00:00Z")
        val partial = trip(Geo(32.0, 34.0), Geo(32.01995, 34.01995), "2026-07-26T10:00:00Z")
        val route = FrequentRoute(
            Geo(32.0, 34.0),
            Geo(32.02, 34.02),
            count = 2,
            lastAt = direct.arriveAt,
            avgMinutes = 20.0,
            avgKm = 2.8,
            occurrenceKeys = listOf("${direct.departAt}|${direct.arriveAt}"),
        )

        val activities = buildHistoryActivities(
            points = emptyList(),
            trips = listOf(direct, partial),
            zones = emptyList(),
            selectedDate = "2026-07-26",
            selectedRoute = route,
        )

        assertEquals(1, activities.size)
        assertEquals(direct.departAt, activities.single().startAt)
    }

    @Test
    fun `daily timeline uses backend stops as stay rows`() {
        val home = Geo(50.93009, 6.94149)
        val gym = Geo(50.934953, 6.974577)
        val activities = buildHistoryActivities(
            points = listOf(
                point(home.lat, home.lng, "2026-07-27T17:05:00Z"),
                point(gym.lat, gym.lng, "2026-07-27T17:24:00Z"),
                point(gym.lat + 0.002, gym.lng, "2026-07-27T17:38:00Z"),
                point(gym.lat, gym.lng, "2026-07-27T18:14:00Z"),
                point(home.lat, home.lng, "2026-07-27T19:20:00Z"),
            ),
            trips = listOf(
                trip(home, gym, "2026-07-27T17:05:00Z"),
                trip(gym, home, "2026-07-27T19:05:00Z"),
            ),
            stops = listOf(
                stop(home, "2026-07-27T16:00:00Z", "2026-07-27T17:05:00Z"),
                stop(gym, "2026-07-27T17:24:00Z", "2026-07-27T19:05:00Z"),
                stop(home, "2026-07-27T19:20:00Z", "2026-07-27T20:06:00Z"),
            ),
            zones = listOf(Zone("home", "Home", home.lat, home.lng, radiusM = 100)),
            selectedDate = "2026-07-27",
        )

        val stays = activities.filterIsInstance<HistoryStay>()

        assertEquals(3, stays.size)
        assertTrue(stays.any { it.zoneName == "Home" && it.startAt == "2026-07-27T19:20:00Z" && it.endAt == "2026-07-27T20:06:00Z" })
        assertTrue(stays.any { abs(it.lat - gym.lat) < 0.001 && it.startAt == "2026-07-27T17:24:00Z" && it.endAt == "2026-07-27T19:05:00Z" })
        assertEquals(2, activities.count { it is HistoryRouteActivity })
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

    @Test
    fun `walk loop without a five minute pause becomes movement but not a filtered route`() {
        val points = listOf(
            point(32.0000, 34.0000, "2026-07-26T08:00:00Z"),
            point(32.0001, 34.0001, "2026-07-26T08:06:00Z"),
            point(32.0020, 34.0020, "2026-07-26T08:08:00Z"),
            point(32.0040, 34.0040, "2026-07-26T08:10:00Z"),
            point(32.0020, 34.0020, "2026-07-26T08:12:00Z"),
            point(32.0001, 34.0001, "2026-07-26T08:15:00Z"),
            point(32.0000, 34.0000, "2026-07-26T08:21:00Z"),
        )

        val activities = buildHistoryActivities(points, trips = emptyList(), zones = emptyList(), selectedDate = "2026-07-26")
        val routeFilter = FrequentRoute(Geo(32.0, 34.0), Geo(32.02, 34.02), count = 2,
            lastAt = "2026-07-26T09:20:00Z", avgMinutes = 20.0, avgKm = 2.8)
        val filteredActivities = buildHistoryActivities(points, trips = emptyList(), zones = emptyList(), selectedDate = "2026-07-26", selectedRoute = routeFilter)

        assertEquals(3, activities.size)
        assertEquals(1, activities.count { it is HistoryMovementActivity })
        assertTrue((activities.first { it is HistoryMovementActivity } as HistoryMovementActivity).points.size >= 3)
        assertEquals(emptyList<HistoryActivity>(), filteredActivities)
    }

    private fun point(lat: Double, lng: Double, recordedAt: String) =
        HistoryPoint(lat = lat, lng = lng, recordedAt = recordedAt)

    private fun trip(from: Geo, to: Geo, departAt: String): RouteTrip {
        val arriveAt = departAt
            .replace("09:00", "09:20")
            .replace("10:00", "10:20")
            .replace("17:05", "17:24")
            .replace("19:05", "19:20")
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

    private fun stop(place: Geo, arriveAt: String, departAt: String): Stop =
        Stop(place.lat, place.lng, arriveAt, departAt, dwellMin = 0.0)
}
