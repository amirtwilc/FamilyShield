package com.familyshield.mobile.kid

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.familyshield.mobile.R
import com.familyshield.mobile.net.ApiClient
import com.familyshield.mobile.net.ApiException
import com.familyshield.mobile.net.HttpApiClient
import com.familyshield.mobile.net.Message
import com.familyshield.mobile.net.Monitor
import com.familyshield.mobile.net.PrefsTokenStore
import com.familyshield.mobile.net.SosState
import com.familyshield.mobile.net.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class KidViewModel(
    private val api: ApiClient,
    private val store: TokenStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val onSimulationChanged: (Context) -> Unit = ::notifyKidSimulationChanged,
) : ViewModel() {

    var deviceToken by mutableStateOf(store.deviceToken)
        private set

    var lat by mutableStateOf<Double?>(null)
    var lng by mutableStateOf<Double?>(null)
    var battery by mutableStateOf<Int?>(null)
    var charging by mutableStateOf<Boolean?>(null)
    var telemetryUpdatedAt by mutableStateOf<String?>(null)
        private set
    var appUsageAccessGranted by mutableStateOf(false)
        private set
    var simulationConfig by mutableStateOf<LocationSimulationConfig?>(null)
        private set
    var simulationBusy by mutableStateOf(false)
        private set

    val canSimulateLocation: Boolean
        get() = monitors.allowsLocationSimulation()

    var message by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var monitors by mutableStateOf<List<Monitor>>(emptyList())
        private set
    var sosState by mutableStateOf(SosState())
        private set
    var sosBusy by mutableStateOf(false)
        private set

    init {
        deviceToken?.let { refreshMonitoring(it) }
    }

    fun clearError() { error = null }
    fun clearMessage() { message = null }

    fun pair(code: String, platform: String, parentDisplayName: String) {
        error = null; message = null; busy = true
        viewModelScope.launch(dispatcher) {
            try {
                val r = api.pair(code.trim(), platform, AndroidTelemetry.deviceModel(), parentDisplayName.trim())
                store.deviceToken = r.deviceToken
                deviceToken = r.deviceToken
                refreshMonitoring(r.deviceToken)
            } catch (e: Exception) { error = e.message } finally { busy = false }
        }
    }

    fun addParent(code: String, platform: String, parentDisplayName: String) {
        val t = deviceToken ?: return
        error = null; message = null; busy = true
        viewModelScope.launch(dispatcher) {
            try {
                val info = api.addParent(t, code.trim(), platform, AndroidTelemetry.deviceModel(), parentDisplayName.trim())
                monitors = info.monitors
                message = "Parent added."
            } catch (e: Exception) { error = e.message } finally { busy = false }
        }
    }

    fun updateMonitorName(parentId: String, parentDisplayName: String) {
        val t = deviceToken ?: return
        error = null; message = null; busy = true
        viewModelScope.launch(dispatcher) {
            try {
                monitors = api.updateMonitorName(t, parentId, parentDisplayName.trim()).monitors
            } catch (e: Exception) {
                if (!handleDeviceAuthError(e)) error = e.message
            } finally {
                busy = false
            }
        }
    }

    fun refreshMonitoring() {
        val t = deviceToken ?: return
        refreshMonitoring(t)
    }

    fun refreshSimulationState(context: Context) {
        simulationConfig = LocationSimulationStore(context.applicationContext).activeConfig()
    }

    fun startFixedSimulation(context: Context, point: SimulationWaypoint) {
        authorizeSimulation(context) { store -> store.startFixed(point) }
    }

    fun startRouteSimulation(
        context: Context,
        waypoints: List<SimulationWaypoint>,
        speedMps: Double,
        dwellMinutes: Int,
        loop: Boolean,
    ) {
        authorizeSimulation(context) { store ->
            store.startRoute(waypoints, speedMps, dwellMinutes, loop)
        }
    }

    fun pauseSimulation(context: Context) {
        simulationConfig = LocationSimulationStore(context.applicationContext).pause()
        onSimulationChanged(context)
    }

    fun resumeSimulation(context: Context) {
        authorizeSimulation(context) { store -> store.resume() ?: error("No simulation to resume") }
    }

    fun stopSimulation(context: Context) {
        LocationSimulationStore(context.applicationContext).stop()
        simulationConfig = null
        onSimulationChanged(context)
        message = context.getString(R.string.kid_simulation_stopped)
    }

    private fun authorizeSimulation(
        context: Context,
        activate: (LocationSimulationStore) -> LocationSimulationConfig,
    ) {
        val token = deviceToken ?: return
        val app = context.applicationContext
        simulationBusy = true
        viewModelScope.launch(dispatcher) {
            try {
                val info = api.monitoring(token)
                monitors = info.monitors
                if (!canSimulateLocation) {
                    LocationSimulationStore(app).stop()
                    simulationConfig = null
                    onSimulationChanged(app)
                    error = app.getString(R.string.kid_simulation_admin_required)
                    return@launch
                }
                simulationConfig = activate(LocationSimulationStore(app))
                onSimulationChanged(app)
                message = app.getString(R.string.kid_simulation_started)
            } catch (e: Exception) {
                if (!handleDeviceAuthError(e)) error = e.message
            } finally {
                simulationBusy = false
            }
        }
    }

    private fun handleDeviceAuthError(e: Exception): Boolean {
        if (e is ApiException && e.status == 401) {
            stopChat()
            store.deviceToken = null
            deviceToken = null
            monitors = emptyList()
            sosState = SosState()
            message = "This device is no longer paired."
            return true
        }
        return false
    }

    private fun refreshMonitoring(token: String) {
        viewModelScope.launch(dispatcher) {
            try { monitors = api.monitoring(token).monitors } catch (e: Exception) { handleDeviceAuthError(e) }
        }
    }

    fun refreshSosState() {
        val t = deviceToken ?: return
        viewModelScope.launch(dispatcher) {
            try { sosState = api.sosState(t, localDay()) } catch (e: Exception) { handleDeviceAuthError(e) }
        }
    }

    fun startSos(context: Context) {
        val t = deviceToken ?: return
        val app = context.applicationContext
        sosBusy = true
        viewModelScope.launch(dispatcher) {
            try {
                val simulationStore = LocationSimulationStore(app)
                var simulationAllowed = simulationStore.activeConfig() == null
                if (!simulationAllowed) {
                    val info = runCatching { api.monitoring(t) }.getOrNull()
                    if (info != null) {
                        monitors = info.monitors
                        simulationAllowed = info.monitors.allowsLocationSimulation()
                        if (!simulationAllowed) {
                            simulationStore.stop()
                            simulationConfig = null
                            onSimulationChanged(app)
                        }
                    }
                }
                battery = AndroidTelemetry.readBattery(app)
                charging = AndroidTelemetry.readCharging(app)
                val location = if (simulationAllowed) {
                    currentKidLocationPoint(app, simulationStore, battery)?.also {
                        lat = it.lat
                        lng = it.lng
                    }
                } else null
                sosState = api.startSos(t, timezoneId(), localDay(), location)
                startKidMonitoring(app)
                message = app.getString(R.string.kid_sos_started_message)
            } catch (e: Exception) {
                if (!handleDeviceAuthError(e)) error = e.message
            } finally {
                sosBusy = false
            }
        }
    }

    fun endSos(context: Context) {
        val t = deviceToken ?: return
        val app = context.applicationContext
        sosBusy = true
        viewModelScope.launch(dispatcher) {
            try {
                sosState = api.endSos(t)
                message = app.getString(R.string.kid_sos_ended_message)
            } catch (e: Exception) {
                if (!handleDeviceAuthError(e)) error = e.message
            } finally {
                sosBusy = false
            }
        }
    }

    fun unpair() { stopChat(); store.deviceToken = null; deviceToken = null; monitors = emptyList(); sosState = SosState() }

    fun removeMonitor(parentId: String) {
        val t = deviceToken ?: return
        error = null; message = null; busy = true
        viewModelScope.launch(dispatcher) {
            try {
                val result = api.removeMonitor(t, parentId)
                if (result.unpaired) {
                    stopChat()
                    store.deviceToken = null
                    deviceToken = null
                    monitors = emptyList()
                    sosState = SosState()
                    message = "Device unpaired."
                } else {
                    monitors = result.monitors
                    message = "Parent removed."
                }
            } catch (e: Exception) { if (!handleDeviceAuthError(e)) error = e.message } finally { busy = false }
        }
    }

    // ---- Chat with parent ----
    var chatMessages by mutableStateOf<List<Message>>(emptyList())
        private set
    var sending by mutableStateOf(false)
        private set
    var chatCursor by mutableStateOf<String?>(null)
        private set
    var loadingOlder by mutableStateOf(false)
        private set
    private var chatJob: Job? = null
    private var chatParentId: String? = null
    private var chatLatestCursor: String? = null

    fun startChat(parentId: String? = null) {
        val t = deviceToken ?: return
        if (chatParentId == parentId && chatJob?.isActive == true) return
        chatJob?.cancel()
        chatParentId = parentId
        chatMessages = emptyList()
        chatCursor = null
        chatLatestCursor = null
        chatJob = viewModelScope.launch(dispatcher) {
            try {
                val response = if (parentId == null) api.deviceMessages(t)
                else api.monitorMessages(t, parentId)
                chatMessages = response.messages
                chatCursor = response.nextCursor
                chatLatestCursor = response.latestCursor
            } catch (_: CancellationException) {
                return@launch
            } catch (_: Exception) {}
            var pollDelay = 3_000L
            while (isActive) {
                delay(pollDelay)
                try {
                    val response = if (parentId == null) api.deviceMessages(t, chatLatestCursor)
                    else api.monitorMessages(t, parentId, chatLatestCursor)
                    val delta = response.messages
                    val have = chatMessages.mapTo(HashSet()) { it.id }
                    val fresh = delta.filter { it.id !in have }
                    response.latestCursor?.let { chatLatestCursor = it }
                    if (fresh.isNotEmpty()) {
                        chatMessages = (chatMessages + fresh).takeLast(1_000)
                        pollDelay = 3_000L
                    } else pollDelay = (pollDelay * 2).coerceAtMost(30_000L)
                } catch (_: CancellationException) {
                    return@launch
                } catch (_: Exception) { pollDelay = (pollDelay * 2).coerceAtMost(30_000L) }
            }
        }
    }

    fun loadOlderChat() {
        val t = deviceToken ?: return
        val cursor = chatCursor ?: return
        val parentId = chatParentId
        if (loadingOlder || chatMessages.size >= 1_000) return
        loadingOlder = true
        viewModelScope.launch(dispatcher) {
            try {
                val response = if (parentId == null) api.deviceMessages(t, before = cursor)
                else api.monitorMessages(t, parentId, before = cursor)
                val have = chatMessages.mapTo(HashSet()) { it.id }
                val available = 1_000 - chatMessages.size
                chatMessages = response.messages.filter { it.id !in have }.takeLast(available) + chatMessages
                chatCursor = if (chatMessages.size >= 1_000) null else response.nextCursor
            } catch (e: Exception) {
                if (!handleDeviceAuthError(e)) error = e.message
            } finally { loadingOlder = false }
        }
    }

    fun stopChat() { chatJob?.cancel(); chatJob = null; chatParentId = null; chatCursor = null; chatLatestCursor = null }

    fun sendChat(body: String, onSent: (() -> Unit)? = null) {
        val t = deviceToken ?: return
        val parentId = chatParentId
        if (body.isBlank()) return
        sending = true
        viewModelScope.launch(dispatcher) {
            try {
                val m = if (parentId == null) api.sendDeviceMessage(t, body.trim())
                else api.sendMonitorMessage(t, parentId, body.trim())
                if (chatMessages.none { it.id == m.id }) chatMessages = chatMessages + m
                onSent?.invoke()
            } catch (e: Exception) { if (!handleDeviceAuthError(e)) error = e.message } finally { sending = false }
        }
    }

    override fun onCleared() { stopChat(); super.onCleared() }

    fun refreshTelemetry(context: Context) {
        viewModelScope.launch(dispatcher) {
            val app = context.applicationContext
            val simulationStore = LocationSimulationStore(app)
            simulationConfig = simulationStore.activeConfig()
            battery = AndroidTelemetry.readBattery(app)
            charging = AndroidTelemetry.readCharging(app)
            currentKidLocationPoint(app, simulationStore, battery)?.let {
                lat = it.lat
                lng = it.lng
            }
            appUsageAccessGranted = AppUsageTelemetry.hasUsageAccess(app)
            telemetryUpdatedAt = java.time.OffsetDateTime.now().toString()
        }
    }

    fun refreshAppUsageAccess(context: Context) {
        appUsageAccessGranted = AppUsageTelemetry.hasUsageAccess(context.applicationContext)
    }

    private fun localDay(): String = LocalDate.now(ZoneId.systemDefault()).toString()

    private fun timezoneId(): String = ZoneId.systemDefault().id

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer {
                KidViewModel(HttpApiClient(), PrefsTokenStore(context.applicationContext))
            }
        }
    }
}
