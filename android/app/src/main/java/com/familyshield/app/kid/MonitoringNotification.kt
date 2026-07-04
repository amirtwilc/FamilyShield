package com.familyshield.app.kid

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.familyshield.app.R

private const val CHANNEL_ID = "familyshield_monitoring"
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
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(app, CHANNEL_ID)
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(app)
    }
    return builder
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(app.getString(R.string.kid_monitoring_notification_title))
        .setContentText(app.getString(R.string.kid_monitoring_notification_body))
        .setOngoing(true)
        .setShowWhen(false)
        .build()
}

fun showMonitoringNotification(context: Context) {
    val app = context.applicationContext
    val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    ensureMonitoringNotificationChannel(app)
    if (Build.VERSION.SDK_INT >= 33 &&
        app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    manager.notify(MONITORING_NOTIFICATION_ID, monitoringNotification(app))
}

fun cancelMonitoringNotification(context: Context) {
    val manager = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.cancel(MONITORING_NOTIFICATION_ID)
}
