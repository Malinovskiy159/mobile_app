// domain/usecase/item/GetItemsUseCase.kt
package com.darim.domain.usecase.item

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.darim.domain.model.Filters
import com.darim.domain.model.Item
import com.darim.domain.model.SortType
import com.darim.domain.repository.ItemRepository
import com.darim.ui.utils.SessionManager
import kotlinx.coroutines.Dispatchers

class GetItemsUseCase(private val itemRepository: ItemRepository) {

    fun getAvailableItems(): LiveData<List<Item>> {
        return liveData(Dispatchers.IO) {
            val currentUserId = SessionManager.getCurrentUserId()
            val items = itemRepository.getAvailableItems(currentUserId)
            emit(items)
        }
    }

    fun getItemsByCategory(category: String): LiveData<List<Item>> {
        return liveData(Dispatchers.IO) {
            val currentUserId = SessionManager.getCurrentUserId()
            val items = itemRepository.getItemsByCategory(category, currentUserId)
            emit(items)
        }
    }

    fun getMyItems(): LiveData<List<Item>> {
        return liveData(Dispatchers.IO) {
            val userId = SessionManager.getCurrentUserId()
            if (userId != null) {
                val items = itemRepository.getMyItems(userId)
                emit(items)
            } else {
                emit(emptyList())
            }
        }
    }

    fun getMyBookings(): LiveData<List<Item>> {
        return liveData(Dispatchers.IO) {
            val userId = SessionManager.getCurrentUserId()
            if (userId != null) {
                val items = itemRepository.getMyBookings(userId)
                emit(items)
            } else {
                emit(emptyList())
            }
        }
    }

    fun execute(filters: Filters? = null, sort: SortType = SortType.NEWEST): LiveData<List<Item>> {
        return liveData(Dispatchers.IO) {
            val items = itemRepository.getItems(filters)
            val sortedItems = when (sort) {
                SortType.NEWEST -> items.sortedByDescending { it.createdAt }
                SortType.OLDEST -> items.sortedBy { it.createdAt }
                SortType.POPULAR -> items.sortedByDescending { it.views }
                SortType.DISTANCE -> {
                    // Сортировка по расстоянию будет добавлена позже
                    items
                }
            }
            emit(sortedItems)
        }
    }
}