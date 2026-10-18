package com.mirandadevsource.exquisssitaappmanager.features.loyalty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.loyalty.FindCustomerUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.loyalty.RegisterCustomerVisitUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoyaltyViewModel @Inject constructor(
    private val findCustomerUseCase: FindCustomerUseCase,
    private val registerCustomerVisitUseCase: RegisterCustomerVisitUseCase,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoyaltyUiState(isOnline = networkMonitor.isOnline()))
    val uiState: StateFlow<LoyaltyUiState> = _uiState.asStateFlow()

    // Opaque scanned token retained in memory only during active session. Never persisted or logged.
    private var activeQrToken: String? = null

    init {
        viewModelScope.launch {
            networkMonitor.online.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
    }

    fun initPhoneQuery(phone: String?) {
        if (!phone.isNullOrBlank() && _uiState.value.customer == null) {
            val digits = phone.filter(Char::isDigit).take(10)
            _uiState.update { it.copy(selectedTab = 1, phoneInput = digits) }
            if (digits.length in 7..10) {
                searchByPhone(digits)
            }
        }
    }

    fun setTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun onPhoneInputChanged(input: String) {
        val digits = input.filter(Char::isDigit).take(10)
        _uiState.update { it.copy(phoneInput = digits, customerNotFound = false) }
    }

    fun onQrTokenInputChanged(input: String) {
        _uiState.update { it.copy(qrTokenInput = input.trim()) }
    }

    fun searchByPhone(phone: String? = null) {
        val query = phone ?: _uiState.value.phoneInput
        if (query.length !in 7..10 || !query.all(Char::isDigit)) {
            _uiState.update { it.copy(errorNotification = "Ingresa un número telefónico válido (10 dígitos).") }
            return
        }

        _uiState.update {
            it.copy(
                isSearching = true,
                errorNotification = null,
                successNotification = null,
                warningNotification = null,
                customerNotFound = false,
            )
        }

        viewModelScope.launch {
            val result = findCustomerUseCase.byPhone(query)
            result.onSuccess { customer ->
                activeQrToken = null // Búsqueda telefónica no aporta token QR
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        customer = customer,
                        hasScannedQrToken = false,
                        customerNotFound = customer == null,
                        errorNotification = if (customer == null) {
                            "Cliente no encontrado. Debe registrarse previamente desde la aplicación de clientes."
                        } else null,
                        warningNotification = if (customer?.isCached == true) {
                            "Ficha obtenida de caché local. Las visitas y puntos pueden no estar actualizados."
                        } else null,
                    )
                }
            }.onFailure { error ->
                activeQrToken = null
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        customer = null,
                        hasScannedQrToken = false,
                        customerNotFound = false,
                        errorNotification = sanitizeErrorMessage(error.message ?: "Error al consultar la ficha del cliente."),
                    )
                }
            }
        }
    }

    fun onQrScanned(qrPayload: String) {
        val token = qrPayload.trim()
        if (token.length !in 32..512) {
            _uiState.update {
                it.copy(errorNotification = "El código QR escaneado no tiene un formato de token válido.")
            }
            return
        }

        if (_uiState.value.isSearching || _uiState.value.isRegisteringVisit) return

        _uiState.update {
            it.copy(
                isSearching = true,
                errorNotification = null,
                successNotification = null,
                warningNotification = null,
                customerNotFound = false,
            )
        }

        viewModelScope.launch {
            val result = findCustomerUseCase.byQr(token)
            result.onSuccess { customer ->
                if (customer != null) {
                    activeQrToken = token
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            customer = customer,
                            hasScannedQrToken = true,
                            customerNotFound = false,
                            qrTokenInput = "",
                            successNotification = "Cliente ${customer.name} identificado con éxito.",
                            notificationEventId = System.currentTimeMillis(),
                        )
                    }
                } else {
                    activeQrToken = null
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            customer = null,
                            hasScannedQrToken = false,
                            customerNotFound = true,
                            errorNotification = "Código QR no reconocido o cliente no registrado en el sistema.",
                        )
                    }
                }
            }.onFailure { error ->
                activeQrToken = null
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        customer = null,
                        hasScannedQrToken = false,
                        customerNotFound = false,
                        errorNotification = sanitizeErrorMessage(error.message ?: "Error al validar el código QR."),
                    )
                }
            }
        }
    }

    fun registerVisit() {
        val state = _uiState.value
        if (!state.isOnline) {
            _uiState.update { it.copy(errorNotification = "El registro de visitas requiere conexión a internet.") }
            return
        }
        val token = activeQrToken
        if (token.isNullOrBlank() || !state.hasScannedQrToken) {
            _uiState.update { it.copy(errorNotification = "Para acreditar la visita diaria se requiere escanear el código QR del cliente.") }
            return
        }
        val currentCust = state.customer
        if (currentCust == null) {
            _uiState.update { it.copy(errorNotification = "No hay ningún cliente seleccionado.") }
            return
        }
        if (currentCust.isCached) {
            _uiState.update { it.copy(errorNotification = "No se puede acreditar visita sobre una ficha en caché.") }
            return
        }

        _uiState.update {
            it.copy(
                isRegisteringVisit = true,
                errorNotification = null,
                successNotification = null,
                warningNotification = null,
            )
        }

        viewModelScope.launch {
            val result = registerCustomerVisitUseCase(token)
            result.onSuccess { visit ->
                if (visit.alreadyRegisteredToday) {
                    _uiState.update {
                        it.copy(
                            isRegisteringVisit = false,
                            customer = currentCust.copy(
                                totalVisits = visit.totalVisits,
                                loyaltyPoints = visit.totalLoyaltyPoints,
                            ),
                            warningNotification = "El cliente ya registró su visita el día de hoy (${visit.date}). No se acreditaron puntos adicionales.",
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isRegisteringVisit = false,
                            customer = currentCust.copy(
                                totalVisits = visit.totalVisits,
                                loyaltyPoints = visit.totalLoyaltyPoints,
                            ),
                            successNotification = "¡Visita acreditada con éxito! +${visit.pointsAwarded} puntos de lealtad.",
                            notificationEventId = System.currentTimeMillis(),
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRegisteringVisit = false,
                        errorNotification = sanitizeErrorMessage(error.message ?: "Error al registrar la visita."),
                    )
                }
            }
        }
    }

    fun onRedeemRewardsClicked() {
        _uiState.update {
            it.copy(
                rewardNotice = "El catálogo y canje de recompensas estará disponible en la próxima versión del sistema."
            )
        }
    }

    fun dismissRewardNotice() {
        _uiState.update { it.copy(rewardNotice = null) }
    }

    fun dismissNotification() {
        _uiState.update {
            it.copy(
                errorNotification = null,
                successNotification = null,
                warningNotification = null,
            )
        }
    }

    fun clearCustomer() {
        activeQrToken = null
        _uiState.update {
            it.copy(
                customer = null,
                hasScannedQrToken = false,
                customerNotFound = false,
                phoneInput = "",
                qrTokenInput = "",
                errorNotification = null,
                successNotification = null,
                warningNotification = null,
            )
        }
    }

    private fun sanitizeErrorMessage(raw: String): String {
        return when {
            raw.contains("JWT", ignoreCase = true) || raw.contains("token", ignoreCase = true) ->
                "Sesión no válida o expirada. Por favor, revalida tu acceso."
            raw.contains("violates", ignoreCase = true) || raw.contains("SQL", ignoreCase = true) ->
                "No fue posible completar la operación en el servidor."
            raw.contains("already", ignoreCase = true) || raw.contains("hoy", ignoreCase = true) ->
                "El cliente ya cuenta con visita registrada hoy."
            raw.contains("offline", ignoreCase = true) || raw.contains("network", ignoreCase = true) ->
                "Sin conexión a internet para validar con el servidor."
            else -> raw
        }
    }
}
