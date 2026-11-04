package com.mirandadevsource.exquisssitaappmanager.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureAuthStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun put(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val stored = ByteBuffer.allocate(cipher.iv.size + encrypted.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        check(preferences.edit().putString(key, Base64.encodeToString(stored, Base64.NO_WRAP)).commit()) {
            "Could not save encrypted authentication data"
        }
    }

    @Synchronized
    fun get(key: String): String? {
        val encoded = preferences.getString(key, null) ?: return null
        return try {
            val stored = Base64.decode(encoded, Base64.NO_WRAP)
            require(stored.size > IV_LENGTH)
            val buffer = ByteBuffer.wrap(stored)
            val iv = ByteArray(IV_LENGTH).also(buffer::get)
            val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w("EXQUISSSITA_AUTH", "Failed to decrypt key $key, clearing entry: ${e.message}")
            preferences.edit().remove(key).commit()
            null
        }
    }

    fun remove(key: String) {
        preferences.edit().remove(key).commit()
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            val existing = runCatching { keyStore.getKey(KEY_ALIAS, null) as? SecretKey }.getOrNull()
            if (existing != null) return existing

            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            keyGenerator.generateKey()
        } catch (e: Exception) {
            Log.e("EXQUISSSITA_AUTH", "AndroidKeyStore failed, falling back to app-private AES key: ${e.message}", e)
            getOrCreateFallbackKey()
        }
    }

    private fun getOrCreateFallbackKey(): SecretKey {
        val fallbackPrefs = context.getSharedPreferences("manager_fallback_keystore", Context.MODE_PRIVATE)
        val storedKey = fallbackPrefs.getString("fallback_aes_key", null)
        if (storedKey != null) {
            val bytes = Base64.decode(storedKey, Base64.NO_WRAP)
            return SecretKeySpec(bytes, "AES")
        }
        val keyBytes = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        fallbackPrefs.edit().putString("fallback_aes_key", Base64.encodeToString(keyBytes, Base64.NO_WRAP)).commit()
        return SecretKeySpec(keyBytes, "AES")
    }

    private companion object {
        const val PREFERENCES_NAME = "secure_auth_state"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "exquisssita.manager.auth.v2"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}
