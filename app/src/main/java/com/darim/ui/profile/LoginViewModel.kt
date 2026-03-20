package com.darim.ui.profile


import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darim.domain.model.User
import com.darim.domain.usecase.user.LoginUseCase
import com.darim.ui.utils.SessionManager
import kotlinx.coroutines.launch

class LoginViewModel(
    private val loginUseCase: LoginUseCase
) : ViewModel() {

    sealed class LoginResult {
        data class Success(val user: User) : LoginResult()
        data class Error(val message: String) : LoginResult()
        object Loading : LoginResult()
        object Idle : LoginResult()
    }

    private val _loginResult = MutableLiveData<LoginResult>(LoginResult.Idle)
    val loginResult: LiveData<LoginResult> = _loginResult

    fun login(phone: String, password: String) {
        viewModelScope.launch {
            _loginResult.value = LoginResult.Loading

            loginUseCase.execute(phone, password).observeForever { result ->
                when (result) {
                    is LoginUseCase.LoginResult.Success -> {
                        // Сохраняем пользователя в сессию
                        SessionManager.saveCurrentUser(result.user)
                        _loginResult.value = LoginResult.Success(result.user)
                    }
                    is LoginUseCase.LoginResult.Error -> {
                        _loginResult.value = LoginResult.Error(result.message)
                    }
                    else -> {}
                }
            }
        }
    }

    fun loginAsGuest() {
        viewModelScope.launch {
            _loginResult.value = LoginResult.Loading

            loginUseCase.loginAsGuest().observeForever { result ->
                when (result) {
                    is LoginUseCase.LoginResult.Success -> {
                        SessionManager.saveCurrentUser(result.user)
                        _loginResult.value = LoginResult.Success(result.user)
                    }
                    is LoginUseCase.LoginResult.Error -> {
                        _loginResult.value = LoginResult.Error(result.message)
                    }
                    else -> {}
                }
            }
        }
    }

    fun resetResult() {
        _loginResult.value = LoginResult.Idle
    }
}