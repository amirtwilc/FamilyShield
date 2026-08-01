package com.familyshield.mobile.kid

import com.familyshield.mobile.fakes.FakeApiClient
import com.familyshield.mobile.fakes.InMemoryTokenStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class KidSimulationAuthorizationTest {
    private val context = RuntimeEnvironment.getApplication()

    @After
    fun cleanUp() {
        LocationSimulationStore(context).stop()
    }

    @Test
    fun `start requires an admin and resume rechecks current parent tiers`() = runTest {
        val api = FakeApiClient()
        val parentToken = api.register("admin@test.io", "password1").accessToken
        val child = api.createChild(parentToken, "Mia")
        val code = api.pairingCode(parentToken, child.id).code
        val pairing = api.pair(code, "android", "Pixel", "Mom")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = KidViewModel(api, InMemoryTokenStore(pairing.deviceToken), dispatcher) {}
        advanceUntilIdle()

        vm.startFixedSimulation(context, SimulationWaypoint(32.0, 34.0))
        advanceUntilIdle()
        assertNull(LocationSimulationStore(context).activeConfig())

        api.setParentTier("admin@test.io", "admin")
        vm.startFixedSimulation(context, SimulationWaypoint(32.0, 34.0))
        advanceUntilIdle()
        assertNotNull(LocationSimulationStore(context).activeConfig())

        vm.pauseSimulation(context)
        api.setParentTier("admin@test.io", "free")
        vm.resumeSimulation(context)
        advanceUntilIdle()

        assertNull(LocationSimulationStore(context).activeConfig())
    }
}
