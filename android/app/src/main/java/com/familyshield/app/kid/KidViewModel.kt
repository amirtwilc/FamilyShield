package com.familyshield.app.kid

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.familyshield.app.net.ApiClient
import com.familyshield.app.net.HttpApiClient
import com.familyshield.app.net.Message
import com.familyshield.app.net.Monitor
import com.familyshield.app.net.PrefsTokenStore
import com.familyshield.app.net.TokenStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class KidViewModel(
    private val api: ApiClient,
    private val store: TokenStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {

    var deviceToken by mutableStateOf(store.deviceToken)
        private set

    // Default near central Tel Aviv; tap the map to move.
    var lat by mutableStateOf(32.0853)
    var lng by mutableStateOf(34.7818)
    var battery by mutableStateOf(80)
    var charging by mutableStateOf(false)

    var message by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var monitors by mutableStateOf<List<Monitor>>(emptyList())
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
                val r = api.pair(code.trim(), platform, "Simulator")
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
                val info = api.addParent(t, code.trim(), platform, "Simulator")
                monitors = info.monitors
                message = "Parent added."
            } catch (e: Exception) { error = e.message } finally { busy = false }
        }
    }

    fun refreshMonitoring() {
        val t = deviceToken ?: return
        refreshMonitoring(t)
    }

    private fun refreshMonitoring(token: String) {
        viewModelScope.launch(dispatcher) {
            try { monitors = api.monitoring(token).monitors } catch (_: Exception) {}
        }
    }

    fun unpair() { stopChat(); store.deviceToken = null; deviceToken = null; monitors = emptyList() }

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
                    message = "Device unpaired."
                } else {
                    monitors = result.monitors
                    message = "Parent removed."
                }
            } catch (e: Exception) { error = e.message } finally { busy = false }
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
        chatJob?.cancel()
        chatParentId = parentId
        chatJob = viewModelScope.launch(dispatcher) {
            try {
                chatMessages = if (parentId == null) api.deviceMessages(t).messages
                else api.monitorMessages(t, parentId).messages
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
            } catch (e: Exception) { error = e.message } finally { sending = false }
        }
    }

    override fun onCleared() { stopChat(); super.onCleared() }

    fun setPosition(newLat: Double, newLng: Double) { lat = newLat; lng = newLng }

    fun sendLocation() {
        val t = deviceToken ?: return
        error = null; message = null
        viewModelScope.launch(dispatcher) {
            try { api.sendLocation(t, lat, lng, battery); message = "Location sent." }
            catch (e: Exception) { error = e.message }
        }
    }

    fun sendStatus() {
        val t = deviceToken ?: return
        error = null; message = null
        viewModelScope.launch(dispatcher) {
            try { api.sendStatus(t, battery, charging); message = "Status sent." }
            catch (e: Exception) { error = e.message }
        }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer {
                KidViewModel(HttpApiClient(), PrefsTokenStore(context.applicationContext))
            }
        }
    }
}
