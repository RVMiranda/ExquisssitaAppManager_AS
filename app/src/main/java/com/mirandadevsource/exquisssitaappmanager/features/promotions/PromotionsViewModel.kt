package com.mirandadevsource.exquisssitaappmanager.features.promotions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Promotion
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.promotions.CreatePromotionUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.promotions.GetPromotionsUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.promotions.SetPromotionActiveUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.promotions.UpdatePromotionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class PromotionsViewModel @Inject constructor(
    private val getPromotionsUseCase: GetPromotionsUseCase,
    private val createPromotionUseCase: CreatePromotionUseCase,
    private val updatePromotionUseCase: UpdatePromotionUseCase,
    private val setPromotionActiveUseCase: SetPromotionActiveUseCase,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PromotionsUiState())
    val uiState: StateFlow<PromotionsUiState> = _uiState.asStateFlow()

    init {
        loadPromotions()
    }

    fun loadPromotions() {
        val isOnline = networkMonitor.isOnline()
        _uiState.update { it.copy(isLoading = true, errorMessage = null, isOnline = isOnline, todayDate = LocalDate.now().toString()) }
        viewModelScope.launch {
            val result = getPromotionsUseCase(includeInactive = true)
            result.onSuccess { list ->
                _uiState.update { it.copy(isLoading = false, promotions = list, isOnline = networkMonitor.isOnline()) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Error al cargar las promociones.",
                        isOnline = networkMonitor.isOnline(),
                    )
                }
            }
        }
    }

    fun refreshPromotions() {
        val isOnline = networkMonitor.isOnline()
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null, isOnline = isOnline, todayDate = LocalDate.now().toString()) }
        viewModelScope.launch {
            val result = getPromotionsUseCase(includeInactive = true)
            result.onSuccess { list ->
                _uiState.update { it.copy(isRefreshing = false, promotions = list, isOnline = networkMonitor.isOnline()) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        errorMessage = err.message ?: "Error al refrescar las promociones.",
                        isOnline = networkMonitor.isOnline(),
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onFilterSelect(filter: PromotionFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun openCreateDialog() {
        _uiState.update {
            it.copy(
                isFormDialogOpen = true,
                editingPromotion = null,
                formTitle = "",
                formDescription = "",
                formTerms = "",
                formImageUrl = "",
                formExpirationDate = "",
                formIsActive = true,
                formError = null,
            )
        }
    }

    fun openEditDialog(promotion: Promotion) {
        _uiState.update {
            it.copy(
                isFormDialogOpen = true,
                editingPromotion = promotion,
                formTitle = promotion.titulo,
                formDescription = promotion.descripcion,
                formTerms = promotion.terminosCondiciones.orEmpty(),
                formImageUrl = promotion.imagenUrl.orEmpty(),
                formExpirationDate = promotion.fechaVencimiento.orEmpty(),
                formIsActive = promotion.activo,
                formError = null,
            )
        }
    }

    fun dismissFormDialog() {
        _uiState.update {
            it.copy(
                isFormDialogOpen = false,
                editingPromotion = null,
                formError = null,
            )
        }
    }

    fun onFormTitleChange(value: String) {
        _uiState.update { it.copy(formTitle = value, formError = null) }
    }

    fun onFormDescriptionChange(value: String) {
        _uiState.update { it.copy(formDescription = value, formError = null) }
    }

    fun onFormTermsChange(value: String) {
        _uiState.update { it.copy(formTerms = value) }
    }

    fun onFormImageUrlChange(value: String) {
        _uiState.update { it.copy(formImageUrl = value) }
    }

    fun onFormExpirationDateChange(value: String) {
        _uiState.update { it.copy(formExpirationDate = value, formError = null) }
    }

    fun onFormIsActiveChange(value: Boolean) {
        _uiState.update { it.copy(formIsActive = value) }
    }

    fun submitForm() {
        val state = _uiState.value
        val title = state.formTitle.trim()
        val description = state.formDescription.trim()
        val terms = state.formTerms.trim().ifEmpty { null }
        val imageUrl = state.formImageUrl.trim().ifEmpty { null }
        val expirationDate = state.formExpirationDate.trim().ifEmpty { null }

        if (title.isEmpty()) {
            _uiState.update { it.copy(formError = "El título de la promoción es obligatorio.") }
            return
        }
        if (description.isEmpty()) {
            _uiState.update { it.copy(formError = "La descripción de la promoción es obligatoria.") }
            return
        }
        if (expirationDate != null && !DATE_REGEX.matches(expirationDate)) {
            _uiState.update { it.copy(formError = "La fecha de vencimiento debe tener el formato AAAA-MM-DD (ej. 2026-12-31).") }
            return
        }

        if (!networkMonitor.isOnline()) {
            _uiState.update {
                it.copy(
                    formError = "Se requiere conexión a internet para guardar cambios en promociones.",
                    isOnline = false,
                )
            }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, formError = null) }
        viewModelScope.launch {
            val editing = state.editingPromotion
            val result = if (editing == null) {
                createPromotionUseCase(
                    titulo = title,
                    descripcion = description,
                    terminosCondiciones = terms,
                    imagenUrl = imageUrl,
                    fechaVencimiento = expirationDate,
                )
            } else {
                updatePromotionUseCase(
                    id = editing.id,
                    titulo = title,
                    descripcion = description,
                    terminosCondiciones = terms,
                    imagenUrl = imageUrl,
                    fechaVencimiento = expirationDate,
                    activo = state.formIsActive,
                )
            }

            result.onSuccess { saved ->
                val updatedList = if (editing == null) {
                    listOf(saved) + state.promotions
                } else {
                    state.promotions.map { if (it.id == saved.id) saved else it }
                }
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        isFormDialogOpen = false,
                        editingPromotion = null,
                        promotions = updatedList,
                        successMessage = if (editing == null) "Promoción creada exitosamente." else "Promoción actualizada exitosamente.",
                        isOnline = true,
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        formError = err.message ?: "Error al guardar la promoción.",
                        isOnline = networkMonitor.isOnline(),
                    )
                }
            }
        }
    }

    fun requestToggleActive(promotion: Promotion) {
        if (promotion.activo) {
            // Confirm deactivation (operational shutdown)
            _uiState.update { it.copy(deactivatingPromotion = promotion) }
        } else {
            // Reactivate directly
            executeToggleActive(promotion.id, true)
        }
    }

    fun confirmDeactivation() {
        val target = _uiState.value.deactivatingPromotion ?: return
        executeToggleActive(target.id, false)
        _uiState.update { it.copy(deactivatingPromotion = null) }
    }

    fun dismissDeactivateDialog() {
        _uiState.update { it.copy(deactivatingPromotion = null) }
    }

    private fun executeToggleActive(id: String, activo: Boolean) {
        if (!networkMonitor.isOnline()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Se requiere conexión a internet para cambiar el estado de la promoción.",
                    isOnline = false,
                )
            }
            return
        }

        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = setPromotionActiveUseCase(id, activo)
            result.onSuccess { updated ->
                val updatedList = _uiState.value.promotions.map { if (it.id == updated.id) updated else it }
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        promotions = updatedList,
                        successMessage = if (activo) "Promoción activada." else "Promoción pausada/desactivada.",
                        isOnline = true,
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = err.message ?: "Error al cambiar el estado de la promoción.",
                        isOnline = networkMonitor.isOnline(),
                    )
                }
            }
        }
    }

    fun openDetailDialog(promotion: Promotion) {
        _uiState.update { it.copy(viewingDetailPromotion = promotion) }
    }

    fun dismissDetailDialog() {
        _uiState.update { it.copy(viewingDetailPromotion = null) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private companion object {
        val DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    }
}
