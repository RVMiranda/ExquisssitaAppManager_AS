package com.mirandadevsource.exquisssitaappmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.ExpenseTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseTemplateDao {
    @Query("SELECT * FROM expense_template_cache WHERE cacheActorUserId = :actorUserId AND (:includeInactive = 1 OR activo = 1) ORDER BY nombre COLLATE NOCASE")
    suspend fun getTemplates(actorUserId: String, includeInactive: Boolean): List<ExpenseTemplateEntity>

    @Query("SELECT * FROM expense_template_cache WHERE cacheActorUserId = :actorUserId AND activo = 1 ORDER BY nombre COLLATE NOCASE")
    fun getActiveTemplatesFlow(actorUserId: String): Flow<List<ExpenseTemplateEntity>>

    @Query("SELECT * FROM expense_template_cache WHERE cacheActorUserId = :actorUserId ORDER BY activo DESC, nombre COLLATE NOCASE")
    fun getAllTemplatesFlow(actorUserId: String): Flow<List<ExpenseTemplateEntity>>

    @Query("SELECT * FROM expense_template_cache WHERE localId = :id LIMIT 1")
    suspend fun getByLocalId(id: String): ExpenseTemplateEntity?

    @Query("SELECT * FROM expense_template_cache WHERE remoteId = :remoteId AND cacheActorUserId = :actorUserId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String, actorUserId: String): ExpenseTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: ExpenseTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(templates: List<ExpenseTemplateEntity>)

    @Query("DELETE FROM expense_template_cache WHERE cacheActorUserId = :actorUserId AND syncState = 'SYNCED' AND remoteId NOT IN (:remoteIds)")
    suspend fun deleteSyncedNotIn(actorUserId: String, remoteIds: List<String>)

    @Query("DELETE FROM expense_template_cache WHERE localId = :localId OR remoteId = :localId")
    suspend fun deleteByLocalId(localId: String)
}
