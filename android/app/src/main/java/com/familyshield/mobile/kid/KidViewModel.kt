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
import com.familyshield.mobile.net.LocationPoint
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

    fun pair(code: String, platform: String) {
        error = null; message = null; busy = true
        viewModelScope.launch(dispatcher) {
            try {
                val r = api.pair(code.trim(), platform, AndroidTelemetry.deviceModel())
                store.parentToken = null
                store.parentRefreshToken = null
                store.deviceToken = r.deviceToken
                deviceToken = r.deviceToken
                refreshMonitoring(r.deviceToken)
            } catch (e: Exception) { error = e.message } finally { busy = false }
        }
    }

    fun addParent(code: String, platform: String) {
        val t = deviceToken ?: return
        error = null; message = null; busy = true
        viewModelScope.launch(dispatcher) {
            try {
                val info = api.addParent(t, code.trim(), platform, AndroidTelemetry.deviceModel())
                monitors = info.monitors
                message = "Parent added."
            } catch (e: Exception) { error = e.message } finally { busy = false }
        }
    }

    fun refreshMonitoring() {
        val t = deviceToken ?: return
        refreshMonitoring(t)
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
                val snapshot = AndroidTelemetry.snapshot(app)
                battery = snapshot.batteryLevel
                charging = snapshot.isCharging
                val location = snapshot.location?.let {
                    lat = it.latitude
                    lng = it.longitude
                    LocationPoint(it.latitude, it.longitude, nowIso(), snapshot.batteryLevel)
                }
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
    private var chatJob: Job? = null
    private var chatParentId: String? = null

    fun startChat(parentId: String? = null) {
        val t = deviceToken ?: return
        if (chatParentId == parentId && chatJob?.isActive == true) return
        chatJob?.cancel()
        chatParentId = parentId
        chatJob = viewModelScope.launch(dispatcher) {
            try {
                chatMessages = if (parentId == null) api.deviceMessages(t).messages
                else api.monitorMessages(t, parentId).messages
            } catch (_: CancellationException) {
                return@launch
            } catch (_: Exception) {}
            while (isActive) {
                delay(3000)
                val after = chatMessages.lastOrNull()?.createdAt
                try {
                    val delta = if (parentId == null) api.deviceMessages(t, after).messages
                    else api.monitorMessages(t, parentId, after).messages
                    val have = chatMessages.mapTo(HashSet()) { it.id }
                    val fresh = delta.filter { it.id !in have }
                    if (fresh.isNotEmpty()) chatMessages = chatMessages + fresh
                } catch (_: CancellationException) {
                    return@launch
                } catch (_: Exception) {}
            }
        }
    }

    fun stopChat() { chatJob?.cancel(); chatJob = null; chatParentId = null }

    fun sendChat(body: String) {
        val t = deviceToken ?: return
        val parentId = chatParentId
        if (body.isBlank()) return
        sending = true
        viewModelScope.launch(dispatcher) {
            try {
                val m = if (parentId == null) api.sendDeviceMessage(t, body.trim())
                else api.sendMonitorMessage(t, parentId, body.trim())
                if (chatMessages.none { it.id == m.id }) chatMessages = chatMessages + m
            } catch (e: Exception) { if (!handleDeviceAuthError(e)) error = e.message } finally { sending = false }
        }
    }

    override fun onCleared() { stopChat(); super.onCleared() }

    fun refreshTelemetry(context: Context) {
        viewModelScope.launch(dispatcher) {
            val app = context.applicationContext
            val snapshot = AndroidTelemetry.snapshot(app)
            battery = snapshot.batteryLevel
            charging = snapshot.isCharging
            snapshot.location?.let {
                lat = it.latitude
                lng = it.longitude
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

    private fun nowIso(): String {
        val df = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        df.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return df.format(java.util.Date())
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer {
                KidViewModel(HttpApiClient(), PrefsTokenStore(context.applicationContext))
            }
        }
    }
}
