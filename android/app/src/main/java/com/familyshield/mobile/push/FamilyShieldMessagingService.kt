package com.familyshield.mobile.push

import com.familyshield.mobile.kid.permissionStatusPayload
import com.familyshield.mobile.net.DeviceTelemetryBody
import com.familyshield.mobile.net.HttpApiClient
import com.familyshield.mobile.net.PrefsTokenStore
import com.familyshield.mobile.net.StatusBody
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class FamilyShieldMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (isUrgentPushData(data)) {
            showUrgentPushNotification(
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
            message.notification?.title,
            message.notification?.body,
            data,
        )
    }

    override fun onNewToken(token: String) {
        runBlocking(Dispatchers.IO) {
            val store = PrefsTokenStore(applicationContext)
            val api = HttpApiClient()
            store.parentToken?.let { parentToken ->
                runCatching { api.registerParentPushToken(parentToken, token) }
            }
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
