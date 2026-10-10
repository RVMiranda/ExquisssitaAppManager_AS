package com.mirandadevsource.exquisssitaappmanager.core.di

import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.core.network.SupabaseConfig
import com.mirandadevsource.exquisssitaappmanager.core.security.EncryptedSessionManager
import com.mirandadevsource.exquisssitaappmanager.data.repository.AuthRepositoryImpl
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideSupabaseConfig(): SupabaseConfig = SupabaseConfig.fromBuildConfig()

    @Provides
    @Singleton
    fun provideSupabaseClient(
        config: SupabaseConfig,
        sessionManager: EncryptedSessionManager,
    ): SupabaseClient = createSupabaseClient(
        supabaseUrl = config.normalizedUrl.ifBlank { "https://not-configured.invalid" },
        supabaseKey = config.normalizedPublishableKey.ifBlank { "not-configured" },
    ) {
        install(Auth) {
            this.sessionManager = sessionManager
            autoLoadFromStorage = true
            autoSaveToStorage = true
            alwaysAutoRefresh = true
        }
        install(Postgrest)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        supabaseClient: SupabaseClient,
        config: SupabaseConfig,
        sessionManager: EncryptedSessionManager,
        secureAuthStore: com.mirandadevsource.exquisssitaappmanager.core.security.SecureAuthStore,
        networkMonitor: NetworkMonitor,
    ): AuthRepository = AuthRepositoryImpl(
        supabase = supabaseClient,
        config = config,
        sessionManager = sessionManager,
        secureAuthStore = secureAuthStore,
        networkMonitor = networkMonitor,
    )
}
