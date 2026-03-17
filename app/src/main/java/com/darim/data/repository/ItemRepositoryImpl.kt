// data/repository/ItemRepositoryImpl.kt
package com.darim.data.repository

import android.content.Context
import com.darim.domain.model.*
import com.darim.domain.repository.ItemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.*

class ItemRepositoryImpl(private val context: Context) : ItemRepository {

    private val filesDir: File
        get() = context.filesDir

    private val itemsFile: File by lazy {
        File(filesDir, "items.json")
    }

    // Используем lazy без обращения к items внутри инициализации
    private val items: MutableList<Item> by lazy {
        loadItemsFromJson().toMutableList()
    }

    override suspend fun getItems(filters: Filters?): List<Item> = withContext(Dispatchers.IO) {
        return@withContext items
    }

    override suspend fun getItemById(id: String): Item? = withContext(Dispatchers.IO) {
        return@withContext items.find { it.id == id }
    }

    override suspend fun getNearbyItems(
        lat: Double,
        lng: Double,
        radius: Double
    ): List<Item> = withContext(Dispatchers.IO) {
        return@withContext items.filter { item ->
            item.status == ItemStatus.AVAILABLE &&
                    calculateDistance(lat, lng, item.location.lat, item.location.lng) <= radius
        }.sortedBy { item ->
            calculateDistance(lat, lng, item.location.lat, item.location.lng)
        }
    }

    override suspend fun getMyItems(userId: String): List<Item> = withContext(Dispatchers.IO) {
        return@withContext items.filter { it.ownerId == userId }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun getMyBookings(userId: String): List<Item> = withContext(Dispatchers.IO) {
        return@withContext items.filter { it.bookedBy == userId }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun publishItem(item: Item): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val newItem = item.copy(
                id = if (item.id.isBlank()) UUID.randomUUID().toString() else item.id,
                status = ItemStatus.AVAILABLE,
                createdAt = System.currentTimeMillis(),
                views = 0
            )
            items.add(0, newItem)
            saveItemsToJson()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateItemStatus(
        itemId: String,
        status: ItemStatus,
        userId: String?
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val index = items.indexOfFirst { it.id == itemId }
            if (index >= 0) {
                val currentItem = items[index]
                val updated = currentItem.copy(
                    status = status,
                    bookedBy = when (status) {
                        ItemStatus.BOOKED -> userId ?: currentItem.bookedBy
                        ItemStatus.AVAILABLE -> null
                        else -> currentItem.bookedBy
                    }
                )
                items[index] = updated
                saveItemsToJson()
                Result.success(true)
            } else {
                Result.success(false)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteItem(itemId: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val removed = items.removeAll { it.id == itemId }
            if (removed) {
                saveItemsToJson()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Item not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============== Работа с JSON файлами ==============

    /**
     * Загружает вещи из JSON файла
     */
    private fun loadItemsFromJson(): List<Item> {
        return try {
            if (!itemsFile.exists()) {
                // Если файл не существует, создаем начальные данные
                return createInitialData()
            }

            val jsonString = itemsFile.readText()
            if (jsonString.isBlank()) {
                return createInitialData()
            }

            val jsonArray = JSONArray(jsonString)
            val itemsList = mutableListOf<Item>()

            for (i in 0 until jsonArray.length()) {
                try {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val item = jsonToItem(jsonObject)
                    itemsList.add(item)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (itemsList.isEmpty()) {
                return createInitialData()
            }

            itemsList
        } catch (e: Exception) {
            e.printStackTrace()
            createInitialData()
        }
    }

    /**
     * Создает начальные тестовые данные
     * ВАЖНО: Не использует items внутри этого метода!
     */
    private fun createInitialData(): List<Item> {
        val initialItems = listOf(
            Item(
                id = "1",
                title = "Кухонный комбайн Braun",
                category = "Техника",
                description = "Почти новый, использовался пару раз. Все насадки в комплекте. Самовывоз от метро Охотный ряд.",
                photos = listOf("photos/1_20240315_143025.jpg"),
                location = Location(55.7558, 37.6176, "м. Охотный ряд"),
                ownerId = "user1",
                ownerName = "Иван Петров",
                ownerPhone = "+7 (999) 123-45-67",
                status = ItemStatus.AVAILABLE,
                createdAt = System.currentTimeMillis() - 86400000,
                bookedBy = null,
                views = 15
            ),
            Item(
                id = "2",
                title = "Детские книжки (20 штук)",
                category = "Книги",
                description = "Для детей 3-5 лет. Сказки, раскраски, обучающие.",
                photos = listOf("photos/2_20240315_143127.jpg"),
                location = Location(55.7512, 37.6289, "м. Китай-город"),
                ownerId = "user2",
                ownerName = "Мария Иванова",
                ownerPhone = "+7 (999) 234-56-78",
                status = ItemStatus.AVAILABLE,
                createdAt = System.currentTimeMillis() - 172800000,
                bookedBy = null,
                views = 23
            ),
            Item(
                id = "3",
                title = "Зимнее пальто, размер 46",
                category = "Одежда",
                description = "Носила один сезон, состояние отличное. Торг уместен.",
                photos = listOf("photos/3_20240315_143230.jpg"),
                location = Location(55.7602, 37.6195, "м. Лубянка"),
                ownerId = "user1",
                ownerName = "Иван Петров",
                ownerPhone = "+7 (999) 123-45-67",
                status = ItemStatus.AVAILABLE,
                createdAt = System.currentTimeMillis() - 43200000,
                bookedBy = null,
                views = 8
            ),
            Item(
                id = "4",
                title = "Настольная лампа",
                category = "Освещение",
                description = "С регулировкой яркости, светодиодная.",
                photos = listOf("photos/4_20240315_143345.jpg"),
                location = Location(55.7587, 37.6153, "м. Театральная"),
                ownerId = "user3",
                ownerName = "Петр Сидоров",
                ownerPhone = "+7 (999) 345-67-89",
                status = ItemStatus.BOOKED,
                createdAt = System.currentTimeMillis() - 345600000,
                bookedBy = "user4",
                views = 42
            ),
            Item(
                id = "5",
                title = "Велосипед детский",
                category = "Детские товары",
                description = "Для ребенка 3-5 лет, колеса 12 дюймов.",
                photos = listOf("photos/5_20240315_143456.jpg"),
                location = Location(55.7492, 37.6215, "м. Лубянка"),
                ownerId = "user4",
                ownerName = "Анна Смирнова",
                ownerPhone = "+7 (999) 456-78-90",
                status = ItemStatus.AVAILABLE,
                createdAt = System.currentTimeMillis() - 259200000,
                bookedBy = null,
                views = 31
            )
        )

        // Сохраняем начальные данные в файл
        saveInitialItemsToJson(initialItems)

        return initialItems
    }

    /**
     * Сохраняет начальные данные в JSON файл
     */
    private fun saveInitialItemsToJson(items: List<Item>) {
        try {
            val jsonArray = JSONArray()
            items.forEach { item ->
                jsonArray.put(itemToJson(item))
            }
            itemsFile.writeText(jsonArray.toString(2))
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Сохраняет текущий список вещей в JSON файл
     */
    private fun saveItemsToJson() {
        try {
            val jsonArray = JSONArray()
            items.forEach { item ->
                jsonArray.put(itemToJson(item))
            }
            itemsFile.writeText(jsonArray.toString(2))
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Преобразует объект Item в JSON
     */
    private fun itemToJson(item: Item): JSONObject {
        return JSONObject().apply {
            put("id", item.id)
            put("title", item.title)
            put("category", item.category)
            put("description", item.description)

            val photosArray = JSONArray()
            item.photos.forEach { photo ->
                photosArray.put(photo)
            }
            put("photos", photosArray)

            put("location", JSONObject().apply {
                put("lat", item.location.lat)
                put("lng", item.location.lng)
                put("address", item.location.address)
            })
            put("ownerId", item.ownerId)
            put("ownerName", item.ownerName ?: "")
            put("ownerPhone", item.ownerPhone ?: "")
            put("status", item.status.name)
            put("createdAt", item.createdAt)
            put("bookedBy", item.bookedBy ?: JSONObject.NULL)
            put("views", item.views)
        }
    }

    /**
     * Преобразует JSON в объект Item
     */
    private fun jsonToItem(json: JSONObject): Item {
        val locationJson = json.getJSONObject("location")

        val photosArray = json.getJSONArray("photos")
        val photos = mutableListOf<String>()
        for (i in 0 until photosArray.length()) {
            photos.add(photosArray.getString(i))
        }

        return Item(
            id = json.getString("id"),
            title = json.getString("title"),
            category = json.getString("category"),
            description = json.getString("description"),
            photos = photos,
            location = Location(
                lat = locationJson.getDouble("lat"),
                lng = locationJson.getDouble("lng"),
                address = locationJson.getString("address")
            ),
            ownerId = json.getString("ownerId"),
            ownerName = if (json.has("ownerName")) json.getString("ownerName") else null,
            ownerPhone = if (json.has("ownerPhone")) json.getString("ownerPhone") else null,
            status = ItemStatus.valueOf(json.getString("status")),
            createdAt = json.getLong("createdAt"),
            bookedBy = if (json.isNull("bookedBy")) null else json.getString("bookedBy"),
            views = if (json.has("views")) json.getInt("views") else 0
        )
    }

    /**
     * Рассчитывает расстояние между двумя точками по формуле гаверсинусов
     */
    private fun calculateDistance(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Double {
        val R = 6371 // Радиус Земли в км
        val latDistance = Math.toRadians(lat1 - lat2)
        val lonDistance = Math.toRadians(lng1 - lng2)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}