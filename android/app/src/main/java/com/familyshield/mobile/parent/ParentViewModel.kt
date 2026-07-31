package com.familyshield.mobile.parent

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.FirebaseTooManyRequestsException
import com.familyshield.mobile.net.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** A place a child visited today (from detected stops). */
data class TopPlace(val childId: String, val childName: String, val lat: Double, val lng: Double, val arriveAt: String, val departAt: String)

/** An alert tagged with the child it belongs to. */
data class FamilyAlert(val childId: String, val childName: String, val alert: Alert)

enum class ParentAuthUiError {
    TooManyAttempts,
}

internal fun isAuthenticationThrottleMessage(message: String?): Boolean {
    val normalized = message?.trim()?.lowercase() ?: return false
    return normalized.contains("too many attempts") ||
        normalized.contains("unusual activity") ||
        normalized.contains("blocked all requests from this device")
}

private const val MAX_CHAT_MESSAGES = 1_000
private const val CHAT_POLL_MIN_MS = 3_000L
private const val CHAT_POLL_MAX_MS = 30_000L

class ParentViewModel(
    private val api: ApiClient,
    private val store: TokenStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val authGateway: ParentAuthGateway,
) : ViewModel() {
    private var sessionGeneration = 0L

    var authState by mutableStateOf(authGateway.currentState())
        private set
    var token by mutableStateOf<String?>(null)
        private set
    var authNotice by mutableStateOf<String?>(null)
        private set
    var authUiError by mutableStateOf<ParentAuthUiError?>(null)
        private set
    var canResendVerification by mutableStateOf(true)
        private set
    private val removeAuthStateListener: () -> Unit

    init {
        removeAuthStateListener = authGateway.observeState { observed ->
            if (observed is ParentAuthState.SignedOut && authState !is ParentAuthState.SignedOut) {
                viewModelScope.launch(dispatcher) {
                    authState = ParentAuthState.SignedOut
                    token = null
                    clearParentSessionData()
                }
            }
        }
        if (authState is ParentAuthState.SignedIn) {
            viewModelScope.launch(dispatcher) {
                try {
                    completeSignedIn(authState, refreshData = false)
                } catch (failure: Exception) {
                    error = failure.message
                    expireSession()
                }
            }
        }
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
    var historyDays by mutableStateOf<List<String>>(emptyList())
        private set
    var distanceKm by mutableStateOf(0.0)
        private set
    var frequentRoutes by mutableStateOf<List<FrequentRoute>>(emptyList())
        private set
    var frequentLocations by mutableStateOf<List<FrequentLocation>>(emptyList())
        private set
    var trips by mutableStateOf<List<RouteTrip>>(emptyList())
        private set
    var stops by mutableStateOf<List<Stop>>(emptyList())
        private set

    // Family-overview (multi-child dashboard) state.
    var allLocations by mutableStateOf<Map<String, CurrentLocation>>(emptyMap())
        private set
    var mapZonesByChild by mutableStateOf<Map<String, List<Zone>>>(emptyMap())
        private set
    var topPlaces by mutableStateOf<List<TopPlace>>(emptyList())
        private set
    var familyAlerts by mutableStateOf<List<FamilyAlert>>(emptyList())
        private set
    var allFamilyAlerts by mutableStateOf<List<FamilyAlert>>(emptyList())
        private set
    var sosByChild by mutableStateOf<Map<String, SosState>>(emptyMap())
        private set
    var acknowledgedSosEventIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var urgentSending by mutableStateOf(false)
        private set
    var loadingAllAlerts by mutableStateOf(false)
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
    private var chatLatestCursor: String? = null

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
        if (chatChildId == childId && chatJob?.isActive == true) return
        chatChildId = childId
        chatMessages = emptyList()
        chatCursor = null
        chatLatestCursor = null
        if (selectedId != childId) select(childId)   // load zones/location for the safe-zone card
        chatJob?.cancel()
        chatJob = viewModelScope.launch(dispatcher) {
            try {
                val resp = authed { api.messages(it, childId, markRead = true) }
                chatMessages = resp.messages
                chatCursor = resp.nextCursor
                chatLatestCursor = resp.latestCursor
            } catch (_: CancellationException) {
                return@launch
            } catch (e: Exception) { error = e.message }
            var pollDelay = CHAT_POLL_MIN_MS
            while (isActive) {
                delay(pollDelay)
                try {
                    val response = authed { api.messages(it, childId, after = chatLatestCursor, markRead = true) }
                    val delta = response.messages
                    val have = chatMessages.mapTo(HashSet()) { m -> m.id }
                    val fresh = delta.filter { m -> m.id !in have }
                    response.latestCursor?.let { chatLatestCursor = it }
                    if (fresh.isNotEmpty()) {
                        chatMessages = (chatMessages + fresh).takeLast(MAX_CHAT_MESSAGES)
                        pollDelay = CHAT_POLL_MIN_MS
                    } else {
                        pollDelay = (pollDelay * 2).coerceAtMost(CHAT_POLL_MAX_MS)
                    }
                } catch (_: CancellationException) {
                    return@launch
                } catch (_: Exception) {
                    pollDelay = (pollDelay * 2).coerceAtMost(CHAT_POLL_MAX_MS)
                }
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
                val available = (MAX_CHAT_MESSAGES - chatMessages.size).coerceAtLeast(0)
                val older = resp.messages.filter { m -> m.id !in have }.takeLast(available)
                chatMessages = older + chatMessages
                chatCursor = if (chatMessages.size >= MAX_CHAT_MESSAGES) null else resp.nextCursor
            } catch (e: Exception) { error = e.message } finally { loadingOlder = false }
        }
    }

    fun closeChat() { chatJob?.cancel(); chatJob = null; chatChildId = null; chatMessages = emptyList(); chatCursor = null; chatLatestCursor = null }

    // ---- App usage (screen time) ----
    var appUsage by mutableStateOf<AppUsageSummary?>(null)
        private set
    var appUsageChildId by mutableStateOf<String?>(null)
        private set
    var appUsageDate by mutableStateOf<String?>(null)
        private set
    var loadingUsage by mutableStateOf(false)
        private set

    fun selectAppUsageDate(date: String) {
        appUsageDate = date
    }

    fun loadAppUsage(childId: String, date: String? = null) {
        appUsageChildId = childId
        appUsageDate = date
        if (token == null) return
        appUsage = null; loadingUsage = true
        viewModelScope.launch(dispatcher) {
            try { appUsage = authed { api.appUsage(it, childId, date) } }
            catch (e: Exception) { error = e.message }
            finally { loadingUsage = false }
        }
    }

    fun saveAppUsageLimit(childId: String, body: AppUsageLimitBody) {
        if (token == null) return
        viewModelScope.launch(dispatcher) {
            try {
                val limit = authed { api.saveAppUsageLimit(it, childId, body) }
                applyAppUsageLimit(limit)
            } catch (e: Exception) { error = e.message }
        }
    }

    fun updateAppUsageLimit(childId: String, limitId: String, body: UpdateAppUsageLimitBody) {
        if (token == null) return
        viewModelScope.launch(dispatcher) {
            try {
                val limit = authed { api.updateAppUsageLimit(it, childId, limitId, body) }
                applyAppUsageLimit(limit)
            } catch (e: Exception) { error = e.message }
        }
    }

    fun deleteAppUsageLimit(childId: String, limitId: String) {
        if (token == null) return
        viewModelScope.launch(dispatcher) {
            try {
                authed { api.deleteAppUsageLimit(it, childId, limitId) }
                appUsage = appUsage?.copy(limits = appUsage?.limits.orEmpty().filterNot { it.id == limitId })
            } catch (e: Exception) { error = e.message }
        }
    }

    private fun applyAppUsageLimit(limit: AppUsageLimit) {
        appUsage = appUsage?.let { usage ->
            val limits = usage.limits.filterNot { existing -> existing.id == limit.id } + limit
            usage.copy(limits = limits)
        } ?: AppUsageSummary(limits = listOf(limit))
    }

    fun sendChat(body: String, onSent: (() -> Unit)? = null) {
        val id = chatChildId ?: return
        if (token == null || body.isBlank()) return
        sending = true
        viewModelScope.launch(dispatcher) {
            try {
                val m = authed { api.sendMessage(it, id, body.trim()) }
                if (chatMessages.none { e -> e.id == m.id }) chatMessages = chatMessages + m
                onSent?.invoke()
            } catch (e: Exception) { error = e.message } finally { sending = false }
        }
    }

    var biometricLock by mutableStateOf(store.biometricLock)
        private set
    var alertsEnabled by mutableStateOf(store.alertsEnabled)
        private set

    val selected: Child? get() = children.find { it.id == selectedId }

    fun clearError() {
        error = null
        authUiError = null
    }

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

    fun sendUrgentAlert(childId: String, body: String) {
        if (token == null || body.isBlank()) return
        urgentSending = true
        viewModelScope.launch(dispatcher) {
            try {
                val result = authed { api.sendUrgentAlert(it, childId, body.trim()) }
                val message = result.message
                if (chatChildId == childId && chatMessages.none { it.id == message.id }) {
                    chatMessages = chatMessages + message
                }
                loadFamilyOverview()
            } catch (e: Exception) { error = e.message } finally { urgentSending = false }
        }
    }

    fun acknowledgeSos(childId: String, eventId: String) {
        if (token == null) return
        viewModelScope.launch(dispatcher) {
            try {
                authed { api.acknowledgeSos(it, childId, eventId) }
                acknowledgedSosEventIds = acknowledgedSosEventIds + eventId
                loadSosStates(children)
            } catch (e: Exception) { error = e.message }
        }
    }

    fun updateChild(name: String, avatar: String? = null, phoneNumber: String? = selected?.phoneNumber) {
        val id = selectedId ?: return
        if (token == null || name.isBlank()) return
        viewModelScope.launch(dispatcher) {
            try { authed { api.updateChild(it, id, name.trim(), avatar, phoneNumber?.trim()?.takeIf { number -> number.isNotEmpty() }) }; refreshChildren() }
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
        val generation = sessionGeneration
        if (token == null) throw ApiException(401, "Not signed in")
        val t = authGateway.idToken()
        return try {
            call(t).also { ensureCurrentSession(generation) }
        } catch (e: ApiException) {
            if (e.status == 401 && e.code != "invalid_app_check") {
                ensureCurrentSession(generation)
                val refreshed = try { authGateway.idToken(forceRefresh = true) } catch (_: Exception) {
                    expireSession()
                    throw ApiException(401, "Session expired — please sign in again")
                }
                try {
                    call(refreshed).also { ensureCurrentSession(generation) }
                } catch (retry: ApiException) {
                    if (retry.status == 401) expireSession()
                    throw retry
                }
            } else throw e
        }
    }

    fun authenticate(email: String, password: String, register: Boolean) {
        clearError(); authNotice = null; busy = true
        viewModelScope.launch(dispatcher) {
            try {
                val state = if (register) {
                    authGateway.register(email, password)
                } else {
                    try {
                        authGateway.login(email, password)
                    } catch (_: LegacyMigrationCandidateException) {
                        val migrated = api.legacyMigrate(email, password)
                        authGateway.signInWithCustomToken(migrated.customToken)
                    }
                }
                completeSignedIn(state)
            } catch (e: Exception) { setAuthFailure(e) } finally { busy = false }
        }
    }

    /** Exchange a verified Google ID token for our session tokens. */
    fun googleSignIn(idToken: String) {
        clearError(); authNotice = null; busy = true
        viewModelScope.launch(dispatcher) {
            try {
                completeSignedIn(authGateway.google(idToken))
            } catch (e: Exception) { setAuthFailure(e) } finally { busy = false }
        }
    }

    fun forgotPassword(email: String) {
        error = null
        authNotice = "password_reset_sent"
        viewModelScope.launch(dispatcher) {
            // Keep the confirmation identical for existing and unknown accounts.
            runCatching { authGateway.sendPasswordReset(email.trim()) }
        }
    }

    fun resendVerification() {
        if (!canResendVerification) return
        clearError()
        canResendVerification = false
        viewModelScope.launch(dispatcher) {
            try {
                authGateway.resendVerification()
                authNotice = "verification_resent"
            } catch (e: ParentAuthSessionExpiredException) {
                authState = ParentAuthState.Expired
                token = null
                error = e.message
            } catch (e: Exception) { setAuthFailure(e) }
            finally {
                delay(60_000)
                canResendVerification = true
            }
        }
    }

    fun refreshVerification() {
        clearError(); busy = true
        viewModelScope.launch(dispatcher) {
            try { completeSignedIn(authGateway.refreshVerification()) }
            catch (e: Exception) { setAuthFailure(e) }
            finally { busy = false }
        }
    }

    fun signOutAllDevicesWithPassword(password: String) {
        clearError(); busy = true
        viewModelScope.launch(dispatcher) {
            try {
                authGateway.reauthenticateWithPassword(password)
                api.revokeParentSessions(authGateway.idToken(forceRefresh = true))
                logout()
            } catch (e: Exception) { setAuthFailure(e) }
            finally { busy = false }
        }
    }

    fun signOutAllDevicesWithGoogle(idToken: String) {
        clearError(); busy = true
        viewModelScope.launch(dispatcher) {
            try {
                authGateway.reauthenticateWithGoogle(idToken)
                api.revokeParentSessions(authGateway.idToken(forceRefresh = true))
                logout()
            } catch (e: Exception) { setAuthFailure(e) }
            finally { busy = false }
        }
    }

    /** Surface an error raised outside the ViewModel (e.g. the credential picker). */
    fun showError(message: String?) {
        authUiError = null
        error = message
    }

    private fun setAuthFailure(failure: Throwable) {
        val throttled = generateSequence(failure as Throwable?) { it.cause }
            .any {
                it is FirebaseTooManyRequestsException ||
                    it is ParentAuthTooManyAttemptsException
            }
        authUiError = if (throttled) ParentAuthUiError.TooManyAttempts else null
        error = if (throttled) null else failure.message
    }

    fun logout() {
        authGateway.logout()
        authState = ParentAuthState.SignedOut
        token = null
        clearParentSessionData()
    }

    private fun expireSession() {
        authGateway.logout()
        authState = ParentAuthState.Expired
        token = null
        clearParentSessionData()
    }

    private suspend fun completeSignedIn(state: ParentAuthState, refreshData: Boolean = true) {
        if (state !is ParentAuthState.SignedIn) {
            authState = state
            token = null
            clearParentSessionData()
            return
        }
        clearParentSessionData(cancelOngoingWork = false)
        authState = state
        val idToken = authGateway.idToken()
        token = idToken
        val generation = sessionGeneration
        api.bootstrapParent(idToken)
        ensureCurrentSession(generation)
        registerPushToken()
        if (refreshData) refreshChildren()
    }

    private fun ensureCurrentSession(generation: Long) {
        if (generation != sessionGeneration || token == null) throw CancellationException()
    }

    /** Remove every piece of parent/account-derived UI state before another account can use this ViewModel. */
    private fun clearParentSessionData(cancelOngoingWork: Boolean = true) {
        sessionGeneration++
        if (cancelOngoingWork) viewModelScope.coroutineContext.cancelChildren()
        stopLive()
        closeChat()

        children = emptyList()
        selectedId = null
        dashboardChildId = null
        location = null
        allLocations = emptyMap()
        alerts = emptyList()
        familyAlerts = emptyList()
        allFamilyAlerts = emptyList()
        pairingCode = null

        zones = emptyList()
        mapZonesByChild = emptyMap()
        history = emptyList()
        historyDate = today()
        historyDays = emptyList()
        distanceKm = 0.0
        frequentRoutes = emptyList()
        frequentLocations = emptyList()
        trips = emptyList()
        stops = emptyList()
        topPlaces = emptyList()

        sosByChild = emptyMap()
        acknowledgedSosEventIds = emptySet()
        urgentSending = false
        unreadByChild = emptyMap()
        lastMessageByChild = emptyMap()
        sending = false
        loadingOlder = false

        appUsage = null
        appUsageChildId = null
        appUsageDate = null
        loadingUsage = false

        error = null
        authNotice = null
        authUiError = null
        loadingChildren = false
        loadingDetail = false
        loadingAllAlerts = false
        firstLoad = true
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

    fun addChild(name: String, phoneNumber: String? = null) {
        if (token == null) return
        if (children.size >= 5) {
            error = "Your free tier allows up to 5 monitored children."
            return
        }
        viewModelScope.launch(dispatcher) {
            try {
                val c = authed { api.createChild(it, name, phoneNumber = phoneNumber?.trim()?.takeIf { number -> number.isNotEmpty() }) }
                refreshChildren()
                select(c.id)
            } catch (e: Exception) { error = e.message }
        }
    }

    fun select(id: String, historyDate: String = today()) {
        selectedId = id; pairingCode = null; location = null; alerts = emptyList()
        zones = mapZonesByChild[id] ?: emptyList(); history = emptyList(); historyDays = emptyList(); distanceKm = 0.0
        frequentRoutes = emptyList(); frequentLocations = emptyList(); trips = emptyList(); stops = emptyList()
        refreshDetail()
        loadZones()
        loadHistory(historyDate)
        loadHistoryDays()
        loadRoutes()
    }

    fun selectMapChild(id: String) {
        selectedId = id; pairingCode = null; alerts = emptyList()
        location = allLocations[id]
        zones = mapZonesByChild[id] ?: emptyList()
        history = emptyList(); historyDays = emptyList(); distanceKm = 0.0
        frequentRoutes = emptyList(); frequentLocations = emptyList(); trips = emptyList(); stops = emptyList()
        loadZones()
        loadHistory(today())
        loadHistoryDays()
        loadRoutes()
    }

    fun ensureMapTargetChild(): String? {
        val current = selectedId
        val target = if (current != null && allLocations.containsKey(current)) {
            current
        } else {
            children.firstOrNull { c -> allLocations.containsKey(c.id) }?.id ?: allLocations.keys.firstOrNull()
        }
        if (target != null && target != selectedId) selectMapChild(target)
        return target
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
                        location = selectedId?.let { locs[it] }
                        loadSosStates(kids)
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
                val sos = HashMap<String, SosState>()
                for (c in kids) {
                    val r = authed { api.routes(it, c.id) }
                    r.stops.filter { s -> overlapsToday(s.arriveAt, s.departAt) }.forEach { s -> places.add(TopPlace(c.id, c.displayName, s.lat, s.lng, s.arriveAt, s.departAt)) }
                    authed { api.alerts(it, c.id) }.take(3).forEach { a -> alerts.add(FamilyAlert(c.id, c.displayName, a)) }
                    val state = authed { api.currentSos(it, c.id) }
                    if (state.active) sos[c.id] = state
                }
                topPlaces = places.sortedByDescending { it.arriveAt }.take(6)
                familyAlerts = alerts.sortedByDescending { it.alert.createdAt }.take(6)
                sosByChild = sos
            } catch (e: Exception) { error = e.message }
        }
    }

    private suspend fun loadSosStates(kids: List<Child>) {
        if (token == null || kids.isEmpty()) return
        val sos = HashMap<String, SosState>()
        for (c in kids) {
            val state = authed { api.currentSos(it, c.id) }
            if (state.active) sos[c.id] = state
        }
        sosByChild = sos
        val activeEventIds = sos.values.mapNotNull { it.event?.id }.toSet()
        acknowledgedSosEventIds = acknowledgedSosEventIds.intersect(activeEventIds)
    }

    fun loadAllFamilyAlerts() {
        if (token == null) return
        loadingAllAlerts = true
        viewModelScope.launch(dispatcher) {
            try {
                val kids = authed { api.listChildren(it) }
                children = kids
                val alerts = ArrayList<FamilyAlert>()
                for (c in kids) {
                    authed { api.alerts(it, c.id) }.forEach { a -> alerts.add(FamilyAlert(c.id, c.displayName, a)) }
                }
                allFamilyAlerts = alerts.sortedByDescending { it.alert.createdAt }
            } catch (e: Exception) { error = e.message }
            finally { loadingAllAlerts = false }
        }
    }

    fun stopLive() { liveJob?.cancel(); liveJob = null }

    override fun onCleared() {
        removeAuthStateListener()
        stopLive()
        chatJob?.cancel()
        super.onCleared()
    }

    fun loadRoutes() {
        val id = selectedId ?: return
        if (token == null) return
        viewModelScope.launch(dispatcher) {
            try {
                val r = authed { api.routes(it, id) }
                frequentRoutes = r.frequent
                frequentLocations = r.frequentLocations
                trips = r.trips
                stops = r.stops
            } catch (e: Exception) { error = e.message }
        }
    }

    fun loadZones() {
        val id = selectedId ?: return
        if (token == null) return
        viewModelScope.launch(dispatcher) {
            try {
                val loaded = authed { api.listZones(it, id) }
                applySharedZones(loaded)
            } catch (e: Exception) { error = e.message }
        }
    }

    fun loadHistoryDays() {
        val id = selectedId ?: return
        if (token == null) return
        viewModelScope.launch(dispatcher) {
            try {
                historyDays = authed { api.locationHistoryDays(it, id, HISTORY_DAY_RANGE_DAYS) }
            } catch (e: Exception) { error = e.message }
        }
    }

    fun loadMapZones() {
        if (token == null) return
        viewModelScope.launch(dispatcher) {
            try {
                val kids = if (children.isNotEmpty()) children else authed { api.listChildren(it) }.also { children = it }
                val anchor = selectedId ?: kids.firstOrNull()?.id ?: return@launch
                val loaded = authed { api.listZones(it, anchor) }
                applySharedZones(loaded)
            } catch (e: Exception) { error = e.message }
        }
    }

    fun addZone(name: String, lat: Double, lng: Double, radiusM: Int) {
        val id = selectedId ?: return
        addZone(name, id, lat, lng, radiusM)
    }

    fun addZone(name: String, centerChildId: String, lat: Double, lng: Double, radiusM: Int) {
        if (token == null) return
        viewModelScope.launch(dispatcher) {
            try { authed { api.createZone(it, centerChildId, name, lat, lng, radiusM) }; loadZones() }
            catch (e: Exception) { error = e.message }
        }
    }

    fun updateZone(zoneId: String, name: String, radiusM: Int, active: Boolean) {
        val id = selectedId ?: children.firstOrNull()?.id ?: return
        if (token == null) return
        viewModelScope.launch(dispatcher) {
            try { authed { api.updateZone(it, id, zoneId, name.trim(), radiusM, active) }; loadZones() }
            catch (e: Exception) { error = e.message }
        }
    }

    fun removeZone(zoneId: String) {
        val id = selectedId ?: children.firstOrNull()?.id ?: return
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

    private fun overlapsToday(arriveAt: String, departAt: String): Boolean = runCatching {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val arriveDay = OffsetDateTime.parse(arriveAt).atZoneSameInstant(zone).toLocalDate()
        val departDay = OffsetDateTime.parse(departAt).atZoneSameInstant(zone).toLocalDate()
        !arriveDay.isAfter(today) && !departDay.isBefore(today)
    }.getOrDefault(false)

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

    private fun applySharedZones(loaded: List<Zone>) {
        zones = loaded
        mapZonesByChild = children.associate { it.id to loaded }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer {
                val auth = FirebaseParentAuthGateway()
                val api = HttpApiClient(
                    appCheckTokenProvider = { forceRefresh ->
                        auth.appCheckToken(forceRefresh)
                    },
                )
                ParentViewModel(
                    api = api,
                    store = PrefsTokenStore(context.applicationContext),
                    authGateway = auth,
                )
            }
        }
    }
}
