package com.mirandadevsource.exquisssitaappmanager.core.di

import com.mirandadevsource.exquisssitaappmanager.core.database.dao.EmployeeDao
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.repository.EmployeeLoanRepositoryImpl
import com.mirandadevsource.exquisssitaappmanager.data.repository.EmployeeRepositoryImpl
import com.mirandadevsource.exquisssitaappmanager.data.sync.EmployeesPayrollSyncCoordinator
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.EmployeeLoanRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.EmployeeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EmployeesModule {
    @Provides
    @Singleton
    fun provideEmployeeRepository(
        supabaseClient: SupabaseClient,
        networkMonitor: NetworkMonitor,
        employeeDao: EmployeeDao,
        syncCoordinator: EmployeesPayrollSyncCoordinator,
        authRepository: AuthRepository,
    ): EmployeeRepository = EmployeeRepositoryImpl(
        supabaseClient,
        networkMonitor,
        employeeDao,
        syncCoordinator,
        authRepository,
    )

    @Provides
    @Singleton
    fun provideEmployeeLoanRepository(
        supabaseClient: SupabaseClient,
        employeeDao: EmployeeDao,
        authRepository: AuthRepository,
        networkMonitor: NetworkMonitor,
    ): EmployeeLoanRepository = EmployeeLoanRepositoryImpl(
        supabaseClient,
        employeeDao,
        authRepository,
        networkMonitor,
    )
}
