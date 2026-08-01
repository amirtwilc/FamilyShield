package com.familyshield.mobile.parent

import com.familyshield.mobile.net.CurrentLocation
import com.familyshield.mobile.net.MovementMode
import java.time.OffsetDateTime

internal const val STATIONARY_MAX_SPEED_MPS = 0.5
internal const val DRIVING_MIN_SPEED_MPS = 4.17

internal fun dashboardMovementMode(isOnline: Boolean, location: CurrentLocation?): MovementMode? =
    location?.movementMode?.takeIf { isOnline && it != MovementMode.Stationary }

internal fun movementModeForPath(
    speeds: List<Double?>,
    distanceM: Double,
    startAt: String,
    endAt: String,
): MovementMode {
    val movingSpeeds = speeds
        .filterNotNull()
        .filter { it.isFinite() && it > STATIONARY_MAX_SPEED_MPS }
        .sorted()
    val durationSeconds = runCatching {
        (OffsetDateTime.parse(endAt).toInstant().toEpochMilli() -
            OffsetDateTime.parse(startAt).toInstant().toEpochMilli()) / 1_000.0
    }.getOrDefault(0.0)
    val representativeSpeed = movingSpeeds.getOrNull(movingSpeeds.size / 2)
        ?: if (durationSeconds > 0) distanceM / durationSeconds else null

    return if (representativeSpeed != null && representativeSpeed >= DRIVING_MIN_SPEED_MPS) {
        MovementMode.Driving
    } else {
        MovementMode.Walking
    }
}
