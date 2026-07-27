package com.familyshield.mobile.parent

import com.familyshield.mobile.net.FrequentRoute
import com.familyshield.mobile.net.Geo
import com.familyshield.mobile.net.HistoryPoint
import com.familyshield.mobile.net.RoutePoint
import com.familyshield.mobile.net.RouteTrip
import com.familyshield.mobile.net.Zone
import java.time.LocalDate
import kotlin.math.cos

internal const val HISTORY_DAY_RANGE_DAYS = 14
internal const val HISTORY_SIMILAR_LOCATION_RADIUS_M = 75.0
internal const val HISTORY_ROUTE_MATCH_PROXIMITY_M = 300.0

sealed interface HistoryActivity {
    val startAt: String
    val endAt: String
}

data class HistoryStay(
    val lat: Double,
    val lng: Double,
    override val startAt: String,
    override val endAt: String,
    val pointCount: Int,
    val zoneName: String? = null,
) : HistoryActivity

data class HistoryRouteActivity(
    val from: Geo,
    val to: Geo,
    override val startAt: String,
    override val endAt: String,
    val distanceKm: Double,
    val points: List<RoutePoint>,
) : HistoryActivity

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

fun buildHistoryActivities(
    points: List<HistoryPoint>,
    trips: List<RouteTrip>,
    zones: List<Zone>,
    selectedDate: String,
    selectedRoute: FrequentRoute? = null,
): List<HistoryActivity> {
    val dayTrips = trips
        .filter { it.overlapsDate(selectedDate) }
        .filter { selectedRoute == null || routeMatchesFrequentRoute(it, selectedRoute) }
    if (selectedRoute != null) return dayTrips.map { it.toActivity() }.sortedByDescending { it.endAt }

    val tripIntervals = dayTrips.map { it.departAt to it.arriveAt }
    val stayPoints = points.filterNot { point -> tripIntervals.any { (start, end) -> point.recordedAt >= start && point.recordedAt <= end } }
    val stays: List<HistoryActivity> = groupHistoryStays(stayPoints, zones)
    val routes: List<HistoryActivity> = dayTrips.map { it.toActivity() }
    return (stays + routes)
        .sortedByDescending { it.endAt }
}

fun routeMatchesFrequentRoute(trip: RouteTrip, route: FrequentRoute, proximityM: Double = HISTORY_ROUTE_MATCH_PROXIMITY_M): Boolean {
    val same = distanceMeters(trip.from.lat, trip.from.lng, route.from.lat, route.from.lng) <= proximityM &&
        distanceMeters(trip.to.lat, trip.to.lng, route.to.lat, route.to.lng) <= proximityM
    val reverse = distanceMeters(trip.from.lat, trip.from.lng, route.to.lat, route.to.lng) <= proximityM &&
        distanceMeters(trip.to.lat, trip.to.lng, route.from.lat, route.from.lng) <= proximityM
    return same || reverse
}

fun historySwitcherDays(today: LocalDate, historyDays: List<String>, rangeDays: Int = HISTORY_DAY_RANGE_DAYS): List<LocalDate> {
    val oldestAllowed = today.minusDays((rangeDays - 1).toLong())
    val oldestVisible = historyDays
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .filter { !it.isBefore(oldestAllowed) && !it.isAfter(today) }
        .minOrNull()
    val start = minOf(today.minusDays(6), oldestVisible ?: today.minusDays(6)).coerceAtLeast(oldestAllowed)
    return generateSequence(start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .toList()
}

fun routeOccurrenceDays(trips: List<RouteTrip>, route: FrequentRoute): Set<String> =
    trips.filter { routeMatchesFrequentRoute(it, route) }
        .flatMap { listOf(it.departAt.take(10), it.arriveAt.take(10)) }
        .toSet()

private fun RouteTrip.overlapsDate(date: String): Boolean =
    departAt.take(10) == date || arriveAt.take(10) == date || points.any { it.at.take(10) == date }

private fun RouteTrip.toActivity(): HistoryRouteActivity {
    val routePoints = points.ifEmpty {
        listOf(
            RoutePoint(from.lat, from.lng, departAt),
            RoutePoint(to.lat, to.lng, arriveAt),
        )
    }
    return HistoryRouteActivity(from, to, departAt, arriveAt, distanceKm, routePoints)
}

private fun LocalDate.coerceAtLeast(minimum: LocalDate): LocalDate =
    if (isBefore(minimum)) minimum else this

private fun distanceMeters(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val dLat = Math.toRadians(bLat - aLat)
    val dLng = Math.toRadians(bLng - aLng)
    val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) *
        Math.sin(dLng / 2) * Math.sin(dLng / 2)
    return 6_371_000.0 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
}
