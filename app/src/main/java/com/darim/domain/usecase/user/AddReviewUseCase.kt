package com.darim.domain.usecase.user

import com.darim.domain.model.Review
import com.darim.domain.repository.UserRepository

class AddReviewUseCase(private val userRepository: UserRepository) {
    suspend fun execute(review: Review): Result<Unit> {
        if (review.rating !in 1..5) {
            return Result.failure(IllegalArgumentException("Рейтинг должен быть от 1 до 5"))
        }
        return userRepository.addReview(review)
    }
}