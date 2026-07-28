package com.familyshield.mobile.kid

import com.familyshield.mobile.net.LocationPoint

/**
 * Keeps movement samples until the server acknowledges them. A current sample
 * always gets one slot so a large offline backlog cannot hide where the child
 * is now; older samples drain over subsequent successful uploads.
 */
internal class PendingLocationBuffer(
    private val maxBatchSize: Int = 200,
) {
    private val points = mutableListOf<LocationPoint>()

    @Synchronized
    fun add(location: LocationPoint) {
        if (points.none { it.recordedAt == location.recordedAt }) points += location
    }

    @Synchronized
    fun batch(currentLocation: LocationPoint?): List<LocationPoint> {
        val bufferedLimit = (maxBatchSize - if (currentLocation == null) 0 else 1).coerceAtLeast(0)
        val buffered = points
            .sortedBy { it.recordedAt }
            .take(bufferedLimit)
        return (buffered + listOfNotNull(currentLocation))
            .distinctBy { it.recordedAt }
            .sortedBy { it.recordedAt }
    }

    @Synchronized
    fun acknowledge(uploaded: List<LocationPoint>) {
        val uploadedTimes = uploaded.mapTo(hashSetOf()) { it.recordedAt }
        points.removeAll { it.recordedAt in uploadedTimes }
    }

    @Synchronized
    fun size(): Int = points.size
}
