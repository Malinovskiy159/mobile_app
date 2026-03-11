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

    sealed class MyItemsResult {
        data class Success(val items: List<Item>) : MyItemsResult()
        data class Error(val message: String) : MyItemsResult()
        object Loading : MyItemsResult()
        object Empty : MyItemsResult()
    }

    sealed class ItemsFilter {
        object All : ItemsFilter()
        object Available : ItemsFilter()
        object Booked : ItemsFilter()
        object Completed : ItemsFilter()
        data class ByStatus(val status: ItemStatus) : ItemsFilter()
    }

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
}

data class UserItemsStats(
    val total: Int,
    val available: Int,
    val booked: Int,
    val completed: Int,
    val cancelled: Int
)