package com.familyshield.app.kid

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.familyshield.app.net.HttpApiClient
import com.familyshield.app.net.ApiException
import com.familyshield.app.net.PrefsTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val ACTION_STOP = "com.familyshield.app.kid.STOP_MONITORING"
private const val FIVE_MINUTES_MS = 5 * 60 * 1000L

class KidMonitoringService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api = HttpApiClient()
    private var monitorJob: Job? = null

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
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun monitorLoop() {
        val store = PrefsTokenStore(applicationContext)
        while (scope.isActive) {
            val token = store.deviceToken
            if (token == null) {
                stopSelf()
                return
            }
            val result = runCatching { uploadTick(token) }
            val error = result.exceptionOrNull()
            if (error is ApiException && error.status == 401) {
                store.deviceToken = null
                stopSelf()
                return
            }
            delay(FIVE_MINUTES_MS)
        }
    }

    private suspend fun uploadTick(token: String) {
        val telemetry = AndroidTelemetry.snapshot(this)
        val battery = telemetry.batteryLevel ?: return
        api.sendStatus(token, battery, telemetry.isCharging)
        val loc = telemetry.location ?: return
        api.sendLocation(token, loc.latitude, loc.longitude, battery)
    }
}

fun startKidMonitoring(context: Context) {
    val app = context.applicationContext
    val intent = Intent(app, KidMonitoringService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent)
    else app.startService(intent)
}

fun stopKidMonitoring(context: Context) {
    val app = context.applicationContext
    app.startService(Intent(app, KidMonitoringService::class.java).setAction(ACTION_STOP))
}
