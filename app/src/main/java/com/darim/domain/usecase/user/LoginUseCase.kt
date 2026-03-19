package com.darim.domain.usecase.user

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.darim.domain.model.User
import com.darim.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import java.util.UUID

class LoginUseCase(
    private val userRepository: UserRepository
) {

    sealed class LoginResult {
        data class Success(val user: User) : LoginResult()
        data class Error(val message: String) : LoginResult()
        object Loading : LoginResult()
    }

    fun execute(phone: String, password: String): LiveData<LoginResult> {
        return liveData(Dispatchers.IO) {
            emit(LoginResult.Loading)

            try {
                val users = userRepository.getAllUsers()
                val user = users.find { it.phone == phone }

                if (user != null) {
                    emit(LoginResult.Success(user))
                } else {
                    emit(LoginResult.Error("Пользователь не найден"))
                }
            } catch (e: Exception) {
                emit(LoginResult.Error("Ошибка входа: ${e.message}"))
            }
        }
    }

    fun loginAsGuest(): LiveData<LoginResult> {
        return liveData(Dispatchers.IO) {
            emit(LoginResult.Loading)

            try {
                // Создаем гостевого пользователя
                val guestUser = User(
                    id = "guest_${UUID.randomUUID().toString()}",
                    name = "Гость",
                    phone = "+7 (999) 000-00-00",
                    rating = 0f,
                    reviews = emptyList(),
                    itemsGiven = 0,
                    itemsTaken = 0
                )

                // Сохраняем гостя в репозиторий
                userRepository.updateUser(guestUser)

                emit(LoginResult.Success(guestUser))
            } catch (e: Exception) {
                emit(LoginResult.Error("Ошибка создания гостя: ${e.message}"))
            }
        }
    }
}