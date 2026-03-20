// ui/map/MapViewModel.kt
package com.darim.ui.map

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darim.domain.model.Item
import com.darim.domain.model.ItemStatus
import com.darim.domain.model.Location
import com.darim.domain.usecase.item.GetItemsUseCase
import com.darim.domain.usecase.location.GetUserLocationUseCase
import com.darim.ui.utils.SessionManager
import kotlinx.coroutines.launch

class MapViewModel(
    private val getItemsUseCase: GetItemsUseCase,
    private val getUserLocationUseCase: GetUserLocationUseCase
) : ViewModel() {

    private val TAG = "MapViewModel"

    // Все доступные вещи для карты
    private val _allItems = MutableLiveData<List<Item>>(emptyList())
    val allItems: LiveData<List<Item>> = _allItems

    // Отфильтрованные вещи для отображения
    private val _filteredItems = MutableLiveData<List<Item>>(emptyList())
    val filteredItems: LiveData<List<Item>> = _filteredItems

    // Местоположение пользователя
    private val _userLocation = MutableLiveData<Location?>()
    val userLocation: LiveData<Location?> = _userLocation

    // Выбранная вещь
    private val _selectedItemId = MutableLiveData<String?>()
    val selectedItemId: LiveData<String?> = _selectedItemId

    // Состояние загрузки
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Ошибки
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Состояние геолокации
    private val _locationState = MutableLiveData<LocationState>(LocationState.Idle)
    val locationState: LiveData<LocationState> = _locationState

    // Текущие фильтры
    private var currentSearchQuery = ""
    private var currentCategories = emptySet<String>()
    private var currentRadius = 5.0
    private var currentIsWholeCity = false
    private var currentSortType = "date"

    sealed class LocationState {
        object Idle : LocationState()
        object Available : LocationState()
        object Disabled : LocationState()
        object PermissionDenied : LocationState()
        object Loading : LocationState()
        data class Error(val message: String) : LocationState()
    }

    init {
        loadAllItems()
        loadUserLocation()
    }

    fun loadAllItems() {
        viewModelScope.launch {
            _isLoading.value = true
            Log.d(TAG, "Loading all items...")
            try {
                getItemsUseCase.getAvailableItems().observeForever { items ->
                    Log.d(TAG, "Loaded ${items.size} items")
                    _allItems.value = items
                    applyFilters()
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading items: ${e.message}")
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun loadUserLocation() {
        viewModelScope.launch {
            _locationState.value = LocationState.Loading
            getUserLocationUseCase.execute().observeForever { result ->
                when (result) {
                    is GetUserLocationUseCase.LocationResult.Success -> {
                        _userLocation.value = result.location
                        _locationState.value = LocationState.Available
                        Log.d(TAG, "User location: ${result.location.lat}, ${result.location.lng}")
                        applyFilters()
                    }
                    is GetUserLocationUseCase.LocationResult.LocationDisabled -> {
                        _locationState.value = LocationState.Disabled
                        Log.d(TAG, "Location disabled")
                        applyFilters()
                    }
                    is GetUserLocationUseCase.LocationResult.PermissionDenied -> {
                        _locationState.value = LocationState.PermissionDenied
                        Log.d(TAG, "Location permission denied")
                        applyFilters()
                    }
                    is GetUserLocationUseCase.LocationResult.Error -> {
                        _locationState.value = LocationState.Error(result.message)
                        Log.e(TAG, "Location error: ${result.message}")
                        applyFilters()
                    }
                    else -> {}
                }
            }
        }
    }

    fun updateFilters(
        searchQuery: String = "",
        categories: Set<String> = emptySet(),
        radius: Double = 5.0,
        isWholeCity: Boolean = false,
        sortType: String = "date"
    ) {
        currentSearchQuery = searchQuery
        currentCategories = categories
        currentRadius = radius
        currentIsWholeCity = isWholeCity
        currentSortType = sortType
        applyFilters()
    }

    fun refresh() {
        loadAllItems()
        loadUserLocation()
    }

    fun onMarkerClick(itemId: String) {
        _selectedItemId.value = itemId
    }

    fun clearSelectedItem() {
        _selectedItemId.value = null
    }

    fun clearError() {
        _error.value = null
    }

    private fun applyFilters() {
        val all = _allItems.value ?: run {
            Log.d(TAG, "applyFilters: allItems is null")
            _filteredItems.value = emptyList()
            return
        }

        val userLoc = _userLocation.value
        val currentUserId = SessionManager.getCurrentUserId()
        val bookedItemIds = SessionManager.getBookedItems()

        Log.d(TAG, "applyFilters: total items: ${all.size}")
        Log.d(TAG, "applyFilters: user location: ${userLoc != null}")
        Log.d(TAG, "applyFilters: currentUserId: $currentUserId")
        Log.d(TAG, "applyFilters: booked items: ${bookedItemIds.size}")

        var result = all

        // 1. Поиск по тексту
        if (currentSearchQuery.isNotEmpty()) {
            result = result.filter { item ->
                item.title.contains(currentSearchQuery, ignoreCase = true) ||
                        item.description.contains(currentSearchQuery, ignoreCase = true)
            }
            Log.d(TAG, "After search filter: ${result.size}")
        }

        // 2. Фильтр по категориям
        if (currentCategories.isNotEmpty()) {
            result = result.filter { item ->
                item.category in currentCategories
            }
            Log.d(TAG, "After category filter: ${result.size}")
        }

        // 3. Только доступные вещи
        result = result.filter { it.status == ItemStatus.AVAILABLE }
        Log.d(TAG, "After status filter: ${result.size}")

        // 4. Исключаем свои вещи
        if (currentUserId != null) {
            result = result.filter { it.ownerId != currentUserId }
            Log.d(TAG, "After exclude owner filter: ${result.size}")
        }

        // 5. Исключаем уже забронированные вещи
        result = result.filter { it.id !in bookedItemIds }
        Log.d(TAG, "After exclude booked filter: ${result.size}")

        // 6. Фильтр по расстоянию
        if (userLoc != null && !currentIsWholeCity) {
            result = result.filter { item ->
                val distance = calculateDistance(
                    userLoc.lat, userLoc.lng,
                    item.location.lat, item.location.lng
                )
                distance <= currentRadius
            }
            Log.d(TAG, "After distance filter: ${result.size}")
        }

        _filteredItems.value = result
        Log.d(TAG, "Final filtered items: ${result.size}")
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
}