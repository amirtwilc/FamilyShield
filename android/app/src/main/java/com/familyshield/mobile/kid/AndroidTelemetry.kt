package com.familyshield.mobile.kid

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal const val LOCATION_STALE_AFTER_MS = 2 * 60 * 1000L
internal const val CURRENT_LOCATION_TIMEOUT_MS = 15 * 1000L

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
        if (!hasLocationPermission(app) || !isLocationServicesEnabled(app)) return null

        val nowMs = System.currentTimeMillis()
        freshestUsableLocation(platformLastKnownLocations(app), nowMs, LOCATION_STALE_AFTER_MS)?.let { return it }

        return supervisorScope {
            val result = CompletableDeferred<Location?>()
            val remaining = AtomicInteger(2)
            fun completeAttempt(location: Location?) {
                val usable = location?.takeIf { isUsableLocation(it, System.currentTimeMillis(), LOCATION_STALE_AFTER_MS) }
                if (usable != null) result.complete(usable)
                else if (remaining.decrementAndGet() == 0) result.complete(null)
            }
            val jobs = listOf(
                launch { completeAttempt(fusedCurrentOrLastLocation(app)) },
                launch { completeAttempt(platformCurrentLocation(app)) },
            )
            val location = withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) { result.await() }
            jobs.forEach { it.cancel() }
            location
        }
    }

    private suspend fun fusedCurrentOrLastLocation(app: Context): Location? {
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

    private suspend fun platformCurrentLocation(app: Context): Location? = suspendCancellableCoroutine { cont ->
        val manager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val completed = AtomicBoolean(false)
        lateinit var listener: LocationListener
        fun finish(location: Location?) {
            if (!completed.compareAndSet(false, true)) return
            runCatching { manager.removeUpdates(listener) }
            if (cont.isActive) cont.resume(location)
        }
        listener = LocationListener { location ->
            if (isUsableLocation(location, System.currentTimeMillis(), LOCATION_STALE_AFTER_MS)) finish(location)
        }
        cont.invokeOnCancellation {
            if (completed.compareAndSet(false, true)) runCatching { manager.removeUpdates(listener) }
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            finish(null)
            return@suspendCancellableCoroutine
        }
        try {
            providers.forEach { provider ->
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            finish(null)
        } catch (_: IllegalArgumentException) {
            finish(null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun platformLastKnownLocations(app: Context): List<Location> {
        val manager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!hasLocationPermission(app)) return emptyList()
        return runCatching {
            manager.getProviders(true).mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    fun movementLocationUpdates(
        context: Context,
        intervalMs: Long,
        minDistanceM: Float,
        onLocation: (Location) -> Unit,
    ): LocationUpdatesHandle? {
        val app = context.applicationContext
        if (!hasLocationPermission(app) || !isLocationServicesEnabled(app)) return null
        val manager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener(onLocation)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }
        if (providers.isEmpty()) return null
        return try {
            // Use the platform providers for the long-running stream. Some MIUI
            // builds accept a fused request but never deliver its callback.
            providers.forEach { provider ->
                manager.requestLocationUpdates(provider, intervalMs, minDistanceM, listener, Looper.getMainLooper())
            }
            object : LocationUpdatesHandle {
                override fun stop() {
                    runCatching { manager.removeUpdates(listener) }
                }
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
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

    fun isLocationServicesEnabled(context: Context): Boolean {
        val manager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            runCatching {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }.getOrDefault(false)
        }
    }

    private fun iso(epochMs: Long): String {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        df.timeZone = TimeZone.getTimeZone("UTC")
        return df.format(Date(epochMs))
    }
}

internal fun freshestUsableLocation(
    locations: List<Location>,
    nowMs: Long,
    staleAfterMs: Long,
): Location? = locations
    .asSequence()
    .filter { AndroidTelemetry.isUsableLocation(it, nowMs, staleAfterMs) }
    .maxByOrNull { it.time }
