// domain/repository/ItemRepository.kt
package com.darim.domain.repository

import com.darim.domain.model.*
import com.darim.domain.usecase.item.GetMyItemsUseCase.UserItemsStats

interface ItemRepository {

    suspend fun getItems(filters: Filters? = null): List<Item>
    suspend fun getAvailableItems(currentUserId: String? = null): List<Item>
    suspend fun getItemById(id: String): Item?
    suspend fun getNearbyItems(lat: Double, lng: Double, radius: Double, currentUserId: String? = null): List<Item>
    suspend fun getMyItems(userId: String): List<Item>
    suspend fun getMyBookings(userId: String): List<Item>

    suspend fun publishItem(item: Item): Result<Unit>
    suspend fun updateItemStatus(itemId: String, status: ItemStatus, userId: String? = null): Result<Boolean>
    suspend fun deleteItem(itemId: String): Result<Unit>
    suspend fun incrementItemViews(itemId: String): Result<Unit>

    suspend fun searchItems(query: String, filters: Filters? = null): List<Item>
    suspend fun getItemsByCategory(category: String, currentUserId: String? = null): List<Item>
    suspend fun getPopularItems(limit: Int = 10, currentUserId: String? = null): List<Item>
    suspend fun getRecentItems(limit: Int = 10, currentUserId: String? = null): List<Item>

    suspend fun getUserStats(userId: String): UserItemsStats
    suspend fun getCategoriesWithCount(currentUserId: String? = null): Map<String, Int>
}