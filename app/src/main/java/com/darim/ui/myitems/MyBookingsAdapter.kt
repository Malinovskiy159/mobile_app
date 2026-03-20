package com.darim.ui.myitems

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.darim.R
import com.darim.databinding.ItemMyBookingBinding
import com.darim.domain.usecase.item.GetMyBookingsUseCase
import com.darim.ui.utils.DateTimeHelper
import com.darim.ui.utils.PhotoManager

class MyBookingsAdapter(
    private var bookings: List<GetMyBookingsUseCase.BookingItem>,
    private val onItemClick: (GetMyBookingsUseCase.BookingItem) -> Unit,
    private val onContactClick: (GetMyBookingsUseCase.BookingItem) -> Unit,
    private val onCancelClick: (GetMyBookingsUseCase.BookingItem) -> Unit,
    private val onViewDetailsClick: (GetMyBookingsUseCase.BookingItem) -> Unit
) : RecyclerView.Adapter<MyBookingsAdapter.BookingViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val binding = ItemMyBookingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BookingViewHolder(binding, onItemClick, onContactClick, onCancelClick, onViewDetailsClick)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        holder.bind(bookings[position])
    }

    override fun getItemCount() = bookings.size

    fun updateBookings(newBookings: List<GetMyBookingsUseCase.BookingItem>) {
        bookings = newBookings
        notifyDataSetChanged()
    }

    fun addBooking(booking: GetMyBookingsUseCase.BookingItem) {
        bookings = bookings.toMutableList().apply { add(0, booking) }
        notifyItemInserted(0)
    }

    fun removeBooking(bookingId: String) {
        val index = bookings.indexOfFirst { it.item.id == bookingId }
        if (index != -1) {
            bookings = bookings.toMutableList().apply { removeAt(index) }
            notifyItemRemoved(index)
        }
    }

    fun updateBooking(updatedBooking: GetMyBookingsUseCase.BookingItem) {
        val index = bookings.indexOfFirst { it.item.id == updatedBooking.item.id }
        if (index != -1) {
            bookings = bookings.toMutableList().apply { set(index, updatedBooking) }
            notifyItemChanged(index)
        }
    }

    class BookingViewHolder(
        private val binding: ItemMyBookingBinding,
        private val onItemClick: (GetMyBookingsUseCase.BookingItem) -> Unit,
        private val onContactClick: (GetMyBookingsUseCase.BookingItem) -> Unit,
        private val onCancelClick: (GetMyBookingsUseCase.BookingItem) -> Unit,
        private val onViewDetailsClick: (GetMyBookingsUseCase.BookingItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(booking: GetMyBookingsUseCase.BookingItem) {
            binding.apply {
                // Основная информация о вещи
                textTitle.text = booking.item.title
                textCategory.text = booking.item.category

                // Информация о владельце
                textOwnerName.text = "Владелец: ${booking.ownerName}"

                // Статус бронирования
                setupStatusBadge(booking.status)

                // Дата бронирования
                textBookingDate.text = "Забронировано: ${DateTimeHelper.formatRelativeTime(booking.bookingDate)}"

                // Информация о встрече (если есть)
                if (booking.transfer != null) {
                    textMeetingInfo.visibility = android.view.View.VISIBLE
                    textMeetingInfo.text = "Встреча: ${DateTimeHelper.formatMeetingTime(booking.transfer.scheduledTime)}"

                    if (booking.transfer.meetingPoint.address.isNotEmpty()) {
                        textMeetingPlace.text = "Место: ${booking.transfer.meetingPoint.address}"
                        textMeetingPlace.visibility = android.view.View.VISIBLE
                    } else {
                        textMeetingPlace.visibility = android.view.View.GONE
                    }
                } else {
                    textMeetingInfo.visibility = android.view.View.GONE
                    textMeetingPlace.visibility = android.view.View.GONE
                }

                // Загружаем первое фото
                loadFirstImage(booking)

                // Настраиваем кнопки в зависимости от статуса
                setupActionButtons(booking)

                // Обработка клика по всей карточке
                root.setOnClickListener { onItemClick(booking) }
            }
        }

        private fun setupStatusBadge(status: GetMyBookingsUseCase.BookingStatus) {
            binding.apply {
                textStatus.visibility = android.view.View.VISIBLE
                textStatus.text = when (status) {
                    GetMyBookingsUseCase.BookingStatus.ACTIVE -> "Ожидает подтверждения"
                    GetMyBookingsUseCase.BookingStatus.SCHEDULED -> "Встреча назначена"
                    GetMyBookingsUseCase.BookingStatus.COMPLETED -> "Завершено"
                    GetMyBookingsUseCase.BookingStatus.CANCELLED -> "Отменено"
                    GetMyBookingsUseCase.BookingStatus.NO_SHOW -> "Не явился"
                }

                val statusColor = when (status) {
                    GetMyBookingsUseCase.BookingStatus.ACTIVE -> android.graphics.Color.parseColor("#4CAF50")    // Зеленый
                    GetMyBookingsUseCase.BookingStatus.SCHEDULED -> android.graphics.Color.parseColor("#2196F3")  // Синий
                    GetMyBookingsUseCase.BookingStatus.COMPLETED -> android.graphics.Color.parseColor("#9C27B0")  // Фиолетовый
                    GetMyBookingsUseCase.BookingStatus.CANCELLED -> android.graphics.Color.parseColor("#F44336")  // Красный
                    GetMyBookingsUseCase.BookingStatus.NO_SHOW -> android.graphics.Color.parseColor("#FF9800")    // Оранжевый
                }
                textStatus.setTextColor(statusColor)
            }
        }

        private fun loadFirstImage(booking: GetMyBookingsUseCase.BookingItem) {
            if (booking.item.photos.isNotEmpty()) {
                val uri = PhotoManager.getPhotoUri(binding.root.context, booking.item.photos[0])
                if (uri != null) {
                    binding.imageItem.setImageURI(uri)
                } else {
                    binding.imageItem.setImageResource(R.drawable.placeholder_image)
                }
            } else {
                binding.imageItem.setImageResource(R.drawable.placeholder_image)
            }
        }

        private fun setupActionButtons(booking: GetMyBookingsUseCase.BookingItem) {
            binding.apply {
                when (booking.status) {
                    GetMyBookingsUseCase.BookingStatus.ACTIVE -> {
                        // Активное бронирование - можно связаться с владельцем или отменить
                        buttonPrimary.visibility = android.view.View.VISIBLE
                        buttonSecondary.visibility = android.view.View.VISIBLE

                        buttonPrimary.text = "Связаться"
                        buttonPrimary.setIconResource(android.R.drawable.ic_menu_call)
                        buttonPrimary.setOnClickListener { onContactClick(booking) }

                        buttonSecondary.text = "Отменить"
                        buttonSecondary.setIconResource(android.R.drawable.ic_menu_delete)
                        buttonSecondary.setOnClickListener { onCancelClick(booking) }
                    }
                    GetMyBookingsUseCase.BookingStatus.SCHEDULED -> {
                        // Назначена встреча - можно посмотреть детали или связаться
                        buttonPrimary.visibility = android.view.View.VISIBLE
                        buttonSecondary.visibility = android.view.View.VISIBLE

                        buttonPrimary.text = "Детали встречи"
                        buttonPrimary.setIconResource(android.R.drawable.ic_menu_info_details)
                        buttonPrimary.setOnClickListener { onViewDetailsClick(booking) }

                        buttonSecondary.text = "Связаться"
                        buttonSecondary.setIconResource(android.R.drawable.ic_menu_call)
                        buttonSecondary.setOnClickListener { onContactClick(booking) }
                    }
                    GetMyBookingsUseCase.BookingStatus.COMPLETED -> {
                        // Завершено - можно посмотреть детали или оставить отзыв
                        buttonPrimary.visibility = android.view.View.VISIBLE
                        buttonSecondary.visibility = android.view.View.VISIBLE

                        buttonPrimary.text = "Детали"
                        buttonPrimary.setIconResource(android.R.drawable.ic_menu_info_details)
                        buttonPrimary.setOnClickListener { onViewDetailsClick(booking) }

                        buttonSecondary.text = "Оставить отзыв"
                        buttonSecondary.setIconResource(android.R.drawable.ic_menu_edit)
                        buttonSecondary.setOnClickListener {
                            // TODO: Open review dialog
                        }
                    }
                    GetMyBookingsUseCase.BookingStatus.CANCELLED -> {
                        // Отменено - только просмотр
                        buttonPrimary.visibility = android.view.View.VISIBLE
                        buttonSecondary.visibility = android.view.View.GONE

                        buttonPrimary.text = "Детали"
                        buttonPrimary.setIconResource(android.R.drawable.ic_menu_info_details)
                        buttonPrimary.setOnClickListener { onViewDetailsClick(booking) }
                    }
                    GetMyBookingsUseCase.BookingStatus.NO_SHOW -> {
                        // Неявка - только просмотр
                        buttonPrimary.visibility = android.view.View.VISIBLE
                        buttonSecondary.visibility = android.view.View.GONE

                        buttonPrimary.text = "Детали"
                        buttonPrimary.setIconResource(android.R.drawable.ic_menu_info_details)
                        buttonPrimary.setOnClickListener { onViewDetailsClick(booking) }
                    }
                }
            }
        }
    }
}