package com.familyshield.mobile

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class FamilyShieldApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isNotEmpty()) {
            val provider = if (BuildConfig.DEBUG) {
                // The debug provider dependency exists only in debug builds, so
                // release APKs cannot accidentally ship its bypass mechanism.
                Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                    .getMethod("getInstance")
                    .invoke(null) as AppCheckProviderFactory
            } else {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(provider)
        }
    }
}
