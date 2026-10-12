package com.mirandadevsource.exquisssitaappmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.TableEntity
import kotlinx.coroutines.flow.Flow

data class TableSummaryRow(
    val id: String,
    val numeroMesa: String,
    val estado: String,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val syncState: String,
    val openOrderCount: Int,
)

@Dao
interface TableDao {
    @Query("SELECT t.id, t.numeroMesa, t.estado, t.createdAt, t.updatedAt, t.syncVersion, t.syncState, COUNT(o.id) AS openOrderCount FROM table_cache t LEFT JOIN order_cache o ON o.mesaId = t.id AND o.estado = 'Abierta' AND (o.syncState != 'PENDING' OR o.actorUserId = :actorId) WHERE t.syncState != 'PENDING' OR t.actorUserId = :actorId GROUP BY t.id ORDER BY t.numeroMesa COLLATE NOCASE")
    fun observeTableSummaries(actorId: String): Flow<List<TableSummaryRow>>

    @Query("SELECT * FROM table_cache ORDER BY numeroMesa COLLATE NOCASE")
    suspend fun getAll(): List<TableEntity>

    @Query("SELECT * FROM table_cache WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TableEntity?

    @Query("SELECT COUNT(*) FROM order_cache WHERE mesaId = :tableId AND estado = 'Abierta'")
    suspend fun countOpenOrders(tableId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tables: List<TableEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(table: TableEntity)

    @Query("UPDATE table_cache SET syncVersion = :version, syncState = 'SYNCED', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markSynced(id: String, version: Long, updatedAt: String)

    @Query("DELETE FROM table_cache WHERE id = :id")
    suspend fun deleteById(id: String)
}
