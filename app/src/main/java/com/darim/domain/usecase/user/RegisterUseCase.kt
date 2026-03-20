package com.darim.domain.usecase.user

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.darim.domain.model.User
import com.darim.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import java.util.UUID

class RegisterUseCase(
    private val userRepository: UserRepository
) {

    sealed class RegisterResult {
        data class Success(val userId: String) : RegisterResult()
        data class Error(val message: String) : RegisterResult()
        object Loading : RegisterResult()
    }

    fun execute(name: String, phone: String, password: String): LiveData<RegisterResult> {
        return liveData(Dispatchers.IO) {
            emit(RegisterResult.Loading)

            try {
                // Проверяем, существует ли уже пользователь с таким телефоном
                val existingUsers = userRepository.getAllUsers()
                val existingUser = existingUsers.find { it.phone == phone }

                if (existingUser != null) {
                    emit(RegisterResult.Error("Пользователь с таким телефоном уже существует"))
                    return@liveData
                }

                // Создаем нового пользователя
                val newUser = User(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    phone = phone,
                    rating = 0f,
                    reviews = emptyList(),
                    itemsGiven = 0,
                    itemsTaken = 0
                )

                // Сохраняем в репозиторий
                userRepository.updateUser(newUser)

                emit(RegisterResult.Success(newUser.id))
            } catch (e: Exception) {
                emit(RegisterResult.Error("Ошибка регистрации: ${e.message}"))
            }
        }
    }
}