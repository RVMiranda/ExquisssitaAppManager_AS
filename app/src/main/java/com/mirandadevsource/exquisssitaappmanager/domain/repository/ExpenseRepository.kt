package com.mirandadevsource.exquisssitaappmanager.domain.repository

import com.mirandadevsource.exquisssitaappmanager.domain.entity.DailyProfitSummary
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Expense
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpenseTemplate
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    /**
     * Emite la lista reactiva de gastos de la fecha operativa dada desde la base de datos local.
     */
    fun getExpensesFlow(date: String): Flow<List<Expense>>

    /**
     * Sincroniza desde Supabase los gastos para la fecha dada y actualiza la caché local.
     */
    suspend fun refreshExpenses(date: String): Result<List<Expense>>

    /**
     * Guarda o actualiza un gasto. Si hay conexión, lo persiste en Supabase; de lo contrario
     * lo almacena en Room con estado PENDING_INSERT/PENDING_UPDATE.
     */
    suspend fun saveExpense(expense: Expense): Result<Expense>

    /**
     * Elimina un gasto por su localId. Si tiene remoteId y hay conexión, lo elimina en Supabase.
     */
    suspend fun deleteExpense(localId: String): Result<Unit>

    /**
     * Reintenta sincronizar gastos pendientes de sincronización hacia el backend remoto.
     */
    suspend fun syncPendingExpenses(): Result<Int>

    /**
     * Emite reactivamente las plantillas activas de gastos frecuentes.
     */
    fun getTemplatesFlow(): Flow<List<ExpenseTemplate>>

    /**
     * Actualiza el catálogo local de plantillas desde el backend Supabase.
     */
    suspend fun refreshTemplates(): Result<List<ExpenseTemplate>>

    /**
     * Obtiene el resumen de rentabilidad diaria (Ventas - Gastos - Nómina).
     * En línea consulta la RPC obtener_resumen_rentabilidad_diaria; sin conexión calcula el
     * contraste a partir de la caché local Room.
     */
    suspend fun getDailyProfitSummary(date: String): Result<DailyProfitSummary>
}
