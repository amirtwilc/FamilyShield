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
    fun `start requires an admin and rechecking start removes simulation after downgrade`() = runTest {
        val api = FakeApiClient()
        val parentToken = api.register("admin@test.io", "password1").accessToken
        val child = api.createChild(parentToken, "Mia")
        val code = api.pairingCode(parentToken, child.id).code
        val pairing = api.pair(code, "android", "Pixel", "Mom")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = KidViewModel(
            api = api,
            store = InMemoryTokenStore(pairing.deviceToken),
            dispatcher = dispatcher,
            onSimulationChanged = { _, _ -> },
            currentLocationProvider = { SimulationWaypoint(32.0, 34.0) },
        )
        advanceUntilIdle()

        vm.startSimulation(context)
        advanceUntilIdle()
        assertNull(LocationSimulationStore(context).activeConfig())

        api.setParentTier("admin@test.io", "admin")
        vm.startSimulation(context)
        advanceUntilIdle()
        assertNotNull(LocationSimulationStore(context).activeConfig())

        api.setParentTier("admin@test.io", "free")
        vm.startSimulation(context)
        advanceUntilIdle()

        assertNull(LocationSimulationStore(context).activeConfig())
    }
}
