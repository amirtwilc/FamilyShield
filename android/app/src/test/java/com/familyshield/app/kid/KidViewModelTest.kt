package com.familyshield.app.kid

import com.familyshield.app.MainDispatcherRule
import com.familyshield.app.fakes.FakeApiClient
import com.familyshield.app.fakes.InMemoryTokenStore
import com.familyshield.app.net.ApiClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KidViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun viewModel(api: ApiClient) =
        KidViewModel(api, InMemoryTokenStore(), mainRule.dispatcher)

    /** Seeds a parent + child + pairing code and returns (api, parentToken, childId, code). */
    private suspend fun seedCode(api: FakeApiClient): Triple<String, String, String> {
        val token = api.register("parent@x.com", "pw123456").accessToken
        val child = api.createChild(token, "Mia")
        val code = api.pairingCode(token, child.id).code
        return Triple(token, child.id, code)
    }

    @Test
    fun `pairing with a valid code stores a device token`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val (_, _, code) = seedCode(api)
        val vm = viewModel(api)

        vm.pair(code, "android")
        advanceUntilIdle()

        assertNotNull("device token should be set after pairing", vm.deviceToken)
        assertNull(vm.error)
        assertEquals(listOf("parent@x.com"), vm.monitors.map { it.email })
    }

    @Test
    fun `pairing with an invalid code surfaces an error`() = runTest(mainRule.dispatcher) {
        val vm = viewModel(FakeApiClient())

        vm.pair("000000", "android")
        advanceUntilIdle()

        assertNull(vm.deviceToken)
        assertNotNull(vm.error)
    }

    @Test
    fun `adding another parent keeps the same device token and updates monitors`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val (_, _, code) = seedCode(api)
        val vm = viewModel(api)
        vm.pair(code, "android")
        advanceUntilIdle()
        val originalDeviceToken = vm.deviceToken

        val secondParentToken = api.register("other@x.com", "pw123456").accessToken
        val placeholder = api.createChild(secondParentToken, "Mimi")
        val secondCode = api.pairingCode(secondParentToken, placeholder.id).code

        vm.addParent(secondCode, "android")
        advanceUntilIdle()

        assertEquals(originalDeviceToken, vm.deviceToken)
        assertEquals(listOf("parent@x.com", "other@x.com"), vm.monitors.map { it.email })
        assertEquals("Parent added.", vm.message)
    }

    @Test
    fun `removing one monitor keeps device paired until last monitor is removed`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val (_, _, code) = seedCode(api)
        val vm = viewModel(api)
        vm.pair(code, "android")
        advanceUntilIdle()

        val secondParentToken = api.register("other-remove@x.com", "pw123456").accessToken
        val placeholder = api.createChild(secondParentToken, "Mimi")
        vm.addParent(api.pairingCode(secondParentToken, placeholder.id).code, "android")
        advanceUntilIdle()
        val originalDeviceToken = vm.deviceToken

        vm.removeMonitor(vm.monitors.first { it.email == "other-remove@x.com" }.parentId)
        advanceUntilIdle()
        assertEquals(originalDeviceToken, vm.deviceToken)
        assertEquals(listOf("parent@x.com"), vm.monitors.map { it.email })

        vm.removeMonitor(vm.monitors.single().parentId)
        advanceUntilIdle()
        assertNull(vm.deviceToken)
        assertEquals(emptyList<String>(), vm.monitors.map { it.email })
    }

    @Test
    fun `chat can target a specific monitoring parent`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val (_, childId, code) = seedCode(api)
        val vm = viewModel(api)
        vm.pair(code, "android")
        advanceUntilIdle()

        val secondParentToken = api.register("chat-other@x.com", "pw123456").accessToken
        val placeholder = api.createChild(secondParentToken, "Mimi")
        vm.addParent(api.pairingCode(secondParentToken, placeholder.id).code, "android")
        advanceUntilIdle()
        api.sendMessage(secondParentToken, childId, "Hi from second")

        val monitor = vm.monitors.first { it.email == "chat-other@x.com" }
        vm.startChat(monitor.parentId)
        runCurrent()
        assertEquals(listOf("Hi from second"), vm.chatMessages.map { it.body })

        vm.sendChat("Hi back")
        runCurrent()
        assertEquals(listOf("Hi from second", "Hi back"), vm.chatMessages.map { it.body })
        vm.stopChat()
    }

    @Test
    fun `sending a GPS location reaches the backend`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val (parentToken, childId, code) = seedCode(api)
        val vm = viewModel(api)
        vm.pair(code, "android")
        advanceUntilIdle()

        vm.setPosition(32.1000, 34.9000)
        vm.battery = 55
        vm.sendLocation()
        advanceUntilIdle()

        assertEquals("Location sent.", vm.message)
        val stored = api.currentLocation(parentToken, childId)
        assertNotNull("the backend should now have the location", stored)
        assertEquals(32.1000, stored!!.lat, 1e-4)
        assertEquals(34.9000, stored.lng, 1e-4)
    }

    @Test
    fun `sending status updates the device battery and charging on the backend`() =
        runTest(mainRule.dispatcher) {
            val api = FakeApiClient()
            val (parentToken, childId, code) = seedCode(api)
            val vm = viewModel(api)
            vm.pair(code, "android")
            advanceUntilIdle()

            vm.battery = 42
            vm.charging = true
            vm.sendStatus()
            advanceUntilIdle()

            assertEquals("Status sent.", vm.message)
            val device = api.listChildren(parentToken).first { it.id == childId }.devices.first()
            assertEquals(42, device.batteryLevel)
            assertEquals(true, device.isCharging)
        }

    @Test
    fun `setPosition updates the reported coordinates`() = runTest(mainRule.dispatcher) {
        val vm = viewModel(FakeApiClient())

        vm.setPosition(40.0, -73.0)

        assertEquals(40.0, vm.lat, 1e-9)
        assertEquals(-73.0, vm.lng, 1e-9)
    }
}
