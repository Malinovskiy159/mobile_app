package com.darim.domain.usecase.item

import com.darim.domain.model.Filters
import com.darim.domain.model.Item
import com.darim.domain.model.SortType
import com.darim.domain.repository.ItemRepository

class GetItemsUseCase(private val itemRepository: ItemRepository) {
    suspend fun execute(filters: Filters?, sort: SortType): List<Item> {
        val items = itemRepository.getItems(filters)
        return when (sort) {
            SortType.NEWEST_FIRST -> items.sortedByDescending { it.createdAt }
            // TODO: update logic Sort (we need user's location first)
            SortType.NEAREST_FIRST -> items
        }
    }
}