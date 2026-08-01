package com.familyshield.mobile.kid

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LocationSimulationStoreTest {
    private val context = RuntimeEnvironment.getApplication()
    private val store = LocationSimulationStore(context)

    @After
    fun cleanUp() {
        store.stop()
    }

    @Test
    fun `active route survives a new store instance and resumes after pause`() {
        val waypoints = listOf(SimulationWaypoint(32.0, 34.0), SimulationWaypoint(32.1, 34.1))
        store.startRoute(waypoints, speedMps = 12.0, dwellMinutes = 5, loop = true, nowMs = 1_000L)
        store.pause(nowMs = 11_000L)

        val restored = LocationSimulationStore(context).activeConfig()
        assertNotNull(restored)
        assertEquals(waypoints, restored!!.waypoints)
        assertEquals(true, restored.isPaused)

        val resumed = LocationSimulationStore(context).resume(nowMs = 21_000L)
        assertEquals(false, resumed!!.isPaused)
        assertEquals(10_000L, resumed.accumulatedPausedMs)
    }

    @Test
    fun `stop clears the persisted simulation`() {
        store.startFixed(SimulationWaypoint(32.0, 34.0), nowMs = 1_000L)
        store.stop()

        assertNull(LocationSimulationStore(context).activeConfig())
    }
}
