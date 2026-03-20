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

    // ДОБАВЛЯЕМ КЛАСС BookingStats
    data class BookingStats(
        val total: Int,
        val active: Int,
        val scheduled: Int,
        val completed: Int,
        val cancelled: Int,
        val noShow: Int
    ) {
        val activeBookings: Int
            get() = active + scheduled

        fun getSummary(): String {
            return "Всего: $total | ✅ Активных: $activeBookings | 🎉 Завершено: $completed | ❌ Отменено: $cancelled"
        }
    }

    sealed class BookingsResult {
        data class Success(val bookings: List<BookingItem>) : BookingsResult()
        data class Error(val message: String) : BookingsResult()
        object Loading : BookingsResult()
        object Empty : BookingsResult()
    }

    sealed class BookingsFilter {
        object All : BookingsFilter()
        object Active : BookingsFilter()      // Активные брони (ACTIVE + SCHEDULED)
        object Upcoming : BookingsFilter()    // Предстоящие встречи (только SCHEDULED)
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
                val bookedItems = itemRepository.getMyBookings(userId)

                if (bookedItems.isEmpty()) {
                    emit(BookingsResult.Empty)
                    return@liveData
                }

                val bookings = mutableListOf<BookingItem>()

                bookedItems.forEach { item ->
                    val owner = userRepository.getUser(item.ownerId)

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

                val sortedBookings = filteredBookings.sortedByDescending { it.bookingDate }

                if (sortedBookings.isEmpty()) {
                    emit(BookingsResult.Empty)
                } else {
                    emit(BookingsResult.Success(sortedBookings))
                }
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
                emit(emptyList())
            } catch (e: Exception) {
                emit(emptyList())
            }
        }
    }

    suspend fun getTransferForBooking(bookingId: String): Transfer? {
        return transferRepository.getTransfer(bookingId)
    }

    fun getBookingStats(userId: String): LiveData<BookingStats> {
        return liveData(Dispatchers.IO) {
            try {
                val bookedItems = itemRepository.getMyBookings(userId)

                var active = 0
                var scheduled = 0
                var completed = 0
                var cancelled = 0
                var noShow = 0

                bookedItems.forEach { item ->
                    val transfers = transferRepository.getTransfersForItem(item.id)
                    val activeTransfer = transfers.firstOrNull {
                        it.status == TransferStatus.SCHEDULED
                    }

                    when {
                        item.status == ItemStatus.COMPLETED -> completed++
                        item.status == ItemStatus.CANCELLED -> cancelled++
                        activeTransfer != null -> {
                            when (activeTransfer.status) {
                                TransferStatus.SCHEDULED -> scheduled++
                                TransferStatus.COMPLETED -> completed++
                                TransferStatus.CANCELLED -> cancelled++
                                TransferStatus.NO_SHOW -> noShow++
                            }
                        }
                        item.status == ItemStatus.BOOKED -> active++
                        else -> cancelled++
                    }
                }

                val stats = BookingStats(
                    total = bookedItems.size,
                    active = active,
                    scheduled = scheduled,
                    completed = completed,
                    cancelled = cancelled,
                    noShow = noShow
                )
                emit(stats)
            } catch (e: Exception) {
                emit(BookingStats(0, 0, 0, 0, 0, 0))
            }
        }
    }
}