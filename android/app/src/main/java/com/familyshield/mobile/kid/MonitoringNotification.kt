package com.familyshield.mobile.kid

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import com.familyshield.mobile.MainActivity
import com.familyshield.mobile.R
import com.familyshield.mobile.push.hasUrgentDndBypass

private const val CHANNEL_ID = "familyshield_monitoring"
private const val ACTION_OPEN_MONITORING = "com.familyshield.mobile.kid.OPEN_MONITORING"
const val MONITORING_NOTIFICATION_ID = 4201

fun ensureMonitoringNotificationChannel(context: Context) {
    val app = context.applicationContext
    val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                app.getString(R.string.kid_monitoring_notification_title),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
}

fun monitoringNotification(context: Context): Notification {
    val app = context.applicationContext
    ensureMonitoringNotificationChannel(app)
    val body = app.getString(
        monitoringNotificationBodyRes(
            appUsageGranted = AppUsageTelemetry.hasUsageAccess(app),
            dndBypassAllowed = hasUrgentDndBypass(app),
        ),
    )
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(app, CHANNEL_ID)
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(app)
    }
    return builder
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(app.getString(R.string.kid_monitoring_notification_title))
        .setContentText(body)
        .setStyle(Notification.BigTextStyle().bigText(body))
        .setContentIntent(monitoringContentIntent(app))
        .setOngoing(true)
        .setShowWhen(false)
        .build()
}

fun sosMonitoringNotification(context: Context): Notification {
    val app = context.applicationContext
    ensureMonitoringNotificationChannel(app)
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(app, CHANNEL_ID)
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(app)
    }
    return builder
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(app.getString(R.string.kid_sos_notification_title))
        .setContentText(app.getString(R.string.kid_sos_notification_body))
        .setContentIntent(monitoringContentIntent(app))
        .setOngoing(true)
        .setShowWhen(true)
        .build()
}

@StringRes
internal fun monitoringNotificationBodyRes(
    appUsageGranted: Boolean,
    dndBypassAllowed: Boolean,
): Int = when {
    appUsageGranted && dndBypassAllowed -> R.string.kid_monitoring_notification_body_app_usage_dnd
    appUsageGranted -> R.string.kid_monitoring_notification_body_app_usage
    dndBypassAllowed -> R.string.kid_monitoring_notification_body_dnd
    else -> R.string.kid_monitoring_notification_body
}

private fun monitoringContentIntent(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        MONITORING_NOTIFICATION_ID,
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_MONITORING
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

fun showMonitoringNotification(context: Context) {
    val app = context.applicationContext
    val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    ensureMonitoringNotificationChannel(app)
    if (Build.VERSION.SDK_INT >= 33 &&
        app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    manager.notify(MONITORING_NOTIFICATION_ID, monitoringNotification(app))
}

fun showSosMonitoringNotification(context: Context) {
    val app = context.applicationContext
    val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    ensureMonitoringNotificationChannel(app)
    if (Build.VERSION.SDK_INT >= 33 &&
        app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    manager.notify(MONITORING_NOTIFICATION_ID, sosMonitoringNotification(app))
}

fun cancelMonitoringNotification(context: Context) {
    val manager = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.cancel(MONITORING_NOTIFICATION_ID)
}
