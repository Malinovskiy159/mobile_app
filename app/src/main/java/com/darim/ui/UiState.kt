// ui/common/UiState.kt
package com.darim.ui

import androidx.annotation.StringRes

/**
 * Общий класс для состояний UI
 */
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, @StringRes val messageRes: Int? = null) : UiState<Nothing>()
    object Empty : UiState<Nothing>()
}

/**
 * Состояние для списков с пагинацией
 */
data class ListUiState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val isEmpty: Boolean = false,
    val hasMore: Boolean = true,
    val currentPage: Int = 0,
    val totalCount: Int = 0
)

/**
 * Состояние для экрана с деталями
 */
data class DetailUiState<T>(
    val data: T? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRefreshing: Boolean = false
)

/**
 * Состояние для аутентификации
 */
sealed class AuthUiState {
    object Authenticated : AuthUiState()
    object Unauthenticated : AuthUiState()
    object Loading : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

/**
 * Состояние для избранного
 */
data class FavoritesUiState(
    val favoriteIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Состояние для фильтров
 */
data class FilterUiState(
    val selectedCategory: String? = null,
    val selectedStatus: String? = null,
    val searchQuery: String = "",
    val sortBy: SortType = SortType.NEWEST,
    val distance: Double? = null,
    val showOnlyAvailable: Boolean = true
)

enum class SortType {
    NEWEST, OLDEST, DISTANCE, POPULAR
}

/**
 * Состояние для местоположения
 */
sealed class LocationUiState {
    object Available : LocationUiState()
    object Unavailable : LocationUiState()
    object PermissionDenied : LocationUiState()
    object Disabled : LocationUiState()
    data class Error(val message: String) : LocationUiState()
}

/**
 * Результат операции
 */
sealed class OperationResult<out T> {
    object Loading : OperationResult<Nothing>()
    data class Success<T>(val data: T) : OperationResult<T>()
    data class Error(val message: String) : OperationResult<Nothing>()
}