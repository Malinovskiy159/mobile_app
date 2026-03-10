package com.darim.domain.usecase.item

import com.darim.domain.model.Item
import com.darim.domain.repository.ItemRepository

class GetNearbyItemsUseCase(private val itemRepository: ItemRepository) {
    suspend fun execute(lat: Double, lng: Double, radius: Double): List<Item> {
        return itemRepository.getNearbyItems(lat, lng, radius)
    }
}