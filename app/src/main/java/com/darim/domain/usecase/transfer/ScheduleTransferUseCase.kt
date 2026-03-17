// domain/usecase/transfer/ScheduleTransferUseCase.kt
package com.darim.domain.usecase.transfer

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.darim.domain.model.*
import com.darim.domain.repository.ItemRepository
import com.darim.domain.repository.TransferRepository
import com.darim.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers

class ScheduleTransferUseCase(
    private val transferRepository: TransferRepository,
    private val itemRepository: ItemRepository,
    private val userRepository: UserRepository
) {

    data class ScheduleRequest(
        val itemId: String,
        val giverId: String,  // Тот, кто отдает вещь
        val takerId: String,   // Тот, кто забирает
        val scheduledTime: Long,
        val meetingPoint: Location,
        val notes: String = ""
    )

    sealed class ScheduleResult {
        data class Success(val transferId: String, val transfer: Transfer) : ScheduleResult()
        data class Error(val message: String, val code: ErrorCode) : ScheduleResult()
        object Loading : ScheduleResult()

        enum class ErrorCode {
            ITEM_NOT_FOUND,
            ITEM_NOT_AVAILABLE,
            ITEM_ALREADY_BOOKED,
            INVALID_TIME,
            PAST_TIME,
            SAME_USER,
            USER_NOT_FOUND,
            TIME_CONFLICT,
            MAX_MEETINGS_REACHED,
            LOCATION_INVALID
        }
    }

    fun execute(request: ScheduleRequest): LiveData<ScheduleResult> {
        return liveData(Dispatchers.IO) {
            emit(ScheduleResult.Loading)

            try {
                // Валидация времени
                val validationError = validateTime(request.scheduledTime)
                if (validationError != null) {
                    emit(validationError)
                    return@liveData
                }

                // Проверка, что отдающий и забирающий - разные люди
                if (request.giverId == request.takerId) {
                    emit(ScheduleResult.Error(
                        "Нельзя назначить встречу самому с собой",
                        ScheduleResult.ErrorCode.SAME_USER
                    ))
                    return@liveData
                }

                // Получаем информацию о вещи
                val item = itemRepository.getItemById(request.itemId)
                if (item == null) {
                    emit(ScheduleResult.Error(
                        "Вещь не найдена",
                        ScheduleResult.ErrorCode.ITEM_NOT_FOUND
                    ))
                    return@liveData
                }

                // Проверяем, что вещь доступна
                if (item.status != ItemStatus.AVAILABLE) {
                    emit(ScheduleResult.Error(
                        "Вещь уже забронирована",
                        ScheduleResult.ErrorCode.ITEM_ALREADY_BOOKED
                    ))
                    return@liveData
                }

                // Проверяем, что вещь принадлежит отдающему
                if (item.ownerId != request.giverId) {
                    emit(ScheduleResult.Error(
                        "Вы не являетесь владельцем этой вещи",
                        ScheduleResult.ErrorCode.ITEM_NOT_FOUND
                    ))
                    return@liveData
                }

                // Проверяем существование пользователей
                val taker = userRepository.getUser(request.takerId)
                if (taker == null) {
                    emit(ScheduleResult.Error(
                        "Пользователь не найден",
                        ScheduleResult.ErrorCode.USER_NOT_FOUND
                    ))
                    return@liveData
                }

                // Проверяем, нет ли конфликтующих встреч
                val existingTransfers = transferRepository.getTransfersForItem(request.itemId)
                val hasConflict = existingTransfers.any { transfer ->
                    transfer.status == TransferStatus.SCHEDULED &&
                            Math.abs(transfer.scheduledTime - request.scheduledTime) < 3600000 // 1 час
                }

                if (hasConflict) {
                    emit(ScheduleResult.Error(
                        "На это время уже назначена встреча",
                        ScheduleResult.ErrorCode.TIME_CONFLICT
                    ))
                    return@liveData
                }

                // Создаем встречу
                val transfer = Transfer(
                    id = "", // будет сгенерировано в репозитории
                    itemId = request.itemId,
                    giverId = request.giverId,
                    takerId = request.takerId,
                    status = TransferStatus.SCHEDULED,
                    scheduledTime = request.scheduledTime,
                    meetingPoint = request.meetingPoint
                )

                val result = transferRepository.createTransfer(transfer)

                if (result.isSuccess) {
                    val transferId = result.getOrNull()
                    if (transferId != null) {
                        // Обновляем статус вещи на BOOKED
                        itemRepository.updateItemStatus(
                            request.itemId,
                            ItemStatus.BOOKED,
                            request.takerId
                        )

                        val createdTransfer = transferRepository.getTransfer(transferId)
                        if (createdTransfer != null) {
                            emit(ScheduleResult.Success(transferId, createdTransfer))
                        } else {
                            emit(ScheduleResult.Success(transferId, transfer.copy(id = transferId)))
                        }
                    } else {
                        emit(ScheduleResult.Error(
                            "Не удалось создать встречу",
                            ScheduleResult.ErrorCode.MAX_MEETINGS_REACHED
                        ))
                    }
                } else {
                    emit(ScheduleResult.Error(
                        result.exceptionOrNull()?.message ?: "Ошибка создания встречи",
                        ScheduleResult.ErrorCode.MAX_MEETINGS_REACHED
                    ))
                }

            } catch (e: Exception) {
                emit(ScheduleResult.Error(
                    e.message ?: "Неизвестная ошибка",
                    ScheduleResult.ErrorCode.MAX_MEETINGS_REACHED
                ))
            }
        }
    }

    private fun validateTime(scheduledTime: Long): ScheduleResult.Error? {
        val now = System.currentTimeMillis()
        val minAdvanceTime = 30 * 60 * 1000 // 30 минут
        val maxAdvanceTime = 7 * 24 * 60 * 60 * 1000L // 7 дней

        return when {
            scheduledTime < now -> ScheduleResult.Error(
                "Нельзя назначить встречу в прошлом",
                ScheduleResult.ErrorCode.PAST_TIME
            )
            scheduledTime < now + minAdvanceTime -> ScheduleResult.Error(
                "Встречу нужно назначить минимум за 30 минут",
                ScheduleResult.ErrorCode.INVALID_TIME
            )
            scheduledTime > now + maxAdvanceTime -> ScheduleResult.Error(
                "Нельзя назначить встречу более чем на 7 дней вперед",
                ScheduleResult.ErrorCode.INVALID_TIME
            )
            else -> null
        }
    }

    fun getAvailableTimeSlots(
        giverId: String,
        date: Long
    ): LiveData<List<TimeSlot>> {
        return liveData(Dispatchers.IO) {
            try {
                // Получаем все встречи отдающего на указанную дату
                val startOfDay = getStartOfDay(date)
                val endOfDay = getEndOfDay(date)

                // В реальном приложении здесь был бы запрос к репозиторию
                // для получения всех встреч пользователя за день

                // Генерируем доступные слоты (пример)
                val availableSlots = generateAvailableSlots(startOfDay, endOfDay)

                emit(availableSlots)
            } catch (e: Exception) {
                emit(emptyList())
            }
        }
    }

    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getEndOfDay(timestamp: Long): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        calendar.set(java.util.Calendar.SECOND, 59)
        calendar.set(java.util.Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    private fun generateAvailableSlots(startOfDay: Long, endOfDay: Long): List<TimeSlot> {
        val slots = mutableListOf<TimeSlot>()
        var currentTime = startOfDay
        val slotDuration = 30 * 60 * 1000 // 30 минут

        while (currentTime < endOfDay) {
            // Пропускаем ночное время (например, с 23:00 до 08:00)
            val hour = java.util.Calendar.getInstance().apply {
                timeInMillis = currentTime
            }.get(java.util.Calendar.HOUR_OF_DAY)

            if (hour in 8..22) {
                slots.add(
                    TimeSlot(
                        startTime = currentTime,
                        endTime = currentTime + slotDuration,
                        available = true
                    )
                )
            }
            currentTime += slotDuration
        }

        return slots
    }
}

data class TimeSlot(
    val startTime: Long,
    val endTime: Long,
    val available: Boolean
)