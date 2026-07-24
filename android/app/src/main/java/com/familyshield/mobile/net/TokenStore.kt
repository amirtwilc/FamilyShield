package com.familyshield.mobile.net

import android.content.Context

/** Stores the parent JWT (+ refresh token) and the kid device token. Abstracted so
 *  ViewModels can be unit-tested with an in-memory fake instead of SharedPreferences. */
interface TokenStore {
    var parentToken: String?
    var parentRefreshToken: String?
    var deviceToken: String?
    var biometricLock: Boolean
    var alertsEnabled: Boolean
}

/** SharedPreferences-backed [TokenStore] for production. */
class PrefsTokenStore(context: Context) : TokenStore {
    private val prefs = context.applicationContext.getSharedPreferences("familyshield", Context.MODE_PRIVATE)

    private fun put(key: String, v: String?) =
        prefs.edit().apply { if (v == null) remove(key) else putString(key, v) }.apply()

    override var parentToken: String?
        get() = prefs.getString("parent_token", null)
        set(v) = put("parent_token", v)

    override var parentRefreshToken: String?
        get() = prefs.getString("parent_refresh_token", null)
        set(v) = put("parent_refresh_token", v)

    override var deviceToken: String?
        get() = prefs.getString("device_token", null)
        set(v) = put("device_token", v)

    override var biometricLock: Boolean
        get() = prefs.getBoolean("biometric_lock", false)
        set(v) = prefs.edit().putBoolean("biometric_lock", v).apply()

    override var alertsEnabled: Boolean
        get() = prefs.getBoolean("alerts_enabled", true)
        set(v) = prefs.edit().putBoolean("alerts_enabled", v).apply()
}
