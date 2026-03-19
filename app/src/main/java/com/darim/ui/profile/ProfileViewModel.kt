package com.darim.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darim.domain.model.Review
import com.darim.domain.model.User
import com.darim.domain.usecase.user.GetUserProfileUseCase
import com.darim.ui.utils.SessionManager
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val getUserProfileUseCase: GetUserProfileUseCase
) : ViewModel() {

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> = _user

    private val _userStats = MutableLiveData<GetUserProfileUseCase.UserStats?>()
    val userStats: LiveData<GetUserProfileUseCase.UserStats?> = _userStats

    private val _reviews = MutableLiveData<List<Review>>(emptyList())
    val reviews: LiveData<List<Review>> = _reviews

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadUserProfile(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            getUserProfileUseCase.execute(userId).observeForever { result ->
                when (result) {
                    is GetUserProfileUseCase.ProfileResult.Success -> {
                        _user.value = result.user
                        _userStats.value = result.stats
                        _reviews.value = result.recentReviews
                        _error.value = null

                        SessionManager.saveCurrentUser(result.user)
                    }
                    is GetUserProfileUseCase.ProfileResult.Error -> {
                        _error.value = result.message
                    }
                    GetUserProfileUseCase.ProfileResult.Loading -> {
                        // Уже обрабатывается
                    }
                    GetUserProfileUseCase.ProfileResult.NotFound -> {
                        _error.value = "Пользователь не найден"
                    }
                }
                _isLoading.value = false
            }
        }
    }

    fun updateProfile(updatedUser: User) {
        viewModelScope.launch {
            _isLoading.value = true

            // TODO: реализовать обновление профиля через UseCase
            // Пока просто обновляем локально и в сессии
            _user.value = updatedUser
            SessionManager.saveCurrentUser(updatedUser)
            _error.value = null
            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}