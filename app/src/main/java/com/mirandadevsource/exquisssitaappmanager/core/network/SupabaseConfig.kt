package com.mirandadevsource.exquisssitaappmanager.core.network

import com.mirandadevsource.exquisssitaappmanager.BuildConfig
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
            val isLegacyAnonJwt = key.startsWith("eyJ") && key.count { it == '.' } == 2
            return hasValidEndpoint && (isPublishableKey || isLegacyAnonJwt)
        }

    companion object {
        fun fromBuildConfig() = SupabaseConfig(
            url = BuildConfig.SUPABASE_URL,
            publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        )
    }
}
