package com.familyshield.mobile.parent

import com.familyshield.mobile.net.CurrentLocation
import com.familyshield.mobile.net.MovementMode
import com.familyshield.mobile.net.RoutePoint
import java.time.OffsetDateTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal const val STATIONARY_MAX_SPEED_MPS = 0.5
internal const val DRIVING_MIN_SPEED_MPS = 4.17
internal const val SUBSTANTIAL_DRIVING_SEGMENT_M = 300.0
internal const val ROUTE_MAX_POINT_ACCURACY_M = 100.0
internal const val ROUTE_MAX_PLAUSIBLE_SPEED_MPS = 70.0
internal const val ROUTE_MAX_INFERRED_SPEED_GAP_SECONDS = 10 * 60.0

internal data class InferredMovementSegment(val speedMps: Double, val distanceM: Double)
internal data class MovementPathMetrics(
    val distanceM: Double,
    val inferredSegments: List<InferredMovementSegment>,
)

internal fun dashboardMovementMode(isOnline: Boolean, location: CurrentLocation?): MovementMode? =
    location?.movementMode?.takeIf { isOnline && it != MovementMode.Stationary }

internal fun movementModeForPath(
    speeds: List<Double?>,
    inferredSegments: List<InferredMovementSegment> = emptyList(),
    distanceM: Double,
    startAt: String,
    endAt: String,
): MovementMode {
    val movingSpeeds = speeds
        .filterNotNull()
        .filter { it.isFinite() && it > STATIONARY_MAX_SPEED_MPS }
    val drivingSamples = movingSpeeds.count { it >= DRIVING_MIN_SPEED_MPS }
    val hasSustainedDriving = drivingSamples >= 2 ||
        (drivingSamples == 1 && movingSpeeds.size <= 2)
    val inferredMoving = inferredSegments
        .filter { it.speedMps.isFinite() && it.speedMps > STATIONARY_MAX_SPEED_MPS }
    val inferredDriving = inferredMoving.filter { it.speedMps >= DRIVING_MIN_SPEED_MPS }
    val hasInferredDriving = inferredDriving.size >= 2 ||
        inferredDriving.any { it.distanceM >= SUBSTANTIAL_DRIVING_SEGMENT_M } ||
        (inferredDriving.size == 1 && inferredMoving.size <= 2)
    val durationSeconds = runCatching {
        (OffsetDateTime.parse(endAt).toInstant().toEpochMilli() -
            OffsetDateTime.parse(startAt).toInstant().toEpochMilli()) / 1_000.0
    }.getOrDefault(0.0)
    val averageRouteSpeed = if (durationSeconds > 0) distanceM / durationSeconds else null

    return if (
        hasSustainedDriving || hasInferredDriving ||
        (averageRouteSpeed != null && averageRouteSpeed >= DRIVING_MIN_SPEED_MPS)
    ) {
        MovementMode.Driving
    } else {
        MovementMode.Walking
    }
}

internal fun movementPathMetrics(points: List<RoutePoint>): MovementPathMetrics {
    var distanceM = 0.0
    val inferredSegments = mutableListOf<InferredMovementSegment>()
    points.zipWithNext().forEach { (from, to) ->
        if ((from.accuracy != null && from.accuracy > ROUTE_MAX_POINT_ACCURACY_M) ||
            (to.accuracy != null && to.accuracy > ROUTE_MAX_POINT_ACCURACY_M)) return@forEach
        val durationSeconds = runCatching {
            (OffsetDateTime.parse(to.at).toInstant().toEpochMilli() -
                OffsetDateTime.parse(from.at).toInstant().toEpochMilli()) / 1_000.0
        }.getOrNull() ?: return@forEach
        if (durationSeconds <= 0) return@forEach
        val segmentDistanceM = movementDistanceMeters(from.lat, from.lng, to.lat, to.lng)
        val speedMps = segmentDistanceM / durationSeconds
        if (!speedMps.isFinite() || speedMps > ROUTE_MAX_PLAUSIBLE_SPEED_MPS) return@forEach
        distanceM += segmentDistanceM
        if (durationSeconds <= ROUTE_MAX_INFERRED_SPEED_GAP_SECONDS) {
            inferredSegments += InferredMovementSegment(speedMps, segmentDistanceM)
        }
    }
    return MovementPathMetrics(distanceM, inferredSegments)
}

private fun movementDistanceMeters(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val dLat = Math.toRadians(bLat - aLat)
    val dLng = Math.toRadians(bLng - aLng)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) *
        sin(dLng / 2) * sin(dLng / 2)
    return 6_371_000.0 * 2 * atan2(sqrt(h), sqrt(1 - h))
}
