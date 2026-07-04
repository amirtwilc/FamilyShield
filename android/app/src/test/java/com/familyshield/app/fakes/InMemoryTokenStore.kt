package com.familyshield.app.fakes

import com.familyshield.app.net.TokenStore

/** In-memory [TokenStore] for unit tests (no SharedPreferences / Android). */
class InMemoryTokenStore(
    override var parentToken: String? = null,
    override var parentRefreshToken: String? = null,
    override var deviceToken: String? = null,
    override var biometricLock: Boolean = false,
    override var alertsEnabled: Boolean = true,
) : TokenStore
