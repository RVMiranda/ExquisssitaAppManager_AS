package com.mirandadevsource.exquisssitaappmanager.core.di

import com.mirandadevsource.exquisssitaappmanager.core.database.AppDatabase
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.repository.CashRegisterRepositoryImpl
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.CashRegisterRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CashRegisterModule {
    @Provides
    @Singleton
    fun provideCashRegisterRepository(
        supabase: SupabaseClient,
        network: NetworkMonitor,
        database: AppDatabase,
        auth: AuthRepository,
    ): CashRegisterRepository = CashRegisterRepositoryImpl(
        supabase = supabase,
        networkMonitor = network,
        commandDao = database.orderCommandDao(),
        authRepository = auth,
        closureDao = database.dailyClosureDao(),
    )
}
