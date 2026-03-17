// domain/usecase/transfer/CancelTransferUseCase.kt
package com.darim.domain.usecase.transfer

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.darim.domain.model.ItemStatus
import com.darim.domain.model.Transfer
import com.darim.domain.model.TransferStatus
import com.darim.domain.repository.ItemRepository
import com.darim.domain.repository.TransferRepository
import kotlinx.coroutines.Dispatchers

class CancelTransferUseCase(
    private val transferRepository: TransferRepository,
    private val itemRepository: ItemRepository
) {

    data class CancelRequest(
        val transferId: String,
        val cancelledBy: String,
        val reason: String,
        val cancellationType: CancellationType = CancellationType.USER_CANCELLED
    )

    enum class CancellationType {
        USER_CANCELLED,     // Отмена пользователем
        SYSTEM_CANCELLED,   // Автоматическая отмена системой
        ADMIN_CANCELLED,    // Отмена администратором
        EXPIRED            // Истек срок
    }

    sealed class CancelResult {
        data class Success(
            val transfer: Transfer,
            val cancellationType: CancellationType
        ) : CancelResult()
        data class Error(val message: String, val code: ErrorCode) : CancelResult()
        object Loading : CancelResult()

        enum class ErrorCode {
            TRANSFER_NOT_FOUND,
            TRANSFER_ALREADY_COMPLETED,
            TRANSFER_ALREADY_CANCELLED,
            TRANSFER_NO_SHOW,
            NOT_AUTHORIZED,
            TOO_LATE_TO_CANCEL,
            UPDATE_FAILED,
            ITEM_UPDATE_FAILED
        }
    }

    fun execute(request: CancelRequest): LiveData<CancelResult> {
        return liveData(Dispatchers.IO) {
            emit(CancelResult.Loading)

            try {
                // Получаем информацию о встрече
                val transfer = transferRepository.getTransfer(request.transferId)
                if (transfer == null) {
                    emit(CancelResult.Error(
                        "Встреча не найдена",
                        CancelResult.ErrorCode.TRANSFER_NOT_FOUND
                    ))
                    return@liveData
                }

                // Проверяем текущий статус
                when (transfer.status) {
                    TransferStatus.COMPLETED -> {
                        emit(CancelResult.Error(
                            "Нельзя отменить завершенную встречу",
                            CancelResult.ErrorCode.TRANSFER_ALREADY_COMPLETED
                        ))
                        return@liveData
                    }
                    TransferStatus.CANCELLED -> {
                        emit(CancelResult.Error(
                            "Встреча уже отменена",
                            CancelResult.ErrorCode.TRANSFER_ALREADY_CANCELLED
                        ))
                        return@liveData
                    }
                    TransferStatus.NO_SHOW -> {
                        emit(CancelResult.Error(
                            "Нельзя отменить встречу с отметкой о неявке",
                            CancelResult.ErrorCode.TRANSFER_NO_SHOW
                        ))
                        return@liveData
                    }
                    else -> {}
                }

                // Проверяем права на отмену
                if (!canCancel(transfer, request.cancelledBy, request.cancellationType)) {
                    emit(CancelResult.Error(
                        "У вас нет прав на отмену этой встречи",
                        CancelResult.ErrorCode.NOT_AUTHORIZED
                    ))
                    return@liveData
                }

                // Проверяем, можно ли отменить по времени
                if (!canCancelByTime(transfer, request.cancellationType)) {
                    emit(CancelResult.Error(
                        "Слишком поздно для отмены (менее 2 часов до встречи)",
                        CancelResult.ErrorCode.TOO_LATE_TO_CANCEL
                    ))
                    return@liveData
                }

                // Обновляем статус встречи
                val updateResult = transferRepository.updateTransferStatus(
                    request.transferId,
                    TransferStatus.CANCELLED
                )

                if (updateResult.isSuccess) {
                    // Возвращаем вещь в статус AVAILABLE
                    val itemUpdateResult = itemRepository.updateItemStatus(
                        transfer.itemId,
                        ItemStatus.AVAILABLE,
                        null
                    )

                    if (itemUpdateResult.isSuccess || itemUpdateResult.getOrNull() == true) {
                        val updatedTransfer = transferRepository.getTransfer(request.transferId)

                        // Логируем причину отмены (в реальном приложении)
                        logCancellation(request)

                        emit(CancelResult.Success(
                            updatedTransfer ?: transfer.copy(status = TransferStatus.CANCELLED),
                            request.cancellationType
                        ))
                    } else {
                        emit(CancelResult.Error(
                            "Не удалось обновить статус вещи",
                            CancelResult.ErrorCode.ITEM_UPDATE_FAILED
                        ))
                    }
                } else {
                    emit(CancelResult.Error(
                        "Не удалось отменить встречу",
                        CancelResult.ErrorCode.UPDATE_FAILED
                    ))
                }

            } catch (e: Exception) {
                emit(CancelResult.Error(
                    e.message ?: "Неизвестная ошибка",
                    CancelResult.ErrorCode.UPDATE_FAILED
                ))
            }
        }
    }

    private fun canCancel(transfer: Transfer, userId: String, type: CancellationType): Boolean {
        return when (type) {
            CancellationType.ADMIN_CANCELLED -> true
            CancellationType.SYSTEM_CANCELLED -> true
            CancellationType.EXPIRED -> true
            CancellationType.USER_CANCELLED -> {
                userId == transfer.giverId || userId == transfer.takerId
            }
        }
    }

    private fun canCancelByTime(transfer: Transfer, type: CancellationType): Boolean {
        // Админ и система могут отменять в любое время
        if (type == CancellationType.ADMIN_CANCELLED ||
            type == CancellationType.SYSTEM_CANCELLED ||
            type == CancellationType.EXPIRED) {
            return true
        }

        // Пользователи могут отменять не менее чем за 2 часа до встречи
        val now = System.currentTimeMillis()
        val minAdvanceTime = 2 * 60 * 60 * 1000L // 2 часа

        return transfer.scheduledTime - now > minAdvanceTime
    }

    private fun logCancellation(request: CancelRequest) {
        // В реальном приложении здесь было бы логирование в Analytics или базу данных
        println("Cancellation: transferId=${request.transferId}, " +
                "cancelledBy=${request.cancelledBy}, " +
                "type=${request.cancellationType}, " +
                "reason=${request.reason}")
    }

    fun cancelByUser(transferId: String, userId: String, reason: String): LiveData<CancelResult> {
        return execute(
            CancelRequest(
                transferId = transferId,
                cancelledBy = userId,
                reason = reason,
                cancellationType = CancellationType.USER_CANCELLED
            )
        )
    }

    fun cancelExpiredTransfers(): LiveData<List<CancelResult>> {
        return liveData(Dispatchers.IO) {
            try {
                val results = mutableListOf<CancelResult>()
                val now = System.currentTimeMillis()
                val expiryTime = 24 * 60 * 60 * 1000L // 24 часа

                // В реальном приложении здесь был бы запрос к репозиторию
                // для получения просроченных встреч

                emit(results)
            } catch (e: Exception) {
                emit(emptyList())
            }
        }
    }
}