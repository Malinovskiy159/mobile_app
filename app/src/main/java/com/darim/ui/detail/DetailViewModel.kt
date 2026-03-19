// ui/detail/DetailViewModel.kt
package com.darim.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darim.domain.model.Item
import com.darim.domain.usecase.item.BookItemUseCase
import com.darim.domain.usecase.item.GetItemDetailsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(
    private val getItemDetailsUseCase: GetItemDetailsUseCase,
    private val bookItemUseCase: BookItemUseCase
) : ViewModel() {

    private val _item = MutableStateFlow<Item?>(null)
    val item: StateFlow<Item?> = _item.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _bookingResult = MutableStateFlow<BookingResult?>(null)
    val bookingResult: StateFlow<BookingResult?> = _bookingResult.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _canBook = MutableStateFlow(false)
    val canBook: StateFlow<Boolean> = _canBook.asStateFlow()

    sealed class BookingResult {
        data class Success(val message: String) : BookingResult()
        data class Error(val message: String) : BookingResult()
    }

    fun loadItem(itemId: String, currentUserId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                getItemDetailsUseCase.execute(itemId).observeForever { result ->
                    when (result) {
                        is GetItemDetailsUseCase.DetailResult.Success -> {
                            _item.value = result.item
                            // Проверяем, может ли пользователь забронировать
                            _canBook.value = result.item.ownerId != currentUserId &&
                                    result.item.status == com.darim.domain.model.ItemStatus.AVAILABLE
                            _error.value = null
                        }
                        is GetItemDetailsUseCase.DetailResult.Error -> {
                            _error.value = result.message
                        }
                        GetItemDetailsUseCase.DetailResult.Loading -> {}
                        GetItemDetailsUseCase.DetailResult.NotFound -> {
                            _error.value = "Вещь не найдена"
                        }
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun bookItem(itemId: String, userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                bookItemUseCase.execute(itemId, userId).observeForever { result ->
                    when (result) {
                        is BookItemUseCase.BookingResult.Success -> {
                            _bookingResult.value = BookingResult.Success(result.message)
                            // Обновляем данные вещи после бронирования
                            loadItem(itemId, userId)
                        }
                        is BookItemUseCase.BookingResult.Error -> {
                            _bookingResult.value = BookingResult.Error(result.message)
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                _bookingResult.value = BookingResult.Error(e.message ?: "Ошибка бронирования")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun checkIfCanBook(itemId: String, userId: String) {
        viewModelScope.launch {
            bookItemUseCase.checkIfCanBook(itemId, userId).observeForever { canBook ->
                _canBook.value = canBook
            }
        }
    }

    fun clearBookingResult() {
        _bookingResult.value = null
    }

    fun clearError() {
        _error.value = null
    }
}