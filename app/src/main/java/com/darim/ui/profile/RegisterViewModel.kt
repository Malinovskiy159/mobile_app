package com.darim.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darim.domain.usecase.user.RegisterUseCase
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val registerUseCase: RegisterUseCase
) : ViewModel() {

    sealed class RegisterResult {
        data class Success(val userId: String) : RegisterResult()
        data class Error(val message: String) : RegisterResult()
        object Loading : RegisterResult()
        object Idle : RegisterResult()
    }

    private val _registerResult = MutableLiveData<RegisterResult>(RegisterResult.Idle)
    val registerResult: LiveData<RegisterResult> = _registerResult

    fun register(name: String, phone: String, password: String) {
        viewModelScope.launch {
            _registerResult.value = RegisterResult.Loading

            registerUseCase.execute(name, phone, password).observeForever { result ->
                when (result) {
                    is RegisterUseCase.RegisterResult.Success -> {
                        _registerResult.value = RegisterResult.Success(result.userId)
                    }
                    is RegisterUseCase.RegisterResult.Error -> {
                        _registerResult.value = RegisterResult.Error(result.message)
                    }
                    else -> {}
                }
            }
        }
    }

    fun resetResult() {
        _registerResult.value = RegisterResult.Idle
    }
}