package com.mirandadevsource.exquisssitaappmanager.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mirandadevsource.exquisssitaappmanager.core.database.AppDatabase
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.EmployeeDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.InventoryCommandDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.PayrollDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.ProductDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.StockMovementDao
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
            .addMigrations(MIGRATION_1_2)
            .addMigrations(MIGRATION_2_3)
            .build()

    @Provides
    fun provideEmployeeDao(database: AppDatabase): EmployeeDao = database.employeeDao()

    @Provides
    fun providePayrollDao(database: AppDatabase): PayrollDao = database.payrollDao()

    @Provides
    fun provideProductDao(database: AppDatabase): ProductDao = database.productDao()

    @Provides
    fun provideStockMovementDao(database: AppDatabase): StockMovementDao = database.stockMovementDao()

    @Provides
    fun provideInventoryCommandDao(database: AppDatabase): InventoryCommandDao = database.inventoryCommandDao()

    private const val DATABASE_NAME = "exquisssita_manager.db"

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `product_cache` (
                    `id` TEXT NOT NULL,
                    `nombre` TEXT NOT NULL,
                    `precioCentavos` INTEGER NOT NULL,
                    `categoria` TEXT NOT NULL,
                    `controlaInventario` INTEGER NOT NULL,
                    `stockActual` INTEGER,
                    `stockMinimo` INTEGER,
                    `activo` INTEGER NOT NULL,
                    `createdAt` TEXT NOT NULL,
                    `updatedAt` TEXT NOT NULL,
                    `syncVersion` INTEGER NOT NULL,
                    `cachedAtMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )""".trimIndent(),
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `inventory_movement_cache` (
                    `remoteId` TEXT NOT NULL,
                    `productId` TEXT NOT NULL,
                    `quantity` INTEGER NOT NULL,
                    `type` TEXT NOT NULL,
                    `referenceId` TEXT,
                    `notes` TEXT,
                    `createdBy` TEXT,
                    `createdAt` TEXT NOT NULL,
                    `idempotencyKey` TEXT NOT NULL,
                    `syncVersion` INTEGER NOT NULL,
                    `cachedAtMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`remoteId`)
                )""".trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_inventory_movement_cache_productId_createdAt` " +
                    "ON `inventory_movement_cache` (`productId`, `createdAt`)" ,
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_inventory_movement_cache_idempotencyKey` " +
                    "ON `inventory_movement_cache` (`idempotencyKey`)" ,
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `inventory_command_outbox` (
                    `idempotencyKey` TEXT NOT NULL,
                    `actorUserId` TEXT NOT NULL,
                    `productId` TEXT NOT NULL,
                    `quantity` INTEGER NOT NULL,
                    `type` TEXT NOT NULL,
                    `referenceId` TEXT,
                    `notes` TEXT,
                    `expectedVersion` INTEGER,
                    `status` TEXT NOT NULL,
                    `attemptCount` INTEGER NOT NULL,
                    `nextAttemptAtMillis` INTEGER,
                    `createdAtMillis` INTEGER NOT NULL,
                    `updatedAtMillis` INTEGER NOT NULL,
                    `movementRemoteId` TEXT,
                    `productVersion` INTEGER,
                    `lastErrorCode` TEXT,
                    PRIMARY KEY(`idempotencyKey`)
                )""".trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_inventory_command_outbox_actorUserId_status_createdAtMillis` " +
                    "ON `inventory_command_outbox` (`actorUserId`, `status`, `createdAtMillis`)" ,
            )
        }
    }
}
