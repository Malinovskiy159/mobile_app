// domain/usecase/item/GetItemDetailsUseCase.kt
package com.darim.domain.usecase.item

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.darim.domain.model.Item
import com.darim.domain.model.Review
import com.darim.domain.model.User
import com.darim.domain.repository.ItemRepository
import com.darim.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers


class GetItemDetailsUseCase(
    private val itemRepository: ItemRepository,
    private val userRepository: UserRepository
) {

    sealed class DetailResult {
        data class Success(
            val item: Item,
            val owner: User?,
            val canBook: Boolean,
            val isOwner: Boolean,
            val similarItems: List<Item> = emptyList()
        ) : DetailResult()

        data class Error(val message: String, val code: ErrorCode) : DetailResult()
        object Loading : DetailResult()
        object NotFound : DetailResult()

        enum class ErrorCode {
            ITEM_NOT_FOUND,
            USER_NOT_FOUND,
            NETWORK_ERROR,
            UNKNOWN_ERROR
        }
    }

    data class ItemDetails(
        val item: Item,
        val owner: User?,
        val ownerReviews: List<Review>,
        val ownerRating: Float,
        val canBook: Boolean,
        val isOwner: Boolean
    )

    /**
     * Получает детальную информацию о вещи
     * @param itemId ID вещи
     * @param currentUserId ID текущего пользователя (для проверки прав)
     */
    fun execute(itemId: String, currentUserId: String? = null): LiveData<DetailResult> {
        return liveData(Dispatchers.IO) {
            emit(DetailResult.Loading)

            try {
                // 1. Получаем вещь по ID
                val item = itemRepository.getItemById(itemId)
                if (item == null) {
                    emit(DetailResult.NotFound)
                    return@liveData
                }

                // 2. Увеличиваем счетчик просмотров
                //itemRepository.incrementItemViews(itemId)

                // 3. Получаем информацию о владельце
                val owner = userRepository.getUser(item.ownerId)

                // 4. Проверяем, может ли текущий пользователь забронировать
                val canBook = when {
                    currentUserId == null -> false
                    currentUserId == item.ownerId -> false
                    item.status != com.darim.domain.model.ItemStatus.AVAILABLE -> false
                    else -> true
                }

                // 5. Проверяем, является ли текущий пользователь владельцем
                val isOwner = currentUserId == item.ownerId

                // 6. Получаем похожие вещи (из той же категории)
                //val similarItems = getSimilarItems(item)

                emit(DetailResult.Success(
                    item = item,
                    owner = owner,
                    canBook = canBook,
                    isOwner = isOwner
                ))

            } catch (e: Exception) {
                e.printStackTrace()
                emit(DetailResult.Error(
                    e.message ?: "Ошибка загрузки деталей",
                    DetailResult.ErrorCode.UNKNOWN_ERROR
                ))
            }
        }
    }

    /**
     * Получает детальную информацию с расширенными данными
     */
    fun executeWithDetails(itemId: String, currentUserId: String? = null): LiveData<ItemDetails?> {
        return liveData(Dispatchers.IO) {
            try {
                val item = itemRepository.getItemById(itemId) ?: run {
                    emit(null)
                    return@liveData
                }

                val owner = userRepository.getUser(item.ownerId)
                val ownerReviews = owner?.reviews ?: emptyList()
                val ownerRating = owner?.rating ?: 0f

                val canBook = when {
                    currentUserId == null -> false
                    currentUserId == item.ownerId -> false
                    item.status != com.darim.domain.model.ItemStatus.AVAILABLE -> false
                    else -> true
                }

                val isOwner = currentUserId == item.ownerId
                //val similarItems = getSimilarItems(item)

                emit(ItemDetails(
                    item = item,
                    owner = owner,
                    ownerReviews = ownerReviews,
                    ownerRating = ownerRating,
                    canBook = canBook,
                    isOwner = isOwner
                ))

            } catch (e: Exception) {
                e.printStackTrace()
                emit(null)
            }
        }
    }

    /**
     * Получает похожие вещи из той же категории
     */
    /*private suspend fun getSimilarItems(item: Item, limit: Int = 5): List<Item> {
        return try {
            val allItems = itemRepository.getItemsByCategory(item.category)
            allItems
                .filter { it.id != item.id && it.status == com.darim.domain.model.ItemStatus.AVAILABLE }
                .sortedByDescending { it.createdAt }
                .take(limit)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }*/

    /**
     * Проверяет существование вещи
     */
    fun checkItemExists(itemId: String): LiveData<Boolean> {
        return liveData(Dispatchers.IO) {
            val item = itemRepository.getItemById(itemId)
            emit(item != null)
        }
    }

    /**
     * Получает статистику по вещи
     */
    fun getItemStats(itemId: String): LiveData<ItemStats> {
        return liveData(Dispatchers.IO) {
            val item = itemRepository.getItemById(itemId)
            if (item != null) {
                emit(ItemStats(
                    views = item.views,
                    createdAt = item.createdAt,
                    timesBooked = getTimesBooked(itemId) // нужно добавить в репозиторий
                ))
            } else {
                emit(ItemStats(0, 0, 0))
            }
        }
    }

    private suspend fun getTimesBooked(itemId: String): Int {
        // TODO: добавить в репозиторий подсчет количества бронирований
        return 0
    }

    data class ItemStats(
        val views: Int,
        val createdAt: Long,
        val timesBooked: Int
    )
}