package com.familyshield.mobile.kid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familyshield.mobile.net.PrefsTokenStore

class KidBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            PrefsTokenStore(context.applicationContext).deviceToken != null
        ) {
            startKidMonitoring(context)
        }
    }
}
