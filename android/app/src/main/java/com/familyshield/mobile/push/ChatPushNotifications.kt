package com.familyshield.mobile.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import com.familyshield.mobile.Locales
import com.familyshield.mobile.MainActivity
import com.familyshield.mobile.R
import kotlin.math.abs

const val CHAT_PUSH_TYPE = "chat_message"
const val CHAT_PUSH_CHANNEL_ID = "familyshield_chat"
private const val ACTION_OPEN_CHAT = "com.familyshield.mobile.push.OPEN_CHAT"
private const val KEY_TYPE = "type"
private const val KEY_RECIPIENT = "recipient"
private const val KEY_CHILD_ID = "childId"
private const val KEY_PARENT_ID = "parentId"
private const val KEY_MESSAGE_ID = "messageId"
private const val KEY_CHILD_NAME = "childName"
private const val KEY_PARENT_NAME = "parentName"

data class ChatPushDestination(
    val recipient: String,
    val childId: String,
    val parentId: String,
    val messageId: String? = null,
) {
    val key: String get() = listOf(recipient, childId, parentId, messageId.orEmpty()).joinToString(":")
}

fun ensureChatPushNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val app = context.applicationContext
    val localized = Locales.wrap(app)
    val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(
        NotificationChannel(
            CHAT_PUSH_CHANNEL_ID,
            localized.getString(R.string.notification_channel_chat_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = localized.getString(R.string.notification_channel_chat_description)
        },
    )
}

fun chatPushNotificationId(data: Map<String, String>): Int {
    val key = data["messageId"] ?: data["parentId"] ?: data["childId"] ?: CHAT_PUSH_TYPE
    return 4300 + abs(key.hashCode() % 100_000)
}

fun chatPushDestinationFromData(data: Map<String, String>): ChatPushDestination? {
    if (data[KEY_TYPE] != CHAT_PUSH_TYPE) return null
    val recipient = data[KEY_RECIPIENT]?.takeIf { it == "parent" || it == "child" } ?: return null
    val childId = data[KEY_CHILD_ID]?.takeIf { it.isNotBlank() } ?: return null
    val parentId = data[KEY_PARENT_ID]?.takeIf { it.isNotBlank() } ?: return null
    return ChatPushDestination(
        recipient = recipient,
        childId = childId,
        parentId = parentId,
        messageId = data[KEY_MESSAGE_ID]?.takeIf { it.isNotBlank() },
    )
}

fun chatPushDestinationFromIntent(intent: Intent?): ChatPushDestination? {
    val extras = intent?.extras ?: return null
    return chatPushDestinationFromData(extras.toStringMap())
}

private fun Bundle.toStringMap(): Map<String, String> =
    keySet().mapNotNull { key -> getString(key)?.let { key to it } }.toMap()

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
    val localized = Locales.wrap(app)

    val contentIntent = PendingIntent.getActivity(
        app,
        chatPushNotificationId(data),
        Intent(app, MainActivity::class.java).apply {
            action = ACTION_OPEN_CHAT
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data.forEach { (key, value) -> putExtra(key, value) }
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notificationTitle = localizedChatTitle(localized, title, data)
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

private fun localizedChatTitle(context: Context, remoteTitle: String?, data: Map<String, String>): String {
    val recipient = data[KEY_RECIPIENT]
    val senderName = when (recipient) {
        "child" -> data[KEY_PARENT_NAME]?.takeIf { it.isNotBlank() }
        "parent" -> data[KEY_CHILD_NAME]?.takeIf { it.isNotBlank() }
        else -> null
    }
    return senderName?.let { context.getString(R.string.notification_chat_from_name, it) }
        ?: remoteTitle?.takeIf { it.isNotBlank() }
        ?: context.getString(R.string.notification_chat_fallback_title)
}
