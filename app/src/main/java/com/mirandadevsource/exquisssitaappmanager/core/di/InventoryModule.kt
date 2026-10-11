package com.mirandadevsource.exquisssitaappmanager.core.di

import com.mirandadevsource.exquisssitaappmanager.core.database.dao.ProductDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.InventoryCommandDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.StockMovementDao
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.repository.InventoryRepositoryImpl
import com.mirandadevsource.exquisssitaappmanager.domain.repository.InventoryRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InventoryModule {
    @Provides
    @Singleton
    fun provideInventoryRepository(
        supabaseClient: SupabaseClient,
        networkMonitor: NetworkMonitor,
        productDao: ProductDao,
        stockMovementDao: StockMovementDao,
        inventoryCommandDao: InventoryCommandDao,
        authRepository: AuthRepository,
    ): InventoryRepository = InventoryRepositoryImpl(
        supabaseClient,
        networkMonitor,
        productDao,
        stockMovementDao,
        inventoryCommandDao,
        authRepository,
    )
}
