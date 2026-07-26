package com.familyshield.mobile.parent

import com.familyshield.mobile.net.HistoryPoint
import com.familyshield.mobile.net.Zone
import kotlin.math.cos

internal const val HISTORY_SIMILAR_LOCATION_RADIUS_M = 75.0

data class HistoryStay(
    val lat: Double,
    val lng: Double,
    val startAt: String,
    val endAt: String,
    val pointCount: Int,
    val zoneName: String? = null,
)

fun groupHistoryStays(
    points: List<HistoryPoint>,
    zones: List<Zone>,
    similarLocationRadiusM: Double = HISTORY_SIMILAR_LOCATION_RADIUS_M,
): List<HistoryStay> {
    if (points.isEmpty()) return emptyList()

    val groups = mutableListOf<MutableList<HistoryPoint>>()
    points.sortedBy { it.recordedAt }.forEach { point ->
        val current = groups.lastOrNull()
        if (current == null) {
            groups += mutableListOf(point)
            return@forEach
        }

        val centerLat = current.sumOf { it.lat } / current.size
        val centerLng = current.sumOf { it.lng } / current.size
        if (distanceMeters(centerLat, centerLng, point.lat, point.lng) <= similarLocationRadiusM) {
            current += point
        } else {
            groups += mutableListOf(point)
        }
    }

    return groups.map { group ->
        val lat = group.sumOf { it.lat } / group.size
        val lng = group.sumOf { it.lng } / group.size
        val zoneName = zones
            .filter { distanceMeters(lat, lng, it.lat, it.lng) <= it.radiusM }
            .minByOrNull { it.radiusM }
            ?.name
        HistoryStay(
            lat = lat,
            lng = lng,
            startAt = group.first().recordedAt,
            endAt = group.last().recordedAt,
            pointCount = group.size,
            zoneName = zoneName,
        )
    }.asReversed()
}

private fun distanceMeters(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val dLat = Math.toRadians(bLat - aLat)
    val dLng = Math.toRadians(bLng - aLng)
    val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) *
        Math.sin(dLng / 2) * Math.sin(dLng / 2)
    return 6_371_000.0 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
}
