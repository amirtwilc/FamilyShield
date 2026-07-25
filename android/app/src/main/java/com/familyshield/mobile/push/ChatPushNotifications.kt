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
import com.familyshield.mobile.MainActivity
import com.familyshield.mobile.R
import kotlin.math.abs

const val CHAT_PUSH_TYPE = "chat_message"
const val CHAT_PUSH_CHANNEL_ID = "familyshield_chat"

fun ensureChatPushNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val app = context.applicationContext
    val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(
        NotificationChannel(
            CHAT_PUSH_CHANNEL_ID,
            app.getString(R.string.notification_channel_chat_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = app.getString(R.string.notification_channel_chat_description)
        },
    )
}

fun chatPushNotificationId(data: Map<String, String>): Int {
    val key = data["messageId"] ?: data["parentId"] ?: data["childId"] ?: CHAT_PUSH_TYPE
    return 4300 + abs(key.hashCode() % 100_000)
}

fun showChatPushNotification(
    context: Context,
    title: String?,
    body: String?,
    data: Map<String, String>,
) {
    val app = context.applicationContext
    if (Build.VERSION.SDK_INT >= 33 &&
        app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    ensureChatPushNotificationChannel(app)

    val contentIntent = PendingIntent.getActivity(
        app,
        0,
        Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notificationTitle = title?.takeIf { it.isNotBlank() }
        ?: app.getString(R.string.notification_chat_fallback_title)
    val notificationBody = body?.takeIf { it.isNotBlank() }.orEmpty()
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(app, CHAT_PUSH_CHANNEL_ID)
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(app)
    }

    val notification = builder
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(notificationTitle)
        .setContentText(notificationBody)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .setShowWhen(true)
        .apply {
            if (notificationBody.isNotBlank()) {
                setStyle(Notification.BigTextStyle().bigText(notificationBody))
            }
        }
        .build()

    val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(chatPushNotificationId(data), notification)
}
