package com.mirandadevsource.exquisssitaappmanager.core.di

import com.mirandadevsource.exquisssitaappmanager.core.database.AppDatabase
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.repository.OrderRepositoryImpl
import com.mirandadevsource.exquisssitaappmanager.data.repository.PaymentRepositoryImpl
import com.mirandadevsource.exquisssitaappmanager.data.repository.TableRepositoryImpl
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.OrderRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PaymentRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.TableRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OrdersModule {
    @Provides @Singleton
    fun provideTableRepository(supabase: SupabaseClient, network: NetworkMonitor, database: AppDatabase, auth: AuthRepository): TableRepository =
        TableRepositoryImpl(supabase, network, database.orderCommandDao(), database.syncExecutionLeaseDao(), auth, database, database.tableDao(), database.orderDao())

    @Provides @Singleton
    fun provideOrderRepository(supabase: SupabaseClient, network: NetworkMonitor, database: AppDatabase, auth: AuthRepository): OrderRepository =
        OrderRepositoryImpl(supabase, network, database.orderCommandDao(), database.syncExecutionLeaseDao(), auth, database, database.orderDao(), database.orderDetailDao(), database.tableDao(), database.transactionDao(), database.productDao())

    @Provides @Singleton
    fun providePaymentRepository(supabase: SupabaseClient, network: NetworkMonitor, database: AppDatabase, auth: AuthRepository): PaymentRepository =
        PaymentRepositoryImpl(supabase, network, database.orderCommandDao(), database.syncExecutionLeaseDao(), auth, database, database.transactionDao(), database.orderDetailDao(), database.orderDao(), database.productDao())
}
