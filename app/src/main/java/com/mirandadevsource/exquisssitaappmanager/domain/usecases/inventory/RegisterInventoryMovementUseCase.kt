package com.mirandadevsource.exquisssitaappmanager.domain.usecases.inventory

import com.mirandadevsource.exquisssitaappmanager.domain.entity.InventoryCommand
import com.mirandadevsource.exquisssitaappmanager.domain.entity.InventoryMovementType
import com.mirandadevsource.exquisssitaappmanager.domain.entity.RegisterInventoryMovement
import com.mirandadevsource.exquisssitaappmanager.domain.repository.InventoryRepository
import java.util.UUID
import javax.inject.Inject

class RegisterInventoryMovementUseCase @Inject constructor(
    private val repository: InventoryRepository,
) {
    suspend operator fun invoke(request: RegisterInventoryMovement): Result<InventoryCommand> {
        if (request.productId.isBlank()) return invalid("El producto es obligatorio.")
        if (request.quantity == 0) return invalid("La cantidad debe ser distinta de cero.")
        if (request.type == InventoryMovementType.UNKNOWN) return invalid("El tipo de movimiento no es válido.")
        if (request.type == InventoryMovementType.VENTA) {
            return invalid("Las ventas deben registrarse desde el flujo de órdenes.")
        }
        if (request.type in setOf(InventoryMovementType.ENTRADA, InventoryMovementType.CANCELACION) && request.quantity < 0) {
            return invalid("La cantidad debe ser positiva para este movimiento.")
        }
        if (request.type in setOf(InventoryMovementType.MERMA) && request.quantity > 0) {
            return invalid("La cantidad debe ser negativa para este movimiento.")
        }
        if (request.type == InventoryMovementType.AJUSTE && request.expectedVersion == null) {
            return invalid("El ajuste requiere la versión de stock consultada.")
        }
        if (request.expectedVersion != null && request.expectedVersion <= 0L) {
            return invalid("La versión esperada debe ser positiva.")
        }
        if (request.type != InventoryMovementType.AJUSTE && request.expectedVersion != null) {
            return invalid("La versión esperada solo se admite en ajustes.")
        }
        if (request.type == InventoryMovementType.CANCELACION && request.referenceId.isNullOrBlank()) {
            return invalid("La cancelación debe referenciar el movimiento original.")
        }

        return repository.registerMovement(
            request.copy(
                productId = request.productId.trim(),
                referenceId = request.referenceId?.trim()?.ifBlank { null },
                notes = request.notes?.trim()?.ifBlank { null },
            ),
        )
    }

    private fun invalid(message: String): Result<InventoryCommand> =
        Result.failure(IllegalArgumentException(message))
}
