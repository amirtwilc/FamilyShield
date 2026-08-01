package com.familyshield.mobile.parent

import com.familyshield.mobile.net.CurrentLocation
import com.familyshield.mobile.net.MovementMode
import com.familyshield.mobile.net.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardStatusTest {
    private val location = CurrentLocation(
        lat = 32.0853,
        lng = 34.7818,
        recordedAt = "2026-07-29T10:00:00Z",
    )

    @Test
    fun `returns active zone containing the current location`() {
        val zones = listOf(
            Zone("school", "School", 32.0853, 34.7818, radiusM = 100),
        )

        assertEquals("School", currentActiveZoneName(location, zones))
    }

    @Test
    fun `ignores inactive and out of range zones`() {
        val zones = listOf(
            Zone("inactive", "Home", 32.0853, 34.7818, radiusM = 100, active = false),
            Zone("far", "School", 32.1000, 34.8000, radiusM = 100),
        )

        assertNull(currentActiveZoneName(location, zones))
    }

    @Test
    fun `shows movement only while the child is online and moving`() {
        val walking = location.copy(movementMode = MovementMode.Walking)
        val stationary = location.copy(movementMode = MovementMode.Stationary)

        assertEquals(MovementMode.Walking, dashboardMovementMode(isOnline = true, walking))
        assertNull(dashboardMovementMode(isOnline = false, walking))
        assertNull(dashboardMovementMode(isOnline = true, stationary))
    }
}
