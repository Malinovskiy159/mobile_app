// domain/usecase/item/BookItemUseCase.kt
package com.darim.domain.usecase.item

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.darim.domain.model.ItemStatus
import com.darim.domain.repository.ItemRepository
import com.darim.domain.repository.TransferRepository
import com.darim.domain.repository.UserRepository
import com.darim.ui.utils.SessionManager
import kotlinx.coroutines.Dispatchers

class BookItemUseCase(
    private val itemRepository: ItemRepository,
    private val userRepository: UserRepository,
    private val transferRepository: TransferRepository
) {

    sealed class BookingResult {
        data class Success(val message: String) : BookingResult()
        data class Error(val message: String, val code: ErrorCode) : BookingResult()
        object Loading : BookingResult()

        enum class ErrorCode {
            ITEM_NOT_FOUND,
            ITEM_NOT_AVAILABLE,
            ITEM_ALREADY_BOOKED,
            OWNER_CANNOT_BOOK,
            USER_NOT_FOUND,
            BOOKING_FAILED,
            GUEST_CANNOT_BOOK
        }
    }

    fun execute(itemId: String, userId: String): LiveData<BookingResult> {
        return liveData(Dispatchers.IO) {
            emit(BookingResult.Loading)

            try {
                // Проверяем, не гость ли пользователь
                if (SessionManager.isGuest()) {
                    emit(BookingResult.Error(
                        "Гости не могут бронировать вещи. Зарегистрируйтесь!",
                        BookingResult.ErrorCode.GUEST_CANNOT_BOOK
                    ))
                    return@liveData
                }

                val item = itemRepository.getItemById(itemId)
                if (item == null) {
                    emit(BookingResult.Error(
                        "Вещь не найдена",
                        BookingResult.ErrorCode.ITEM_NOT_FOUND
                    ))
                    return@liveData
                }

                if (item.ownerId == userId) {
                    emit(BookingResult.Error(
                        "Вы не можете забронировать свою собственную вещь",
                        BookingResult.ErrorCode.OWNER_CANNOT_BOOK
                    ))
                    return@liveData
                }

                if (!validateItemStatus(item)) {
                    emit(BookingResult.Error(
                        "Вещь недоступна для бронирования",
                        BookingResult.ErrorCode.ITEM_NOT_AVAILABLE
                    ))
                    return@liveData
                }

                val user = userRepository.getUser(userId)
                if (user == null) {
                    emit(BookingResult.Error(
                        "Пользователь не найден",
                        BookingResult.ErrorCode.USER_NOT_FOUND
                    ))
                    return@liveData
                }

                val result = itemRepository.updateItemStatus(itemId, ItemStatus.BOOKED, userId)

                if (result.isSuccess && result.getOrNull() == true) {
                    emit(BookingResult.Success("Вещь успешно забронирована"))
                } else {
                    emit(BookingResult.Error(
                        "Не удалось забронировать вещь",
                        BookingResult.ErrorCode.BOOKING_FAILED
                    ))
                }

            } catch (e: Exception) {
                emit(BookingResult.Error(
                    e.message ?: "Ошибка бронирования",
                    BookingResult.ErrorCode.BOOKING_FAILED
                ))
            }
        }
    }

    private fun validateItemStatus(item: com.darim.domain.model.Item): Boolean {
        return item.status == ItemStatus.AVAILABLE
    }

    fun checkIfCanBook(itemId: String, userId: String): LiveData<Boolean> {
        return liveData(Dispatchers.IO) {
            if (SessionManager.isGuest()) {
                emit(false)
                return@liveData
            }

            val item = itemRepository.getItemById(itemId)
            val canBook = item != null &&
                    item.status == ItemStatus.AVAILABLE &&
                    item.ownerId != userId
            emit(canBook)
        }
    }
}