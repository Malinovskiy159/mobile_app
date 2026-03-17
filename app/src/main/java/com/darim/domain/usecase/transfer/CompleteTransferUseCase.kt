// domain/usecase/transfer/CompleteTransferUseCase.kt
package com.darim.domain.usecase.transfer

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.darim.domain.model.*
import com.darim.domain.repository.ItemRepository
import com.darim.domain.repository.TransferRepository
import com.darim.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import java.util.UUID

class CompleteTransferUseCase(
    private val transferRepository: TransferRepository,
    private val itemRepository: ItemRepository,
    private val userRepository: UserRepository
) {

    data class CompleteRequest(
        val transferId: String,
        val completedBy: String, // ID пользователя, который завершает
        val rating: Int? = null,  // Оценка (1-5)
        val comment: String? = null // Комментарий к оценке
    )

    sealed class CompleteResult {
        data class Success(
            val transfer: Transfer,
            val updatedRatings: Pair<Float, Float>? // (рейтинг отдающего, рейтинг забирающего)
        ) : CompleteResult()
        data class Error(val message: String, val code: ErrorCode) : CompleteResult()
        object Loading : CompleteResult()

        enum class ErrorCode {
            TRANSFER_NOT_FOUND,
            TRANSFER_ALREADY_COMPLETED,
            TRANSFER_CANCELLED,
            INVALID_RATING,
            NOT_AUTHORIZED,
            TOO_EARLY,
            ITEM_NOT_FOUND,
            UPDATE_FAILED
        }
    }

    fun execute(request: CompleteRequest): LiveData<CompleteResult> {
        return liveData(Dispatchers.IO) {
            emit(CompleteResult.Loading)

            try {
                // Проверка валидности оценки
                if (request.rating != null && (request.rating < 1 || request.rating > 5)) {
                    emit(CompleteResult.Error(
                        "Оценка должна быть от 1 до 5",
                        CompleteResult.ErrorCode.INVALID_RATING
                    ))
                    return@liveData
                }

                // Получаем информацию о встрече
                val transfer = transferRepository.getTransfer(request.transferId)
                if (transfer == null) {
                    emit(CompleteResult.Error(
                        "Встреча не найдена",
                        CompleteResult.ErrorCode.TRANSFER_NOT_FOUND
                    ))
                    return@liveData
                }

                // Проверяем, что встреча еще не завершена
                if (transfer.status == TransferStatus.COMPLETED) {
                    emit(CompleteResult.Error(
                        "Встреча уже завершена",
                        CompleteResult.ErrorCode.TRANSFER_ALREADY_COMPLETED
                    ))
                    return@liveData
                }

                // Проверяем, что встреча не отменена
                if (transfer.status == TransferStatus.CANCELLED) {
                    emit(CompleteResult.Error(
                        "Встреча была отменена",
                        CompleteResult.ErrorCode.TRANSFER_CANCELLED
                    ))
                    return@liveData
                }

                // Проверяем, что завершает встречу участник
                if (transfer.giverId != request.completedBy && transfer.takerId != request.completedBy) {
                    emit(CompleteResult.Error(
                        "Вы не являетесь участником этой встречи",
                        CompleteResult.ErrorCode.NOT_AUTHORIZED
                    ))
                    return@liveData
                }

                // Проверяем, что встреча должна была уже состояться
                val now = System.currentTimeMillis()
                if (transfer.scheduledTime > now && transfer.status == TransferStatus.SCHEDULED) {
                    emit(CompleteResult.Error(
                        "Встреча еще не состоялась",
                        CompleteResult.ErrorCode.TOO_EARLY
                    ))
                    return@liveData
                }

                // Завершаем встречу
                val completeResult = transferRepository.completeTransfer(request.transferId)

                if (completeResult.isSuccess) {
                    // Обновляем статус вещи
                    val item = itemRepository.getItemById(transfer.itemId)
                    if (item != null) {
                        itemRepository.updateItemStatus(
                            transfer.itemId,
                            ItemStatus.COMPLETED,
                            null
                        )
                    }

                    // Обновляем статистику пользователей
                    updateUserStats(transfer)

                    // Добавляем оценку, если она есть
                    var updatedRatings: Pair<Float, Float>? = null
                    if (request.rating != null) {
                        updatedRatings = addRating(transfer, request)
                    }

                    // Получаем обновленную встречу
                    val updatedTransfer = transferRepository.getTransfer(request.transferId)

                    emit(CompleteResult.Success(
                        updatedTransfer ?: transfer.copy(status = TransferStatus.COMPLETED),
                        updatedRatings
                    ))
                } else {
                    emit(CompleteResult.Error(
                        "Не удалось завершить встречу",
                        CompleteResult.ErrorCode.UPDATE_FAILED
                    ))
                }

            } catch (e: Exception) {
                emit(CompleteResult.Error(
                    e.message ?: "Неизвестная ошибка",
                    CompleteResult.ErrorCode.UPDATE_FAILED
                ))
            }
        }
    }

    private suspend fun updateUserStats(transfer: Transfer) {
        // Обновляем статистику отдающего
        val giver = userRepository.getUser(transfer.giverId)
        giver?.let {
            val updatedGiver = it.copy(
                itemsGiven = it.itemsGiven + 1
            )
            userRepository.updateUser(updatedGiver)
        }

        // Обновляем статистику забирающего
        val taker = userRepository.getUser(transfer.takerId)
        taker?.let {
            val updatedTaker = it.copy(
                itemsTaken = it.itemsTaken + 1
            )
            userRepository.updateUser(updatedTaker)
        }
    }

    private suspend fun addRating(transfer: Transfer, request: CompleteRequest): Pair<Float, Float> {
        // Определяем, кому ставим оценку
        val reviewerId = request.completedBy
        val targetId = if (reviewerId == transfer.giverId) transfer.takerId else transfer.giverId

        // Создаем отзыв
        val review = Review(
            id = UUID.randomUUID().toString(),
            transferId = transfer.id,
            fromUserId = reviewerId,
            toUserId = targetId,
            rating = request.rating!!,
            comment = request.comment ?: "",
            date = System.currentTimeMillis()
        )

        // Добавляем отзыв
        userRepository.addReview(review)

        // Получаем обновленные рейтинги
        val updatedGiver = userRepository.getUser(transfer.giverId)
        val updatedTaker = userRepository.getUser(transfer.takerId)

        return Pair(
            updatedGiver?.rating ?: 0f,
            updatedTaker?.rating ?: 0f
        )
    }

    fun completeWithRating(
        transferId: String,
        userId: String,
        rating: Int,
        comment: String? = null
    ): LiveData<CompleteResult> {
        return execute(
            CompleteRequest(
                transferId = transferId,
                completedBy = userId,
                rating = rating,
                comment = comment
            )
        )
    }
}