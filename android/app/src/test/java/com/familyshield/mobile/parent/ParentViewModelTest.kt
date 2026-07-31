package com.familyshield.mobile.parent

import com.familyshield.mobile.MainDispatcherRule
import com.familyshield.mobile.fakes.FakeApiClient
import com.familyshield.mobile.fakes.InMemoryTokenStore
import com.familyshield.mobile.net.AppUsageDayDetail
import com.familyshield.mobile.net.AppUsageEntry
import com.familyshield.mobile.net.AppUsageLimitBody
import com.familyshield.mobile.net.AppUsageSummary
import com.familyshield.mobile.net.Device
import com.familyshield.mobile.net.PermissionStatus
import com.familyshield.mobile.net.UpdateAppUsageLimitBody
import com.familyshield.mobile.net.UsageDay
import com.familyshield.mobile.permissions.requiredPermissionsSatisfied
import com.familyshield.mobile.net.FrequentRoute
import com.familyshield.mobile.net.FrequentLocation
import com.familyshield.mobile.net.Geo
import com.familyshield.mobile.net.LocationPoint
import com.familyshield.mobile.net.RoutesResponse
import com.familyshield.mobile.net.Stop
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ParentViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun viewModel(api: FakeApiClient) =
        ParentViewModel(api, InMemoryTokenStore(), mainRule.dispatcher, FakeParentAuthGateway(api))

    private fun viewModel(api: FakeApiClient, store: InMemoryTokenStore) =
        ParentViewModel(api, store, mainRule.dispatcher, FakeParentAuthGateway(api))

    @Test
    fun `register succeeds and stores a token`() = runTest(mainRule.dispatcher) {
        val vm = viewModel(FakeApiClient())

        vm.authenticate("parent@x.com", "pw123456", register = true)
        advanceUntilIdle()

        assertNotNull("token should be set after register", vm.token)
        assertNull(vm.error)
    }

    @Test
    fun `parent authentication preserves kid device token`() = runTest(mainRule.dispatcher) {
        val store = InMemoryTokenStore(deviceToken = "kid-device-token")
        val vm = viewModel(FakeApiClient(), store)

        vm.authenticate("parent@x.com", "pw123456", register = true)
        advanceUntilIdle()

        assertNotNull(vm.token)
        assertEquals("kid-device-token", store.deviceToken)
    }

    @Test
    fun `Firebase auth state is restored without a stored parent JWT`() = runTest(mainRule.dispatcher) {
        val auth = FakeParentAuthGateway(
            state = ParentAuthState.SignedIn("firebase-uid", "parent@x.com"),
        )
        val vm = ParentViewModel(
            FakeApiClient(),
            InMemoryTokenStore(),
            mainRule.dispatcher,
            auth,
        )
        advanceUntilIdle()

        assertNotNull(vm.token)
        assertTrue(vm.authState is ParentAuthState.SignedIn)
    }

    @Test
    fun `Firebase sign-out removes a stale verification screen`() = runTest(mainRule.dispatcher) {
        val auth = FakeParentAuthGateway(
            state = ParentAuthState.AwaitingVerification("parent@x.com"),
        )
        val vm = ParentViewModel(
            FakeApiClient(),
            InMemoryTokenStore(),
            mainRule.dispatcher,
            auth,
        )

        auth.emitState(ParentAuthState.SignedOut)
        advanceUntilIdle()

        assertTrue(vm.authState is ParentAuthState.SignedOut)
        assertNull(vm.token)
    }

    @Test
    fun `failed Firebase password login falls back to one-time legacy migration`() =
        runTest(mainRule.dispatcher) {
            val api = FakeApiClient()
            api.register("legacy@x.com", "OldPassword!1")
            val auth = FakeParentAuthGateway(
                api = api,
                loginFailure = LegacyMigrationCandidateException(
                    IllegalStateException("Firebase password login failed"),
                ),
            )
            val vm = ParentViewModel(api, InMemoryTokenStore(), mainRule.dispatcher, auth)

            vm.authenticate("legacy@x.com", "OldPassword!1", register = false)
            advanceUntilIdle()

            assertTrue(auth.customTokenUsed)
            assertTrue(vm.authState is ParentAuthState.SignedIn)
            assertNotNull(vm.token)
        }

    @Test
    fun `Firebase throttling does not invoke legacy migration`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val auth = FakeParentAuthGateway(
            api = api,
            loginFailure = IllegalStateException("Requests from this device are temporarily blocked"),
        )
        val vm = ParentViewModel(api, InMemoryTokenStore(), mainRule.dispatcher, auth)

        vm.authenticate("parent@x.com", "Password!1", register = false)
        advanceUntilIdle()

        assertFalse(auth.customTokenUsed)
        assertNull(vm.token)
        assertEquals("Requests from this device are temporarily blocked", vm.error)
    }

    @Test
    fun `Firebase throttling is mapped to a clear user-facing error`() =
        runTest(mainRule.dispatcher) {
            val auth = FakeParentAuthGateway(
                loginFailure = ParentAuthTooManyAttemptsException(),
            )
            val vm = ParentViewModel(
                FakeApiClient(),
                InMemoryTokenStore(),
                mainRule.dispatcher,
                auth,
            )

            vm.authenticate("parent@x.com", "Password!1", register = false)
            advanceUntilIdle()

            assertEquals(ParentAuthUiError.TooManyAttempts, vm.authUiError)
            assertNull(vm.error)
            assertFalse(auth.customTokenUsed)
        }

    @Test
    fun `raw Firebase throttle messages from dashboard operations use friendly wording`() {
        assertTrue(isAuthenticationThrottleMessage("Too many attempts"))
        assertTrue(
            isAuthenticationThrottleMessage(
                "We have blocked all requests from this device due to unusual activity",
            ),
        )
        assertFalse(isAuthenticationThrottleMessage("App attestation failed"))
        assertFalse(isAuthenticationThrottleMessage("Network unavailable"))
    }

    @Test
    fun `only invalid credential errors qualify for legacy migration`() {
        assertTrue(isLegacyMigrationErrorCode("ERROR_INVALID_CREDENTIAL"))
        assertTrue(isLegacyMigrationErrorCode("ERROR_WRONG_PASSWORD"))
        assertTrue(isLegacyMigrationErrorCode("ERROR_USER_NOT_FOUND"))
        assertFalse(isLegacyMigrationErrorCode("ERROR_TOO_MANY_REQUESTS"))
        assertFalse(isLegacyMigrationErrorCode("ERROR_USER_DISABLED"))
        assertFalse(isLegacyMigrationErrorCode("ERROR_NETWORK_REQUEST_FAILED"))
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
    fun `App Check rejection does not sign out an authenticated parent`() =
        runTest(mainRule.dispatcher) {
            val api = FakeApiClient()
            val auth = FakeParentAuthGateway(api = api)
            val vm = ParentViewModel(api, InMemoryTokenStore(), mainRule.dispatcher, auth)
            vm.authenticate("parent@x.com", "Password!1", register = true)
            advanceUntilIdle()

            api.rejectAppCheck = true
            vm.refreshChildren()
            advanceUntilIdle()

            assertTrue(vm.authState is ParentAuthState.SignedIn)
            assertNotNull(vm.token)
            assertEquals("App attestation failed", vm.error)
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
        val deviceToken = api.pair(code, "android", null, "Mom").deviceToken
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
            frequentLocations = listOf(
                FrequentLocation(
                    lat = 32.0,
                    lng = 34.0,
                    count = 4,
                    lastAt = "2026-06-20T08:00:00Z",
                ),
            ),
        )
        val vm = viewModel(api)
        vm.authenticate("parent@x.com", "pw123456", register = true)
        advanceUntilIdle()
        vm.addChild("Mia")     // selecting the child loads routes
        advanceUntilIdle()

        assertEquals(1, vm.frequentRoutes.size)
        assertEquals(3, vm.frequentRoutes[0].count)
        assertEquals(1, vm.frequentLocations.size)
        assertEquals(4, vm.frequentLocations[0].count)
    }

    @Test
    fun `family overview collects top places tagged per child`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val today = LocalDate.now()
        api.routesResult = RoutesResponse(
            stops = listOf(Stop(32.0, 34.0, "${today}T08:00:00Z", "${today}T15:00:00Z", dwellMin = 420.0)),
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
    fun `logout clears all account derived data before another parent signs in`() =
        runTest(mainRule.dispatcher) {
            val api = FakeApiClient()
            val today = LocalDate.now()
            api.routesResult = RoutesResponse(
                frequent = listOf(
                    FrequentRoute(
                        Geo(32.0, 34.0),
                        Geo(32.02, 34.02),
                        count = 3,
                        lastAt = "${today}T09:00:00Z",
                        avgMinutes = 20.0,
                        avgKm = 2.8,
                    ),
                ),
                frequentLocations = listOf(
                    FrequentLocation(32.0, 34.0, count = 4, lastAt = "${today}T09:00:00Z"),
                ),
                stops = listOf(
                    Stop(32.0, 34.0, "${today}T08:00:00Z", "${today}T09:00:00Z", dwellMin = 60.0),
                ),
            )
            val vm = viewModel(api)
            vm.authenticate("first-parent@x.com", "pw123456", register = true)
            advanceUntilIdle()
            vm.addChild("Mia")
            advanceUntilIdle()
            val childId = vm.selectedId!!
            val code = api.pairingCode(vm.token!!, childId).code
            val deviceToken = api.pair(code, "android", null, "Mom").deviceToken
            api.sendLocation(deviceToken, lat = 32.0, lng = 34.0, battery = 10)
            api.createZone(vm.token!!, childId, "School", 32.0, 34.0, 300, active = true)

            vm.refreshDetail()
            vm.loadZones()
            vm.loadHistory(today.toString())
            vm.loadRoutes()
            vm.loadFamilyOverview()
            advanceUntilIdle()

            assertTrue(vm.topPlaces.isNotEmpty())
            assertTrue(vm.familyAlerts.isNotEmpty())
            assertTrue(vm.history.isNotEmpty())
            assertTrue(vm.zones.isNotEmpty())
            assertTrue(vm.frequentRoutes.isNotEmpty())

            vm.logout()

            assertTrue(vm.children.isEmpty())
            assertTrue(vm.topPlaces.isEmpty())
            assertTrue(vm.familyAlerts.isEmpty())
            assertTrue(vm.allFamilyAlerts.isEmpty())
            assertTrue(vm.alerts.isEmpty())
            assertTrue(vm.history.isEmpty())
            assertTrue(vm.historyDays.isEmpty())
            assertTrue(vm.zones.isEmpty())
            assertTrue(vm.mapZonesByChild.isEmpty())
            assertTrue(vm.frequentRoutes.isEmpty())
            assertTrue(vm.frequentLocations.isEmpty())
            assertTrue(vm.trips.isEmpty())
            assertTrue(vm.stops.isEmpty())

            vm.authenticate("second-parent@x.com", "pw123456", register = true)
            advanceUntilIdle()

            assertTrue(vm.children.isEmpty())
            assertTrue(vm.topPlaces.isEmpty())
            assertTrue(vm.familyAlerts.isEmpty())
            assertTrue(vm.history.isEmpty())
            assertTrue(vm.zones.isEmpty())
        }

    @Test
    fun `family overview excludes places from previous days`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        api.routesResult = RoutesResponse(
            stops = listOf(
                Stop(31.0, 34.0, "${yesterday}T18:00:00Z", "${yesterday}T19:00:00Z", dwellMin = 60.0),
                Stop(32.0, 35.0, "${today}T08:00:00Z", "${today}T09:00:00Z", dwellMin = 60.0),
            ),
        )
        val vm = viewModel(api)
        vm.authenticate("today-places@x.com", "pw123456", register = true)
        advanceUntilIdle()
        vm.addChild("Mia")
        advanceUntilIdle()

        vm.loadFamilyOverview()
        advanceUntilIdle()

        assertEquals(1, vm.topPlaces.size)
        assertEquals(32.0, vm.topPlaces.single().lat, 1e-9)
    }

    @Test
    fun `family overview includes a place that started before midnight and continued today`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        api.routesResult = RoutesResponse(
            stops = listOf(
                Stop(31.0, 34.0, "${yesterday}T18:00:00Z", "${yesterday}T19:00:00Z", dwellMin = 60.0),
                Stop(32.0, 35.0, "${yesterday}T23:30:00Z", "${today}T10:00:00Z", dwellMin = 630.0),
            ),
        )
        val vm = viewModel(api)
        vm.authenticate("overnight-place@x.com", "pw123456", register = true)
        advanceUntilIdle()
        vm.addChild("Mia")
        advanceUntilIdle()

        vm.loadFamilyOverview()
        advanceUntilIdle()

        assertEquals(1, vm.topPlaces.size)
        assertEquals(32.0, vm.topPlaces.single().lat, 1e-9)
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
                val dt = api.pair(code, "android", null, "Mom").deviceToken
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
    fun `map target defaults to first child with live location`() =
        runTest(mainRule.dispatcher) {
            val api = FakeApiClient()
            val vm = viewModel(api)
            vm.authenticate("parent@x.com", "pw123456", register = true)
            advanceUntilIdle()
            vm.addChild("Mia"); advanceUntilIdle()
            val miaId = vm.selectedId!!
            vm.addChild("Noah"); advanceUntilIdle()
            val noahId = vm.selectedId!!
            vm.select(miaId)
            advanceUntilIdle()

            val code = api.pairingCode(vm.token!!, noahId).code
            val dt = api.pair(code, "android", null, "Mom").deviceToken
            api.sendLocation(dt, lat = 9.0, lng = 1.0, battery = 80)

            vm.startLive(intervalMs = 1000)
            advanceTimeBy(50); runCurrent()

            assertEquals(noahId, vm.ensureMapTargetChild())
            assertEquals(noahId, vm.selectedId)
            assertEquals(9.0, vm.location!!.lat, 1e-9)
            vm.stopLive()
        }

    @Test
    fun `selecting a map child updates selected state and current location`() =
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
                val dt = api.pair(code, "android", null, "Mom").deviceToken
                api.sendLocation(dt, lat = lat, lng = 0.0, battery = 80)
            }
            vm.startLive(intervalMs = 1000)
            advanceTimeBy(50); runCurrent()

            vm.selectMapChild(miaId)
            runCurrent()

            assertEquals(miaId, vm.selectedId)
            assertEquals(1.0, vm.location!!.lat, 1e-9)
            vm.stopLive()
        }

    @Test
    fun `loads shared map zones for every child`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val vm = viewModel(api)
        vm.authenticate("parent@x.com", "pw123456", register = true)
        advanceUntilIdle()
        vm.addChild("Mia"); advanceUntilIdle()
        val miaId = vm.selectedId!!
        vm.addChild("Noah"); advanceUntilIdle()
        val noahId = vm.selectedId!!
        api.createZone(vm.token!!, miaId, "School", 1.0, 2.0, 300, active = true)
        api.createZone(vm.token!!, noahId, "Home", 3.0, 4.0, 150, active = true)

        vm.loadMapZones()
        advanceUntilIdle()

        assertEquals(setOf(miaId, noahId), vm.mapZonesByChild.keys)
        assertEquals(listOf("Home", "School"), vm.mapZonesByChild[miaId]!!.map { it.name })
        assertEquals(listOf("Home", "School"), vm.mapZonesByChild[noahId]!!.map { it.name })
    }

    @Test
    fun `chat round-trip and conversation unread counts`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val vm = viewModel(api)
        vm.authenticate("parent@x.com", "pw123456", register = true); advanceUntilIdle()
        vm.addChild("Mia"); advanceUntilIdle()
        val id = vm.selectedId!!
        val code = api.pairingCode(vm.token!!, id).code
        val dt = api.pair(code, "android", null, "Mom").deviceToken

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
    fun `opening the already active chat does not restart or surface cancellation`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val vm = viewModel(api)
        vm.authenticate("parent-repeat-chat@x.com", "pw123456", register = true)
        advanceUntilIdle()
        vm.addChild("Mia")
        advanceUntilIdle()
        val id = vm.selectedId!!

        vm.openChat(id)
        runCurrent()
        vm.sendChat("Still here")
        runCurrent()
        vm.openChat(id)
        runCurrent()

        assertNull(vm.error)
        assertEquals(listOf("Still here"), vm.chatMessages.map { it.body })
        vm.closeChat()
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
    fun `loads app usage summary for a selected day`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        api.appUsageResult = AppUsageSummary(
            totalTodayMin = 90,
            week = listOf(UsageDay("2026-06-23", "Tue", 90)),
            apps = listOf(AppUsageEntry("Video", "Entertainment", 90)),
        )
        val vm = viewModel(api)
        vm.authenticate("usage-day@x.com", "pw123456", register = true); advanceUntilIdle()
        vm.addChild("Mia"); advanceUntilIdle()
        val childId = vm.selectedId!!

        vm.loadAppUsage(childId, "2026-06-23"); advanceUntilIdle()

        assertEquals("2026-06-23", vm.appUsageDate)
        assertEquals(childId to "2026-06-23", api.appUsageRequests.last())
        assertEquals("2026-06-23", vm.appUsage?.selectedDay)
        assertEquals("Video", vm.appUsage?.apps?.single()?.app)
    }

    @Test
    fun `selecting an app usage day is local after weekly details load`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        api.appUsageResult = AppUsageSummary(
            totalTodayMin = 25,
            week = listOf(
                UsageDay("2026-06-22", "Mon", 10, hasData = true),
                UsageDay("2026-06-23", "Tue", 25, hasData = true),
            ),
            dayDetails = listOf(
                AppUsageDayDetail("2026-06-22", totalMin = 10, apps = listOf(AppUsageEntry("Chat", "Social", 10))),
                AppUsageDayDetail("2026-06-23", totalMin = 25, apps = listOf(AppUsageEntry("Video", "Entertainment", 25))),
            ),
        )
        val vm = viewModel(api)
        vm.authenticate("usage-local@x.com", "pw123456", register = true); advanceUntilIdle()
        vm.addChild("Mia"); advanceUntilIdle()
        val childId = vm.selectedId!!

        vm.loadAppUsage(childId); advanceUntilIdle()
        val requestCount = api.appUsageRequests.size
        vm.selectAppUsageDate("2026-06-22")

        assertEquals("2026-06-22", vm.appUsageDate)
        assertEquals(requestCount, api.appUsageRequests.size)
    }

    @Test
    fun `creates updates and deletes app usage alerts locally without refreshing usage`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val vm = viewModel(api)
        vm.authenticate("usage-limits@x.com", "pw123456", register = true); advanceUntilIdle()
        vm.addChild("Mia"); advanceUntilIdle()
        val childId = vm.selectedId!!
        vm.loadAppUsage(childId); advanceUntilIdle()
        val requestsAfterLoad = api.appUsageRequests.size

        vm.saveAppUsageLimit(childId, AppUsageLimitBody(type = "total", limitMinutes = 60))
        advanceUntilIdle()

        val created = vm.appUsage?.limits?.single()
        assertNotNull(created)
        assertEquals("total", created!!.type)
        assertEquals(60, created.limitMinutes)
        assertEquals(requestsAfterLoad, api.appUsageRequests.size)

        vm.updateAppUsageLimit(childId, created.id, UpdateAppUsageLimitBody(limitMinutes = 90))
        advanceUntilIdle()

        val updated = vm.appUsage?.limits?.single()
        assertEquals(90, updated?.limitMinutes)
        assertEquals(requestsAfterLoad, api.appUsageRequests.size)

        vm.deleteAppUsageLimit(childId, created.id)
        advanceUntilIdle()

        assertTrue(vm.appUsage?.limits.orEmpty().isEmpty())
        assertEquals(requestsAfterLoad, api.appUsageRequests.size)
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
    fun `child phone number can be added edited and cleared`() = runTest(mainRule.dispatcher) {
        val vm = viewModel(FakeApiClient())
        vm.authenticate("phone-parent@x.com", "pw123456", register = true)
        advanceUntilIdle()

        vm.addChild("Mia", "+1 555 0100")
        advanceUntilIdle()
        assertEquals("+1 555 0100", vm.children.first().phoneNumber)

        vm.updateChild("Mia", phoneNumber = "+1 555 0101")
        advanceUntilIdle()
        assertEquals("+1 555 0101", vm.children.first().phoneNumber)

        vm.updateChild("Mia", phoneNumber = "")
        advanceUntilIdle()
        assertNull(vm.children.first().phoneNumber)
    }

    @Test
    fun `urgent alert is stored as an urgent child message`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val vm = viewModel(api)
        vm.authenticate("urgent-parent@x.com", "pw123456", register = true)
        advanceUntilIdle()
        vm.addChild("Mia")
        advanceUntilIdle()
        val childId = vm.children.first().id

        vm.sendUrgentAlert(childId, "Please call me now")
        advanceUntilIdle()

        val messages = api.messages(vm.token!!, childId).messages
        assertEquals("urgent", messages.last().priority)
        assertEquals("Please call me now", messages.last().body)
    }

    @Test
    fun `family overview exposes active child SOS state`() = runTest(mainRule.dispatcher) {
        val api = FakeApiClient()
        val vm = viewModel(api)
        vm.authenticate("sos-parent@x.com", "pw123456", register = true)
        advanceUntilIdle()
        vm.addChild("Noa")
        advanceUntilIdle()
        val childId = vm.children.first().id
        val code = api.pairingCode(vm.token!!, childId).code
        val pair = api.pair(code, "android", "Pixel", "Mom")
        api.startSos(
            pair.deviceToken,
            "Asia/Jerusalem",
            LocalDate.now().toString(),
            LocationPoint(32.1, 34.8, "2026-07-26T09:00:00Z", 82),
        )

        vm.loadFamilyOverview()
        advanceUntilIdle()

        assertTrue(vm.sosByChild[childId]?.active == true)
        assertEquals(60, vm.sosByChild[childId]?.highRateIntervalSeconds)
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
    fun `edits a safe zone name radius and active state`() = runTest(mainRule.dispatcher) {
        val vm = viewModel(FakeApiClient())
        vm.authenticate("parent@x.com", "pw123456", register = true)
        advanceUntilIdle()
        vm.addChild("Mia")
        advanceUntilIdle()

        vm.addZone("School", 32.0, 34.0, 300)
        advanceUntilIdle()
        val zoneId = vm.zones.single().id

        vm.updateZone(zoneId, "School gate", 500, active = false)
        advanceUntilIdle()

        assertEquals("School gate", vm.zones.single().name)
        assertEquals(500, vm.zones.single().radiusM)
        assertTrue("zone should be inactive", !vm.zones.single().active)
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
            val paired = api.pair(code, "android", "Simulator", "Mom")
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
            val paired = api.pair(code, "android", "Pixel", "Mom")
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

    @Test
    fun `background access help is shown only for stale paired devices`() {
        val stalePaired = Device(
            id = "device-1",
            platform = "android",
            lastSeenAt = "2026-07-25T10:00:00Z",
            isOnline = false,
        )

        assertTrue(stalePaired.shouldShowBackgroundAccessHelp())
        assertTrue(!stalePaired.copy(isOnline = true).shouldShowBackgroundAccessHelp())
        assertTrue(!stalePaired.copy(revokedAt = "2026-07-25T10:05:00Z").shouldShowBackgroundAccessHelp())
        assertTrue(!stalePaired.copy(lastSeenAt = null).shouldShowBackgroundAccessHelp())
        assertTrue(!(null as Device?).shouldShowBackgroundAccessHelp())
    }

    @Test
    fun `permission warning is shown only when reported required access is missing`() {
        val device = Device(
            id = "device-permissions",
            platform = "android",
            permissionStatus = PermissionStatus(g = "g", r = 223, m = 223),
        )

        assertTrue(!device.hasMissingPermissions())
        assertTrue(!device.copy(permissionStatus = PermissionStatus(g = "g", r = 223, m = 207)).hasMissingPermissions())
        assertTrue(device.copy(permissionStatus = PermissionStatus(g = "g", r = 207, m = 79)).hasMissingPermissions())
        assertTrue(device.copy(permissionStatus = PermissionStatus(g = "g", r = 207, m = 79)).permissionStatus.hasMissingUrgentAccess())
        assertTrue(device.copy(permissionStatus = null).hasMissingPermissions() == false)
        assertTrue(PermissionStatus(g = "g", r = 223, m = 207).requiredPermissionsSatisfied())
        assertFalse(PermissionStatus(g = "g", r = 207, m = 79).requiredPermissionsSatisfied())
    }
}

private class FakeParentAuthGateway(
    private val api: FakeApiClient? = null,
    var state: ParentAuthState = ParentAuthState.SignedOut,
    private val loginFailure: Exception? = null,
) : ParentAuthGateway {
    var customTokenUsed = false
    private var token = if (state is ParentAuthState.SignedIn) "acc:parent@x.com" else null
    private var stateListener: ((ParentAuthState) -> Unit)? = null

    override fun currentState() = state

    override fun observeState(listener: (ParentAuthState) -> Unit): () -> Unit {
        stateListener = listener
        return { stateListener = null }
    }

    fun emitState(newState: ParentAuthState) {
        state = newState
        stateListener?.invoke(newState)
    }

    override suspend fun register(email: String, password: String): ParentAuthState {
        token = api?.register(email, password)?.accessToken ?: "acc:$email"
        state = ParentAuthState.SignedIn("firebase-uid", email)
        return state
    }

    override suspend fun login(email: String, password: String): ParentAuthState {
        loginFailure?.let { throw it }
        token = api?.login(email, password)?.accessToken ?: "acc:$email"
        state = ParentAuthState.SignedIn("firebase-uid", email)
        return state
    }

    override suspend fun google(idToken: String): ParentAuthState {
        token = api?.googleLogin(idToken)?.accessToken ?: "acc:google:$idToken"
        state = ParentAuthState.SignedIn("firebase-google", "google@x.com")
        return state
    }

    override suspend fun signInWithCustomToken(customToken: String): ParentAuthState {
        customTokenUsed = true
        token = customToken
        state = ParentAuthState.SignedIn("firebase-uid", "legacy@x.com")
        return state
    }

    override suspend fun sendPasswordReset(email: String) = Unit
    override suspend fun resendVerification() = Unit
    override suspend fun refreshVerification() = state
    override suspend fun reauthenticateWithPassword(password: String) = Unit
    override suspend fun reauthenticateWithGoogle(idToken: String) = Unit
    override suspend fun idToken(forceRefresh: Boolean) = token ?: error("Not signed in")
    override suspend fun appCheckToken(forceRefresh: Boolean): String? = "app-check"
    override fun logout() {
        token = null
        state = ParentAuthState.SignedOut
    }
}

