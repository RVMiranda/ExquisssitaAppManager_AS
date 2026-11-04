package com.mirandadevsource.exquisssitaappmanager.features.rewards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.entity.LoyaltyReward
import com.mirandadevsource.exquisssitaappmanager.domain.entity.UserRole
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.loyalty.GetLoyaltyRewardsUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.loyalty.ManageLoyaltyRewardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RewardsCatalogViewModel @Inject constructor(
    private val getLoyaltyRewardsUseCase: GetLoyaltyRewardsUseCase,
    private val manageLoyaltyRewardUseCase: ManageLoyaltyRewardUseCase,
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RewardsCatalogUiState(
            isOnline = networkMonitor.isOnline(),
            canManageRewards = isCurrentUserAdmin(),
        )
    )
    val uiState: StateFlow<RewardsCatalogUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            networkMonitor.online.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }

        viewModelScope.launch {
            authRepository.state.collect { state ->
                val isAdmin = (state as? AuthState.SignedIn)?.session?.role == UserRole.ADMIN
                _uiState.update { it.copy(canManageRewards = isAdmin) }
            }
        }

        loadRewards()
    }

    private fun isCurrentUserAdmin(): Boolean {
        val state = authRepository.state.value
        return (state as? AuthState.SignedIn)?.session?.role == UserRole.ADMIN
    }

    fun loadRewards() {
        if (!_uiState.value.isOnline) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = "Sin conexión a internet para consultar el catálogo de recompensas.",
                )
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val includeInactive = _uiState.value.canManageRewards
            val result = getLoyaltyRewardsUseCase(includeInactive = includeInactive)
            result.onSuccess { rewardsList ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        rewards = rewardsList,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = sanitizeErrorMessage(error.message ?: "Error al cargar las recompensas."),
                    )
                }
            }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null, successMessage = null) }
        loadRewards()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onFilterSelected(filter: RewardFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun openCreateDialog() {
        if (!_uiState.value.canManageRewards) return
        _uiState.update {
            it.copy(
                isFormDialogOpen = true,
                editingReward = null,
                formTitle = "",
                formDescription = "",
                formPointsCost = "",
                formIsActive = true,
                formError = null,
            )
        }
    }

    fun openEditDialog(reward: LoyaltyReward) {
        if (!_uiState.value.canManageRewards) return
        _uiState.update {
            it.copy(
                isFormDialogOpen = true,
                editingReward = reward,
                formTitle = reward.title,
                formDescription = reward.description,
                formPointsCost = reward.pointsCost.toString(),
                formIsActive = reward.active,
                formError = null,
            )
        }
    }

    fun dismissFormDialog() {
        _uiState.update {
            it.copy(
                isFormDialogOpen = false,
                editingReward = null,
                formError = null,
                isSubmitting = false,
            )
        }
    }

    fun onFormTitleChange(title: String) {
        _uiState.update { it.copy(formTitle = title, formError = null) }
    }

    fun onFormDescriptionChange(description: String) {
        _uiState.update { it.copy(formDescription = description, formError = null) }
    }

    fun onFormPointsCostChange(points: String) {
        val digits = points.filter(Char::isDigit)
        _uiState.update { it.copy(formPointsCost = digits, formError = null) }
    }

    fun onFormIsActiveChange(active: Boolean) {
        _uiState.update { it.copy(formIsActive = active) }
    }

    fun saveReward() {
        val state = _uiState.value
        if (!state.canManageRewards) {
            _uiState.update { it.copy(formError = "Solo el rol Administrador puede gestionar recompensas.") }
            return
        }
        if (!state.isOnline) {
            _uiState.update { it.copy(formError = "Se requiere conexión a internet para guardar cambios.") }
            return
        }

        val cleanTitle = state.formTitle.trim()
        val cleanDescription = state.formDescription.trim()
        val pointsCost = state.formPointsCost.toIntOrNull() ?: 0

        if (cleanTitle.isBlank()) {
            _uiState.update { it.copy(formError = "El título de la recompensa es obligatorio.") }
            return
        }
        if (cleanTitle.length > 160) {
            _uiState.update { it.copy(formError = "El título no puede exceder 160 caracteres.") }
            return
        }
        if (pointsCost <= 0) {
            _uiState.update { it.copy(formError = "El costo en puntos debe ser mayor a 0.") }
            return
        }

        val isEditing = state.editingReward != null
        val rewardId = state.editingReward?.id ?: UUID.randomUUID().toString()
        val expectedVersion = state.editingReward?.syncVersion ?: 0L

        _uiState.update { it.copy(isSubmitting = true, formError = null) }

        viewModelScope.launch {
            val result = manageLoyaltyRewardUseCase(
                rewardId = rewardId,
                title = cleanTitle,
                description = cleanDescription,
                pointsCost = pointsCost,
                active = state.formIsActive,
                expectedVersion = expectedVersion,
            )
            result.onSuccess { savedReward ->
                val updatedRewards = if (isEditing) {
                    state.rewards.map { if (it.id == savedReward.id) savedReward else it }
                } else {
                    state.rewards + savedReward
                }.sortedBy { it.title }

                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        isFormDialogOpen = false,
                        editingReward = null,
                        rewards = updatedRewards,
                        successMessage = if (isEditing) {
                            "Recompensa '${savedReward.title}' actualizada correctamente."
                        } else {
                            "Recompensa '${savedReward.title}' creada correctamente."
                        },
                    )
                }
            }.onFailure { error ->
                val rawMsg = error.message ?: ""
                val specificError = when {
                    rawMsg.contains("version_conflict", ignoreCase = true) || rawMsg.contains("P0004", ignoreCase = true) ->
                        "Conflicto de concurrencia: la recompensa fue modificada en otro dispositivo. Recarga el catálogo."
                    rawMsg.contains("not_authorized", ignoreCase = true) || rawMsg.contains("42501", ignoreCase = true) ->
                        "No tienes permisos de administrador para guardar esta recompensa."
                    else -> sanitizeErrorMessage(rawMsg.ifBlank { "Error al guardar la recompensa." })
                }
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        formError = specificError,
                    )
                }
            }
        }
    }

    fun requestToggleActive(reward: LoyaltyReward) {
        if (!_uiState.value.canManageRewards) return
        _uiState.update { it.copy(togglingReward = reward) }
    }

    fun dismissToggleActive() {
        _uiState.update { it.copy(togglingReward = null) }
    }

    fun confirmToggleActive() {
        val state = _uiState.value
        val reward = state.togglingReward ?: return
        if (!state.canManageRewards || !state.isOnline) return

        val newActive = !reward.active
        _uiState.update { it.copy(isSubmitting = true, togglingReward = null) }

        viewModelScope.launch {
            val result = manageLoyaltyRewardUseCase(
                rewardId = reward.id,
                title = reward.title,
                description = reward.description,
                pointsCost = reward.pointsCost,
                active = newActive,
                expectedVersion = reward.syncVersion,
            )
            result.onSuccess { updated ->
                val updatedList = state.rewards.map { if (it.id == updated.id) updated else it }
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        rewards = updatedList,
                        successMessage = if (newActive) {
                            "Recompensa '${updated.title}' activada."
                        } else {
                            "Recompensa '${updated.title}' desactivada."
                        },
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = sanitizeErrorMessage(error.message ?: "Error al cambiar el estado de la recompensa."),
                    )
                }
            }
        }
    }

    fun dismissNotification() {
        _uiState.update {
            it.copy(
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    private fun sanitizeErrorMessage(raw: String): String {
        return when {
            raw.contains("JWT", ignoreCase = true) || raw.contains("token", ignoreCase = true) ->
                "Sesión no válida o expirada. Por favor, revalida tu acceso."
            raw.contains("offline", ignoreCase = true) || raw.contains("network", ignoreCase = true) ->
                "Sin conexión a internet para validar con el servidor."
            else -> raw
        }
    }
}
