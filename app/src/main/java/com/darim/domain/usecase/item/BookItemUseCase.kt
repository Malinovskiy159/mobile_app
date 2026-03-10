package com.darim.domain.usecase.item

import com.darim.domain.model.Item
import com.darim.domain.model.ItemStatus
import com.darim.domain.repository.ItemRepository

class BookItemUseCase(private val itemRepository: ItemRepository) {
    suspend fun execute(itemId: String, userId: String): Result<Boolean> {
        val item = itemRepository.getItemById(itemId)
            ?: return Result.failure(Exception("Вещь не найдена"))

        if (!validateItemStatus(item)) {
            return Result.failure(Exception("Кто-то уже спешит за этой вещью. Посмотрите другие рядом!"))
        }

        return itemRepository.updateItemStatus(itemId, ItemStatus.BOOKED, userId)
    }

    private fun validateItemStatus(item: Item): Boolean {
        return item.isAvailable()
    }
}