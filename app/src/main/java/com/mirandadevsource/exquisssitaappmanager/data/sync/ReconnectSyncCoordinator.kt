package com.mirandadevsource.exquisssitaappmanager.data.sync

import android.util.Log
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.InventoryRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.OrderRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PaymentRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.TableRepository
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.payroll.GetWeeklyPayrollStatusUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Revalidates the current actor and replays each domain queue when a session becomes online. */
@Singleton
class ReconnectSyncCoordinator @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val authRepository: AuthRepository,
    private val tableRepository: TableRepository,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val inventoryRepository: InventoryRepository,
    private val getWeeklyPayrollStatus: GetWeeklyPayrollStatusUseCase,
) {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            networkMonitor.online
                .onStart { emit(false) }
                .distinctUntilChanged()
                .collect { online -> if (online) synchronizeCurrentSession() }
        }
    }

    private suspend fun synchronizeCurrentSession() {
        try {
            // This performs the online profile/role validation before any outbox is replayed.
            authRepository.restoreSession()
            val session = (authRepository.state.value as? AuthState.SignedIn)?.session
                ?: return
            if (session.isOffline || !networkMonitor.isOnline()) return

            tableRepository.syncPendingCommands().logFailure("tables")
            orderRepository.syncPendingCommands().logFailure("orders")
            paymentRepository.syncPendingPayments().logFailure("payments")
            inventoryRepository.syncPendingCommands().logFailure("inventory")
            getWeeklyPayrollStatus(LocalDate.now(BUSINESS_ZONE)).logFailure("employee/payroll")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "La sincronización de reconexión no terminó; se conservaron las colas locales.", failure)
        }
    }

    private fun Result<*>.logFailure(domain: String) {
        exceptionOrNull()?.let { Log.w(TAG, "Falló la sincronización de $domain.", it) }
    }

    private companion object {
        const val TAG = "ReconnectSync"
        val BUSINESS_ZONE: ZoneId = ZoneId.of("America/Mexico_City")
    }
}
