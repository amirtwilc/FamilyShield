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
internal const val HISTORY_MOVEMENT_PAUSE_MIN = 5.0
internal const val HISTORY_MOVEMENT_MIN_DISTANCE_M = 250.0
// GPS can occasionally report a short-lived point hundreds of meters away from
// the real position. If stable groups before and after that point are within
// this distance, keep the stay continuous instead of splitting it into movement.
internal const val HISTORY_OUTLIER_BRIDGE_RADIUS_M = 300.0

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
    val nameCandidates: List<Geo> = emptyList(),
) : HistoryActivity

data class HistoryRouteActivity(
    val from: Geo,
    val to: Geo,
    override val startAt: String,
    override val endAt: String,
    val distanceKm: Double,
    val points: List<RoutePoint>,
) : HistoryActivity

data class HistoryMovementActivity(
    val from: Geo,
    val to: Geo,
    override val startAt: String,
    override val endAt: String,
    val points: List<RoutePoint>,
) : HistoryActivity

private data class HistoryPointGroup(
    val points: List<HistoryPoint>,
    val lat: Double,
    val lng: Double,
    val startAt: String,
    val endAt: String,
) {
    val dwellMin: Double get() = minutesBetween(startAt, endAt)
    val isResting: Boolean get() = dwellMin >= HISTORY_MOVEMENT_PAUSE_MIN
}

fun groupHistoryStays(
    points: List<HistoryPoint>,
    zones: List<Zone>,
    similarLocationRadiusM: Double = HISTORY_SIMILAR_LOCATION_RADIUS_M,
): List<HistoryStay> {
    if (points.isEmpty()) return emptyList()

    return buildHistoryPointGroups(points, similarLocationRadiusM)
        .map { it.toStay(zones) }
        .asReversed()
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
    val stays: List<HistoryActivity> = buildStayAndMovementActivities(stayPoints, zones)
    val routes: List<HistoryActivity> = dayTrips.map { it.toActivity() }
    return (stays + routes)
        .sortedByDescending { it.endAt }
}

private fun buildStayAndMovementActivities(points: List<HistoryPoint>, zones: List<Zone>): List<HistoryActivity> {
    val groups = buildHistoryPointGroups(points, HISTORY_SIMILAR_LOCATION_RADIUS_M)
    if (groups.isEmpty()) return emptyList()
    val activities = mutableListOf<HistoryActivity>()
    val movement = mutableListOf<HistoryPointGroup>()

    fun flushMovement() {
        if (movement.isEmpty()) return
        val movementActivity = movement.toMovementActivity()
        if (movementActivity != null) {
            activities += movementActivity
        } else {
            activities += movement.map { it.toStay(zones) }
        }
        movement.clear()
    }

    groups.forEach { group ->
        if (group.isResting) {
            flushMovement()
            activities += group.toStay(zones)
        } else {
            movement += group
        }
    }
    flushMovement()
    return activities.sortedByDescending { it.endAt }
}

private fun buildHistoryPointGroups(points: List<HistoryPoint>, similarLocationRadiusM: Double): List<HistoryPointGroup> {
    val groups = mutableListOf<MutableList<HistoryPoint>>()
    points
        .filter { it.isValidHistoryPoint() }
        .sortedBy { it.recordedAt }
        .forEach { point ->
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
    return bridgeIsolatedOutlierGroups(
        groups.map { it.toHistoryPointGroup() },
        similarLocationRadiusM,
    )
}

private fun bridgeIsolatedOutlierGroups(
    groups: List<HistoryPointGroup>,
    similarLocationRadiusM: Double,
): List<HistoryPointGroup> {
    val bridged = mutableListOf<HistoryPointGroup>()
    var changed = false
    var index = 0
    while (index < groups.size) {
        val canBridge = index + 2 < groups.size &&
            !groups[index + 1].isResting &&
            groups[index].points.size + groups[index + 2].points.size >= 3 &&
            distanceMeters(
                groups[index].lat,
                groups[index].lng,
                groups[index + 2].lat,
                groups[index + 2].lng,
            ) <= maxOf(similarLocationRadiusM, HISTORY_OUTLIER_BRIDGE_RADIUS_M)

        if (canBridge) {
            bridged += (groups[index].points + groups[index + 1].points + groups[index + 2].points)
                .sortedBy { it.recordedAt }
                .toHistoryPointGroup()
            changed = true
            index += 3
        } else {
            bridged += groups[index]
            index += 1
        }
    }
    return if (changed) bridgeIsolatedOutlierGroups(bridged, similarLocationRadiusM) else bridged
}

private fun List<HistoryPoint>.toHistoryPointGroup(): HistoryPointGroup {
    return HistoryPointGroup(
        points = this,
        lat = sumOf { it.lat } / size,
        lng = sumOf { it.lng } / size,
        startAt = first().recordedAt,
        endAt = last().recordedAt,
    )
}

private fun HistoryPointGroup.toStay(zones: List<Zone>): HistoryStay {
    val zoneName = zones
        .filter { distanceMeters(lat, lng, it.lat, it.lng) <= it.radiusM }
        .minByOrNull { it.radiusM }
        ?.name
    return HistoryStay(
        lat = lat,
        lng = lng,
        startAt = startAt,
        endAt = endAt,
        pointCount = points.size,
        zoneName = zoneName,
        nameCandidates = points.nameCandidates(),
    )
}

private fun List<HistoryPointGroup>.toMovementActivity(): HistoryMovementActivity? {
    val movementPoints = flatMap { it.points }.sortedBy { it.recordedAt }
    if (movementPoints.size < 2) return null
    val distanceM = pathDistanceMeters(movementPoints)
    if (distanceM < HISTORY_MOVEMENT_MIN_DISTANCE_M) return null
    val first = movementPoints.first()
    val last = movementPoints.last()
    return HistoryMovementActivity(
        from = Geo(first.lat, first.lng),
        to = Geo(last.lat, last.lng),
        startAt = first.recordedAt,
        endAt = last.recordedAt,
        points = movementPoints.map { RoutePoint(it.lat, it.lng, it.recordedAt) },
    )
}

private fun List<HistoryPoint>.nameCandidates(): List<Geo> {
    if (isEmpty()) return emptyList()
    return listOf(first(), this[size / 2], last())
        .distinctBy { "%.6f,%.6f".format(it.lat, it.lng) }
        .map { Geo(it.lat, it.lng) }
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

private fun pathDistanceMeters(points: List<HistoryPoint>): Double {
    if (points.size < 2) return 0.0
    return points.zipWithNext().sumOf { (a, b) -> distanceMeters(a.lat, a.lng, b.lat, b.lng) }
}

private fun minutesBetween(startAt: String, endAt: String): Double =
    (java.time.OffsetDateTime.parse(endAt).toInstant().toEpochMilli() -
        java.time.OffsetDateTime.parse(startAt).toInstant().toEpochMilli()) / 60_000.0

private fun HistoryPoint.isValidHistoryPoint(): Boolean =
    lat.isFinite() &&
        lng.isFinite() &&
        lat in -90.0..90.0 &&
        lng in -180.0..180.0 &&
        runCatching { java.time.OffsetDateTime.parse(recordedAt) }.isSuccess

private fun distanceMeters(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val dLat = Math.toRadians(bLat - aLat)
    val dLng = Math.toRadians(bLng - aLng)
    val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) *
        Math.sin(dLng / 2) * Math.sin(dLng / 2)
    return 6_371_000.0 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
}
