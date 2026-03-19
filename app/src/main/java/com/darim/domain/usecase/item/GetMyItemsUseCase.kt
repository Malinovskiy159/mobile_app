// domain/usecase/item/GetMyItemsUseCase.kt
package com.darim.domain.usecase.item

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.darim.domain.model.Item
import com.darim.domain.model.ItemStatus
import com.darim.domain.repository.ItemRepository
import kotlinx.coroutines.Dispatchers

class GetMyItemsUseCase(
    private val itemRepository: ItemRepository
) {

    /**
     * Результат выполнения запроса моих вещей
     */
    sealed class MyItemsResult {
        data class Success(val items: List<Item>) : MyItemsResult()
        data class Error(val message: String) : MyItemsResult()
        object Loading : MyItemsResult()
        object Empty : MyItemsResult()
    }

    /**
     * Фильтр для моих вещей
     */
    sealed class ItemsFilter {
        object All : ItemsFilter()
        object Available : ItemsFilter()
        object Booked : ItemsFilter()
        object Completed : ItemsFilter()
        object Cancelled : ItemsFilter()
        data class ByStatus(val status: ItemStatus) : ItemsFilter()
    }

    /**
     * Статистика по вещам пользователя
     */
    data class UserItemsStats(
        val total: Int,
        val available: Int,
        val booked: Int,
        val completed: Int,
        val cancelled: Int
    ) {
        val completionRate: Float
            get() = if (total > 0) (completed.toFloat() / total.toFloat()) * 100 else 0f

        val bookingRate: Float
            get() = if (total > 0) (booked.toFloat() / total.toFloat()) * 100 else 0f

        fun getSummary(): String = "Всего: $total | ✅ $available | 📌 $booked | 🎉 $completed | ❌ $cancelled"
    }

    /**
     * Получить мои вещи с фильтром
     */
    fun execute(
        userId: String,
        filter: ItemsFilter = ItemsFilter.All
    ): LiveData<MyItemsResult> {
        return liveData(Dispatchers.IO) {
            emit(MyItemsResult.Loading)

            try {
                val myItems = itemRepository.getMyItems(userId)

                val filteredItems = when (filter) {
                    is ItemsFilter.All -> myItems
                    is ItemsFilter.Available -> myItems.filter { it.status == ItemStatus.AVAILABLE }
                    is ItemsFilter.Booked -> myItems.filter { it.status == ItemStatus.BOOKED }
                    is ItemsFilter.Completed -> myItems.filter { it.status == ItemStatus.COMPLETED }
                    is ItemsFilter.Cancelled -> myItems.filter { it.status == ItemStatus.CANCELLED }
                    is ItemsFilter.ByStatus -> myItems.filter { it.status == filter.status }
                }

                if (filteredItems.isEmpty()) {
                    emit(MyItemsResult.Empty)
                } else {
                    // Сортируем по дате создания (сначала новые)
                    val sortedItems = filteredItems.sortedByDescending { it.createdAt }
                    emit(MyItemsResult.Success(sortedItems))
                }
            } catch (e: Exception) {
                emit(MyItemsResult.Error(e.message ?: "Ошибка загрузки моих вещей"))
            }
        }
    }

    /**
     * Получить мои вещи, сгруппированные по статусу
     */
    fun executeGrouped(userId: String): LiveData<Map<ItemStatus, List<Item>>> {
        return liveData(Dispatchers.IO) {
            try {
                val myItems = itemRepository.getMyItems(userId)
                val groupedItems = myItems.groupBy { it.status }
                emit(groupedItems)
            } catch (e: Exception) {
                emit(emptyMap())
            }
        }
    }

    /**
     * Получить статистику по моим вещам
     */
    fun getStats(userId: String): LiveData<UserItemsStats> {
        return liveData(Dispatchers.IO) {
            try {
                val myItems = itemRepository.getMyItems(userId)

                val stats = UserItemsStats(
                    total = myItems.size,
                    available = myItems.count { it.status == ItemStatus.AVAILABLE },
                    booked = myItems.count { it.status == ItemStatus.BOOKED },
                    completed = myItems.count { it.status == ItemStatus.COMPLETED },
                    cancelled = myItems.count { it.status == ItemStatus.CANCELLED }
                )
                emit(stats)
            } catch (e: Exception) {
                emit(UserItemsStats(0, 0, 0, 0, 0))
            }
        }
    }

    /**
     * Получить только доступные вещи
     */
    fun getAvailableItems(userId: String): LiveData<List<Item>> {
        return liveData(Dispatchers.IO) {
            try {
                val items = itemRepository.getMyItems(userId)
                    .filter { it.status == ItemStatus.AVAILABLE }
                    .sortedByDescending { it.createdAt }
                emit(items)
            } catch (e: Exception) {
                emit(emptyList())
            }
        }
    }

    /**
     * Получить только забронированные вещи
     */
    fun getBookedItems(userId: String): LiveData<List<Item>> {
        return liveData(Dispatchers.IO) {
            try {
                val items = itemRepository.getMyItems(userId)
                    .filter { it.status == ItemStatus.BOOKED }
                    .sortedByDescending { it.createdAt }
                emit(items)
            } catch (e: Exception) {
                emit(emptyList())
            }
        }
    }

    /**
     * Получить только завершенные вещи
     */
    fun getCompletedItems(userId: String): LiveData<List<Item>> {
        return liveData(Dispatchers.IO) {
            try {
                val items = itemRepository.getMyItems(userId)
                    .filter { it.status == ItemStatus.COMPLETED }
                    .sortedByDescending { it.createdAt }
                emit(items)
            } catch (e: Exception) {
                emit(emptyList())
            }
        }
    }

    /**
     * Получить только отмененные вещи
     */
    fun getCancelledItems(userId: String): LiveData<List<Item>> {
        return liveData(Dispatchers.IO) {
            try {
                val items = itemRepository.getMyItems(userId)
                    .filter { it.status == ItemStatus.CANCELLED }
                    .sortedByDescending { it.createdAt }
                emit(items)
            } catch (e: Exception) {
                emit(emptyList())
            }
        }
    }

    /**
     * Проверить, есть ли у пользователя вещи
     */
    fun hasItems(userId: String): LiveData<Boolean> {
        return liveData(Dispatchers.IO) {
            try {
                val items = itemRepository.getMyItems(userId)
                emit(items.isNotEmpty())
            } catch (e: Exception) {
                emit(false)
            }
        }
    }

    /**
     * Получить количество вещей по статусу
     */
    fun getCountByStatus(userId: String, status: ItemStatus): LiveData<Int> {
        return liveData(Dispatchers.IO) {
            try {
                val count = itemRepository.getMyItems(userId)
                    .count { it.status == status }
                emit(count)
            } catch (e: Exception) {
                emit(0)
            }
        }
    }

    /**
     * Получить вещи, отсортированные по популярности (просмотрам)
     */
    fun getPopularItems(userId: String, limit: Int = 10): LiveData<List<Item>> {
        return liveData(Dispatchers.IO) {
            try {
                val items = itemRepository.getMyItems(userId)
                    .sortedByDescending { it.views }
                    .take(limit)
                emit(items)
            } catch (e: Exception) {
                emit(emptyList())
            }
        }
    }

    /**
     * Получить вещи, отсортированные по дате (сначала новые)
     */
    fun getRecentItems(userId: String, limit: Int = 10): LiveData<List<Item>> {
        return liveData(Dispatchers.IO) {
            try {
                val items = itemRepository.getMyItems(userId)
                    .sortedByDescending { it.createdAt }
                    .take(limit)
                emit(items)
            } catch (e: Exception) {
                emit(emptyList())
            }
        }
    }

    /**
     * Получить вещи, которые скоро истекут (старые)
     */
    fun getExpiringItems(userId: String, daysThreshold: Int = 30): LiveData<List<Item>> {
        return liveData(Dispatchers.IO) {
            try {
                val thresholdTime = System.currentTimeMillis() - (daysThreshold * 24 * 60 * 60 * 1000L)
                val items = itemRepository.getMyItems(userId)
                    .filter { it.createdAt < thresholdTime && it.status == ItemStatus.AVAILABLE }
                    .sortedBy { it.createdAt }
                emit(items)
            } catch (e: Exception) {
                emit(emptyList())
            }
        }
    }

    /**
     * Поиск по моим вещам
     */
    fun searchMyItems(userId: String, query: String): LiveData<List<Item>> {
        return liveData(Dispatchers.IO) {
            try {
                val items = itemRepository.getMyItems(userId)
                    .filter {
                        it.title.contains(query, ignoreCase = true) ||
                                it.description.contains(query, ignoreCase = true)
                    }
                    .sortedByDescending { it.createdAt }
                emit(items)
            } catch (e: Exception) {
                emit(emptyList())
            }
        }
    }
}