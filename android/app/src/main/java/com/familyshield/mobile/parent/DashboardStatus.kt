package com.familyshield.mobile.parent

import com.familyshield.mobile.net.CurrentLocation
import com.familyshield.mobile.net.Zone

internal fun currentActiveZoneName(location: CurrentLocation, zones: List<Zone>): String? =
    zones.firstOrNull { zone ->
        zone.active && distanceM(location.lat, location.lng, zone.lat, zone.lng) <= zone.radiusM
    }?.name
