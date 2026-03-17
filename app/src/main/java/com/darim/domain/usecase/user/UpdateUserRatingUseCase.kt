// domain/usecase/user/UpdateUserRatingUseCase.kt
package com.darim.domain.usecase.user

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.darim.domain.model.User
import com.darim.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers

class UpdateUserRatingUseCase(
    private val userRepository: UserRepository
) {

    sealed class UpdateRatingResult {
        data class Success(
            val userId: String,
            val oldRating: Float,
            val newRating: Float,
            val reviewsCount: Int
        ) : UpdateRatingResult()
        data class Error(val message: String, val code: ErrorCode) : UpdateRatingResult()
        object Loading : UpdateRatingResult()

        enum class ErrorCode {
            USER_NOT_FOUND,
            INVALID_RATING,
            UPDATE_FAILED,
            NO_REVIEWS
        }
    }

    // Ручное обновление рейтинга (администратор)
    fun executeManual(userId: String, newRating: Float): LiveData<UpdateRatingResult> {
        return liveData(Dispatchers.IO) {
            emit(UpdateRatingResult.Loading)

            try {
                // Проверка валидности
                if (newRating < 0 || newRating > 5) {
                    emit(UpdateRatingResult.Error(
                        "Рейтинг должен быть от 0 до 5",
                        UpdateRatingResult.ErrorCode.INVALID_RATING
                    ))
                    return@liveData
                }

                val user = userRepository.getUser(userId)
                if (user == null) {
                    emit(UpdateRatingResult.Error(
                        "Пользователь не найден",
                        UpdateRatingResult.ErrorCode.USER_NOT_FOUND
                    ))
                    return@liveData
                }

                val oldRating = user.rating
                val result = userRepository.updateRating(userId, newRating.toInt())

                if (result.isSuccess) {
                    emit(UpdateRatingResult.Success(
                        userId = userId,
                        oldRating = oldRating,
                        newRating = newRating,
                        reviewsCount = user.reviews.size
                    ))
                } else {
                    emit(UpdateRatingResult.Error(
                        result.exceptionOrNull()?.message ?: "Ошибка обновления",
                        UpdateRatingResult.ErrorCode.UPDATE_FAILED
                    ))
                }

            } catch (e: Exception) {
                emit(UpdateRatingResult.Error(
                    e.message ?: "Неизвестная ошибка",
                    UpdateRatingResult.ErrorCode.UPDATE_FAILED
                ))
            }
        }
    }

    // Автоматическое обновление на основе всех отзывов
    fun recalculateFromReviews(userId: String): LiveData<UpdateRatingResult> {
        return liveData(Dispatchers.IO) {
            emit(UpdateRatingResult.Loading)

            try {
                val user = userRepository.getUser(userId)
                if (user == null) {
                    emit(UpdateRatingResult.Error(
                        "Пользователь не найден",
                        UpdateRatingResult.ErrorCode.USER_NOT_FOUND
                    ))
                    return@liveData
                }

                if (user.reviews.isEmpty()) {
                    emit(UpdateRatingResult.Error(
                        "У пользователя нет отзывов",
                        UpdateRatingResult.ErrorCode.NO_REVIEWS
                    ))
                    return@liveData
                }

                // Вычисляем средний рейтинг
                val totalRating = user.reviews.sumOf { it.rating.toDouble() }
                val averageRating = (totalRating / user.reviews.size).toFloat()

                val oldRating = user.rating
                val result = userRepository.updateRating(userId, averageRating.toInt())

                if (result.isSuccess) {
                    emit(UpdateRatingResult.Success(
                        userId = userId,
                        oldRating = oldRating,
                        newRating = averageRating,
                        reviewsCount = user.reviews.size
                    ))
                } else {
                    emit(UpdateRatingResult.Error(
                        result.exceptionOrNull()?.message ?: "Ошибка обновления",
                        UpdateRatingResult.ErrorCode.UPDATE_FAILED
                    ))
                }

            } catch (e: Exception) {
                emit(UpdateRatingResult.Error(
                    e.message ?: "Неизвестная ошибка",
                    UpdateRatingResult.ErrorCode.UPDATE_FAILED
                ))
            }
        }
    }

    // Обновление рейтинга после добавления нового отзыва
    fun updateAfterNewReview(userId: String, newReviewRating: Int): LiveData<UpdateRatingResult> {
        return liveData(Dispatchers.IO) {
            emit(UpdateRatingResult.Loading)

            try {
                val user = userRepository.getUser(userId)
                if (user == null) {
                    emit(UpdateRatingResult.Error(
                        "Пользователь не найден",
                        UpdateRatingResult.ErrorCode.USER_NOT_FOUND
                    ))
                    return@liveData
                }

                // Добавляем новый отзыв к существующим
                val allReviews = user.reviews.toMutableList()
                // В реальности новый отзыв уже должен быть добавлен в репозиторий

                val totalRating = allReviews.sumOf { it.rating.toDouble() } + newReviewRating
                val averageRating = (totalRating / (allReviews.size + 1)).toFloat()

                val oldRating = user.rating
                val result = userRepository.updateRating(userId, averageRating.toInt())

                if (result.isSuccess) {
                    emit(UpdateRatingResult.Success(
                        userId = userId,
                        oldRating = oldRating,
                        newRating = averageRating,
                        reviewsCount = allReviews.size + 1
                    ))
                } else {
                    emit(UpdateRatingResult.Error(
                        result.exceptionOrNull()?.message ?: "Ошибка обновления",
                        UpdateRatingResult.ErrorCode.UPDATE_FAILED
                    ))
                }

            } catch (e: Exception) {
                emit(UpdateRatingResult.Error(
                    e.message ?: "Неизвестная ошибка",
                    UpdateRatingResult.ErrorCode.UPDATE_FAILED
                ))
            }
        }
    }

    // Получение статистики рейтинга
    fun getRatingStats(userId: String): LiveData<RatingStats> {
        return liveData(Dispatchers.IO) {
            try {
                val user = userRepository.getUser(userId)
                if (user == null) {
                    emit(RatingStats.EMPTY)
                    return@liveData
                }

                val distribution = user.reviews.groupBy { it.rating }
                    .mapValues { it.value.size }

                val stats = RatingStats(
                    currentRating = user.rating,
                    totalReviews = user.reviews.size,
                    distribution5 = distribution[5] ?: 0,
                    distribution4 = distribution[4] ?: 0,
                    distribution3 = distribution[3] ?: 0,
                    distribution2 = distribution[2] ?: 0,
                    distribution1 = distribution[1] ?: 0,
                    averageFromReviews = if (user.reviews.isNotEmpty()) {
                        user.reviews.map { it.rating }.average().toFloat()
                    } else 0f
                )

                emit(stats)
            } catch (e: Exception) {
                emit(RatingStats.EMPTY)
            }
        }
    }
}

data class RatingStats(
    val currentRating: Float,
    val totalReviews: Int,
    val distribution5: Int,
    val distribution4: Int,
    val distribution3: Int,
    val distribution2: Int,
    val distribution1: Int,
    val averageFromReviews: Float
) {
    companion object {
        val EMPTY = RatingStats(0f, 0, 0, 0, 0, 0, 0, 0f)
    }
}