package com.mirandadevsource.exquisssitaappmanager.features.promotions

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Promotion
import java.time.LocalDate

enum class PromotionFilter(val label: String) {
    TODAS("Todas"),
    ACTIVAS("Activas"),
    INACTIVAS("Inactivas"),
    VENCIDAS("Vencidas"),
}

data class PromotionsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isOnline: Boolean = true,
    val promotions: List<Promotion> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: PromotionFilter = PromotionFilter.TODAS,
    // Dialog / Form States
    val isFormDialogOpen: Boolean = false,
    val editingPromotion: Promotion? = null,
    val formTitle: String = "",
    val formDescription: String = "",
    val formTerms: String = "",
    val formImageUrl: String = "",
    val formExpirationDate: String = "",
    val formIsActive: Boolean = true,
    val formError: String? = null,
    // Action Confirmation / Detail
    val deactivatingPromotion: Promotion? = null,
    val viewingDetailPromotion: Promotion? = null,
    val todayDate: String = LocalDate.now().toString(),
) {
    val totalCount: Int
        get() = promotions.size

    val activeCount: Int
        get() = promotions.count { it.activo && !isExpired(it) }

    val inactiveCount: Int
        get() = promotions.count { !it.activo }

    val expiredCount: Int
        get() = promotions.count { isExpired(it) }

    val filteredPromotions: List<Promotion>
        get() = promotions.filter { promo ->
            val matchesSearch = searchQuery.isBlank() ||
                promo.titulo.contains(searchQuery, ignoreCase = true) ||
                promo.descripcion.contains(searchQuery, ignoreCase = true) ||
                (promo.terminosCondiciones?.contains(searchQuery, ignoreCase = true) == true)

            val matchesFilter = when (selectedFilter) {
                PromotionFilter.TODAS -> true
                PromotionFilter.ACTIVAS -> promo.activo && !isExpired(promo)
                PromotionFilter.INACTIVAS -> !promo.activo
                PromotionFilter.VENCIDAS -> isExpired(promo)
            }

            matchesSearch && matchesFilter
        }

    fun isExpired(promo: Promotion): Boolean {
        val expiration = promo.fechaVencimiento ?: return false
        return expiration.isNotBlank() && expiration < todayDate
    }
}
