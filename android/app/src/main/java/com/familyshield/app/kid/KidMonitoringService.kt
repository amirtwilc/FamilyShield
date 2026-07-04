package com.familyshield.app.kid

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.familyshield.app.net.HttpApiClient
import com.familyshield.app.net.PrefsTokenStore
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
            runCatching { uploadTick(token) }
            delay(FIVE_MINUTES_MS)
        }
    }

    private suspend fun uploadTick(token: String) {
        val battery = readBattery()
        val charging = readCharging()
        api.sendStatus(token, battery, charging)
        val loc = currentLocation() ?: return
        api.sendLocation(token, loc.latitude, loc.longitude, battery)
    }

    private fun readBattery(): Int {
        val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else 100
    }

    private fun readCharging(): Boolean {
        val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return when (status?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL -> true
            else -> false
        }
    }

    private suspend fun currentLocation(): Location? {
        if (!hasLocationPermission()) return null
        val fused = LocationServices.getFusedLocationProviderClient(this)
        return suspendCancellableCoroutine { cont ->
            try {
                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            } catch (_: SecurityException) {
                cont.resume(null)
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
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
