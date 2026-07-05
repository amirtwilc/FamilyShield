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

    private fun viewModel(api: ApiClient, store: InMemoryTokenStore) =
        KidViewModel(api, store, mainRule.dispatcher)

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
    fun `pairing as kid clears parent session tokens`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val (_, _, code) = seedCode(api)
        val store = InMemoryTokenStore(parentToken = "parent-access", parentRefreshToken = "parent-refresh")
        val vm = viewModel(api, store)

        vm.pair(code, "android")
        advanceUntilIdle()

        assertNotNull(vm.deviceToken)
        assertNull(store.parentToken)
        assertNull(store.parentRefreshToken)
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
    fun `removed child clears stale kid pairing on refresh`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val (parentToken, childId, code) = seedCode(api)
        val vm = viewModel(api)
        vm.pair(code, "android")
        advanceUntilIdle()
        assertNotNull(vm.deviceToken)

        api.deleteChild(parentToken, childId)
        vm.refreshMonitoring()
        advanceUntilIdle()

        assertNull(vm.deviceToken)
        assertEquals("This device is no longer paired.", vm.message)
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
    fun `kid telemetry starts empty until Android provides real readings`() = runTest(mainRule.dispatcher) {
        val vm = viewModel(FakeApiClient())

        assertNull(vm.lat)
        assertNull(vm.lng)
        assertNull(vm.battery)
        assertNull(vm.charging)
    }
}
