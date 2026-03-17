// domain/usecase/user/GetUserProfileUseCase.kt
package com.darim.domain.usecase.user

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.darim.domain.model.Review
import com.darim.domain.model.Transfer
import com.darim.domain.model.TransferStatus
import com.darim.domain.model.User
import com.darim.domain.repository.ItemRepository
import com.darim.domain.repository.TransferRepository
import com.darim.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers

class GetUserProfileUseCase(
    private val userRepository: UserRepository,
    private val itemRepository: ItemRepository,
    private val transferRepository: TransferRepository
) {

    data class UserProfile(
        val user: User,
        val stats: UserStats,
        val recentReviews: List<ReviewWithMeta>,
        val isCurrentUser: Boolean
    )

    data class UserStats(
        val itemsPublished: Int,           // Опубликовано вещей
        val itemsGiven: Int,                // Отдано вещей
        val itemsTaken: Int,                 // Получено вещей
        val completedTransfers: Int,         // Завершенных встреч
        val successRate: Double,              // Процент успешных встреч
        val averageRating: Float,             // Средний рейтинг
        val totalReviews: Int,                 // Всего отзывов
        val registrationDate: Long,            // Дата регистрации
        val lastActive: Long,                   // Последняя активность
        val ratingDistribution: Map<Int, Int>   // Распределение оценок (1-5)
    )

    data class ReviewWithMeta(
        val review: Review,
        val reviewerName: String,
        val reviewerAvatar: String?,
        val transferDate: Long,
        val itemTitle: String,
        val itemId: String
    )

    sealed class ProfileResult {
        data class Success(val profile: UserProfile) : ProfileResult()
        data class Error(val message: String) : ProfileResult()
        object Loading : ProfileResult()
        object NotFound : ProfileResult()
    }

    fun execute(userId: String, currentUserId: String? = null): LiveData<ProfileResult> {
        return liveData(Dispatchers.IO) {
            emit(ProfileResult.Loading)

            try {
                // Получаем пользователя
                val user = userRepository.getUser(userId)
                if (user == null) {
                    emit(ProfileResult.NotFound)
                    return@liveData
                }

                // Получаем статистику
                val stats = calculateUserStats(userId)

                // Получаем последние отзывы с деталями
                val recentReviews = getRecentReviewsWithDetails(user.reviews)

                val profile = UserProfile(
                    user = user,
                    stats = stats,
                    recentReviews = recentReviews,
                    isCurrentUser = userId == currentUserId
                )

                emit(ProfileResult.Success(profile))

            } catch (e: Exception) {
                emit(ProfileResult.Error(e.message ?: "Ошибка загрузки профиля"))
            }
        }
    }

    private suspend fun calculateUserStats(userId: String): UserStats {
        // Получаем все вещи пользователя
        val userItems = itemRepository.getMyItems(userId)

        // Получаем все встречи пользователя
        // В реальном приложении здесь должен быть метод в TransferRepository
        // Для примера используем заглушку
        val userTransfers = emptyList<Transfer>()

        val completedTransfers = userTransfers.count { it.status == TransferStatus.COMPLETED }
        val cancelledTransfers = userTransfers.count { it.status == TransferStatus.CANCELLED }
        val noShowTransfers = userTransfers.count { it.status == TransferStatus.NO_SHOW }

        val totalTransfers = completedTransfers + cancelledTransfers + noShowTransfers
        val successRate = if (totalTransfers > 0) {
            (completedTransfers.toDouble() / totalTransfers) * 100
        } else 0.0

        // Получаем пользователя для рейтинга и отзывов
        val user = userRepository.getUser(userId)

        // Распределение оценок
        val ratingDistribution = user?.reviews?.groupBy { it.rating }
            ?.mapValues { it.value.size } ?: emptyMap()

        return UserStats(
            itemsPublished = userItems.size,
            itemsGiven = user?.itemsGiven ?: 0,
            itemsTaken = user?.itemsTaken ?: 0,
            completedTransfers = completedTransfers,
            successRate = successRate,
            averageRating = user?.rating ?: 0f,
            totalReviews = user?.reviews?.size ?: 0,
            registrationDate = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000, // Пример: 30 дней назад
            lastActive = System.currentTimeMillis() - 2 * 60 * 60 * 1000, // Пример: 2 часа назад
            ratingDistribution = ratingDistribution
        )
    }

    private suspend fun getRecentReviewsWithDetails(reviews: List<Review>): List<ReviewWithMeta> {
        return reviews.sortedByDescending { it.date }
            .take(10) // Последние 10 отзывов
            .mapNotNull { review ->
                // Получаем информацию о встрече
                val transfer = transferRepository.getTransfer(review.transferId)

                // Получаем информацию о вещи
                val item = transfer?.let { itemRepository.getItemById(it.itemId) }

                // Получаем информацию об авторе отзыва
                val reviewer = userRepository.getUser(review.fromUserId)

                ReviewWithMeta(
                    review = review,
                    reviewerName = reviewer?.name ?: "Пользователь",
                    reviewerAvatar = null, // Здесь был бы URL аватара
                    transferDate = transfer?.scheduledTime ?: review.date,
                    itemTitle = item?.title ?: "Неизвестная вещь",
                    itemId = item?.id ?: ""
                )
            }
    }

    // Получение краткой информации для карточки пользователя
    fun getUserCard(userId: String): LiveData<UserCard> {
        return liveData(Dispatchers.IO) {
            try {
                val user = userRepository.getUser(userId)
                if (user != null) {
                    emit(
                        UserCard(
                            id = user.id,
                            name = user.name,
                            rating = user.rating,
                            reviewsCount = user.reviews.size,
                            itemsGiven = user.itemsGiven,
                            itemsTaken = user.itemsTaken,
                            avatar = null
                        )
                    )
                } else {
                    emit(UserCard.EMPTY)
                }
            } catch (e: Exception) {
                emit(UserCard.EMPTY)
            }
        }
    }
}

data class UserCard(
    val id: String,
    val name: String,
    val rating: Float,
    val reviewsCount: Int,
    val itemsGiven: Int,
    val itemsTaken: Int,
    val avatar: String?
) {
    companion object {
        val EMPTY = UserCard("", "Пользователь", 0f, 0, 0, 0, null)
    }
}