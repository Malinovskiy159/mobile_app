// domain/usecase/item/GetItemsUseCase.kt
package com.darim.domain.usecase.item

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.darim.domain.model.Filters
import com.darim.domain.model.Item
import com.darim.domain.model.SortType
import com.darim.domain.repository.ItemRepository
import kotlinx.coroutines.Dispatchers

class GetItemsUseCase(private val itemRepository: ItemRepository) {

    fun execute(filters: Filters? = null, sort: SortType = SortType.DEFAULT): LiveData<List<Item>> {
        return liveData(Dispatchers.IO) {
            val items = itemRepository.getItems(filters)
            val sortedItems = when (sort) {
                SortType.NEWEST -> items.sortedByDescending { it.createdAt }
                SortType.OLDEST -> items.sortedBy { it.createdAt }
                SortType.DEFAULT -> items
            }
            emit(sortedItems)
        }
    }
}

