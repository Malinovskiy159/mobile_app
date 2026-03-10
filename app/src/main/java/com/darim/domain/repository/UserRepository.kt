package com.darim.domain.repository

import com.darim.domain.model.Review
import com.darim.domain.model.User

interface UserRepository {
    suspend fun getUser(userId: String): User?

    suspend fun updateUser(user: User): Result<Unit>

    suspend fun updateRating(userId: String, newRating: Int): Result<Unit>

    suspend fun addReview(review: Review): Result<Unit>
}