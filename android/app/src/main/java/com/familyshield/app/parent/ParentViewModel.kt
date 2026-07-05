package com.familyshield.app.parent

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.familyshield.app.net.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A place a child visited today (from detected stops). */
data class TopPlace(val childId: String, val childName: String, val lat: Double, val lng: Double, val arriveAt: String, val departAt: String)

/** An alert tagged with the child it belongs to. */
data class FamilyAlert(val childId: String, val childName: String, val alert: Alert)

class ParentViewModel(
    private val api: ApiClient,
    private val store: TokenStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    var token by mutableStateOf(store.parentToken)
        private set

    init {
        registerPushToken()
    }
    var children by mutableStateOf<List<Child>>(emptyList())
        private set
    var selectedId by mutableStateOf<String?>(null)
    var location by mutableStateOf<CurrentLocation?>(null)
        private set
    var alerts by mutableStateOf<List<Alert>>(emptyList())
        private set
    var pairingCode by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var loadingChildren by mutableStateOf(false)
        private set
    var loadingDetail by mutableStateOf(false)
        private set
    var firstLoad by mutableStateOf(true)
        private set
    var zones by mutableStateOf<List<Zone>>(emptyList())
        private set
    var history by mutableStateOf<List<HistoryPoint>>(emptyList())
        private set
    var historyDate by mutableStateOf(today())
        private set
    var distanceKm by mutableStateOf(0.0)
        private set
    var frequentRoutes by mutableStateOf<List<FrequentRoute>>(emptyList())
        private set
    var trips by mutableStateOf<List<RouteTrip>>(emptyList())
        private set

    // Family-overview (multi-child dashboard) state.
    var allLocations by mutableStateOf<Map<String, CurrentLocation>>(emptyMap())
        private set
    var topPlaces by mutableStateOf<List<TopPlace>>(emptyList())
        private set
    var familyAlerts by mutableStateOf<List<FamilyAlert>>(emptyList())
        private set
    /** null = "All Children"; otherwise the focused child id. */
    var dashboardChildId by mutableStateOf<String?>(null)
        private set

    fun setDashboardChild(id: String?) {
        dashboardChildId = id
        if (id != null) select(id)
    }

    // ---- Chat ----
    var chatMessages by mutableStateOf<List<Message>>(emptyList())
        private set
    var chatChildId by mutableStateOf<String?>(null)
        private set
    var sending by mutableStateOf(false)
        private set
    var unreadByChild by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    var lastMessageByChild by mutableStateOf<Map<String, Message>>(emptyMap())
        private set
    /** Cursor for paging older messages in the open thread; null = no more. */
    var chatCursor by mutableStateOf<String?>(null)
        private set
    var loadingOlder by mutableStateOf(false)
        private set

    private var chatJob: Job? = null

    val chatChild: Child? get() = children.find { it.id == chatChildId }

    /** Conversation list: last message + unread per child in ONE summary call. */
    fun loadConversations() {
        if (token == null) return
        viewModelScope.launch(dispatcher) {
            try {
                children = authed { api.listChildren(it) }
                val summary = authed { api.conversationsSummary(it) }
                unreadByChild = summary.associate { it.childId to it.unread }
                lastMessageByChild = summary.mapNotNull { s -> s.last?.let { s.childId to it } }.toMap()
            } catch (e: Exception) { error = e.message }
        }
    }

    /** Open a child's thread (newest page) and poll for new messages (marking read). */
    fun openChat(childId: String) {
        chatChildId = childId
        chatMessages = emptyList()
        chatCursor = null
        if (selectedId != childId) select(childId)   // load zones/location for the safe-zone card
        chatJob?.cancel()
        chatJob = viewModelScope.launch(dispatcher) {
            try {
                val resp = authed { api.messages(it, childId, markRead = true) }
                chatMessages = resp.messages
                chatCursor = resp.nextCursor
            } catch (e: Exception) { error = e.message }
            while (isActive) {
                delay(3000)
                val after = chatMessages.lastOrNull()?.createdAt
                try {
                    val delta = authed { api.messages(it, childId, after = after, markRead = true) }.messages
                    val have = chatMessages.mapTo(HashSet()) { m -> m.id }
                    val fresh = delta.filter { m -> m.id !in have }
                    if (fresh.isNotEmpty()) chatMessages = chatMessages + fresh
                } catch (_: Exception) { /* quiet during polling */ }
            }
        }
    }

    /** Page in older history (scroll-up), prepending it to the thread. */
    fun loadOlder() {
        val id = chatChildId ?: return
        val cur = chatCursor ?: return
        if (loadingOlder || token == null) return
        loadingOlder = true
        viewModelScope.launch(dispatcher) {
            try {
                val resp = authed { api.messages(it, id, before = cur) }
                val have = chatMessages.mapTo(HashSet()) { m -> m.id }
                val older = resp.messages.filter { m -> m.id !in have }
                chatMessages = older + chatMessages
                chatCursor = resp.nextCursor
            } catch (e: Exception) { error = e.message } finally { loadingOlder = false }
        }
    }

    fun closeChat() { chatJob?.cancel(); chatJob = null; chatChildId = null; chatMessages = emptyList(); chatCursor = null }

    // ---- App usage (screen time) ----
    var appUsage by mutableStateOf<AppUsageSummary?>(null)
        private set
    var appUsageChildId by mutableStateOf<String?>(null)
        private set
    var loadingUsage by mutableStateOf(false)
        private set

    fun loadAppUsage(childId: String) {
        appUsageChildId = childId
        if (token == null) return
        appUsage = null; loadingUsage = true
        viewModelScope.launch(dispatcher) {
            try { appUsage = authed { api.appUsage(it, childId) } }
            catch (e: Exception) { error = e.message }
            finally { loadingUsage = false }
        }
    }

    fun sendChat(body: String) {
        val id = chatChildId ?: return
        if (token == null || body.isBlank()) return
        sending = true
        viewModelScope.launch(dispatcher) {
            try {
                val m = authed { api.sendMessage(it, id, body.trim()) }
                if (chatMessages.none { e -> e.id == m.id }) chatMessages = chatMessages + m
            } catch (e: Exception) { error = e.message } finally { sending = false }
        }
    }

    var biometricLock by mutableStateOf(store.biometricLock)
        private set
    var alertsEnabled by mutableStateOf(store.alertsEnabled)
        private set

    val selected: Child? get() = children.find { it.id == selectedId }

    fun clearError() { error = null }

    fun updateBiometricLock(v: Boolean) { store.biometricLock = v; biometricLock = v }
    fun updateAlertsEnabled(v: Boolean) { store.alertsEnabled = v; alertsEnabled = v }

    private fun registerPushToken() {
        if (token == null) return
        viewModelScope.launch(dispatcher) {
            try {
                val fcmToken = parentFcmTokenOrNull() ?: return@launch
                authed { api.registerParentPushToken(it, fcmToken) }
            } catch (_: Exception) {
                // Push registration should never block the parent app from opening.
            }
        }
    }

    fun updateChild(name: String, avatar: String? = null) {
        val id = selectedId ?: return
        if (token == null || name.isBlank()) return
        viewModelScope.launch(dispatcher) {
            try { authed { api.updateChild(it, id, name.trim(), avatar) }; refreshChildren() }
            catch (e: Exception) { error = e.message }
        }
    }

    fun renameChild(name: String) = updateChild(name)

    fun removeChild(childId: String) {
        if (token == null) return
        viewModelScope.launch(dispatcher) {
            try {
                authed { api.deleteChild(it, childId) }
                if (selectedId == childId) {
                    selectedId = children.firstOrNull { it.id != childId }?.id
                    location = null
                    alerts = emptyList()
                }
                refreshChildren()
            } catch (e: Exception) { error = e.message }
        }
    }

    /** Runs an authed call with the current access token; on a 401, refreshes the
     *  token once and retries. If the refresh also fails, signs the user out. */
    private suspend fun <T> authed(call: suspend (String) -> T): T {
        val t = token ?: throw ApiException(401, "Not signed in")
        return try {
            call(t)
        } catch (e: ApiException) {
            val rt = store.parentRefreshToken
            if (e.status == 401 && rt != null) {
                val fresh = try { api.refreshTokens(rt) } catch (_: Exception) {
                    logout(); throw ApiException(401, "Session expired — please sign in again")
                }
                store.parentToken = fresh.accessToken
                store.parentRefreshToken = fresh.refreshToken
                token = fresh.accessToken
                registerPushToken()
                call(fresh.accessToken)
            } else throw e
        }
    }

    fun authenticate(email: String, password: String, register: Boolean) {
        error = null; busy = true
        viewModelScope.launch(dispatcher) {
            try {
                val t = if (register) api.register(email, password) else api.login(email, password)
                store.deviceToken = null
                store.parentToken = t.accessToken
                store.parentRefreshToken = t.refreshToken
                token = t.accessToken
                registerPushToken()
                refreshChildren()
            } catch (e: Exception) { error = e.message } finally { busy = false }
        }
    }

    /** Exchange a verified Google ID token for our session tokens. */
    fun googleSignIn(idToken: String) {
        error = null; busy = true
        viewModelScope.launch(dispatcher) {
            try {
                val t = api.googleLogin(idToken)
                store.deviceToken = null
                store.parentToken = t.accessToken
                store.parentRefreshToken = t.refreshToken
                token = t.accessToken
                registerPushToken()
                refreshChildren()
            } catch (e: Exception) { error = e.message } finally { busy = false }
        }
    }

    /** Surface an error raised outside the ViewModel (e.g. the credential picker). */
    fun showError(message: String?) { error = message }

    fun logout() {
        store.parentToken = null; store.parentRefreshToken = null
        token = null; children = emptyList(); selectedId = null
    }

    fun refreshChildren() {
        if (token == null) return
        loadingChildren = true
        viewModelScope.launch(dispatcher) {
            try {
                children = authed { api.listChildren(it) }
                if (selectedId != null && children.none { c -> c.id == selectedId }) {
                    selectedId = children.firstOrNull()?.id
                    location = null
                    alerts = emptyList()
                }
                if (selectedId == null) children.firstOrNull()?.let { select(it.id) }
            } catch (e: Exception) { error = e.message }
            finally { loadingChildren = false; firstLoad = false }
        }
    }

    fun addChild(name: String) {
        if (token == null) return
        if (children.size >= 5) {
            error = "Your free tier allows up to 5 monitored children."
            return
        }
        viewModelScope.launch(dispatcher) {
            try {
                val c = authed { api.createChild(it, name) }
                refreshChildren()
                select(c.id)
            } catch (e: Exception) { error = e.message }
        }
    }

    fun select(id: String) {
        selectedId = id; pairingCode = null; location = null; alerts = emptyList()
        zones = emptyList(); history = emptyList(); distanceKm = 0.0
        frequentRoutes = emptyList(); trips = emptyList()
        refreshDetail()
        loadZones()
        loadHistory(today())
        loadRoutes()
    }

    private var liveJob: Job? = null

    /** Real-time tracking: poll every child's location + status on an interval so the
     *  parent sees the whole family move live without tapping refresh. */
    fun startLive(intervalMs: Long = 4000) {
        liveJob?.cancel()
        liveJob = viewModelScope.launch(dispatcher) {
            while (isActive) {
                if (token != null) {
                    try {
                        val kids = authed { api.listChildren(it) }
                        children = kids
                        val locs = HashMap<String, CurrentLocation>()
                        for (c in kids) authed { api.currentLocation(it, c.id) }?.let { locs[c.id] = it }
                        allLocations = locs
                        selectedId?.let { location = locs[it] }
                    } catch (_: Exception) { /* stay quiet during live polling */ }
                }
                delay(intervalMs)
            }
        }
    }

    /** Loads the family overview for the dashboard: each child's recent places
     *  (from detected stops) and recent alerts, tagged by child. */
    fun loadFamilyOverview() {
        if (token == null) return
        viewModelScope.launch(dispatcher) {
            try {
                val kids = authed { api.listChildren(it) }
                children = kids
                val places = ArrayList<TopPlace>()
                val alerts = ArrayList<FamilyAlert>()
                for (c in kids) {
                    val r = authed { api.routes(it, c.id) }
                    r.stops.forEach { s -> places.add(TopPlace(c.id, c.displayName, s.lat, s.lng, s.arriveAt, s.departAt)) }
                    authed { api.alerts(it, c.id) }.take(3).forEach { a -> alerts.add(FamilyAlert(c.id, c.displayName, a)) }
                }
                topPlaces = places.sortedByDescending { it.arriveAt }.take(6)
                familyAlerts = alerts.sortedByDescending { it.alert.createdAt }.take(6)
            } catch (e: Exception) { error = e.message }
        }
    }

    fun stopLive() { liveJob?.cancel(); liveJob = null }

    override fun onCleared() { stopLive(); chatJob?.cancel(); super.onCleared() }

    fun loadRoutes() {
        val id = selectedId ?: return
        if (token == null) return
        viewModelScope.launch(dispatcher) {
            try {
                val r = authed { api.routes(it, id) }
                frequentRoutes = r.frequent
                trips = r.trips
            } catch (e: Exception) { error = e.message }
        }
    }

    fun loadZones() {
        val id = selectedId ?: return
        if (token == null) return
        viewModelScope.launch(dispatcher) {
            try { zones = authed { api.listZones(it, id) } } catch (e: Exception) { error = e.message }
        }
    }

    fun addZone(name: String, lat: Double, lng: Double, radiusM: Int) {
        val id = selectedId ?: return
        if (token == null) return
        viewModelScope.launch(dispatcher) {
            try { authed { api.createZone(it, id, name, lat, lng, radiusM) }; loadZones() }
            catch (e: Exception) { error = e.message }
        }
    }

    fun removeZone(zoneId: String) {
        val id = selectedId ?: return
        if (token == null) return
        viewModelScope.launch(dispatcher) {
            try { authed { api.deleteZone(it, id, zoneId) }; loadZones() }
            catch (e: Exception) { error = e.message }
        }
    }

    fun loadHistory(date: String) {
        val id = selectedId ?: return
        if (token == null) return
        historyDate = date
        viewModelScope.launch(dispatcher) {
            try {
                history = authed { api.locationHistory(it, id, date) }
                distanceKm = haversineKm(history)
            } catch (e: Exception) { error = e.message }
        }
    }

    fun refreshDetail() {
        val id = selectedId ?: return
        if (token == null) return
        loadingDetail = true
        viewModelScope.launch(dispatcher) {
            try {
                location = authed { api.currentLocation(it, id) }
                alerts = authed { api.alerts(it, id) }
                children = authed { api.listChildren(it) }
            } catch (e: Exception) {
                if (e is ApiException && e.status == 404) {
                    error = "This child is no longer linked to your account."
                    refreshChildren()
                } else {
                    error = e.message
                }
            }
            finally { loadingDetail = false }
        }
    }

    fun generateCode() {
        val id = selectedId ?: return
        if (token == null) return
        viewModelScope.launch(dispatcher) {
            try { pairingCode = authed { api.pairingCode(it, id) }.code }
            catch (e: Exception) { error = e.message }
        }
    }

    private fun today() = java.time.LocalDate.now().toString()

    /** Total path length (km) across the day's location points. */
    private fun haversineKm(points: List<HistoryPoint>): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]; val b = points[i]
            val dLat = Math.toRadians(b.lat - a.lat); val dLng = Math.toRadians(b.lng - a.lng)
            val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
            sum += 6371.0 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
        }
        return sum
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer {
                ParentViewModel(HttpApiClient(), PrefsTokenStore(context.applicationContext))
            }
        }
    }
}
