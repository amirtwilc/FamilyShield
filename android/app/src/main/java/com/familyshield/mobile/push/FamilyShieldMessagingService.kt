package com.familyshield.mobile.push

import com.familyshield.mobile.kid.permissionStatusPayload
import com.familyshield.mobile.net.DeviceTelemetryBody
import com.familyshield.mobile.net.HttpApiClient
import com.familyshield.mobile.net.PrefsTokenStore
import com.familyshield.mobile.net.StatusBody
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

class FamilyShieldMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (!shouldDisplayParentPush(data, PrefsTokenStore(applicationContext).activeParentId)) return
        if (isUrgentPushData(data)) {
            showUrgentPushNotification(
                this,
                message.notification?.title,
                message.notification?.body,
                data,
            )
            return
        }
        if (isSafetyAlertPushData(data)) {
            showSafetyAlertPushNotification(
                this,
                message.notification?.title,
                message.notification?.body,
                data,
            )
            return
        }
        if (data["type"] != CHAT_PUSH_TYPE) return
        showChatPushNotification(
            this,
            chatPushTitle(data, message.notification?.title),
            chatPushBody(data, message.notification?.body),
            data,
        )
    }

    override fun onNewToken(token: String) {
        runBlocking(Dispatchers.IO) {
            val store = PrefsTokenStore(applicationContext)
            if (FirebaseApp.getApps(this@FamilyShieldMessagingService).isNotEmpty()) {
                val api = HttpApiClient(appCheckTokenProvider = { forceRefresh ->
                    runCatching {
                        FirebaseAppCheck.getInstance().getAppCheckToken(forceRefresh).await().token
                    }.getOrNull()
                })
                FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token?.let { parentToken ->
                    runCatching { api.registerParentPushToken(parentToken, token) }
                }
            }
            val api = HttpApiClient()
            store.deviceToken?.let { deviceToken ->
                runCatching {
                    api.sendTelemetry(
                        deviceToken,
                        DeviceTelemetryBody(status = StatusBody(fcmToken = token, permissionStatus = permissionStatusPayload(this@FamilyShieldMessagingService))),
                    )
                }
            }
        }
    }
}

internal fun shouldDisplayParentPush(data: Map<String, String>, activeParentId: String?): Boolean {
    val targetsParent = data["recipient"] == "parent" ||
        data["parentId"] != null && data["recipient"] != "child"
    return !targetsParent || activeParentId != null && data["parentId"] == activeParentId
}

internal fun chatPushTitle(data: Map<String, String>, notificationTitle: String?): String? =
    data["title"]?.takeIf { it.isNotBlank() } ?: notificationTitle

internal fun chatPushBody(data: Map<String, String>, notificationBody: String?): String? =
    data["body"]?.takeIf { it.isNotBlank() } ?: notificationBody
