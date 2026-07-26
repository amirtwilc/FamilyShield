package com.familyshield.mobile.parent

import com.familyshield.mobile.net.HistoryPoint
import com.familyshield.mobile.net.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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

    private fun point(lat: Double, lng: Double, recordedAt: String) =
        HistoryPoint(lat = lat, lng = lng, recordedAt = recordedAt)
}
