// ui/list/ListViewModel.kt
package com.darim.ui.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darim.domain.model.Filters
import com.darim.domain.model.Item
import com.darim.domain.model.ItemStatus
import com.darim.domain.model.Location
import com.darim.domain.usecase.item.GetItemsUseCase
import com.darim.domain.usecase.location.GetUserLocationUseCase
import com.darim.ui.utils.SessionManager
import kotlinx.coroutines.launch

class ListViewModel(
    private val getItemsUseCase: GetItemsUseCase,
    private val getUserLocationUseCase: GetUserLocationUseCase
) : ViewModel() {

    // Все доступные вещи из репозитория
    private val _allItems = MutableLiveData<List<Item>>(emptyList())
    val allItems: LiveData<List<Item>> = _allItems

    // Отфильтрованные вещи для отображения
    private val _filteredItems = MutableLiveData<List<Item>>(emptyList())
    val filteredItems: LiveData<List<Item>> = _filteredItems

    // Текущие фильтры
    private val _filters = MutableLiveData(Filters())
    val filters: LiveData<Filters> = _filters

    // Поисковый запрос
    private val _searchQuery = MutableLiveData("")
    val searchQuery: LiveData<String> = _searchQuery

    // Выбранные категории
    private val _selectedCategories = MutableLiveData<Set<String>>(emptySet())
    val selectedCategories: LiveData<Set<String>> = _selectedCategories

    // Радиус поиска
    private val _radius = MutableLiveData(5.0)
    val radius: LiveData<Double> = _radius

    // Весь город
    private val _isWholeCity = MutableLiveData(false)
    val isWholeCity: LiveData<Boolean> = _isWholeCity

    // Тип сортировки
    private val _sortType = MutableLiveData("date")
    val sortType: LiveData<String> = _sortType

    // Местоположение пользователя
    private val _userLocation = MutableLiveData<Location?>()
    val userLocation: LiveData<Location?> = _userLocation

    // Состояние загрузки
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Ошибки
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init {
        loadAllItems()
        loadUserLocation()
    }

    fun loadAllItems() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                getItemsUseCase.getAvailableItems().observeForever { items ->
                    _allItems.value = items
                    applyFilters()
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun loadUserLocation() {
        viewModelScope.launch {
            getUserLocationUseCase.execute().observeForever { result ->
                when (result) {
                    is GetUserLocationUseCase.LocationResult.Success -> {
                        _userLocation.value = result.location
                        applyFilters()
                    }
                    is GetUserLocationUseCase.LocationResult.Error -> {
                        _error.value = result.message
                    }
                    else -> {}
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    fun updateCategories(categories: Set<String>) {
        _selectedCategories.value = categories
        applyFilters()
    }

    fun updateRadius(radius: Double) {
        _radius.value = radius
        applyFilters()
    }

    fun updateWholeCity(isChecked: Boolean) {
        _isWholeCity.value = isChecked
        applyFilters()
    }

    fun updateSortType(sortType: String) {
        _sortType.value = sortType
        applyFilters()
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _selectedCategories.value = emptySet()
        _radius.value = 5.0
        _isWholeCity.value = false
        _sortType.value = "date"
        applyFilters()
    }

    private fun applyFilters() {
        val all = _allItems.value ?: return
        val userLoc = _userLocation.value
        val currentUserId = SessionManager.getCurrentUserId()
        val bookedItemIds = SessionManager.getBookedItems()

        var result = all

        // 1. Поиск по тексту
        val query = _searchQuery.value ?: ""
        if (query.isNotEmpty()) {
            result = result.filter { item ->
                item.title.contains(query, ignoreCase = true) ||
                        item.description.contains(query, ignoreCase = true)
            }
        }

        // 2. Фильтр по категориям
        val categories = _selectedCategories.value ?: emptySet()
        if (categories.isNotEmpty()) {
            result = result.filter { item ->
                item.category in categories
            }
        }

        // 3. Только доступные вещи
        result = result.filter { it.status == ItemStatus.AVAILABLE }

        // 4. Исключаем свои вещи
        if (currentUserId != null) {
            result = result.filter { it.ownerId != currentUserId }
        }

        // 5. Исключаем уже забронированные вещи
        result = result.filter { it.id !in bookedItemIds }

        // 6. Фильтр по расстоянию
        if (userLoc != null && _isWholeCity.value == false) {
            val radius = _radius.value ?: 5.0
            result = result.filter { item ->
                val distance = calculateDistance(
                    userLoc.lat, userLoc.lng,
                    item.location.lat, item.location.lng
                )
                distance <= radius
            }
        }

        // 7. Сортировка
        result = when (_sortType.value) {
            "distance" -> {
                if (userLoc != null) {
                    result.sortedBy { item ->
                        calculateDistance(
                            userLoc.lat, userLoc.lng,
                            item.location.lat, item.location.lng
                        )
                    }
                } else {
                    result.sortedByDescending { it.createdAt }
                }
            }
            else -> {
                result.sortedByDescending { it.createdAt }
            }
        }

        _filteredItems.value = result
    }

    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371
        val latDistance = Math.toRadians(lat1 - lat2)
        val lonDistance = Math.toRadians(lng1 - lng2)
        val a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    fun refresh() {
        loadAllItems()
        loadUserLocation()
    }

    fun clearError() {
        _error.value = null
    }
}