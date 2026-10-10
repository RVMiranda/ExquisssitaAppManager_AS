package com.mirandadevsource.exquisssitaappmanager.core.di

import com.mirandadevsource.exquisssitaappmanager.core.database.dao.EmployeeDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.PayrollDao
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.repository.PayrollRepositoryImpl
import com.mirandadevsource.exquisssitaappmanager.data.sync.EmployeesPayrollSyncCoordinator
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PayrollRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PayrollModule {
    @Provides
    @Singleton
    fun providePayrollRepository(
        supabaseClient: SupabaseClient,
        networkMonitor: NetworkMonitor,
        payrollDao: PayrollDao,
        employeeDao: EmployeeDao,
        syncCoordinator: EmployeesPayrollSyncCoordinator,
    ): PayrollRepository = PayrollRepositoryImpl(
        supabaseClient,
        networkMonitor,
        payrollDao,
        employeeDao,
        syncCoordinator,
    )
}
