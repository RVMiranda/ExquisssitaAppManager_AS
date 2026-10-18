package com.mirandadevsource.exquisssitaappmanager.core.network

import com.mirandadevsource.exquisssitaappmanager.BuildConfig
import android.util.Base64
import java.net.URI

data class SupabaseConfig(
    val url: String,
    val publishableKey: String,
) {
    val normalizedUrl: String
        get() = url.trim().trimEnd('/')

    val normalizedPublishableKey: String
        get() = publishableKey.trim()

    val isConfigured: Boolean
        get() {
            val endpoint = runCatching { URI(normalizedUrl) }.getOrNull() ?: return false
            val hasValidEndpoint = endpoint.scheme.equals("https", ignoreCase = true) &&
                !endpoint.host.isNullOrBlank()
            val key = normalizedPublishableKey
            val isPublishableKey = key.startsWith("sb_publishable_") && key.length > "sb_publishable_".length
            val isLegacyAnonJwt = runCatching {
                val parts = key.split('.')
                if (parts.size != 3 || !key.startsWith("eyJ")) return@runCatching false
                val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
                Regex("\\\"role\\\"\\s*:\\s*\\\"anon\\\"").containsMatchIn(payload)
            }.getOrDefault(false)
            return hasValidEndpoint && (isPublishableKey || isLegacyAnonJwt)
        }

    companion object {
        fun fromBuildConfig() = SupabaseConfig(
            url = BuildConfig.SUPABASE_URL,
            publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        )
    }
}
