package com.familyshield.mobile.push

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

fun hasAppNotificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context.applicationContext).areNotificationsEnabled()

fun openAppNotificationSettings(context: Context) {
    val app = context.applicationContext
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
    } else {
        appDetailsIntent(app)
    }
    openSettings(app, intent)
}

private fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:${context.packageName}"))

private fun openSettings(context: Context, intent: Intent) {
    val app = context.applicationContext
    val withFlags = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { app.startActivity(withFlags) }
        .recoverCatching { app.startActivity(appDetailsIntent(app).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
