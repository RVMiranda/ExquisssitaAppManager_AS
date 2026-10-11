package com.mirandadevsource.exquisssitaappmanager.domain.entity

enum class InventoryMovementType(val databaseValue: String) {
    ENTRADA("entrada"),
    VENTA("venta"),
    AJUSTE("ajuste"),
    CANCELACION("cancelacion"),
    MERMA("merma"),
    UNKNOWN("unknown");

    companion object {
        fun fromDatabase(value: String): InventoryMovementType =
            entries.firstOrNull { it.databaseValue == value } ?: UNKNOWN
    }
}

data class StockMovement(
    val id: String,
    val productId: String,
    val quantity: Int,
    val type: InventoryMovementType,
    val referenceId: String?,
    val notes: String?,
    val createdBy: String?,
    val createdAt: String,
    val idempotencyKey: String,
    val syncVersion: Long,
)

data class RegisterInventoryMovement(
    val productId: String,
    /** Signed delta: entry/cancellation positive; sale/waste negative; adjustment either sign. */
    val quantity: Int,
    val type: InventoryMovementType,
    val referenceId: String? = null,
    val notes: String? = null,
    val expectedVersion: Long? = null,
)

enum class InventoryCommandStatus {
    PENDING,
    PROCESSING,
    CONFIRMED_AWAITING_REFRESH,
    REJECTED,
    SYNCED,
}

data class InventoryCommand(
    val idempotencyKey: String,
    val actorUserId: String,
    val productId: String,
    val quantity: Int,
    val type: InventoryMovementType,
    val referenceId: String?,
    val notes: String?,
    val expectedVersion: Long?,
    val status: InventoryCommandStatus,
    val attemptCount: Int,
    val nextAttemptAtMillis: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val movementId: String?,
    val productVersion: Long?,
    val lastErrorCode: String?,
)

enum class InventoryFailureCode {
    NOT_AUTHORIZED,
    INVALID_COMMAND,
    VERSION_REQUIRED,
    PRODUCT_UNAVAILABLE,
    STOCK_VERSION_CONFLICT,
    INSUFFICIENT_STOCK,
    INVALID_COMPENSATION,
    COMPENSATION_EXCEEDS_ORIGINAL,
    IDEMPOTENCY_MISMATCH,
    LEGACY_MOVEMENT_REQUIRES_REVIEW,
    NETWORK_UNKNOWN_RESULT,
    UNKNOWN;

    companion object {
        fun fromServer(reason: String?): InventoryFailureCode = when (reason?.lowercase()) {
            "not_authorized", "42501", "401", "403" -> NOT_AUTHORIZED
            "invalid_command" -> INVALID_COMMAND
            "version_required" -> VERSION_REQUIRED
            "product_unavailable" -> PRODUCT_UNAVAILABLE
            "stock_version_conflict" -> STOCK_VERSION_CONFLICT
            "insufficient_stock" -> INSUFFICIENT_STOCK
            "invalid_compensation" -> INVALID_COMPENSATION
            "compensation_exceeds_original" -> COMPENSATION_EXCEEDS_ORIGINAL
            "idempotency_mismatch" -> IDEMPOTENCY_MISMATCH
            "legacy_movement_requires_review" -> LEGACY_MOVEMENT_REQUIRES_REVIEW
            else -> UNKNOWN
        }
    }
}
