package com.familyshield.mobile.parent

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

suspend fun parentFcmTokenOrNull(): String? = try {
    FirebaseMessaging.getInstance().token.await()
} catch (_: Throwable) {
    null
}
