// ui/viewmodel/TransferViewModel.kt
package com.darim.ui.transfer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darim.domain.model.Transfer
import com.darim.domain.usecase.transfer.*
import kotlinx.coroutines.launch

class TransferViewModel(
    private val scheduleTransferUseCase: ScheduleTransferUseCase,
    private val completeTransferUseCase: CompleteTransferUseCase,
    private val markUserNoShowUseCase: MarkUserNoShowUseCase,
    private val cancelTransferUseCase: CancelTransferUseCase
) : ViewModel() {

    private val _transfer = MutableLiveData<Transfer?>()
    val transfer: LiveData<Transfer?> = _transfer

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _completeResult = MutableLiveData<TransferResult?>()
    val completeResult: LiveData<TransferResult?> = _completeResult

    private val _cancelResult = MutableLiveData<TransferResult?>()
    val cancelResult: LiveData<TransferResult?> = _cancelResult

    private val _noShowResult = MutableLiveData<TransferResult?>()
    val noShowResult: LiveData<TransferResult?> = _noShowResult

    sealed class TransferResult {
        data class Success(val message: String) : TransferResult()
        data class Error(val message: String) : TransferResult()
        object Loading : TransferResult()
    }

    fun loadTransfer(transferId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            // TODO: Реализовать получение трансфера по ID через репозиторий
            _isLoading.value = false
        }
    }

    fun completeTransfer(transferId: String, userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _completeResult.value = TransferResult.Loading

            completeTransferUseCase.execute(
                CompleteTransferUseCase.CompleteRequest(
                    transferId = transferId,
                    completedBy = userId,
                    rating = null,
                    comment = null
                )
            ).observeForever { result ->
                when (result) {
                    is CompleteTransferUseCase.CompleteResult.Success -> {
                        _completeResult.value = TransferResult.Success("Встреча подтверждена")
                        loadTransfer(transferId)
                    }
                    is CompleteTransferUseCase.CompleteResult.Error -> {
                        _completeResult.value = TransferResult.Error(result.message)
                    }
                    else -> {}
                }
                _isLoading.value = false
            }
        }
    }

    fun cancelTransfer(transferId: String, userId: String, reason: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _cancelResult.value = TransferResult.Loading

            cancelTransferUseCase.cancelByUser(transferId, userId, reason).observeForever { result ->
                when (result) {
                    is CancelTransferUseCase.CancelResult.Success -> {
                        _cancelResult.value = TransferResult.Success("Встреча отменена")
                        loadTransfer(transferId)
                    }
                    is CancelTransferUseCase.CancelResult.Error -> {
                        _cancelResult.value = TransferResult.Error(result.message)
                    }
                    else -> {}
                }
                _isLoading.value = false
            }
        }
    }

    fun markNoShow(transferId: String, userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _noShowResult.value = TransferResult.Loading

            markUserNoShowUseCase.execute(
                MarkUserNoShowUseCase.NoShowRequest(
                    transferId = transferId,
                    reporterId = userId
                )
            ).observeForever { result ->
                when (result) {
                    is MarkUserNoShowUseCase.NoShowResult.Success -> {
                        _noShowResult.value = TransferResult.Success("Неявка отмечена")
                        loadTransfer(transferId)
                    }
                    is MarkUserNoShowUseCase.NoShowResult.Error -> {
                        _noShowResult.value = TransferResult.Error(result.message)
                    }
                    else -> {}
                }
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearResults() {
        _completeResult.value = null
        _cancelResult.value = null
        _noShowResult.value = null
    }
}