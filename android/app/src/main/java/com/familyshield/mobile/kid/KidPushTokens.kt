package com.familyshield.mobile.kid

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

suspend fun kidFcmTokenOrNull(): String? = try {
    FirebaseMessaging.getInstance().token.await()
} catch (_: Throwable) {
    null
}
