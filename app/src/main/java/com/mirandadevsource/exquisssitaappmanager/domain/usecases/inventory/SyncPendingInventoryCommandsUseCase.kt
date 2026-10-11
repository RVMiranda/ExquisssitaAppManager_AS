package com.mirandadevsource.exquisssitaappmanager.domain.usecases.inventory

import com.mirandadevsource.exquisssitaappmanager.domain.repository.InventoryRepository
import javax.inject.Inject

class SyncPendingInventoryCommandsUseCase @Inject constructor(
    private val repository: InventoryRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repository.syncPendingCommands()
}
