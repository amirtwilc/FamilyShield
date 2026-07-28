package com.familyshield.mobile.net

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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

    private fun putSensitive(key: String, value: String?) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, encrypt(value))
        }.apply()
    }

    private fun getSensitive(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        if (!stored.startsWith(ENCRYPTED_VALUE_PREFIX)) {
            // One-time migration for credentials written by older app versions.
            putSensitive(key, stored)
            return stored
        }
        return runCatching { decrypt(stored) }.getOrElse {
            // A restored preference or invalidated keystore key must fail closed.
            prefs.edit().remove(key).apply()
            null
        }
    }

    override var parentToken: String?
        get() = getSensitive("parent_token")
        set(v) = putSensitive("parent_token", v)

    override var parentRefreshToken: String?
        get() = getSensitive("parent_refresh_token")
        set(v) = putSensitive("parent_refresh_token", v)

    override var deviceToken: String?
        get() = getSensitive("device_token")
        set(v) = putSensitive("device_token", v)

    override var biometricLock: Boolean
        get() = prefs.getBoolean("biometric_lock", false)
        set(v) = prefs.edit().putBoolean("biometric_lock", v).apply()

    override var alertsEnabled: Boolean
        get() = prefs.getBoolean("alerts_enabled", true)
        set(v) = prefs.edit().putBoolean("alerts_enabled", v).apply()

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(
            cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP,
        )
        return "$ENCRYPTED_VALUE_PREFIX$iv.$encrypted"
    }

    private fun decrypt(stored: String): String {
        val parts = stored.removePrefix(ENCRYPTED_VALUE_PREFIX).split('.', limit = 2)
        require(parts.size == 2) { "Malformed encrypted credential" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    @Synchronized
    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "familyshield.credentials.v1"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val ENCRYPTED_VALUE_PREFIX = "enc-v1:"
    }
}
