package com.familyshield.mobile.parent

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.util.Locale

internal const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
internal const val WAZE_PACKAGE = "com.waze"

enum class DirectionApp {
    GoogleMaps,
    Waze,
    Other,
}

data class DirectionDestination(val lat: Double, val lng: Double, val label: String)

data class DirectionOption(val app: DirectionApp, val intent: Intent)

fun coordinateForNavigation(value: Double): String =
    String.format(Locale.US, "%.6f", value)

fun googleMapsNavigationUri(destination: DirectionDestination): String =
    "google.navigation:q=${coordinateForNavigation(destination.lat)},${coordinateForNavigation(destination.lng)}"

fun wazeNavigationUri(destination: DirectionDestination): String =
    "waze://?ll=${coordinateForNavigation(destination.lat)},${coordinateForNavigation(destination.lng)}&navigate=yes"

fun genericMapUri(destination: DirectionDestination): String {
    val encodedLabel = URLEncoder.encode(destination.label, Charsets.UTF_8.name()).replace("+", "%20")
    return "geo:0,0?q=${coordinateForNavigation(destination.lat)},${coordinateForNavigation(destination.lng)}($encodedLabel)"
}

fun externalDirectionOptions(context: Context, destination: DirectionDestination): List<DirectionOption> {
    val packageManager = context.packageManager
    return listOf(
        DirectionOption(
            DirectionApp.GoogleMaps,
            Intent(Intent.ACTION_VIEW, Uri.parse(googleMapsNavigationUri(destination))).setPackage(GOOGLE_MAPS_PACKAGE),
        ),
        DirectionOption(
            DirectionApp.Waze,
            Intent(Intent.ACTION_VIEW, Uri.parse(wazeNavigationUri(destination))).setPackage(WAZE_PACKAGE),
        ),
        DirectionOption(
            DirectionApp.Other,
            Intent(Intent.ACTION_VIEW, Uri.parse(genericMapUri(destination))),
        ),
    ).filter { option -> option.intent.resolveActivity(packageManager) != null }
}

fun openExternalDirections(context: Context, option: DirectionOption): Boolean {
    if (option.intent.resolveActivity(context.packageManager) == null) return false
    context.startActivity(option.intent)
    return true
}
