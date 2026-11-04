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
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.TableDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.OrderDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.OrderDetailDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.OrderCommandDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.TransactionDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.PromotionDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.DailyClosureDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.CustomerCacheDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.SyncExecutionLeaseDao
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
            .addMigrations(MIGRATION_3_4)
            .addMigrations(MIGRATION_4_5)
            .addMigrations(MIGRATION_5_6)
            .addMigrations(MIGRATION_6_7)
            .addMigrations(MIGRATION_7_8)
            .addMigrations(MIGRATION_8_9)
            .addMigrations(MIGRATION_9_10)
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

    @Provides
    fun provideTableDao(database: AppDatabase): TableDao = database.tableDao()

    @Provides
    fun provideOrderDao(database: AppDatabase): OrderDao = database.orderDao()

    @Provides
    fun provideOrderDetailDao(database: AppDatabase): OrderDetailDao = database.orderDetailDao()

    @Provides
    fun provideOrderCommandDao(database: AppDatabase): OrderCommandDao = database.orderCommandDao()

    @Provides
    fun provideTransactionDao(database: AppDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun providePromotionDao(database: AppDatabase): PromotionDao = database.promotionDao()

    @Provides
    fun provideDailyClosureDao(database: AppDatabase): DailyClosureDao = database.dailyClosureDao()

    @Provides
    fun provideCustomerCacheDao(database: AppDatabase): CustomerCacheDao = database.customerCacheDao()

    @Provides
    fun provideSyncExecutionLeaseDao(database: AppDatabase): SyncExecutionLeaseDao = database.syncExecutionLeaseDao()

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

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `table_cache` (`id` TEXT NOT NULL, `numeroMesa` TEXT NOT NULL, `estado` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `syncVersion` INTEGER NOT NULL, `syncState` TEXT NOT NULL, `cachedAtMillis` INTEGER NOT NULL, `actorUserId` TEXT, PRIMARY KEY(`id`))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_table_cache_numeroMesa` ON `table_cache` (`numeroMesa`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_table_cache_estado` ON `table_cache` (`estado`)")

            db.execSQL("""CREATE TABLE IF NOT EXISTS `order_cache` (`id` TEXT NOT NULL, `mesaId` TEXT, `clienteId` TEXT, `tipoServicio` TEXT NOT NULL, `estado` TEXT NOT NULL, `fechaApertura` TEXT NOT NULL, `fechaCierre` TEXT, `notas` TEXT, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `syncVersion` INTEGER NOT NULL, `actorUserId` TEXT NOT NULL, `syncState` TEXT NOT NULL, `cachedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_cache_mesaId_estado` ON `order_cache` (`mesaId`, `estado`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_cache_estado` ON `order_cache` (`estado`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_cache_actorUserId` ON `order_cache` (`actorUserId`)")

            db.execSQL("""CREATE TABLE IF NOT EXISTS `order_detail_cache` (`id` TEXT NOT NULL, `ordenId` TEXT NOT NULL, `productoId` TEXT NOT NULL, `cantidad` INTEGER NOT NULL, `precioUnitarioCentavos` INTEGER NOT NULL, `estadoPago` TEXT NOT NULL, `notas` TEXT, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `syncVersion` INTEGER NOT NULL, `syncState` TEXT NOT NULL, `cachedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_detail_cache_ordenId` ON `order_detail_cache` (`ordenId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_detail_cache_productoId` ON `order_detail_cache` (`productoId`)")

            db.execSQL("""CREATE TABLE IF NOT EXISTS `transaction_cache` (`id` TEXT NOT NULL, `ordenId` TEXT NOT NULL, `montoCentavos` INTEGER NOT NULL, `metodoPago` TEXT NOT NULL, `fecha` TEXT NOT NULL, `idempotencyKey` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `syncVersion` INTEGER NOT NULL, `cachedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_cache_ordenId` ON `transaction_cache` (`ordenId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transaction_cache_idempotencyKey` ON `transaction_cache` (`idempotencyKey`)")

            db.execSQL("""CREATE TABLE IF NOT EXISTS `payment_detail_cache` (`id` TEXT NOT NULL, `transaccionId` TEXT NOT NULL, `detalleOrdenId` TEXT NOT NULL, `cantidad` INTEGER NOT NULL, `montoCentavos` INTEGER NOT NULL, `cachedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_detail_cache_transaccionId` ON `payment_detail_cache` (`transaccionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_detail_cache_detalleOrdenId` ON `payment_detail_cache` (`detalleOrdenId`)")

            db.execSQL("""CREATE TABLE IF NOT EXISTS `payment_stock_cache` (`id` TEXT NOT NULL, `transaccionId` TEXT NOT NULL, `detalleOrdenId` TEXT NOT NULL, `productoId` TEXT NOT NULL, `cantidad` INTEGER NOT NULL, `movimientoId` TEXT NOT NULL, `idempotencyKey` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `cachedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_stock_cache_transaccionId` ON `payment_stock_cache` (`transaccionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_stock_cache_detalleOrdenId` ON `payment_stock_cache` (`detalleOrdenId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_payment_stock_cache_idempotencyKey` ON `payment_stock_cache` (`idempotencyKey`)")

            db.execSQL("""CREATE TABLE IF NOT EXISTS `order_command_outbox` (`idempotencyKey` TEXT NOT NULL, `actorUserId` TEXT NOT NULL, `aggregateId` TEXT NOT NULL, `entity` TEXT NOT NULL, `entityId` TEXT NOT NULL, `operation` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `expectedVersion` INTEGER, `status` TEXT NOT NULL, `attemptCount` INTEGER NOT NULL, `nextAttemptAtMillis` INTEGER, `createdAtMillis` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, `resultJson` TEXT, `lastErrorCode` TEXT, PRIMARY KEY(`idempotencyKey`))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_command_outbox_actorUserId_status_createdAtMillis` ON `order_command_outbox` (`actorUserId`, `status`, `createdAtMillis`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_command_outbox_aggregateId_createdAtMillis` ON `order_command_outbox` (`aggregateId`, `createdAtMillis`)")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `promotion_cache` (
                `id` TEXT NOT NULL,
                `titulo` TEXT NOT NULL,
                `descripcion` TEXT NOT NULL,
                `terminosCondiciones` TEXT,
                `imagenUrl` TEXT,
                `fechaPublicacion` TEXT NOT NULL,
                `fechaVencimiento` TEXT,
                `activo` INTEGER NOT NULL,
                `createdAt` TEXT NOT NULL,
                `updatedAt` TEXT NOT NULL,
                `syncVersion` INTEGER NOT NULL,
                `cachedAtMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )""")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `cash_cut_cache` (
                `id` TEXT NOT NULL,
                `businessDate` TEXT NOT NULL,
                `cutNumber` INTEGER NOT NULL,
                `periodStart` TEXT NOT NULL,
                `periodEnd` TEXT NOT NULL,
                `cashCents` INTEGER NOT NULL,
                `cardCents` INTEGER NOT NULL,
                `otherCents` INTEGER NOT NULL,
                `totalCents` INTEGER NOT NULL,
                `closedOrderCount` INTEGER NOT NULL,
                `closedAt` TEXT NOT NULL,
                `closedBy` TEXT,
                `syncVersion` INTEGER NOT NULL,
                `cachedAtMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )""")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cash_cut_cache_businessDate_cutNumber` ON `cash_cut_cache` (`businessDate`, `cutNumber`)")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Preserve old rows, but leave their actor unknown. Sync queries explicitly exclude NULL.
            db.execSQL("ALTER TABLE employee_cache ADD COLUMN `cacheActorUserId` TEXT")
            db.execSQL("ALTER TABLE employee_cache ADD COLUMN `updateActorUserId` TEXT")
            db.execSQL("ALTER TABLE employee_cache ADD COLUMN `conflictServerJson` TEXT")
            db.execSQL("DROP INDEX IF EXISTS `index_employee_cache_remoteId` ")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_employee_cache_remoteId_cacheActorUserId` ON `employee_cache` (`remoteId`, `cacheActorUserId`)")
            db.execSQL("ALTER TABLE payroll_record_cache ADD COLUMN `cacheActorUserId` TEXT")
            db.execSQL("DROP INDEX IF EXISTS `index_payroll_record_cache_remoteId` ")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_payroll_record_cache_remoteId_cacheActorUserId` ON `payroll_record_cache` (`remoteId`, `cacheActorUserId`)")
            db.execSQL("ALTER TABLE payroll_payment_intents ADD COLUMN `actorUserId` TEXT")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `customer_profile_cache` (
                `id` TEXT NOT NULL,
                `cacheActorUserId` TEXT NOT NULL,
                `nombre` TEXT NOT NULL,
                `telefono` TEXT,
                `visitasTotales` INTEGER NOT NULL,
                `puntosLealtad` INTEGER NOT NULL,
                `fechaRegistro` TEXT,
                `fetchedAtMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`, `cacheActorUserId`)
            )""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_profile_cache_cacheActorUserId_telefono` ON `customer_profile_cache` (`cacheActorUserId`, `telefono`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_profile_cache_cacheActorUserId_fetchedAtMillis` ON `customer_profile_cache` (`cacheActorUserId`, `fetchedAtMillis`)")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `sync_execution_leases` (
                `actorUserId` TEXT NOT NULL,
                `leaseOwner` TEXT NOT NULL,
                `leaseUntilMillis` INTEGER NOT NULL,
                PRIMARY KEY(`actorUserId`)
            )""")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE product_cache ADD COLUMN `precioAbierto` INTEGER NOT NULL DEFAULT 0")
        }
    }
}
