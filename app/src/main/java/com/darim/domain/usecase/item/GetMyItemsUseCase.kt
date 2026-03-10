package com.darim.domain.usecase.item

import com.darim.domain.model.Item
import com.darim.domain.repository.ItemRepository

class GetMyItemsUseCase(private val itemRepository: ItemRepository) {
    suspend fun execute(userId: String): List<Item> {
        return itemRepository.getMyItems(userId)
    }
}