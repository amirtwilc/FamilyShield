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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

private const val ACTION_STOP = "com.familyshield.mobile.kid.STOP_MONITORING"
internal const val TELEMETRY_UPLOAD_INTERVAL_MS = 5 * 60 * 1000L
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

    override fun onCreate() {
        super.onCreate()
        startForeground(MONITORING_NOTIFICATION_ID, monitoringNotification(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (monitorJob?.isActive != true) monitorJob = scope.launch { monitorLoop() }
        if (sosJob?.isActive != true) sosJob = scope.launch { sosLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
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
            val result = runCatching { uploadTick(token, uploadPolicy) }
            val error = result.exceptionOrNull()
            if (error is ApiException && error.status == 401) {
                store.deviceToken = null
                stopSelf()
                return
            }
            delay(TELEMETRY_UPLOAD_INTERVAL_MS)
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

            val telemetry = AndroidTelemetry.snapshot(this)
            val loc = telemetry.location
            if (loc != null) {
                val result = runCatching {
                    api.sendSosLocation(
                        token,
                        timezoneId(),
                        day,
                        LocationPoint(loc.latitude, loc.longitude, nowIso(), telemetry.batteryLevel),
                    )
                }
                val error = result.exceptionOrNull()
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

    private suspend fun uploadTick(token: String, uploadPolicy: TelemetryUploadPolicy) {
        val nowMs = System.currentTimeMillis()
        val optionalFields = uploadPolicy.optionalFields(nowMs)
        val telemetry = AndroidTelemetry.snapshot(this)
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
        val battery = telemetry.batteryLevel
        val loc = telemetry.location
        val status = StatusBody(battery, telemetry.isCharging, fcmToken, permissionStatusPayload(this))
        api.sendTelemetry(
            token,
            DeviceTelemetryBody(
                status = status,
                location = if (loc != null) LocationPoint(loc.latitude, loc.longitude, nowIso(), battery) else null,
                appUsage = appUsage,
            ),
        )
        uploadPolicy.markUploaded(optionalFields, nowMs)
    }

    private fun nowIso(): String {
        val df = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        df.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return df.format(java.util.Date())
    }

    private fun localDay(): String = LocalDate.now(ZoneId.systemDefault()).toString()

    private fun timezoneId(): String = ZoneId.systemDefault().id
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
