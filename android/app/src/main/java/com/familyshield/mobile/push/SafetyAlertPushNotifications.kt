package com.familyshield.mobile.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.familyshield.mobile.Locales
import com.familyshield.mobile.MainActivity
import com.familyshield.mobile.R
import kotlin.math.abs

const val ALERT_PUSH_CHANNEL_ID = "familyshield_alerts"

private const val ACTION_OPEN_ALERT = "com.familyshield.mobile.push.OPEN_ALERT"

private val safetyAlertTypes = setOf(
    "low_battery",
    "offline",
    "child_unpaired",
    "safe_zone_enter",
    "safe_zone_exit",
    "app_usage_limit_exceeded",
)

fun isSafetyAlertPushData(data: Map<String, String>): Boolean =
    data["type"] in safetyAlertTypes

fun ensureSafetyAlertNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val app = context.applicationContext
    val localized = Locales.wrap(app)
    val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(
        NotificationChannel(
            ALERT_PUSH_CHANNEL_ID,
            localized.getString(R.string.notification_channel_alerts_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = localized.getString(R.string.notification_channel_alerts_description)
        },
    )
}

fun safetyAlertNotificationId(data: Map<String, String>): Int {
    val key = listOf(
        data["type"].orEmpty(),
        data["childId"].orEmpty(),
        data["zoneId"].orEmpty(),
        data["limitId"].orEmpty(),
    ).joinToString(":")
    return 12_000 + abs(key.hashCode() % 100_000)
}

private fun formatLimitMinutes(min: Int): String {
    val h = min / 60
    val m = min % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

internal fun safetyAlertNotificationCopy(
    context: Context,
    title: String?,
    body: String?,
    data: Map<String, String>,
): PushNotificationCopy {
    val fallbackTitle = title?.takeIf { it.isNotBlank() }
        ?: context.getString(R.string.notification_alert_fallback_title)
    val fallbackBody = body?.takeIf { it.isNotBlank() }.orEmpty()
    return when (data["type"]) {
        "low_battery" -> {
            val battery = data["batteryLevel"]?.toIntOrNull()
            PushNotificationCopy(
                context.getString(R.string.alert_low_battery),
                if (battery != null) {
                    context.getString(R.string.notification_low_battery_body, battery)
                } else {
                    context.getString(R.string.alert_battery_body)
                },
            )
        }
        "offline" -> PushNotificationCopy(
            context.getString(R.string.alert_offline),
            context.getString(R.string.alert_offline_body),
        )
        "child_unpaired" -> PushNotificationCopy(
            context.getString(R.string.alert_child_unpaired),
            context.getString(R.string.notification_child_unpaired_body),
        )
        "safe_zone_enter" -> {
            val zoneName = data["zoneName"]?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.chip_safe_zone)
            PushNotificationCopy(
                context.getString(R.string.alert_safe_zone_enter),
                context.getString(R.string.notification_safe_zone_enter_body, zoneName),
            )
        }
        "safe_zone_exit" -> {
            val zoneName = data["zoneName"]?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.chip_safe_zone)
            PushNotificationCopy(
                context.getString(R.string.alert_safe_zone_exit),
                context.getString(R.string.notification_safe_zone_exit_body, zoneName),
            )
        }
        "app_usage_limit_exceeded" -> {
            val usage = data["usageMinutes"]?.toIntOrNull()?.let(::formatLimitMinutes)
            val limit = data["limitMinutes"]?.toIntOrNull()?.let(::formatLimitMinutes)
            val app = data["app"]?.takeIf { it.isNotBlank() }
            val childName = data["childName"]?.takeIf { it.isNotBlank() }
            PushNotificationCopy(
                context.getString(R.string.alert_app_usage_limit),
                when {
                    usage != null && limit != null && app != null && childName != null ->
                        context.getString(R.string.notification_app_usage_limit_app_body, childName, app, usage, limit)
                    usage != null && limit != null && childName != null ->
                        context.getString(R.string.notification_app_usage_limit_total_body, childName, usage, limit)
                    usage != null && limit != null && app != null ->
                        context.getString(R.string.notification_app_usage_limit_app_legacy_body, app, usage, limit)
                    usage != null && limit != null ->
                        context.getString(R.string.notification_app_usage_limit_total_legacy_body, usage, limit)
                    else -> context.getString(R.string.alert_app_usage_limit_body)
                },
            )
        }
        else -> PushNotificationCopy(fallbackTitle, fallbackBody)
    }
}

fun showSafetyAlertPushNotification(
    context: Context,
    title: String?,
    body: String?,
    data: Map<String, String>,
) {
    val app = context.applicationContext
    if (Build.VERSION.SDK_INT >= 33 &&
        app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    ensureSafetyAlertNotificationChannel(app)
    val localized = Locales.wrap(app)
    val copy = safetyAlertNotificationCopy(localized, title, body, data)

    val contentIntent = PendingIntent.getActivity(
        app,
        safetyAlertNotificationId(data),
        Intent(app, MainActivity::class.java).apply {
            action = ACTION_OPEN_ALERT
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data.forEach { (key, value) -> putExtra(key, value) }
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(app, ALERT_PUSH_CHANNEL_ID)
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(app).setPriority(Notification.PRIORITY_HIGH)
    }

    val notification = builder
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(copy.title)
        .setContentText(copy.body)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .setShowWhen(true)
        .apply {
            if (copy.body.isNotBlank()) {
                setStyle(Notification.BigTextStyle().bigText(copy.body))
            }
        }
        .build()

    val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(safetyAlertNotificationId(data), notification)
}
