// domain/usecase/item/GetMyBookingsUseCase.kt
package com.darim.domain.usecase.item

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.darim.domain.model.Item
import com.darim.domain.model.ItemStatus
import com.darim.domain.model.Transfer
import com.darim.domain.model.TransferStatus
import com.darim.domain.repository.ItemRepository
import com.darim.domain.repository.TransferRepository
import com.darim.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers

class GetMyBookingsUseCase(
    private val itemRepository: ItemRepository,
    private val transferRepository: TransferRepository,
    private val userRepository: UserRepository
) {

    data class BookingItem(
        val item: Item,
        val ownerName: String,
        val ownerPhone: String,
        val transfer: Transfer?,
        val bookingDate: Long,
        val status: BookingStatus
    )

    enum class BookingStatus {
        ACTIVE,          // Забронировано, ожидает встречи
        SCHEDULED,       // Встреча назначена
        COMPLETED,       // Завершено
        CANCELLED,       // Отменено
        NO_SHOW         // Владелец не пришел
    }

    sealed class BookingsResult {
        data class Success(val bookings: List<BookingItem>) : BookingsResult()
        data class Error(val message: String) : BookingsResult()
        object Loading : BookingsResult()
        object Empty : BookingsResult()
    }

    sealed class BookingsFilter {
        object All : BookingsFilter()
        object Active : BookingsFilter()      // Активные брони
        object Upcoming : BookingsFilter()    // Предстоящие встречи
        object Completed : BookingsFilter()   // Завершенные
        object Cancelled : BookingsFilter()   // Отмененные
    }

    fun execute(
        userId: String,
        filter: BookingsFilter = BookingsFilter.All
    ): LiveData<BookingsResult> {
        return liveData(Dispatchers.IO) {
            emit(BookingsResult.Loading)

            try {
                // Получаем все вещи, которые забронировал пользователь
                val bookedItems = itemRepository.getMyBookings(userId)

                if (bookedItems.isEmpty()) {
                    emit(BookingsResult.Empty)
                    return@liveData
                }

                val bookings = mutableListOf<BookingItem>()

                bookedItems.forEach { item ->
                    // Получаем информацию о владельце
                    val owner = userRepository.getUser(item.ownerId)

                    // Получаем информацию о встрече
                    val transfers = transferRepository.getTransfersForItem(item.id)
                    val activeTransfer = transfers.firstOrNull {
                        it.status == TransferStatus.SCHEDULED
                    }

                    val bookingStatus = determineBookingStatus(item, activeTransfer)

                    val bookingItem = BookingItem(
                        item = item,
                        ownerName = owner?.name ?: "Неизвестный пользователь",
                        ownerPhone = owner?.phone ?: "",
                        transfer = activeTransfer,
                        bookingDate = item.createdAt,
                        status = bookingStatus
                    )

                    bookings.add(bookingItem)
                }

                // Применяем фильтр
                val filteredBookings = when (filter) {
                    is BookingsFilter.All -> bookings
                    is BookingsFilter.Active -> bookings.filter {
                        it.status == BookingStatus.ACTIVE ||
                                it.status == BookingStatus.SCHEDULED
                    }
                    is BookingsFilter.Upcoming -> bookings.filter {
                        it.status == BookingStatus.SCHEDULED
                    }
                    is BookingsFilter.Completed -> bookings.filter {
                        it.status == BookingStatus.COMPLETED
                    }
                    is BookingsFilter.Cancelled -> bookings.filter {
                        it.status == BookingStatus.CANCELLED
                    }
                }

                // Сортируем по дате бронирования (сначала новые)
                val sortedBookings = filteredBookings.sortedByDescending { it.bookingDate }

                emit(BookingsResult.Success(sortedBookings))
            } catch (e: Exception) {
                emit(BookingsResult.Error(e.message ?: "Ошибка загрузки броней"))
            }
        }
    }

    private fun determineBookingStatus(item: Item, transfer: Transfer?): BookingStatus {
        return when {
            item.status == ItemStatus.COMPLETED -> BookingStatus.COMPLETED
            item.status == ItemStatus.CANCELLED -> BookingStatus.CANCELLED
            transfer != null -> {
                when (transfer.status) {
                    TransferStatus.SCHEDULED -> BookingStatus.SCHEDULED
                    TransferStatus.COMPLETED -> BookingStatus.COMPLETED
                    TransferStatus.CANCELLED -> BookingStatus.CANCELLED
                    TransferStatus.NO_SHOW -> BookingStatus.NO_SHOW
                }
            }
            item.status == ItemStatus.BOOKED -> BookingStatus.ACTIVE
            else -> BookingStatus.CANCELLED
        }
    }

    fun getUpcomingMeetings(userId: String): LiveData<List<BookingItem>> {
        return liveData(Dispatchers.IO) {
            try {
                val result = execute(userId, BookingsFilter.Upcoming)
                // В реальном коде нужно обработать LiveData правильно
                // Для упрощения возвращаем пустой список
                emit(emptyList())
            } catch (e: Exception) {
                emit(emptyList())
            }
        }
    }

    fun getBookingStats(userId: String): LiveData<BookingStats> {
        return liveData(Dispatchers.IO) {
            try {
                val bookedItems = itemRepository.getMyBookings(userId)

                val stats = BookingStats(
                    total = bookedItems.size,
                    active = bookedItems.count { it.status == ItemStatus.BOOKED },
                    completed = bookedItems.count { it.status == ItemStatus.COMPLETED },
                    cancelled = bookedItems.count { it.status == ItemStatus.CANCELLED }
                )
                emit(stats)
            } catch (e: Exception) {
                emit(BookingStats(0, 0, 0, 0))
            }
        }
    }
}

data class BookingStats(
    val total: Int,
    val active: Int,
    val completed: Int,
    val cancelled: Int
)