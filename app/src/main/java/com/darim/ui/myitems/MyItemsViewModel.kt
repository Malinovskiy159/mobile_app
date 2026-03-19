package com.darim.ui.myitems

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darim.domain.model.Item
import com.darim.domain.usecase.item.GetMyItemsUseCase
import com.darim.domain.usecase.item.GetMyBookingsUseCase
import com.darim.domain.usecase.item.GetMyBookingsUseCase.BookingsFilter
import com.darim.ui.utils.SessionManager
import kotlinx.coroutines.launch
import com.darim.domain.usecase.item.GetMyItemsUseCase.ItemsFilter

class MyItemsViewModel(
    private val getMyItemsUseCase: GetMyItemsUseCase,
    private val getMyBookingsUseCase: GetMyBookingsUseCase
) : ViewModel() {

    private val _myItems = MutableLiveData<List<Item>>(emptyList())
    val myItems: LiveData<List<Item>> = _myItems

    private val _myBookings = MutableLiveData<List<GetMyBookingsUseCase.BookingItem>>(emptyList())
    val myBookings: LiveData<List<GetMyBookingsUseCase.BookingItem>> = _myBookings

    private val _myItemsStats = MutableLiveData<GetMyItemsUseCase.UserItemsStats?>()
    val myItemsStats: LiveData<GetMyItemsUseCase.UserItemsStats?> = _myItemsStats

    // ИСПРАВЛЕНО: используем правильный тип
    private val _bookingStats = MutableLiveData<GetMyBookingsUseCase.BookingStats?>()
    val bookingStats: LiveData<GetMyBookingsUseCase.BookingStats?> = _bookingStats

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _myItemsFilter = MutableLiveData(GetMyItemsUseCase.ItemsFilter.All)
    val myItemsFilter: LiveData<GetMyItemsUseCase.ItemsFilter> = _myItemsFilter as LiveData<GetMyItemsUseCase.ItemsFilter>

    private val _bookingsFilter = MutableLiveData(GetMyBookingsUseCase.BookingsFilter.All)
    val bookingsFilter: LiveData<GetMyBookingsUseCase.BookingsFilter> = _bookingsFilter as LiveData<GetMyBookingsUseCase.BookingsFilter>

    init {
        loadData()
    }

    fun loadData() {
        val userId = SessionManager.getCurrentUserId()
        if (userId != null) {
            loadMyItems(userId)
            loadMyBookings(userId)
            loadStats(userId)
        } else {
            _error.value = "Пользователь не авторизован"
        }
    }

    fun refresh() {
        loadData()
    }

    private fun loadMyItems(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            getMyItemsUseCase.execute(userId, _myItemsFilter.value!!).observeForever { result ->
                when (result) {
                    is GetMyItemsUseCase.MyItemsResult.Success -> {
                        _myItems.value = result.items
                        _error.value = null
                    }
                    is GetMyItemsUseCase.MyItemsResult.Error -> {
                        _error.value = result.message
                        _myItems.value = emptyList()
                    }
                    GetMyItemsUseCase.MyItemsResult.Empty -> {
                        _myItems.value = emptyList()
                    }
                    else -> {}
                }
                _isLoading.value = false
            }
        }
    }

    private fun loadMyBookings(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            getMyBookingsUseCase.execute(userId, _bookingsFilter.value!!).observeForever { result ->
                when (result) {
                    is GetMyBookingsUseCase.BookingsResult.Success -> {
                        _myBookings.value = result.bookings
                        _error.value = null
                    }
                    is GetMyBookingsUseCase.BookingsResult.Error -> {
                        _error.value = result.message
                        _myBookings.value = emptyList()
                    }
                    GetMyBookingsUseCase.BookingsResult.Empty -> {
                        _myBookings.value = emptyList()
                    }
                    else -> {}
                }
                _isLoading.value = false
            }
        }
    }

    private fun loadStats(userId: String) {
        viewModelScope.launch {
            getMyItemsUseCase.getStats(userId).observeForever { stats ->
                _myItemsStats.value = stats
            }

            // ИСПРАВЛЕНО: используем правильный метод
            getMyBookingsUseCase.getBookingStats(userId).observeForever { stats ->
                _bookingStats.value = stats
            }
        }
    }

    fun setMyItemsFilter(filter: ItemsFilter) {
        _myItemsFilter.value = ItemsFilter.All
        val userId = SessionManager.getCurrentUserId()
        if (userId != null) {
            loadMyItems(userId)
        }
    }

    fun setBookingsFilter(filter: BookingsFilter) {
        _bookingsFilter.value = filter as BookingsFilter.All?
        val userId = SessionManager.getCurrentUserId()
        if (userId != null) {
            loadMyBookings(userId)
        }
    }

    fun cancelBooking(itemId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            // TODO: Implement cancel booking use case
            val userId = SessionManager.getCurrentUserId()
            if (userId != null) {
                loadMyBookings(userId)
                loadStats(userId)
            }
            _isLoading.value = false
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            // TODO: Implement delete item use case
            val userId = SessionManager.getCurrentUserId()
            if (userId != null) {
                loadMyItems(userId)
                loadStats(userId)
            }
            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun getActiveBookingsCount(): Int {
        return _bookingStats.value?.activeBookings ?: 0
    }

    fun getCompletedBookingsCount(): Int {
        return _bookingStats.value?.completed ?: 0
    }

    fun getCancelledBookingsCount(): Int {
        return _bookingStats.value?.cancelled ?: 0
    }
}