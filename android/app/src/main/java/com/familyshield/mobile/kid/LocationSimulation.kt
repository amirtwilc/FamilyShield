package com.familyshield.mobile.kid

import android.content.Context
import android.location.Location
import com.familyshield.mobile.net.LocationPoint
import com.familyshield.mobile.net.Monitor
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal const val ADMIN_TIER_CODE = "admin"
internal const val SIMULATION_INTERVAL_MS = 20_000L
internal const val SIMULATION_AUTH_REFRESH_MS = 60_000L
internal const val SIMULATION_MAX_PENDING_POINTS = 200

internal fun List<Monitor>.allowsLocationSimulation(): Boolean = any { it.tierCode == ADMIN_TIER_CODE }

@Serializable
enum class LocationSimulationMode { Fixed, Route }

@Serializable
data class SimulationWaypoint(val lat: Double, val lng: Double) {
    fun isValid(): Boolean = lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
}

@Serializable
data class LocationSimulationConfig(
    val mode: LocationSimulationMode,
    val waypoints: List<SimulationWaypoint>,
    val speedMps: Double = 1.4,
    val startedAtMs: Long,
    val schemaVersion: Int = 1,
)

class LocationSimulationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun activeConfig(): LocationSimulationConfig? = prefs.getString(KEY_CONFIG, null)?.let { encoded ->
        val config = runCatching { json.decodeFromString<LocationSimulationConfig>(encoded) }.getOrNull()
        if (config == null || config.schemaVersion != CURRENT_SCHEMA_VERSION || !config.isValid()) {
            stop()
            null
        } else {
            config
        }
    }

    fun start(point: SimulationWaypoint, nowMs: Long = System.currentTimeMillis()): LocationSimulationConfig {
        require(point.isValid()) { "Invalid start location" }
        return save(LocationSimulationConfig(
            mode = LocationSimulationMode.Fixed,
            waypoints = listOf(point),
            speedMps = 1.4,
            startedAtMs = nowMs,
            schemaVersion = CURRENT_SCHEMA_VERSION,
        ))
    }

    fun selectMode(mode: LocationSimulationMode, nowMs: Long = System.currentTimeMillis()): LocationSimulationConfig? {
        val current = activeConfig() ?: return null
        if (current.mode == mode) return current
        val point = current.currentWaypoint(nowMs) ?: return null
        return save(current.copy(mode = mode, waypoints = listOf(point), startedAtMs = nowMs))
    }

    fun moveFixed(point: SimulationWaypoint, nowMs: Long = System.currentTimeMillis()): LocationSimulationConfig? {
        require(point.isValid()) { "Invalid fixed location" }
        val current = activeConfig() ?: return null
        return save(current.copy(
            mode = LocationSimulationMode.Fixed,
            waypoints = listOf(point),
            startedAtMs = nowMs,
        ))
    }

    fun setRouteDestination(
        destination: SimulationWaypoint,
        nowMs: Long = System.currentTimeMillis(),
    ): LocationSimulationConfig? {
        require(destination.isValid()) { "Invalid route destination" }
        val current = activeConfig() ?: return null
        val origin = current.currentWaypoint(nowMs) ?: return null
        return save(current.copy(
            mode = LocationSimulationMode.Route,
            waypoints = listOf(origin, destination),
            startedAtMs = nowMs,
        ))
    }

    fun setSpeed(speedMps: Double, nowMs: Long = System.currentTimeMillis()): LocationSimulationConfig? {
        require(speedMps in 0.6..50.0) { "Speed must be between 0.6 and 50 m/s" }
        val current = activeConfig() ?: return null
        val origin = current.currentWaypoint(nowMs) ?: return null
        val points = if (current.mode == LocationSimulationMode.Route && current.waypoints.size >= 2) {
            listOf(origin, current.waypoints.last())
        } else {
            listOf(origin)
        }
        return save(current.copy(waypoints = points, speedMps = speedMps, startedAtMs = nowMs))
    }

    fun stop() {
        prefs.edit().remove(KEY_CONFIG).apply()
    }

    private fun save(config: LocationSimulationConfig): LocationSimulationConfig {
        prefs.edit().putString(KEY_CONFIG, json.encodeToString(config)).apply()
        return config
    }

    private companion object {
        const val PREFS_NAME = "familyshield_location_simulation"
        const val KEY_CONFIG = "active_config"
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

private fun LocationSimulationConfig.isValid(): Boolean =
    speedMps in 0.6..50.0 && waypoints.isNotEmpty() && waypoints.size <= 2 && waypoints.all { it.isValid() }

private fun LocationSimulationConfig.currentWaypoint(nowMs: Long): SimulationWaypoint? =
    LocationSimulationEngine.sample(this, nowMs)?.let { SimulationWaypoint(it.lat, it.lng) }

internal object LocationSimulationEngine {
    fun sample(
        config: LocationSimulationConfig,
        nowMs: Long = System.currentTimeMillis(),
        batteryLevel: Int? = null,
    ): LocationPoint? {
        if (config.waypoints.isEmpty() || config.waypoints.any { !it.isValid() }) return null
        val elapsedSeconds = ((nowMs - config.startedAtMs).coerceAtLeast(0L)) / 1000.0
        val positioned = when (config.mode) {
            LocationSimulationMode.Fixed -> PositionedSample(config.waypoints.first(), 0.0)
            LocationSimulationMode.Route -> routeSample(config, elapsedSeconds)
        }
        return LocationPoint(
            lat = positioned.point.lat,
            lng = positioned.point.lng,
            recordedAt = iso(nowMs),
            batteryLevel = batteryLevel,
            speed = positioned.speedMps,
        )
    }

    private fun routeSample(config: LocationSimulationConfig, elapsedSeconds: Double): PositionedSample {
        if (config.waypoints.size < 2) return PositionedSample(config.waypoints.first(), 0.0)
        val from = config.waypoints.first()
        val to = config.waypoints.last()
        val distance = distanceM(from, to)
        val travelSeconds = distance / config.speedMps
        if (travelSeconds > 0.0 && elapsedSeconds < travelSeconds) {
            return PositionedSample(interpolate(from, to, elapsedSeconds / travelSeconds), config.speedMps)
        }
        return PositionedSample(to, 0.0)
    }

    private fun interpolate(from: SimulationWaypoint, to: SimulationWaypoint, fraction: Double): SimulationWaypoint {
        val f = fraction.coerceIn(0.0, 1.0)
        return SimulationWaypoint(
            lat = from.lat + (to.lat - from.lat) * f,
            lng = from.lng + (to.lng - from.lng) * f,
        )
    }

    internal fun distanceM(from: SimulationWaypoint, to: SimulationWaypoint): Double {
        val earthRadiusM = 6_371_000.0
        val dLat = Math.toRadians(to.lat - from.lat)
        val dLng = Math.toRadians(to.lng - from.lng)
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val h = (sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2))
            .coerceIn(0.0, 1.0)
        return earthRadiusM * 2 * atan2(sqrt(h), sqrt(1 - h))
    }

    private data class PositionedSample(val point: SimulationWaypoint, val speedMps: Double)
}

internal suspend fun currentKidLocationPoint(
    context: Context,
    simulationStore: LocationSimulationStore,
    batteryLevel: Int?,
    nowMs: Long = System.currentTimeMillis(),
): LocationPoint? {
    val simulation = simulationStore.activeConfig()
    if (simulation != null) return LocationSimulationEngine.sample(simulation, nowMs, batteryLevel)
    return AndroidTelemetry.currentLocation(context)?.toLocationPoint(batteryLevel, nowMs)
}

internal fun Location.toLocationPoint(
    batteryLevel: Int?,
    nowMs: Long = System.currentTimeMillis(),
): LocationPoint? {
    if (!AndroidTelemetry.isUsableLocation(this, nowMs, LOCATION_STALE_AFTER_MS)) return null
    return LocationPoint(
        latitude,
        longitude,
        AndroidTelemetry.locationRecordedAtIso(this),
        batteryLevel,
        speed = speed.toDouble().takeIf { hasSpeed() && it.isFinite() && it >= 0.0 },
    )
}

private fun iso(epochMs: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(epochMs))
}
