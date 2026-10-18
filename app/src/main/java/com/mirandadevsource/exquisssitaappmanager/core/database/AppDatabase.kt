package com.mirandadevsource.exquisssitaappmanager.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.EmployeeDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.InventoryCommandDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.ProductDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.PayrollDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.StockMovementDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.TableDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.OrderDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.OrderDetailDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.OrderCommandDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.TransactionDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.PromotionDao
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.EmployeeEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.InventoryCommandEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PayrollPaymentIntentEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PayrollRecordEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.ProductEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.StockMovementEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.TableEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.OrderEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.OrderDetailEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.TransactionEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PaymentDetailEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PaymentStockEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.OrderCommandEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PromotionEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.DailyClosureDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.CustomerCacheDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.SyncExecutionLeaseDao
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.DailyClosureEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.CustomerCacheEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.SyncExecutionLeaseEntity

@Database(
    entities = [
        EmployeeEntity::class,
        PayrollRecordEntity::class,
        PayrollPaymentIntentEntity::class,
        ProductEntity::class,
        StockMovementEntity::class,
        InventoryCommandEntity::class,
        TableEntity::class,
        OrderEntity::class,
        OrderDetailEntity::class,
        TransactionEntity::class,
        PaymentDetailEntity::class,
        PaymentStockEntity::class,
        OrderCommandEntity::class,
        PromotionEntity::class,
        DailyClosureEntity::class,
        CustomerCacheEntity::class,
        SyncExecutionLeaseEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun employeeDao(): EmployeeDao
    abstract fun payrollDao(): PayrollDao
    abstract fun productDao(): ProductDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun inventoryCommandDao(): InventoryCommandDao
    abstract fun tableDao(): TableDao
    abstract fun orderDao(): OrderDao
    abstract fun orderDetailDao(): OrderDetailDao
    abstract fun orderCommandDao(): OrderCommandDao
    abstract fun transactionDao(): TransactionDao
    abstract fun promotionDao(): PromotionDao
    abstract fun dailyClosureDao(): DailyClosureDao
    abstract fun customerCacheDao(): CustomerCacheDao
    abstract fun syncExecutionLeaseDao(): SyncExecutionLeaseDao
}
