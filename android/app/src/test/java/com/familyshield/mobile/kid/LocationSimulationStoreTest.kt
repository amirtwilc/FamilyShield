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
    fun `active route survives restart and speed changes preserve its exact current position`() {
        val start = SimulationWaypoint(32.0, 34.0)
        val destination = SimulationWaypoint(32.0, 34.01)
        store.start(start, nowMs = 1_000L)
        store.selectMode(LocationSimulationMode.Route, nowMs = 2_000L)
        val route = store.setRouteDestination(destination, nowMs = 2_000L)!!
        val beforeSpeedChange = LocationSimulationEngine.sample(route, nowMs = 12_000L)!!
        store.setSpeed(
            speedMps = 12.0,
            speedMode = LocationSimulationSpeedMode.Driving,
            nowMs = 12_000L,
        )

        val restored = LocationSimulationStore(context).activeConfig()
        assertNotNull(restored)
        assertEquals(LocationSimulationMode.Route, restored!!.mode)
        assertEquals(beforeSpeedChange.lat, restored.waypoints.first().lat, 0.0000001)
        assertEquals(beforeSpeedChange.lng, restored.waypoints.first().lng, 0.0000001)
        assertEquals(destination, restored.waypoints.last())
        assertEquals(12.0, restored.speedMps, 0.0)
        assertEquals(LocationSimulationSpeedMode.Driving, restored.resolvedSpeedMode)
    }

    @Test
    fun `fixed map tap moves immediately and stop clears the persisted simulation`() {
        store.start(SimulationWaypoint(32.0, 34.0), nowMs = 1_000L)
        val moved = store.moveFixed(SimulationWaypoint(33.0, 35.0), nowMs = 2_000L)
        assertEquals(SimulationWaypoint(33.0, 35.0), moved!!.waypoints.single())
        store.stop()

        assertNull(LocationSimulationStore(context).activeConfig())
    }

    @Test
    fun `replacing a destination continues from the in-flight simulated position`() {
        val start = SimulationWaypoint(0.0, 0.0)
        val firstDestination = SimulationWaypoint(0.0, 0.01)
        val nextDestination = SimulationWaypoint(0.01, 0.01)
        store.start(start, nowMs = 0L)
        store.selectMode(LocationSimulationMode.Route, nowMs = 0L)
        val firstRoute = store.setRouteDestination(firstDestination, nowMs = 0L)!!
        val beforeChange = LocationSimulationEngine.sample(firstRoute, nowMs = 10_000L)!!

        val changed = store.setRouteDestination(nextDestination, nowMs = 10_000L)!!

        assertEquals(beforeChange.lat, changed.waypoints.first().lat, 0.0000001)
        assertEquals(beforeChange.lng, changed.waypoints.first().lng, 0.0000001)
        assertEquals(nextDestination, changed.waypoints.last())
    }

    @Test
    fun `custom speed selection survives restart even when it equals walking speed`() {
        store.start(SimulationWaypoint(32.0, 34.0), nowMs = 1_000L)
        store.setSpeed(
            speedMps = 1.4,
            speedMode = LocationSimulationSpeedMode.Custom,
            nowMs = 2_000L,
        )

        val restored = LocationSimulationStore(context).activeConfig()!!

        assertEquals(1.4, restored.speedMps, 0.0)
        assertEquals(LocationSimulationSpeedMode.Custom, restored.resolvedSpeedMode)
    }
}
