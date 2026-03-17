package com.darim.data.repository

import com.darim.domain.model.Item
import com.darim.domain.model.ItemStatus
import com.darim.domain.model.Location
import com.darim.domain.repository.ItemRepository

class ItemRepositoryImpl : ItemRepository {

    private val allItems = mutableListOf(
        Item(
            id = "1",
            title = "Старый монитор",
            category = "Электроника",
            description = "Рабочий монитор, 19 дюймов",
            location = Location(55.75, 37.61, "Москва"), // Координаты для примера
            ownerId = "user_1",
            phone = "+79991234567",
            photos = listOf("https://example.com/photo1.jpg"),
            status = ItemStatus.AVAILABLE,
            createdAt = System.currentTimeMillis()
        ),
        Item(
            id = "2",
            title = "Стул офисный",
            category = "Мебель",
            description = "Немного скрипит, но целый",
            location = Location(55.76, 37.62, "Москва"),
            ownerId = "user_2",
            phone = "+79997654321",
            photos = listOf(),
            status = ItemStatus.AVAILABLE,
            createdAt = System.currentTimeMillis()
        )
    )

    override suspend fun getItems(category: String?): List<Item> {
        return if (category == null || category == "Все") allItems
        else allItems.filter { it.category == category }
    }

    override suspend fun getItemById(id: String): Item? {
        return allItems.find { it.id == id }
    }

    // ИСПРАВЛЕНИЕ: Метод теперь возвращает Result<Boolean> и принимает ItemStatus
    override suspend fun updateItemStatus(itemId: String, status: ItemStatus, userId: String?): Result<Boolean> {
        val index = allItems.indexOfFirst { it.id == itemId }
        return if (index != -1) {
            val currentItem = allItems[index]
            allItems[index] = currentItem.copy(status = status, bookedBy = userId)
            Result.success(true)
        } else {
            Result.failure(Exception("Item not found"))
        }
    }
 override suspend fun getNearbyItems(lat: Double, lng: Double, radius: Double): List<Item> {
        return allItems
    }

    override suspend fun getMyItems(userId: String): List<Item> {
        return allItems.filter { it.ownerId == userId }
    }

    override suspend fun getMyBookings(userId: String): List<Item> {
        return allItems.filter { it.bookedBy == userId }
    }

    override suspend fun publishItem(item: Item): Result<Unit> {
        allItems.add(item)
        return Result.success(Unit)
    }

    override suspend fun deleteItem(itemId: String): Result<Unit> {
        allItems.removeAll { it.id == itemId }
        return Result.success(Unit)
    }
}