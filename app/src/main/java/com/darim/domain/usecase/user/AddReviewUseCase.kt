// domain/usecase/user/AddReviewUseCase.kt
package com.darim.domain.usecase.user

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.darim.domain.model.Review
import com.darim.domain.model.Transfer
import com.darim.domain.model.TransferStatus
import com.darim.domain.repository.TransferRepository
import com.darim.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers

class AddReviewUseCase(
    private val userRepository: UserRepository,
    private val transferRepository: TransferRepository
) {

    data class ReviewRequest(
        val fromUserId: String,
        val toUserId: String,
        val transferId: String,      // ID встречи, по которой оставляем отзыв
        val rating: Int,              // Оценка от 1 до 5
        val comment: String = ""
    )

    sealed class AddReviewResult {
        data class Success(
            val review: Review,
            val newRating: Float,
            val reviewerName: String,
            val reviewedUserName: String
        ) : AddReviewResult()
        data class Error(val message: String, val code: ErrorCode) : AddReviewResult()
        object Loading : AddReviewResult()

        enum class ErrorCode {
            INVALID_RATING,
            TRANSFER_NOT_FOUND,
            TRANSFER_NOT_COMPLETED,
            NOT_PARTICIPANT,
            ALREADY_REVIEWED,
            USER_NOT_FOUND,
            SELF_REVIEW,
            INVALID_TRANSFER_STATUS
        }
    }

    fun execute(request: ReviewRequest): LiveData<AddReviewResult> {
        return liveData(Dispatchers.IO) {
            emit(AddReviewResult.Loading)

            try {
                // 1. Проверка валидности оценки
                if (request.rating < 1 || request.rating > 5) {
                    emit(AddReviewResult.Error(
                        "Оценка должна быть от 1 до 5",
                        AddReviewResult.ErrorCode.INVALID_RATING
                    ))
                    return@liveData
                }

                // 2. Проверка, что не отзыв самому себе
                if (request.fromUserId == request.toUserId) {
                    emit(AddReviewResult.Error(
                        "Нельзя оставить отзыв самому себе",
                        AddReviewResult.ErrorCode.SELF_REVIEW
                    ))
                    return@liveData
                }

                // 3. Проверка существования пользователей
                val fromUser = userRepository.getUser(request.fromUserId)
                val toUser = userRepository.getUser(request.toUserId)

                if (fromUser == null || toUser == null) {
                    emit(AddReviewResult.Error(
                        "Пользователь не найден",
                        AddReviewResult.ErrorCode.USER_NOT_FOUND
                    ))
                    return@liveData
                }

                // 4. Проверка встречи
                val transfer = transferRepository.getTransfer(request.transferId)
                if (transfer == null) {
                    emit(AddReviewResult.Error(
                        "Встреча не найдена",
                        AddReviewResult.ErrorCode.TRANSFER_NOT_FOUND
                    ))
                    return@liveData
                }

                // 5. Проверка, что пользователи участвовали во встрече
                if (transfer.giverId != request.fromUserId && transfer.takerId != request.fromUserId) {
                    emit(AddReviewResult.Error(
                        "Вы не участвовали в этой встрече",
                        AddReviewResult.ErrorCode.NOT_PARTICIPANT
                    ))
                    return@liveData
                }

                if (transfer.giverId != request.toUserId && transfer.takerId != request.toUserId) {
                    emit(AddReviewResult.Error(
                        "Пользователь не участвовал в этой встрече",
                        AddReviewResult.ErrorCode.NOT_PARTICIPANT
                    ))
                    return@liveData
                }

                // 6. Проверка статуса встречи
                if (transfer.status != TransferStatus.COMPLETED) {
                    emit(AddReviewResult.Error(
                        "Можно оставить отзыв только после завершенной встречи",
                        AddReviewResult.ErrorCode.INVALID_TRANSFER_STATUS
                    ))
                    return@liveData
                }

                // 7. Проверка, не оставляли ли уже отзыв
                val existingReviews = toUser.reviews.filter { it.fromUserId == request.fromUserId }
                if (existingReviews.any { it.transferId == request.transferId }) {
                    emit(AddReviewResult.Error(
                        "Вы уже оставили отзыв об этой встрече",
                        AddReviewResult.ErrorCode.ALREADY_REVIEWED
                    ))
                    return@liveData
                }

                // 8. Создание отзыва
                val review = Review(
                    id = System.currentTimeMillis().toString(), // или UUID
                    fromUserId = request.fromUserId,
                    toUserId = request.toUserId,
                    transferId = request.transferId,
                    rating = request.rating,
                    comment = request.comment,
                    date = System.currentTimeMillis()
                )

                // 9. Сохранение отзыва
                val result = userRepository.addReview(review)

                if (result.isSuccess) {
                    // 10. Получаем обновленного пользователя с новым рейтингом
                    val updatedUser = userRepository.getUser(request.toUserId)

                    emit(AddReviewResult.Success(
                        review = review,
                        newRating = updatedUser?.rating ?: toUser.rating,
                        reviewerName = fromUser.name,
                        reviewedUserName = toUser.name
                    ))
                } else {
                    emit(AddReviewResult.Error(
                        result.exceptionOrNull()?.message ?: "Ошибка сохранения отзыва",
                        AddReviewResult.ErrorCode.INVALID_RATING
                    ))
                }

            } catch (e: Exception) {
                emit(AddReviewResult.Error(
                    e.message ?: "Неизвестная ошибка",
                    AddReviewResult.ErrorCode.INVALID_RATING
                ))
            }
        }
    }

    // Получение списка отзывов о пользователе
    fun getUserReviews(userId: String): LiveData<List<Review>> {
        return liveData(Dispatchers.IO) {
            try {
                val user = userRepository.getUser(userId)
                emit(user?.reviews ?: emptyList())
            } catch (e: Exception) {
                emit(emptyList())
            }
        }
    }

    // Проверка, может ли пользователь оставить отзыв
    fun canLeaveReview(fromUserId: String, toUserId: String, transferId: String): LiveData<Boolean> {
        return liveData(Dispatchers.IO) {
            try {
                val transfer = transferRepository.getTransfer(transferId)
                val user = userRepository.getUser(toUserId)

                val canLeave = when {
                    transfer == null -> false
                    transfer.status != TransferStatus.COMPLETED -> false
                    transfer.giverId != fromUserId && transfer.takerId != fromUserId -> false
                    user == null -> false
                    user.reviews.any { it.fromUserId == fromUserId && it.transferId == transferId } -> false
                    else -> true
                }

                emit(canLeave)
            } catch (e: Exception) {
                emit(false)
            }
        }
    }
}