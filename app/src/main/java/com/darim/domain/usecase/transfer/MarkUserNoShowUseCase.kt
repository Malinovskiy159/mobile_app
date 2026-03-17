// domain/usecase/transfer/MarkUserNoShowUseCase.kt
package com.darim.domain.usecase.transfer

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.darim.domain.model.Transfer
import com.darim.domain.model.TransferStatus
import com.darim.domain.repository.TransferRepository
import com.darim.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers

class MarkUserNoShowUseCase(
    private val transferRepository: TransferRepository,
    private val userRepository: UserRepository
) {

    data class NoShowRequest(
        val transferId: String,
        val reporterId: String // ID пользователя, который сообщает о неявке
    )

    sealed class NoShowResult {
        data class Success(
            val transfer: Transfer,
            val penalizedUser: String,
            val warningCount: Int
        ) : NoShowResult()
        data class Error(val message: String, val code: ErrorCode) : NoShowResult()
        object Loading : NoShowResult()

        enum class ErrorCode {
            TRANSFER_NOT_FOUND,
            TRANSFER_ALREADY_COMPLETED,
            TRANSFER_CANCELLED,
            NOT_AUTHORIZED,
            ALREADY_REPORTED,
            TOO_EARLY,
            INVALID_REPORTER,
            UPDATE_FAILED
        }
    }

    fun execute(request: NoShowRequest): LiveData<NoShowResult> {
        return liveData(Dispatchers.IO) {
            emit(NoShowResult.Loading)

            try {
                // Получаем информацию о встрече
                val transfer = transferRepository.getTransfer(request.transferId)
                if (transfer == null) {
                    emit(NoShowResult.Error(
                        "Встреча не найдена",
                        NoShowResult.ErrorCode.TRANSFER_NOT_FOUND
                    ))
                    return@liveData
                }

                // Проверяем, что встреча еще не завершена
                if (transfer.status == TransferStatus.COMPLETED) {
                    emit(NoShowResult.Error(
                        "Встреча уже завершена",
                        NoShowResult.ErrorCode.TRANSFER_ALREADY_COMPLETED
                    ))
                    return@liveData
                }

                // Проверяем, что встреча не отменена
                if (transfer.status == TransferStatus.CANCELLED) {
                    emit(NoShowResult.Error(
                        "Встреча была отменена",
                        NoShowResult.ErrorCode.TRANSFER_CANCELLED
                    ))
                    return@liveData
                }

                // Проверяем, что встреча не отмечена как "не пришел"
                if (transfer.status == TransferStatus.NO_SHOW) {
                    emit(NoShowResult.Error(
                        "Неявка уже отмечена",
                        NoShowResult.ErrorCode.ALREADY_REPORTED
                    ))
                    return@liveData
                }

                // Проверяем, что сообщает участник встречи
                if (transfer.giverId != request.reporterId && transfer.takerId != request.reporterId) {
                    emit(NoShowResult.Error(
                        "Вы не являетесь участником этой встречи",
                        NoShowResult.ErrorCode.NOT_AUTHORIZED
                    ))
                    return@liveData
                }

                // Проверяем, что встреча должна была уже состояться
                val now = System.currentTimeMillis()
                val waitTime = 30 * 60 * 1000 // 30 минут после назначенного времени

                if (now < transfer.scheduledTime + waitTime) {
                    emit(NoShowResult.Error(
                        "Подождите 30 минут после назначенного времени",
                        NoShowResult.ErrorCode.TOO_EARLY
                    ))
                    return@liveData
                }

                // Определяем, кто не пришел
                val noShowUserId = if (request.reporterId == transfer.giverId) {
                    transfer.takerId // Забирающий не пришел
                } else {
                    transfer.giverId // Отдающий не пришел
                }

                // Применяем штрафные санкции
                val warningCount = penalizeUser(noShowUserId)

                // Обновляем статус встречи
                val updateResult = transferRepository.updateTransferStatus(
                    request.transferId,
                    TransferStatus.NO_SHOW
                )

                if (updateResult.isSuccess) {
                    val updatedTransfer = transferRepository.getTransfer(request.transferId)

                    emit(NoShowResult.Success(
                        updatedTransfer ?: transfer.copy(status = TransferStatus.NO_SHOW),
                        noShowUserId,
                        warningCount
                    ))
                } else {
                    emit(NoShowResult.Error(
                        "Не удалось обновить статус встречи",
                        NoShowResult.ErrorCode.UPDATE_FAILED
                    ))
                }

            } catch (e: Exception) {
                emit(NoShowResult.Error(
                    e.message ?: "Неизвестная ошибка",
                    NoShowResult.ErrorCode.UPDATE_FAILED
                ))
            }
        }
    }

    private suspend fun penalizeUser(userId: String): Int {
        // В реальном приложении здесь была бы логика штрафов
        // Например, счетчик предупреждений, временная блокировка и т.д.

        // Для примера просто возвращаем количество предупреждений
        val user = userRepository.getUser(userId)

        // Здесь можно реализовать логику подсчета неявок
        // Например, через отдельное поле в User или отдельный репозиторий

        return 1 // Первое предупреждение
    }

    fun getNoShowHistory(userId: String): LiveData<List<Transfer>> {
        return liveData(Dispatchers.IO) {
            try {
                // В реальном приложении здесь был бы запрос к репозиторию
                // для получения истории неявок пользователя
                emit(emptyList())
            } catch (e: Exception) {
                emit(emptyList())
            }
        }
    }
}