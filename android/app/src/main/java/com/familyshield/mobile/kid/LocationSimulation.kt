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
    val dwellMinutes: Int = 5,
    val loop: Boolean = false,
    val startedAtMs: Long,
    val pausedAtMs: Long? = null,
    val accumulatedPausedMs: Long = 0L,
) {
    val isPaused: Boolean get() = pausedAtMs != null
}

class LocationSimulationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun activeConfig(): LocationSimulationConfig? = prefs.getString(KEY_CONFIG, null)?.let { encoded ->
        runCatching { json.decodeFromString<LocationSimulationConfig>(encoded) }.getOrNull()
    }

    fun startFixed(point: SimulationWaypoint, nowMs: Long = System.currentTimeMillis()): LocationSimulationConfig {
        require(point.isValid()) { "Invalid fixed location" }
        return save(LocationSimulationConfig(LocationSimulationMode.Fixed, listOf(point), speedMps = 0.0, startedAtMs = nowMs))
    }

    fun startRoute(
        waypoints: List<SimulationWaypoint>,
        speedMps: Double,
        dwellMinutes: Int,
        loop: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): LocationSimulationConfig {
        require(waypoints.size >= 2 && waypoints.all { it.isValid() }) { "A route requires at least two valid waypoints" }
        require(speedMps in 0.6..50.0) { "Speed must be between 0.6 and 50 m/s" }
        require(dwellMinutes in 0..60) { "Dwell time must be between 0 and 60 minutes" }
        return save(LocationSimulationConfig(LocationSimulationMode.Route, waypoints, speedMps, dwellMinutes, loop, nowMs))
    }

    fun pause(nowMs: Long = System.currentTimeMillis()): LocationSimulationConfig? {
        val config = activeConfig() ?: return null
        if (config.isPaused) return config
        return save(config.copy(pausedAtMs = nowMs))
    }

    fun resume(nowMs: Long = System.currentTimeMillis()): LocationSimulationConfig? {
        val config = activeConfig() ?: return null
        val pausedAt = config.pausedAtMs ?: return config
        return save(config.copy(
            pausedAtMs = null,
            accumulatedPausedMs = config.accumulatedPausedMs + (nowMs - pausedAt).coerceAtLeast(0L),
        ))
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
    }
}

internal object LocationSimulationEngine {
    fun sample(
        config: LocationSimulationConfig,
        nowMs: Long = System.currentTimeMillis(),
        batteryLevel: Int? = null,
    ): LocationPoint? {
        if (config.waypoints.isEmpty() || config.waypoints.any { !it.isValid() }) return null
        val effectiveNow = config.pausedAtMs?.coerceAtMost(nowMs) ?: nowMs
        val elapsedSeconds = ((effectiveNow - config.startedAtMs - config.accumulatedPausedMs).coerceAtLeast(0L)) / 1000.0
        val positioned = when (config.mode) {
            LocationSimulationMode.Fixed -> PositionedSample(config.waypoints.first(), 0.0)
            LocationSimulationMode.Route -> routeSample(config, elapsedSeconds)
        }
        return LocationPoint(
            lat = positioned.point.lat,
            lng = positioned.point.lng,
            recordedAt = iso(nowMs),
            batteryLevel = batteryLevel,
            speed = if (config.isPaused) 0.0 else positioned.speedMps,
        )
    }

    private fun routeSample(config: LocationSimulationConfig, elapsedSeconds: Double): PositionedSample {
        if (config.waypoints.size < 2) return PositionedSample(config.waypoints.first(), 0.0)
        val legs = buildList {
            config.waypoints.zipWithNext().forEach { (from, to) -> add(from to to) }
            if (config.loop) add(config.waypoints.last() to config.waypoints.first())
        }
        val dwellSeconds = config.dwellMinutes * 60.0
        val cycleSeconds = legs.sumOf { (from, to) -> distanceM(from, to) / config.speedMps + dwellSeconds }
        var remaining = if (config.loop && cycleSeconds > 0.0) elapsedSeconds % cycleSeconds else elapsedSeconds

        for ((index, leg) in legs.withIndex()) {
            val (from, to) = leg
            val distance = distanceM(from, to)
            val travelSeconds = distance / config.speedMps
            if (travelSeconds > 0.0 && remaining < travelSeconds) {
                return PositionedSample(interpolate(from, to, remaining / travelSeconds), config.speedMps)
            }
            remaining = (remaining - travelSeconds).coerceAtLeast(0.0)
            val isFinalNonLoopingLeg = !config.loop && index == legs.lastIndex
            if (isFinalNonLoopingLeg) return PositionedSample(to, 0.0)
            if (remaining < dwellSeconds) return PositionedSample(to, 0.0)
            remaining = (remaining - dwellSeconds).coerceAtLeast(0.0)
        }
        return PositionedSample(config.waypoints.last(), 0.0)
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
