// domain/usecase/user/GetUserProfileUseCase.kt
package com.darim.domain.usecase.user

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.darim.domain.model.Review
import com.darim.domain.model.User
import com.darim.domain.repository.ItemRepository
import com.darim.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.*

class GetUserProfileUseCase(
    private val userRepository: UserRepository,
    private val itemRepository: ItemRepository
) {

    data class UserStats(
        val itemsGiven: Int,
        val itemsTaken: Int,
        val rating: Float,
        val totalReviews: Int,
        val totalViews: Int,
        val activeItems: Int,
        val memberSince: String
    )

    sealed class ProfileResult {
        data class Success(
            val user: User,
            val stats: UserStats,
            val recentReviews: List<Review>
        ) : ProfileResult()

        data class Error(val message: String) : ProfileResult()
        object Loading : ProfileResult()
        object NotFound : ProfileResult()
    }

    fun execute(userId: String): LiveData<ProfileResult> {
        return liveData(Dispatchers.IO) {
            emit(ProfileResult.Loading)

            try {
                // Получаем пользователя
                val user = userRepository.getUser(userId)
                if (user == null) {
                    emit(ProfileResult.NotFound)
                    return@liveData
                }

                // Получаем статистику по вещам пользователя
                //val userStats = itemRepository.getUserStats(userId)

                // Формируем расширенную статистику
                val stats = UserStats(
                    itemsGiven = user.itemsGiven,
                    itemsTaken = user.itemsTaken,
                    rating = user.rating,
                    totalReviews = user.reviews.size,
                    totalViews = 0,
                    activeItems = 0,
                    memberSince = formatMemberSince(user.id)
                )

                // Берем последние 5 отзывов
                val recentReviews = user.reviews
                    .sortedByDescending { it.date }
                    .take(5)

                emit(ProfileResult.Success(user, stats, recentReviews))

            } catch (e: Exception) {
                emit(ProfileResult.Error(e.message ?: "Ошибка загрузки профиля"))
            }
        }
    }

    private fun formatMemberSince(userId: String): String {
        // В реальном приложении здесь берется дата регистрации из БД
        // Для демо возвращаем текущую дату
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -6) // 6 месяцев назад
        return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
    }
}