package com.mirandadevsource.exquisssitaappmanager.features.rewards

import com.mirandadevsource.exquisssitaappmanager.domain.entity.LoyaltyReward

enum class RewardFilter(val label: String) {
    TODAS("Todas"),
    ACTIVAS("Activas"),
    INACTIVAS("Inactivas"),
}

data class RewardsCatalogUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
    val isOnline: Boolean = true,
    val canManageRewards: Boolean = false, // True sólo para rol ADMIN
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val rewards: List<LoyaltyReward> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: RewardFilter = RewardFilter.TODAS,
    // Diálogo Formulario Creación / Edición
    val isFormDialogOpen: Boolean = false,
    val editingReward: LoyaltyReward? = null,
    val formTitle: String = "",
    val formDescription: String = "",
    val formPointsCost: String = "",
    val formIsActive: Boolean = true,
    val formError: String? = null,
    // Confirmación de cambio de estado activo/inactivo
    val togglingReward: LoyaltyReward? = null,
) {
    val totalCount: Int
        get() = rewards.size

    val activeCount: Int
        get() = rewards.count { it.active }

    val inactiveCount: Int
        get() = rewards.count { !it.active }

    val filteredRewards: List<LoyaltyReward>
        get() = rewards.filter { reward ->
            val matchesSearch = searchQuery.isBlank() ||
                reward.title.contains(searchQuery, ignoreCase = true) ||
                reward.description.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                RewardFilter.TODAS -> true
                RewardFilter.ACTIVAS -> reward.active
                RewardFilter.INACTIVAS -> !reward.active
            }

            matchesSearch && matchesFilter
        }
}
