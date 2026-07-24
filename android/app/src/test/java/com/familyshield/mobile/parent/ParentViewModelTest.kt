package com.familyshield.mobile.parent

import com.familyshield.mobile.MainDispatcherRule
import com.familyshield.mobile.fakes.FakeApiClient
import com.familyshield.mobile.fakes.InMemoryTokenStore
import com.familyshield.mobile.net.ApiClient
import com.familyshield.mobile.net.AppUsageEntry
import com.familyshield.mobile.net.AppUsageSummary
import com.familyshield.mobile.net.UsageDay
import com.familyshield.mobile.net.FrequentRoute
import com.familyshield.mobile.net.Geo
import com.familyshield.mobile.net.RoutesResponse
import com.familyshield.mobile.net.Stop
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ParentViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun viewModel(api: ApiClient) =
        ParentViewModel(api, InMemoryTokenStore(), mainRule.dispatcher)

    private fun viewModel(api: ApiClient, store: InMemoryTokenStore) =
        ParentViewModel(api, store, mainRule.dispatcher)

    @Test
    fun `register succeeds and stores a token`() = runTest(mainRule.dispatcher) {
        val vm = viewModel(FakeApiClient())

        vm.authenticate("parent@x.com", "pw123456", register = true)
        advanceUntilIdle()

        assertNotNull("token should be set after register", vm.token)
        assertNull(vm.error)
    }

    @Test
    fun `parent authentication clears kid device token`() = runTest(mainRule.dispatcher) {
        val store = InMemoryTokenStore(deviceToken = "kid-device-token")
        val vm = viewModel(FakeApiClient(), store)

        vm.authenticate("parent@x.com", "pw123456", register = true)
        advanceUntilIdle()

        assertNotNull(vm.token)
        assertNull(store.deviceToken)
    }

    @Test
    fun `login with wrong password surfaces an error and no token`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        api.register("parent@x.com", "correct-pw")
        val vm = viewModel(api)

        vm.authenticate("parent@x.com", "wrong-pw", register = false)
        advanceUntilIdle()

        assertNull(vm.token)
        assertNotNull("an error message should be shown", vm.error)
    }

    @Test
    fun `adding a child lists it and selects it`() = runTest(mainRule.dispatcher) {
        val vm = viewModel(FakeApiClient())
        vm.authenticate("parent@x.com", "pw123456", register = true)
        advanceUntilIdle()

        vm.addChild("Mia")
        advanceUntilIdle()

        assertEquals(1, vm.children.size)
        assertEquals("Mia", vm.children[0].displayName)
        assertEquals(vm.children[0].id, vm.selectedId)
    }

    @Test
    fun `generating a pairing code returns a 6-digit code`() = runTest(mainRule.dispatcher) {
        val vm = viewModel(FakeApiClient())
        vm.authenticate("parent@x.com", "pw123456", register = true)
        advanceUntilIdle()
        vm.addChild("Mia")
        advanceUntilIdle()

        vm.generateCode()
        advanceUntilIdle()

        assertNotNull(vm.pairingCode)
        assertTrue("code should be 6 digits", vm.pairingCode!!.matches(Regex("\\d{6}")))
    }

    @Test
    fun `an expired access token is refreshed transparently (no error surfaced)`() =
        runTest(mainRule.dispatcher) {
            val api = FakeApiClient()
            val vm = viewModel(api)
            vm.authenticate("parent@x.com", "pw123456", register = true)
            advanceUntilIdle()
            val firstToken = vm.token

            api.expireNextCall = true   // next call 401s -> should auto-refresh + retry
            vm.refreshChildren()
            advanceUntilIdle()

            assertNull("a refreshed token should hide the 401", vm.error)
            assertNotNull(vm.token)
            assertNotNull("still signed in after refresh", firstToken)
        }

    @Test
    fun `live tracking polls the location periodically`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val vm = viewModel(api)
        vm.authenticate("parent@x.com", "pw123456", register = true)
        advanceUntilIdle()
        vm.addChild("Mia")
        advanceUntilIdle()
        val childId = vm.selectedId!!
        val code = api.pairingCode(vm.token!!, childId).code
        val deviceToken = api.pair(code, "android", null).deviceToken
        api.sendLocation(deviceToken, lat = 1.0, lng = 2.0, battery = 80)

        vm.startLive(intervalMs = 1000)
        advanceTimeBy(50); runCurrent()
        assertEquals("first live poll", 1.0, vm.location!!.lat, 1e-9)

        api.sendLocation(deviceToken, lat = 5.0, lng = 6.0, battery = 80) // child moved
        advanceTimeBy(1100); runCurrent()
        assertEquals("location updated live without manual refresh", 5.0, vm.location!!.lat, 1e-9)

        vm.stopLive()
    }

    @Test
    fun `loads frequent routes for the selected child`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        api.routesResult = RoutesResponse(
            frequent = listOf(FrequentRoute(Geo(32.0, 34.0), Geo(32.02, 34.02), count = 3,
                lastAt = "2026-06-20T08:00:00Z", avgMinutes = 20.0, avgKm = 2.8)),
        )
        val vm = viewModel(api)
        vm.authenticate("parent@x.com", "pw123456", register = true)
        advanceUntilIdle()
        vm.addChild("Mia")     // selecting the child loads routes
        advanceUntilIdle()

        assertEquals(1, vm.frequentRoutes.size)
        assertEquals(3, vm.frequentRoutes[0].count)
    }

    @Test
    fun `family overview collects top places tagged per child`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        api.routesResult = RoutesResponse(
            stops = listOf(Stop(32.0, 34.0, "2026-06-20T08:00:00Z", "2026-06-20T15:00:00Z", dwellMin = 420.0)),
        )
        val vm = viewModel(api)
        vm.authenticate("parent@x.com", "pw123456", register = true)
        advanceUntilIdle()
        vm.addChild("Mia")
        advanceUntilIdle()
        vm.addChild("Noah")
        advanceUntilIdle()

        vm.loadFamilyOverview()
        advanceUntilIdle()

        assertEquals("one stop per child", 2, vm.topPlaces.size)
        assertTrue("both children represented",
            vm.topPlaces.map { it.childName }.toSet() == setOf("Mia", "Noah"))
        assertEquals(32.0, vm.topPlaces[0].lat, 1e-9)
    }

    @Test
    fun `live tracking exposes every child's location, not just the selected one`() =
        runTest(mainRule.dispatcher) {
            val api = FakeApiClient()
            val vm = viewModel(api)
            vm.authenticate("parent@x.com", "pw123456", register = true)
            advanceUntilIdle()
            vm.addChild("Mia"); advanceUntilIdle()
            val miaId = vm.selectedId!!
            vm.addChild("Noah"); advanceUntilIdle()
            val noahId = vm.selectedId!!

            for ((id, lat) in listOf(miaId to 1.0, noahId to 9.0)) {
                val code = api.pairingCode(vm.token!!, id).code
                val dt = api.pair(code, "android", null).deviceToken
                api.sendLocation(dt, lat = lat, lng = 0.0, battery = 80)
            }

            vm.startLive(intervalMs = 1000)
            advanceTimeBy(50); runCurrent()

            assertEquals(2, vm.allLocations.size)
            assertEquals(1.0, vm.allLocations[miaId]!!.lat, 1e-9)
            assertEquals(9.0, vm.allLocations[noahId]!!.lat, 1e-9)
            vm.stopLive()
        }

    @Test
    fun `chat round-trip and conversation unread counts`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val vm = viewModel(api)
        vm.authenticate("parent@x.com", "pw123456", register = true); advanceUntilIdle()
        vm.addChild("Mia"); advanceUntilIdle()
        val id = vm.selectedId!!
        val code = api.pairingCode(vm.token!!, id).code
        val dt = api.pair(code, "android", null).deviceToken

        // Parent opens the thread (starts polling) and sends a message.
        vm.openChat(id); runCurrent()
        vm.sendChat("Hi Mia"); runCurrent()
        assertTrue("parent message present", vm.chatMessages.any { it.sender == "parent" && it.body == "Hi Mia" })

        // Kid replies; parent leaves the thread.
        api.sendDeviceMessage(dt, "Hi mom")
        vm.closeChat()

        vm.loadConversations(); advanceUntilIdle()
        assertEquals("one unread child message", 1, vm.unreadByChild[id])
        assertEquals("Hi mom", vm.lastMessageByChild[id]?.body)
    }

    @Test
    fun `loads app usage summary for a child`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        api.appUsageResult = AppUsageSummary(
            totalTodayMin = 155, yesterdayMin = 120, avgWeekMin = 140,
            week = listOf(UsageDay("2026-06-23", "Tue", 155)),
            apps = listOf(AppUsageEntry("YouTube", "Entertainment", 80)),
        )
        val vm = viewModel(api)
        vm.authenticate("p@x.com", "pw123456", register = true); advanceUntilIdle()
        vm.addChild("Mia"); advanceUntilIdle()

        vm.loadAppUsage(vm.selectedId!!); advanceUntilIdle()

        assertEquals(155, vm.appUsage?.totalTodayMin)
        assertEquals("YouTube", vm.appUsage?.apps?.first()?.app)
        assertEquals(vm.selectedId, vm.appUsageChildId)
    }

    @Test
    fun `renaming a child updates the list`() = runTest(mainRule.dispatcher) {
        val vm = viewModel(FakeApiClient())
        vm.authenticate("parent@x.com", "pw123456", register = true)
        advanceUntilIdle()
        vm.addChild("Mia")            // selects Mia
        advanceUntilIdle()

        vm.renameChild("Mia Cohen")
        advanceUntilIdle()

        assertEquals("Mia Cohen", vm.children.first().displayName)
    }

    @Test
    fun `free tier child limit surfaces an error before creating sixth child`() = runTest(mainRule.dispatcher) {
        val vm = viewModel(FakeApiClient())
        vm.authenticate("limit-parent@x.com", "pw123456", register = true)
        advanceUntilIdle()
        repeat(5) {
            vm.addChild("Kid $it")
            advanceUntilIdle()
        }

        vm.addChild("Kid 6")
        advanceUntilIdle()

        assertEquals(5, vm.children.size)
        assertEquals("Your free tier allows up to 5 monitored children.", vm.error)
    }

    @Test
    fun `new children receive unused avatars first and avatar can be edited`() = runTest(mainRule.dispatcher) {
        val vm = viewModel(FakeApiClient())
        vm.authenticate("avatars-parent@x.com", "pw123456", register = true)
        advanceUntilIdle()

        vm.addChild("Mia"); advanceUntilIdle()
        vm.addChild("Noah"); advanceUntilIdle()

        assertEquals(listOf("fox", "panda"), vm.children.map { it.avatar })

        vm.select(vm.children.first().id)
        advanceUntilIdle()
        vm.updateChild("Mia", "owl")
        advanceUntilIdle()

        assertEquals("owl", vm.children.first { it.displayName == "Mia" }.avatar)
    }

    @Test
    fun `removing a child updates the parent list`() = runTest(mainRule.dispatcher) {
        val vm = viewModel(FakeApiClient())
        vm.authenticate("delete-parent@x.com", "pw123456", register = true)
        advanceUntilIdle()
        vm.addChild("Mia"); advanceUntilIdle()
        vm.addChild("Noah"); advanceUntilIdle()
        val removedId = vm.children.first { it.displayName == "Mia" }.id

        vm.removeChild(removedId)
        advanceUntilIdle()

        assertEquals(listOf("Noah"), vm.children.map { it.displayName })
    }

    @Test
    fun `add and remove a safe zone`() = runTest(mainRule.dispatcher) {
        val vm = viewModel(FakeApiClient())
        vm.authenticate("parent@x.com", "pw123456", register = true)
        advanceUntilIdle()
        vm.addChild("Mia")            // also selects Mia
        advanceUntilIdle()

        vm.addZone("School", 32.0, 34.0, 300)
        advanceUntilIdle()
        assertEquals(1, vm.zones.size)
        assertEquals("School", vm.zones[0].name)
        assertEquals(300, vm.zones[0].radiusM)

        vm.removeZone(vm.zones[0].id)
        advanceUntilIdle()
        assertTrue("zone should be removed", vm.zones.isEmpty())
    }

    @Test
    fun `parent sees the kids GPS location, online status and low-battery alert`() =
        runTest(mainRule.dispatcher) {
            val api = FakeApiClient()
            val vm = viewModel(api)

            // Parent: register -> add child -> generate pairing code
            vm.authenticate("parent@x.com", "pw123456", register = true)
            advanceUntilIdle()
            vm.addChild("Mia")
            advanceUntilIdle()
            val childId = vm.selectedId!!
            vm.generateCode()
            advanceUntilIdle()
            val code = vm.pairingCode!!

            // Kid device: pair with that code and report a low-battery GPS fix
            val paired = api.pair(code, "android", "Simulator")
            api.sendLocation(paired.deviceToken, lat = 32.0853, lng = 34.7818, battery = 10)

            // Parent: refresh the child detail
            vm.refreshDetail()
            advanceUntilIdle()

            // GPS location is now visible to the parent
            assertNotNull("parent should see the child's location", vm.location)
            assertEquals(32.0853, vm.location!!.lat, 1e-4)
            assertEquals(34.7818, vm.location!!.lng, 1e-4)

            // Low-battery alert fired and is listed
            assertTrue("a low_battery alert should be present",
                vm.alerts.any { it.type == "low_battery" })

            // Device shows up as paired, online, with the reported battery
            val child = vm.children.first { it.id == childId }
            assertEquals(1, child.devices.size)
            assertNotNull("device should be online (lastSeenAt set)", child.devices[0].lastSeenAt)
            assertEquals(10, child.devices[0].batteryLevel)
        }

    @Test
    fun `kid unpair keeps child visible with revoked device and last location`() =
        runTest(mainRule.dispatcher) {
            val api = FakeApiClient()
            val vm = viewModel(api)

            vm.authenticate("parent-unpair@x.com", "pw123456", register = true)
            advanceUntilIdle()
            vm.addChild("Mia")
            advanceUntilIdle()
            val childId = vm.selectedId!!
            val code = api.pairingCode(vm.token!!, childId).code
            val paired = api.pair(code, "android", "Pixel")
            api.sendLocation(paired.deviceToken, lat = 32.1, lng = 34.8, battery = 71)
            val parentId = api.monitoring(paired.deviceToken).monitors.single().parentId

            api.removeMonitor(paired.deviceToken, parentId)
            vm.refreshDetail()
            advanceUntilIdle()

            val child = vm.children.first { it.id == childId }
            assertEquals(1, child.devices.size)
            assertNotNull("revokedAt marks a deliberate kid-side unpair", child.devices[0].revokedAt)
            assertEquals(32.1, vm.location!!.lat, 1e-4)
            assertEquals(34.8, vm.location!!.lng, 1e-4)
            assertNull("refresh should not surface an error after kid unpairs", vm.error)
        }
}
