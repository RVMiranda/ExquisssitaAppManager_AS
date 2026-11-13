package com.mirandadevsource.exquisssitaappmanager.data.mappers

import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.ExpenseEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.ExpenseTemplateEntity
import com.mirandadevsource.exquisssitaappmanager.data.model.DailyProfitSummaryDto
import com.mirandadevsource.exquisssitaappmanager.data.model.ExpenseDto
import com.mirandadevsource.exquisssitaappmanager.data.model.ExpenseTemplateDto
import com.mirandadevsource.exquisssitaappmanager.domain.entity.DailyProfitSummary
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Expense
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpensePaymentMethod
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpenseTemplate
import java.time.Instant

fun ExpenseEntity.toDomain(): Expense = Expense(
    localId = localId,
    remoteId = remoteId,
    date = fecha,
    concept = concepto,
    category = categoria,
    amountCents = montoCents,
    quantity = cantidad,
    unitPriceCents = precioUnitarioCents,
    paymentMethod = ExpensePaymentMethod.fromRaw(metodoPago),
    templateId = plantillaId,
    notes = notas,
    registeredBy = registradoPor,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncVersion = syncVersion,
    isPendingSync = syncState != ExpenseEntity.SYNCED,
)

fun Expense.toEntity(
    actorUserId: String,
    syncState: String = if (isPendingSync) ExpenseEntity.PENDING_INSERT else ExpenseEntity.SYNCED,
    syncError: String? = null,
): ExpenseEntity = ExpenseEntity(
    localId = localId,
    remoteId = remoteId,
    cacheActorUserId = actorUserId,
    fecha = date,
    concepto = concept,
    categoria = category,
    montoCents = amountCents,
    cantidad = quantity,
    precioUnitarioCents = unitPriceCents,
    metodoPago = paymentMethod.rawValue,
    plantillaId = templateId,
    notas = notes,
    registradoPor = registeredBy,
    createdAt = createdAt.ifBlank { Instant.now().toString() },
    updatedAt = updatedAt.ifBlank { Instant.now().toString() },
    syncVersion = syncVersion,
    syncState = syncState,
    syncError = syncError,
    localUpdatedAtMillis = System.currentTimeMillis(),
)

fun ExpenseDto.toEntity(actorUserId: String): ExpenseEntity = ExpenseEntity(
    localId = id,
    remoteId = id,
    cacheActorUserId = actorUserId,
    fecha = fecha,
    concepto = concepto,
    categoria = categoria,
    montoCents = monto,
    cantidad = cantidad,
    precioUnitarioCents = precioUnitario,
    metodoPago = metodoPago,
    plantillaId = plantillaId,
    notas = notas,
    registradoPor = registradoPor,
    createdAt = createdAt.orEmpty(),
    updatedAt = updatedAt.orEmpty(),
    syncVersion = syncVersion,
    syncState = ExpenseEntity.SYNCED,
    syncError = null,
    localUpdatedAtMillis = System.currentTimeMillis(),
)

fun ExpenseDto.toDomain(): Expense = Expense(
    localId = id,
    remoteId = id,
    date = fecha,
    concept = concepto,
    category = categoria,
    amountCents = monto,
    quantity = cantidad,
    unitPriceCents = precioUnitario,
    paymentMethod = ExpensePaymentMethod.fromRaw(metodoPago),
    templateId = plantillaId,
    notes = notas,
    registeredBy = registradoPor,
    createdAt = createdAt.orEmpty(),
    updatedAt = updatedAt.orEmpty(),
    syncVersion = syncVersion,
    isPendingSync = false,
)

fun ExpenseTemplateEntity.toDomain(): ExpenseTemplate = ExpenseTemplate(
    localId = localId,
    remoteId = remoteId,
    name = nombre,
    category = categoria,
    unitPriceCents = precioUnitarioCents,
    unitOfMeasure = unidadMedida,
    isActive = activo,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncVersion = syncVersion,
)

fun ExpenseTemplate.toEntity(
    actorUserId: String,
    syncState: String = ExpenseTemplateEntity.SYNCED,
): ExpenseTemplateEntity = ExpenseTemplateEntity(
    localId = localId,
    remoteId = remoteId,
    cacheActorUserId = actorUserId,
    nombre = name,
    categoria = category,
    precioUnitarioCents = unitPriceCents,
    unidadMedida = unitOfMeasure,
    activo = isActive,
    createdAt = createdAt.ifBlank { Instant.now().toString() },
    updatedAt = updatedAt.ifBlank { Instant.now().toString() },
    syncVersion = syncVersion,
    syncState = syncState,
    localUpdatedAtMillis = System.currentTimeMillis(),
)

fun ExpenseTemplateDto.toEntity(actorUserId: String): ExpenseTemplateEntity = ExpenseTemplateEntity(
    localId = id,
    remoteId = id,
    cacheActorUserId = actorUserId,
    nombre = nombre,
    categoria = categoria,
    precioUnitarioCents = precioUnitario,
    unidadMedida = unidadMedida,
    activo = activo,
    createdAt = createdAt.orEmpty(),
    updatedAt = updatedAt.orEmpty(),
    syncVersion = syncVersion,
    syncState = ExpenseTemplateEntity.SYNCED,
    localUpdatedAtMillis = System.currentTimeMillis(),
)

fun ExpenseTemplateDto.toDomain(): ExpenseTemplate = ExpenseTemplate(
    localId = id,
    remoteId = id,
    name = nombre,
    category = categoria,
    unitPriceCents = precioUnitario,
    unitOfMeasure = unidadMedida,
    isActive = activo,
    createdAt = createdAt.orEmpty(),
    updatedAt = updatedAt.orEmpty(),
    syncVersion = syncVersion,
)

fun DailyProfitSummaryDto.toDomain(): DailyProfitSummary = DailyProfitSummary(
    date = fecha,
    totalSalesCents = resolvedVentasTotales,
    cashSalesCents = resolvedVentasEfectivo,
    cardSalesCents = ventasTarjeta,
    otherSalesCents = ventasOtro,
    closedOrdersCount = numOrdenesCobradas,
    totalExpensesCents = resolvedGastosTotales,
    cashExpensesCents = resolvedGastosEfectivo,
    otherExpensesCents = gastosOtrosMetodos,
    registeredExpensesCount = numGastosRegistrados,
    totalPayrollCents = resolvedNominaTotal,
    payrollPaymentsCount = numPagosNomina,
    totalOutflowsCents = resolvedEgresosTotales,
    netProfitCents = resolvedGananciaNeta,
    expectedCashInDrawerCents = resolvedEfectivoCajaEsperado,
)
