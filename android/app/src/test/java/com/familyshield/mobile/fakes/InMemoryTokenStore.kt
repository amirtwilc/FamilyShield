package com.familyshield.mobile.fakes

import com.familyshield.mobile.net.TokenStore

/** In-memory [TokenStore] for unit tests (no SharedPreferences / Android). */
class InMemoryTokenStore(
    override var deviceToken: String? = null,
    override var biometricLock: Boolean = false,
    override var alertsEnabled: Boolean = true,
) : TokenStore
