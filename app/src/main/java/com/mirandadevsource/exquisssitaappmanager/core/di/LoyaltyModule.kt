package com.mirandadevsource.exquisssitaappmanager.core.di

import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.repository.LoyaltyRepositoryImpl
import com.mirandadevsource.exquisssitaappmanager.domain.repository.LoyaltyRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LoyaltyModule {
    @Provides
    @Singleton
    fun provideLoyaltyRepository(
        supabaseClient: SupabaseClient,
        networkMonitor: NetworkMonitor,
    ): LoyaltyRepository = LoyaltyRepositoryImpl(supabaseClient, networkMonitor)
}
