package com.familyshield.mobile.kid

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.familyshield.mobile.net.PermissionStatus
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

const val PERMISSION_FOREGROUND_LOCATION = 1
const val PERMISSION_BACKGROUND_LOCATION = 2
const val PERMISSION_NOTIFICATIONS = 4
const val PERMISSION_BATTERY_UNRESTRICTED = 8
const val PERMISSION_APP_USAGE = 16
const val PERMISSION_MANUFACTURER_BACKGROUND = 32

private const val PREFS_BACKGROUND_ACCESS = "familyshield_background_access"
private const val KEY_MANUFACTURER_ACCESS_CONFIRMED = "manufacturer_access_confirmed"
private const val KEY_MANUFACTURER_ACCESS_CONFIRMED_AT = "manufacturer_access_confirmed_at"
private const val KEY_MANUFACTURER_ACCESS_DEVICE = "manufacturer_access_device"

enum class ManufacturerBackgroundGuide {
    Generic,
    Xiaomi,
}

data class BackgroundMonitoringStatus(
    val foregroundLocationGranted: Boolean,
    val backgroundLocationGranted: Boolean,
    val notificationsEnabled: Boolean,
    val batteryUnrestricted: Boolean,
    val appUsageGranted: Boolean,
    val manufacturerGuide: ManufacturerBackgroundGuide,
    val manufacturerAccessConfirmed: Boolean,
) {
    val ready: Boolean
        get() = foregroundLocationGranted &&
            backgroundLocationGranted &&
            notificationsEnabled &&
            batteryUnrestricted &&
            appUsageGranted &&
            (!manufacturerGuide.requiresManualConfirmation || manufacturerAccessConfirmed)

    val requiredMask: Int
        get() = PERMISSION_FOREGROUND_LOCATION or
            PERMISSION_BACKGROUND_LOCATION or
            PERMISSION_NOTIFICATIONS or
            PERMISSION_BATTERY_UNRESTRICTED or
            PERMISSION_APP_USAGE or
            (if (manufacturerGuide.requiresManualConfirmation) PERMISSION_MANUFACTURER_BACKGROUND else 0)

    val grantedMask: Int
        get() =
            (if (foregroundLocationGranted) PERMISSION_FOREGROUND_LOCATION else 0) or
                (if (backgroundLocationGranted) PERMISSION_BACKGROUND_LOCATION else 0) or
                (if (notificationsEnabled) PERMISSION_NOTIFICATIONS else 0) or
                (if (batteryUnrestricted) PERMISSION_BATTERY_UNRESTRICTED else 0) or
                (if (appUsageGranted) PERMISSION_APP_USAGE else 0) or
                (if (manufacturerGuide.requiresManualConfirmation && manufacturerAccessConfirmed) PERMISSION_MANUFACTURER_BACKGROUND else 0)

    fun toPermissionStatus(): PermissionStatus =
        PermissionStatus(
            g = if (manufacturerGuide == ManufacturerBackgroundGuide.Xiaomi) "x" else "g",
            r = requiredMask,
            m = grantedMask,
        )
}

fun backgroundMonitoringStatus(context: Context): BackgroundMonitoringStatus {
    val app = context.applicationContext
    val guide = backgroundGuideFor(Build.MANUFACTURER, Build.BRAND)
    return BackgroundMonitoringStatus(
        foregroundLocationGranted = hasKidForegroundLocationPermission(app),
        backgroundLocationGranted = hasKidBackgroundLocationPermission(app),
        notificationsEnabled = hasKidNotificationPermission(app),
        batteryUnrestricted = isIgnoringBatteryOptimizations(app),
        appUsageGranted = AppUsageTelemetry.hasUsageAccess(app),
        manufacturerGuide = guide,
        manufacturerAccessConfirmed = isManufacturerBackgroundAccessConfirmed(app),
    )
}

fun permissionStatusPayload(context: Context): PermissionStatus =
    backgroundMonitoringStatus(context).toPermissionStatus()

val ManufacturerBackgroundGuide.requiresManualConfirmation: Boolean
    get() = this == ManufacturerBackgroundGuide.Xiaomi

fun backgroundGuideFor(manufacturer: String?, brand: String?): ManufacturerBackgroundGuide {
    val text = listOfNotNull(manufacturer, brand).joinToString(" ").lowercase()
    return if (text.contains("xiaomi") || text.contains("poco") || text.contains("redmi")) {
        ManufacturerBackgroundGuide.Xiaomi
    } else {
        ManufacturerBackgroundGuide.Generic
    }
}

fun hasKidForegroundLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

fun hasKidBackgroundLocationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

fun hasKidNotificationPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

fun openBatteryOptimizationSettings(context: Context) {
    openSettings(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
}

fun openManufacturerBackgroundSettings(context: Context) {
    val app = context.applicationContext
    val intent = when (backgroundGuideFor(Build.MANUFACTURER, Build.BRAND)) {
        ManufacturerBackgroundGuide.Xiaomi -> xiaomiBackgroundSettingsIntent(app)
        ManufacturerBackgroundGuide.Generic -> null
    }
    openSettings(app, intent ?: appDetailsIntent(app))
}

fun isManufacturerBackgroundAccessConfirmed(context: Context): Boolean {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_BACKGROUND_ACCESS, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_MANUFACTURER_ACCESS_CONFIRMED, false) &&
        prefs.getString(KEY_MANUFACTURER_ACCESS_DEVICE, null) == manufacturerDeviceKey()
}

fun setManufacturerBackgroundAccessConfirmed(context: Context, confirmed: Boolean) {
    context.applicationContext.getSharedPreferences(PREFS_BACKGROUND_ACCESS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_MANUFACTURER_ACCESS_CONFIRMED, confirmed)
        .putLong(KEY_MANUFACTURER_ACCESS_CONFIRMED_AT, System.currentTimeMillis())
        .putString(KEY_MANUFACTURER_ACCESS_DEVICE, manufacturerDeviceKey())
        .apply()
}

fun openAppPermissionSettings(context: Context) {
    openSettings(
        context,
        appDetailsIntent(context),
    )
}

fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
    }
    openSettings(context, intent)
}

private fun xiaomiBackgroundSettingsIntent(context: Context): Intent? {
    val packageName = context.packageName
    return listOf(
        Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        Intent().setClassName("com.miui.securitycenter", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
            .putExtra("package_name", packageName)
            .putExtra("package_label", context.getString(com.familyshield.mobile.R.string.app_name)),
        Intent().setClassName("com.miui.securitycenter", "com.miui.appmanager.ApplicationsDetailsActivity")
            .putExtra("package_name", packageName),
    ).firstOrNull { it.resolveActivity(context.packageManager) != null }
}

private fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:${context.packageName}"))

private fun manufacturerDeviceKey(): String =
    listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL).joinToString("|")

private fun openSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            appDetailsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
