package com.familyshield.mobile.kid

import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidTelemetryTest {
    @Test
    fun `freshest usable location ignores stale and invalid provider results`() {
        val nowMs = 1_000_000L
        val stale = location("fused", 32.0, 34.0, nowMs - LOCATION_STALE_AFTER_MS - 1)
        val olderFresh = location("network", 32.1, 34.1, nowMs - 10_000L)
        val newestFresh = location("gps", 32.2, 34.2, nowMs - 1_000L)
        val invalid = location("gps", 100.0, 34.3, nowMs)

        val selected = freshestUsableLocation(
            listOf(stale, olderFresh, newestFresh, invalid),
            nowMs,
            LOCATION_STALE_AFTER_MS,
        )

        assertEquals(newestFresh, selected)
    }

    @Test
    fun `freshest usable location returns null when every provider result is stale`() {
        val nowMs = 1_000_000L
        val stale = location("network", 32.0, 34.0, nowMs - LOCATION_STALE_AFTER_MS - 1)

        assertNull(freshestUsableLocation(listOf(stale), nowMs, LOCATION_STALE_AFTER_MS))
    }

    private fun location(provider: String, lat: Double, lng: Double, timeMs: Long) =
        Location(provider).apply {
            latitude = lat
            longitude = lng
            time = timeMs
        }
}
