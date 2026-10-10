package com.mirandadevsource.exquisssitaappmanager.core.di

import android.content.Context
import androidx.room.Room
import com.mirandadevsource.exquisssitaappmanager.core.database.AppDatabase
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.EmployeeDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.PayrollDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .build()

    @Provides
    fun provideEmployeeDao(database: AppDatabase): EmployeeDao = database.employeeDao()

    @Provides
    fun providePayrollDao(database: AppDatabase): PayrollDao = database.payrollDao()

    private const val DATABASE_NAME = "exquisssita_manager.db"
}
