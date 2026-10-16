package com.mirandadevsource.exquisssitaappmanager.core.di

import com.mirandadevsource.exquisssitaappmanager.core.database.dao.PromotionDao
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.repository.PromotionsRepositoryImpl
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PromotionsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PromotionsModule {
    @Provides
    @Singleton
    fun providePromotionsRepository(
        supabaseClient: SupabaseClient,
        networkMonitor: NetworkMonitor,
        promotionDao: PromotionDao,
    ): PromotionsRepository = PromotionsRepositoryImpl(supabaseClient, networkMonitor, promotionDao)
}
