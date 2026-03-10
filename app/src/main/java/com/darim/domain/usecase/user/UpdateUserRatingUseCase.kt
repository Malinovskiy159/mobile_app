package com.darim.domain.usecase.user

import com.darim.domain.repository.UserRepository

class UpdateUserRatingUseCase(private val userRepository: UserRepository) {
    suspend fun execute(userId: String, rating: Int): Result<Unit> {
        return userRepository.updateRating(userId, rating)
    }
}