package com.darim.domain.usecase.user

import com.darim.domain.model.User
import com.darim.domain.repository.UserRepository

class GetUserProfileUseCase(private val userRepository: UserRepository) {
    suspend fun execute(userId: String): Result<User> {
        val user = userRepository.getUser(userId)
        return if (user != null) {
            Result.success(user)
        } else {
            Result.failure(Exception("Пользователь не найден"))
        }
    }
}