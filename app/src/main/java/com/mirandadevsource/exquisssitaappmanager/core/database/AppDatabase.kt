package com.mirandadevsource.exquisssitaappmanager.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.EmployeeDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.InventoryCommandDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.ProductDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.PayrollDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.StockMovementDao
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.EmployeeEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.InventoryCommandEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PayrollPaymentIntentEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PayrollRecordEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.ProductEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.StockMovementEntity

@Database(
    entities = [
        EmployeeEntity::class,
        PayrollRecordEntity::class,
        PayrollPaymentIntentEntity::class,
        ProductEntity::class,
        StockMovementEntity::class,
        InventoryCommandEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun employeeDao(): EmployeeDao
    abstract fun payrollDao(): PayrollDao
    abstract fun productDao(): ProductDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun inventoryCommandDao(): InventoryCommandDao
}
