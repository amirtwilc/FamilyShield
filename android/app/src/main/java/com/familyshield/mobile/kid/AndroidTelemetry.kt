package com.familyshield.mobile.kid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume

data class TelemetrySnapshot(
    val location: Location?,
    val batteryLevel: Int?,
    val isCharging: Boolean,
)

object AndroidTelemetry {
    interface LocationUpdatesHandle {
        fun stop()
    }

    fun deviceModel(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return listOf(manufacturer, model)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
            .ifBlank { "Android device" }
    }

    suspend fun snapshot(context: Context): TelemetrySnapshot {
        val app = context.applicationContext
        return TelemetrySnapshot(
            location = currentLocation(app),
            batteryLevel = readBattery(app),
            isCharging = readCharging(app),
        )
    }

    fun readBattery(context: Context): Int? {
        val status = context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else null
    }

    fun readCharging(context: Context): Boolean {
        val status = context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return when (status?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL -> true
            else -> false
        }
    }

    suspend fun currentLocation(context: Context): Location? {
        val app = context.applicationContext
        if (!hasLocationPermission(app)) return null
        val fused = LocationServices.getFusedLocationProviderClient(app)
        return suspendCancellableCoroutine { cont ->
            val cancellation = CancellationTokenSource()
            cont.invokeOnCancellation { cancellation.cancel() }
            try {
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            if (cont.isActive) cont.resume(location)
                        } else {
                            fused.lastLocation
                                .addOnSuccessListener { last -> if (cont.isActive) cont.resume(last) }
                                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                        }
                    }
                    .addOnFailureListener {
                        fused.lastLocation
                            .addOnSuccessListener { last -> if (cont.isActive) cont.resume(last) }
                            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                    }
            } catch (_: SecurityException) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    fun movementLocationUpdates(
        context: Context,
        intervalMs: Long,
        minDistanceM: Float,
        onLocation: (Location) -> Unit,
    ): LocationUpdatesHandle? {
        val app = context.applicationContext
        if (!hasLocationPermission(app)) return null
        val fused = LocationServices.getFusedLocationProviderClient(app)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach(onLocation)
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs)
            .setMinUpdateDistanceMeters(minDistanceM)
            .build()
        return try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            object : LocationUpdatesHandle {
                override fun stop() {
                    fused.removeLocationUpdates(callback)
                }
            }
        } catch (_: SecurityException) {
            null
        }
    }

    fun locationRecordedAtIso(location: Location): String = iso(location.time)

    fun isUsableLocation(location: Location, nowMs: Long, staleAfterMs: Long): Boolean =
        location.latitude.isFinite() &&
            location.longitude.isFinite() &&
            location.latitude in -90.0..90.0 &&
            location.longitude in -180.0..180.0 &&
            location.time > 0L &&
            nowMs - location.time in 0..staleAfterMs

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun iso(epochMs: Long): String {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        df.timeZone = TimeZone.getTimeZone("UTC")
        return df.format(Date(epochMs))
    }
}
