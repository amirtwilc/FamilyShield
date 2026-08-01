package com.familyshield.mobile.kid

import com.familyshield.mobile.net.Monitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSimulationEngineTest {
    @Test
    fun `fixed simulation stays at its point with stationary speed`() {
        val config = LocationSimulationConfig(
            mode = LocationSimulationMode.Fixed,
            waypoints = listOf(SimulationWaypoint(32.0853, 34.7818)),
            startedAtMs = 1_000L,
        )

        val sample = LocationSimulationEngine.sample(config, nowMs = 61_000L, batteryLevel = 80)!!

        assertEquals(32.0853, sample.lat, 0.0)
        assertEquals(34.7818, sample.lng, 0.0)
        assertEquals(0.0, sample.speed!!, 0.0)
        assertEquals(80, sample.batteryLevel)
    }

    @Test
    fun `route simulation interpolates by configured speed`() {
        val from = SimulationWaypoint(0.0, 0.0)
        val to = SimulationWaypoint(0.0, 0.001)
        val distance = LocationSimulationEngine.distanceM(from, to)
        val config = LocationSimulationConfig(
            mode = LocationSimulationMode.Route,
            waypoints = listOf(from, to),
            speedMps = 1.0,
            startedAtMs = 0L,
        )

        val halfway = LocationSimulationEngine.sample(config, nowMs = (distance * 500).toLong())!!
        val arrived = LocationSimulationEngine.sample(config, nowMs = (distance * 1_000 + 1_000).toLong())!!

        assertEquals(0.0005, halfway.lng, 0.00002)
        assertEquals(1.0, halfway.speed!!, 0.0)
        assertEquals(to.lng, arrived.lng, 0.0)
        assertEquals(0.0, arrived.speed!!, 0.0)
    }

    @Test
    fun `completed route remains at its destination with stationary speed`() {
        val destination = SimulationWaypoint(0.0, 0.001)
        val config = LocationSimulationConfig(
            mode = LocationSimulationMode.Route,
            waypoints = listOf(SimulationWaypoint(0.0, 0.0), destination),
            speedMps = 10.0,
            startedAtMs = 0L,
        )

        val arrived = LocationSimulationEngine.sample(config, nowMs = 120_000L)!!
        val later = LocationSimulationEngine.sample(config, nowMs = 220_000L)!!

        assertEquals(destination.lng, arrived.lng, 0.0)
        assertEquals(arrived.lng, later.lng, 0.0)
        assertEquals(0.0, later.speed!!, 0.0)
    }

    @Test
    fun `any linked admin monitor grants simulation`() {
        val free = Monitor("p1", "free@test.io", "Mia")
        val admin = Monitor("p2", "admin@test.io", "Mia", tierCode = "admin")

        assertFalse(listOf(free).allowsLocationSimulation())
        assertTrue(listOf(free, admin).allowsLocationSimulation())
    }
}
