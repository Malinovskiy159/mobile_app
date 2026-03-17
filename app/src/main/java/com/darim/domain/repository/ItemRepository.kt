package com.darim.domain.repository

import com.darim.domain.model.Filters
import com.darim.domain.model.Item
import com.darim.domain.model.ItemStatus

interface ItemRepository {
    suspend fun getItems(filters: String? = null): List<Item>

    suspend fun getItemById(id: String): Item?

    suspend fun getNearbyItems(lat: Double, lng: Double, radius: Double): List<Item>

    suspend fun getMyItems(userId: String): List<Item>

    suspend fun getMyBookings(userId: String): List<Item>

    suspend fun publishItem(item: Item): Result<Unit>

    suspend fun updateItemStatus(itemId: String, status: ItemStatus, userId: String? = null): Result<Boolean>

    suspend fun deleteItem(itemId: String): Result<Unit>
}