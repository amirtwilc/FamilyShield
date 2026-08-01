package com.familyshield.mobile.kid

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.familyshield.mobile.net.AppUsageTelemetryBody
import com.familyshield.mobile.net.ApiException
import com.familyshield.mobile.net.DeviceTelemetryBody
import com.familyshield.mobile.net.HttpApiClient
import com.familyshield.mobile.net.LocationPoint
import com.familyshield.mobile.net.PrefsTokenStore
import com.familyshield.mobile.net.StatusBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

private const val ACTION_STOP = "com.familyshield.mobile.kid.STOP_MONITORING"
private const val ACTION_SIMULATION_CHANGED = "com.familyshield.mobile.kid.SIMULATION_CHANGED"
internal const val TELEMETRY_UPLOAD_INTERVAL_MS = 5 * 60 * 1000L
internal const val MOVEMENT_LOCATION_SAMPLE_INTERVAL_MS = 60 * 1000L
internal const val MOVEMENT_LOCATION_MIN_DISTANCE_M = 75f
internal const val LOCATION_STALE_AFTER_MS = 2 * 60 * 1000L
internal const val APP_USAGE_UPLOAD_INTERVAL_MS = 30 * 60 * 1000L
internal const val FCM_TOKEN_REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L
private const val SOS_IDLE_CHECK_INTERVAL_MS = 60 * 1000L

internal data class TelemetryOptionalFields(
    val appUsage: Boolean,
    val fcmToken: Boolean,
)

internal class TelemetryUploadPolicy(
    private val appUsageIntervalMs: Long = APP_USAGE_UPLOAD_INTERVAL_MS,
    private val fcmTokenRefreshIntervalMs: Long = FCM_TOKEN_REFRESH_INTERVAL_MS,
) {
    private var lastAppUsageUploadAtMs: Long? = null
    private var lastFcmTokenUploadAtMs: Long? = null

    fun optionalFields(nowMs: Long): TelemetryOptionalFields =
        TelemetryOptionalFields(
            appUsage = due(lastAppUsageUploadAtMs, appUsageIntervalMs, nowMs),
            fcmToken = due(lastFcmTokenUploadAtMs, fcmTokenRefreshIntervalMs, nowMs),
        )

    fun markUploaded(fields: TelemetryOptionalFields, nowMs: Long) {
        if (fields.appUsage) lastAppUsageUploadAtMs = nowMs
        if (fields.fcmToken) lastFcmTokenUploadAtMs = nowMs
    }

    private fun due(lastUploadAtMs: Long?, intervalMs: Long, nowMs: Long): Boolean =
        lastUploadAtMs == null || nowMs - lastUploadAtMs >= intervalMs
}

class KidMonitoringService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api = HttpApiClient()
    private var monitorJob: Job? = null
    private var sosJob: Job? = null
    private var movementLocationUpdates: AndroidTelemetry.LocationUpdatesHandle? = null
    private val pendingLocations = PendingLocationBuffer(maxPendingSize = SIMULATION_MAX_PENDING_POINTS)
    private val sourceMutex = Mutex()
    private val simulationStore by lazy { LocationSimulationStore(applicationContext) }
    private var lastSimulationAuthCheckAtMs: Long? = null
    private var simulationAuthorized = false

    override fun onCreate() {
        super.onCreate()
        startForeground(MONITORING_NOTIFICATION_ID, monitoringNotification(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_SIMULATION_CHANGED) {
            monitorJob?.cancel()
            pendingLocations.clear()
            lastSimulationAuthCheckAtMs = null
            simulationAuthorized = false
            syncMovementLocationCollection()
            showMonitoringNotification(this)
        }
        if (monitorJob?.isActive != true) monitorJob = scope.launch { monitorLoop() }
        if (sosJob?.isActive != true) sosJob = scope.launch { sosLoop() }
        syncMovementLocationCollection()
        return START_STICKY
    }

    override fun onDestroy() {
        movementLocationUpdates?.stop()
        movementLocationUpdates = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun monitorLoop() {
        val store = PrefsTokenStore(applicationContext)
        val uploadPolicy = TelemetryUploadPolicy()
        while (scope.isActive) {
            val token = store.deviceToken
            if (token == null) {
                stopSelf()
                return
            }
            val simulation = simulationStore.activeConfig()
            val result = if (simulation != null) {
                stopMovementLocationCollection()
                val nowMs = System.currentTimeMillis()
                val simulatedPoint = LocationSimulationEngine.sample(
                    simulation,
                    nowMs,
                    AndroidTelemetry.readBattery(this),
                )
                simulatedPoint?.let { pendingLocations.add(it) }
                when (refreshSimulationAuthorization(token, nowMs)) {
                    SimulationAuthorization.Authorized -> runCatching {
                        uploadTick(token, uploadPolicy, simulatedPoint)
                    }
                    SimulationAuthorization.Revoked -> {
                        stopSimulationAndResumeGps()
                        runCatching { uploadTick(token, uploadPolicy) }
                    }
                    SimulationAuthorization.Unavailable -> Result.success(Unit)
                    SimulationAuthorization.DeviceUnauthorized -> {
                        store.deviceToken = null
                        stopSelf()
                        return
                    }
                }
            } else {
                startMovementLocationCollection()
                runCatching { uploadTick(token, uploadPolicy) }
            }
            val error = result.exceptionOrNull()
            if (simulation != null && error != null) invalidateSimulationAuthorization()
            if (error is ApiException && error.status == 401) {
                store.deviceToken = null
                stopSelf()
                return
            }
            delay(if (simulationStore.activeConfig() != null) SIMULATION_INTERVAL_MS else TELEMETRY_UPLOAD_INTERVAL_MS)
        }
    }

    private suspend fun sosLoop() {
        val store = PrefsTokenStore(applicationContext)
        while (scope.isActive) {
            val token = store.deviceToken
            if (token == null) {
                stopSelf()
                return
            }
            val day = localDay()
            val state = try {
                api.sosState(token, day)
            } catch (e: Exception) {
                if (e is ApiException && e.status == 401) {
                    store.deviceToken = null
                    stopSelf()
                    return
                }
                delay(SOS_IDLE_CHECK_INTERVAL_MS)
                continue
            }

            if (!state.active) {
                showMonitoringNotification(this)
                delay(SOS_IDLE_CHECK_INTERVAL_MS)
                continue
            }

            showSosMonitoringNotification(this)
            if (state.remainingSeconds <= 0) {
                delay(SOS_IDLE_CHECK_INTERVAL_MS)
                continue
            }

            val battery = AndroidTelemetry.readBattery(this)
            val simulation = simulationStore.activeConfig()
            val locationPoint = if (simulation != null) {
                when (refreshSimulationAuthorization(token, System.currentTimeMillis())) {
                    SimulationAuthorization.Authorized -> LocationSimulationEngine.sample(simulation, batteryLevel = battery)
                    SimulationAuthorization.Revoked -> {
                        stopSimulationAndResumeGps()
                        currentKidLocationPoint(this, simulationStore, battery)
                    }
                    SimulationAuthorization.Unavailable -> null
                    SimulationAuthorization.DeviceUnauthorized -> {
                        store.deviceToken = null
                        stopSelf()
                        return
                    }
                }
            } else {
                currentKidLocationPoint(this, simulationStore, battery)
            }
            if (locationPoint != null) {
                if (simulation != null && simulationStore.activeConfig() != null && !simulationAuthorized) {
                    delay(SOS_IDLE_CHECK_INTERVAL_MS)
                    continue
                }
                val result = runCatching {
                    api.sendSosLocation(
                        token,
                        timezoneId(),
                        day,
                        locationPoint,
                    )
                }
                val error = result.exceptionOrNull()
                if (simulation != null && error != null) invalidateSimulationAuthorization()
                if (error is ApiException && error.status == 401) {
                    store.deviceToken = null
                    stopSelf()
                    return
                }
                if (result.getOrNull()?.state?.active != true) {
                    showMonitoringNotification(this)
                }
            }
            val intervalMs = (state.highRateIntervalSeconds.coerceAtLeast(10) * 1000L)
            delay(intervalMs)
        }
    }

    private suspend fun uploadTick(
        token: String,
        uploadPolicy: TelemetryUploadPolicy,
        locationOverride: LocationPoint? = null,
    ) {
        val nowMs = System.currentTimeMillis()
        val optionalFields = uploadPolicy.optionalFields(nowMs)
        val appUsage = if (optionalFields.appUsage) {
            val appUsageAccessGranted = AppUsageTelemetry.hasUsageAccess(this)
            AppUsageTelemetryBody(
                accessGranted = appUsageAccessGranted,
                items = if (appUsageAccessGranted) AppUsageTelemetry.todayUsage(this) else emptyList(),
            )
        } else {
            null
        }
        val fcmToken = if (optionalFields.fcmToken) kidFcmTokenOrNull() else null
        val battery = AndroidTelemetry.readBattery(this)
        val charging = AndroidTelemetry.readCharging(this)
        val currentLocation = locationOverride ?: currentKidLocationPoint(this, simulationStore, battery)
        val locations = pendingLocations.batch(currentLocation)
        val latestLocation = locations.lastOrNull()
        val status = StatusBody(battery, charging, fcmToken, permissionStatusPayload(this))
        api.sendTelemetry(
            token,
            DeviceTelemetryBody(
                status = status,
                location = latestLocation,
                locations = locations,
                appUsage = appUsage,
            ),
        )
        pendingLocations.acknowledge(locations)
        uploadPolicy.markUploaded(optionalFields, nowMs)
    }

    private fun startMovementLocationCollection() {
        if (simulationStore.activeConfig() != null) return
        if (movementLocationUpdates != null) return
        movementLocationUpdates = AndroidTelemetry.movementLocationUpdates(
            this,
            MOVEMENT_LOCATION_SAMPLE_INTERVAL_MS,
            MOVEMENT_LOCATION_MIN_DISTANCE_M,
        ) { location ->
            location.toLocationPoint(batteryLevel = null)?.let { pendingLocations.add(it) }
        }
    }

    private fun stopMovementLocationCollection() {
        movementLocationUpdates?.stop()
        movementLocationUpdates = null
    }

    private fun syncMovementLocationCollection() {
        if (simulationStore.activeConfig() == null) startMovementLocationCollection()
        else stopMovementLocationCollection()
    }

    private suspend fun refreshSimulationAuthorization(token: String, nowMs: Long): SimulationAuthorization =
        sourceMutex.withLock {
            if (simulationStore.activeConfig() == null) return@withLock SimulationAuthorization.Revoked
            val lastCheck = lastSimulationAuthCheckAtMs
            if (simulationAuthorized && lastCheck != null && nowMs - lastCheck < SIMULATION_AUTH_REFRESH_MS) {
                return@withLock SimulationAuthorization.Authorized
            }
            try {
                val eligible = api.monitoring(token).monitors.allowsLocationSimulation()
                lastSimulationAuthCheckAtMs = nowMs
                simulationAuthorized = eligible
                if (eligible) SimulationAuthorization.Authorized else SimulationAuthorization.Revoked
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (error is ApiException && error.status == 401) {
                    simulationAuthorized = false
                    return@withLock SimulationAuthorization.DeviceUnauthorized
                }
                simulationAuthorized = false
                SimulationAuthorization.Unavailable
            }
        }

    private suspend fun stopSimulationAndResumeGps() = sourceMutex.withLock {
        simulationStore.stop()
        pendingLocations.clear()
        simulationAuthorized = false
        lastSimulationAuthCheckAtMs = null
        startMovementLocationCollection()
        showMonitoringNotification(this)
    }

    private suspend fun invalidateSimulationAuthorization() = sourceMutex.withLock {
        simulationAuthorized = false
        lastSimulationAuthCheckAtMs = null
    }

    private fun localDay(): String = LocalDate.now(ZoneId.systemDefault()).toString()

    private fun timezoneId(): String = ZoneId.systemDefault().id

    private enum class SimulationAuthorization { Authorized, Revoked, Unavailable, DeviceUnauthorized }
}

fun startKidMonitoring(context: Context): Boolean {
    val app = context.applicationContext
    val intent = Intent(app, KidMonitoringService::class.java)
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent)
        else app.startService(intent)
        true
    } catch (_: RuntimeException) {
        false
    }
}

fun stopKidMonitoring(context: Context) {
    val app = context.applicationContext
    app.startService(Intent(app, KidMonitoringService::class.java).setAction(ACTION_STOP))
}

fun notifyKidSimulationChanged(context: Context) {
    val app = context.applicationContext
    val intent = Intent(app, KidMonitoringService::class.java).setAction(ACTION_SIMULATION_CHANGED)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent)
    else app.startService(intent)
}
