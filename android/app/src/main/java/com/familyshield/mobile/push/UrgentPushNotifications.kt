package com.familyshield.mobile.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.familyshield.mobile.MainActivity
import com.familyshield.mobile.R
import kotlin.math.abs

const val URGENT_PUSH_CHANNEL_ID = "familyshield_urgent"

private const val ACTION_OPEN_URGENT = "com.familyshield.mobile.push.OPEN_URGENT"

private val urgentTypes = setOf(
    "urgent_alert",
    "kid_sos_started",
    "kid_sos_ended",
    "sos_acknowledged",
)

fun isUrgentPushData(data: Map<String, String>): Boolean =
    data["type"] in urgentTypes || data["priority"] == "urgent"

fun ensureUrgentPushNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val app = context.applicationContext
    val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        URGENT_PUSH_CHANNEL_ID,
        app.getString(R.string.notification_channel_urgent_name),
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = app.getString(R.string.notification_channel_urgent_description)
        enableVibration(true)
        setBypassDnd(true)
    }
    manager.createNotificationChannel(channel)
}

fun urgentPushNotificationId(data: Map<String, String>): Int {
    val key = data["eventId"] ?: data["messageId"] ?: data["childId"] ?: data["type"] ?: URGENT_PUSH_CHANNEL_ID
    return 9000 + abs(key.hashCode() % 100_000)
}

fun showUrgentPushNotification(
    context: Context,
    title: String?,
    body: String?,
    data: Map<String, String>,
) {
    val app = context.applicationContext
    if (Build.VERSION.SDK_INT >= 33 &&
        app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    ensureUrgentPushNotificationChannel(app)

    val contentIntent = PendingIntent.getActivity(
        app,
        urgentPushNotificationId(data),
        Intent(app, MainActivity::class.java).apply {
            action = ACTION_OPEN_URGENT
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data.forEach { (key, value) -> putExtra(key, value) }
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notificationTitle = title?.takeIf { it.isNotBlank() }
        ?: app.getString(R.string.notification_urgent_fallback_title)
    val notificationBody = body?.takeIf { it.isNotBlank() }.orEmpty()
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(app, URGENT_PUSH_CHANNEL_ID)
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(app).setPriority(Notification.PRIORITY_MAX)
    }

    val notification = builder
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(notificationTitle)
        .setContentText(notificationBody)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .setShowWhen(true)
        .apply {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
            }
            if (notificationBody.isNotBlank()) {
                setStyle(Notification.BigTextStyle().bigText(notificationBody))
            }
        }
        .build()

    val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(urgentPushNotificationId(data), notification)
}

fun hasUrgentNotificationsEnabled(context: Context): Boolean {
    val app = context.applicationContext
    if (Build.VERSION.SDK_INT >= 33 &&
        app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false
    if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
    ensureUrgentPushNotificationChannel(app)
    val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = manager.getNotificationChannel(URGENT_PUSH_CHANNEL_ID) ?: return false
    return channel.importance != NotificationManager.IMPORTANCE_NONE
}

fun hasUrgentDndBypass(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val app = context.applicationContext
    ensureUrgentPushNotificationChannel(app)
    val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = manager.getNotificationChannel(URGENT_PUSH_CHANNEL_ID) ?: return false
    return manager.isNotificationPolicyAccessGranted && channel.canBypassDnd()
}

fun openUrgentNotificationSettings(context: Context) {
    val app = context.applicationContext
    ensureUrgentPushNotificationChannel(app)
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, URGENT_PUSH_CHANNEL_ID)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${app.packageName}"))
    }
    openSettings(app, intent)
}

fun openNotificationPolicyAccessSettings(context: Context) {
    val app = context.applicationContext
    openSettings(app, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
}

private fun openSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
