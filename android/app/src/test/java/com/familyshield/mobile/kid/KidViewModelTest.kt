package com.familyshield.mobile.kid

import com.familyshield.mobile.MainDispatcherRule
import com.familyshield.mobile.fakes.FakeApiClient
import com.familyshield.mobile.fakes.InMemoryTokenStore
import com.familyshield.mobile.net.ApiClient
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

        vm.pair(code, "android", "Mom")
        advanceUntilIdle()

        assertNotNull("device token should be set after pairing", vm.deviceToken)
        assertNull(vm.error)
        assertEquals(listOf("parent@x.com"), vm.monitors.map { it.email })
        assertEquals(listOf("Mom"), vm.monitors.map { it.childFacingName })
    }

    @Test
    fun `simulation eligibility follows any linked admin parent`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val (_, childId, code) = seedCode(api)
        val vm = viewModel(api)
        vm.pair(code, "android", "Mom")
        advanceUntilIdle()
        assertEquals(false, vm.canSimulateLocation)

        val adminToken = api.register("admin@x.com", "pw123456").accessToken
        api.setParentTier("admin@x.com", "admin")
        val placeholder = api.createChild(adminToken, "Mimi")
        vm.addParent(api.pairingCode(adminToken, placeholder.id).code, "android", "Dad")
        advanceUntilIdle()

        assertEquals(childId, api.monitoring(vm.deviceToken!!).childId)
        assertEquals(true, vm.canSimulateLocation)
        assertEquals("free", api.monitoring(vm.deviceToken!!).monitors.first { it.email == "parent@x.com" }.tierCode)
        assertEquals("admin", api.monitoring(vm.deviceToken!!).monitors.first { it.email == "admin@x.com" }.tierCode)
    }

    @Test
    fun `pairing with an invalid code surfaces an error`() = runTest(mainRule.dispatcher) {
        val vm = viewModel(FakeApiClient())

        vm.pair("000000", "android", "Mom")
        advanceUntilIdle()

        assertNull(vm.deviceToken)
        assertNotNull(vm.error)
    }

    @Test
    fun `adding another parent keeps the same device token and updates monitors`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val (_, _, code) = seedCode(api)
        val vm = viewModel(api)
        vm.pair(code, "android", "Mom")
        advanceUntilIdle()
        val originalDeviceToken = vm.deviceToken

        val secondParentToken = api.register("other@x.com", "pw123456").accessToken
        val placeholder = api.createChild(secondParentToken, "Mimi")
        val secondCode = api.pairingCode(secondParentToken, placeholder.id).code

        vm.addParent(secondCode, "android", "Dad")
        advanceUntilIdle()

        assertEquals(originalDeviceToken, vm.deviceToken)
        assertEquals(listOf("parent@x.com", "other@x.com"), vm.monitors.map { it.email })
        assertEquals(listOf("Mom", "Dad"), vm.monitors.map { it.childFacingName })
        assertEquals("Parent added.", vm.message)
    }

    @Test
    fun `removing one monitor keeps device paired until last monitor is removed`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val (_, _, code) = seedCode(api)
        val vm = viewModel(api)
        vm.pair(code, "android", "Mom")
        advanceUntilIdle()

        val secondParentToken = api.register("other-remove@x.com", "pw123456").accessToken
        val placeholder = api.createChild(secondParentToken, "Mimi")
        vm.addParent(api.pairingCode(secondParentToken, placeholder.id).code, "android", "Dad")
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
        vm.pair(code, "android", "Mom")
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
        vm.pair(code, "android", "Mom")
        advanceUntilIdle()

        val secondParentToken = api.register("chat-other@x.com", "pw123456").accessToken
        val placeholder = api.createChild(secondParentToken, "Mimi")
        vm.addParent(api.pairingCode(secondParentToken, placeholder.id).code, "android", "Dad")
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
    fun `kid can rename a monitoring parent`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val (_, _, code) = seedCode(api)
        val vm = viewModel(api)
        vm.pair(code, "android", "Mom")
        advanceUntilIdle()

        vm.updateMonitorName(vm.monitors.single().parentId, "Ima")
        advanceUntilIdle()

        assertEquals("Ima", vm.monitors.single().childFacingName)
        assertNull(vm.error)
    }

    @Test
    fun `kid telemetry starts empty until Android provides real readings`() = runTest(mainRule.dispatcher) {
        val vm = viewModel(FakeApiClient())

        assertNull(vm.lat)
        assertNull(vm.lng)
        assertNull(vm.battery)
        assertNull(vm.charging)
    }

    @Test
    fun `background monitoring status is ready when required access is granted`() {
        assertEquals(
            true,
            BackgroundMonitoringStatus(
                foregroundLocationGranted = true,
                backgroundLocationGranted = true,
                notificationsEnabled = true,
                batteryUnrestricted = true,
                appUsageGranted = true,
                urgentNotificationsEnabled = true,
                urgentDndBypassAllowed = true,
                manufacturerGuide = ManufacturerBackgroundGuide.Xiaomi,
                manufacturerAccessConfirmed = true,
            ).ready,
        )
        assertEquals(
            false,
            BackgroundMonitoringStatus(
                foregroundLocationGranted = true,
                backgroundLocationGranted = true,
                notificationsEnabled = false,
                batteryUnrestricted = true,
                appUsageGranted = true,
                urgentNotificationsEnabled = true,
                urgentDndBypassAllowed = true,
                manufacturerGuide = ManufacturerBackgroundGuide.Xiaomi,
                manufacturerAccessConfirmed = true,
            ).ready,
        )
        assertEquals(
            false,
            BackgroundMonitoringStatus(
                foregroundLocationGranted = true,
                backgroundLocationGranted = true,
                notificationsEnabled = true,
                batteryUnrestricted = true,
                appUsageGranted = true,
                urgentNotificationsEnabled = true,
                urgentDndBypassAllowed = true,
                manufacturerGuide = ManufacturerBackgroundGuide.Xiaomi,
                manufacturerAccessConfirmed = false,
            ).ready,
        )
        assertEquals(
            true,
            BackgroundMonitoringStatus(
                foregroundLocationGranted = true,
                backgroundLocationGranted = true,
                notificationsEnabled = true,
                batteryUnrestricted = true,
                appUsageGranted = false,
                urgentNotificationsEnabled = true,
                urgentDndBypassAllowed = true,
                manufacturerGuide = ManufacturerBackgroundGuide.Generic,
                manufacturerAccessConfirmed = false,
            ).ready,
        )
        assertEquals(
            false,
            BackgroundMonitoringStatus(
                foregroundLocationGranted = true,
                backgroundLocationGranted = true,
                notificationsEnabled = true,
                batteryUnrestricted = true,
                appUsageGranted = true,
                urgentNotificationsEnabled = false,
                urgentDndBypassAllowed = true,
                manufacturerGuide = ManufacturerBackgroundGuide.Generic,
                manufacturerAccessConfirmed = false,
            ).ready,
        )
    }

    @Test
    fun `permission status payload uses compact required and granted bitmasks`() {
        val generic = BackgroundMonitoringStatus(
            foregroundLocationGranted = true,
            backgroundLocationGranted = true,
            notificationsEnabled = true,
            batteryUnrestricted = true,
            appUsageGranted = false,
            urgentNotificationsEnabled = true,
            urgentDndBypassAllowed = true,
            manufacturerGuide = ManufacturerBackgroundGuide.Generic,
            manufacturerAccessConfirmed = false,
        ).toPermissionStatus()
        assertEquals("g", generic.g)
        assertEquals(207, generic.r)
        assertEquals(207, generic.m)

        val xiaomi = BackgroundMonitoringStatus(
            foregroundLocationGranted = true,
            backgroundLocationGranted = true,
            notificationsEnabled = true,
            batteryUnrestricted = true,
            appUsageGranted = true,
            urgentNotificationsEnabled = true,
            urgentDndBypassAllowed = true,
            manufacturerGuide = ManufacturerBackgroundGuide.Xiaomi,
            manufacturerAccessConfirmed = false,
        ).toPermissionStatus()
        assertEquals("x", xiaomi.g)
        assertEquals(239, xiaomi.r)
        assertEquals(223, xiaomi.m)
    }

    @Test
    fun `manufacturer background guide detects Xiaomi Poco and Redmi devices`() {
        assertEquals(ManufacturerBackgroundGuide.Xiaomi, backgroundGuideFor("Xiaomi", "Xiaomi"))
        assertEquals(ManufacturerBackgroundGuide.Xiaomi, backgroundGuideFor("POCO", "POCO"))
        assertEquals(ManufacturerBackgroundGuide.Xiaomi, backgroundGuideFor("Xiaomi", "Redmi"))
        assertEquals(ManufacturerBackgroundGuide.Generic, backgroundGuideFor("Google", "Pixel"))
        assertEquals(ManufacturerBackgroundGuide.Generic, backgroundGuideFor("Samsung", "Samsung"))
    }
}

