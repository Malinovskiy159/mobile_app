package com.darim.domain.usecase.item

import com.darim.domain.model.Item
import com.darim.domain.repository.ItemRepository

class PublishItemUseCase(private val itemRepository: ItemRepository) {

    data class Request(val item: Item, val photos: List<ByteArray>)

    suspend fun execute(request: Request): Result<String> {
        val result = itemRepository.publishItem(request.item)
        return if (result.isSuccess) {
            Result.success(request.item.id)
        } else {
            Result.failure(Exception("Не удалось опубликовать вещь"))
        }
    }
}