package com.mirandadevsource.exquisssitaappmanager.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureAuthStore @Inject constructor(
    @ApplicationContext context: Context,
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
        } catch (_: Exception) {
            preferences.edit().remove(key).commit()
            null
        }
    }

    fun remove(key: String) {
        preferences.edit().remove(key).commit()
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "secure_auth_state"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "exquisssita.auth.aes"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}
