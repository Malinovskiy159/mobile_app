// ui/detail/DetailViewModel.kt
package com.darim.ui.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darim.domain.model.Item
import com.darim.domain.model.User
import com.darim.domain.usecase.item.BookItemUseCase
import com.darim.domain.usecase.item.GetItemDetailsUseCase
import kotlinx.coroutines.launch

class DetailViewModel(
    private val getItemDetailsUseCase: GetItemDetailsUseCase,
    private val bookItemUseCase: BookItemUseCase
) : ViewModel() {

    private val _item = MutableLiveData<Item?>()
    val item: LiveData<Item?> = _item

    private val _owner = MutableLiveData<User?>()
    val owner: LiveData<User?> = _owner

    private val _similarItems = MutableLiveData<List<Item>>(emptyList())
    val similarItems: LiveData<List<Item>> = _similarItems

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _bookingResult = MutableLiveData<BookingResult?>()
    val bookingResult: LiveData<BookingResult?> = _bookingResult

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _canBook = MutableLiveData(false)
    val canBook: LiveData<Boolean> = _canBook

    private val _isOwner = MutableLiveData(false)
    val isOwner: LiveData<Boolean> = _isOwner

    sealed class BookingResult {
        data class Success(val message: String) : BookingResult()
        data class Error(val message: String) : BookingResult()
    }

    fun loadItemDetails(itemId: String, currentUserId: String?) {
        viewModelScope.launch {
            _isLoading.value = true

            // Используем observe вместо collect
            getItemDetailsUseCase.execute(itemId, currentUserId).observeForever { result ->
                when (result) {
                    is GetItemDetailsUseCase.DetailResult.Success -> {
                        _item.value = result.item
                        _owner.value = result.owner
                        _similarItems.value = result.similarItems
                        _canBook.value = result.canBook
                        _isOwner.value = result.isOwner
                        _error.value = null
                        _isLoading.value = false
                    }
                    is GetItemDetailsUseCase.DetailResult.Error -> {
                        _error.value = result.message
                        _isLoading.value = false
                    }
                    GetItemDetailsUseCase.DetailResult.NotFound -> {
                        _error.value = "Вещь не найдена"
                        _isLoading.value = false
                    }
                    GetItemDetailsUseCase.DetailResult.Loading -> {
                        // Уже обрабатывается через isLoading
                    }
                }
            }
        }
    }

    fun bookItem(itemId: String, userId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            bookItemUseCase.execute(itemId, userId).observeForever { result ->
                when (result) {
                    is BookItemUseCase.BookingResult.Success -> {
                        _bookingResult.value = BookingResult.Success(result.message)
                        // Обновляем данные после бронирования
                        loadItemDetails(itemId, userId)
                        _isLoading.value = false
                    }
                    is BookItemUseCase.BookingResult.Error -> {
                        _bookingResult.value = BookingResult.Error(result.message)
                        _isLoading.value = false
                    }
                    else -> {}
                }
            }
        }
    }

    fun clearBookingResult() {
        _bookingResult.value = null
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // Здесь можно удалить observers если нужно
    }
}