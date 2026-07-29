package com.familyshield.mobile.ui

internal const val FAMILY_MAP_MAX_ZOOM = 15.0

internal fun cappedFamilyMapZoom(requestedZoom: Double): Double =
    requestedZoom.coerceAtMost(FAMILY_MAP_MAX_ZOOM)

internal fun familyMapLocationsCoincide(locations: List<Pair<Double, Double>>): Boolean =
    locations.size > 1 && locations.drop(1).all { it == locations.first() }
