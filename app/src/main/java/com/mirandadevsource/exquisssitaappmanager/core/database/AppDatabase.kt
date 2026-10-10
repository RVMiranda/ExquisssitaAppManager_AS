package com.mirandadevsource.exquisssitaappmanager.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.EmployeeDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.PayrollDao
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.EmployeeEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PayrollPaymentIntentEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PayrollRecordEntity

@Database(
    entities = [EmployeeEntity::class, PayrollRecordEntity::class, PayrollPaymentIntentEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun employeeDao(): EmployeeDao
    abstract fun payrollDao(): PayrollDao
}
