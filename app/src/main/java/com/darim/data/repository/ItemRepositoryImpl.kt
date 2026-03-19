// data/repository/ItemRepositoryImpl.kt
package com.darim.data.repository

import android.content.Context
import com.darim.domain.model.*
import com.darim.domain.repository.ItemRepository
import com.darim.domain.usecase.item.GetMyItemsUseCase.UserItemsStats
import com.darim.ui.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.*
import kotlin.random.Random

class ItemRepositoryImpl(private val context: Context) : ItemRepository {

    private val filesDir: File
        get() = context.filesDir

    private val itemsFile: File by lazy {
        File(filesDir, "items.json")
    }

    private val items: MutableList<Item> by lazy {
        loadItemsFromJson().toMutableList()
    }

    private val placeholderImages = listOf(
        "ic_placeholder_1",
        "ic_placeholder_2",
        "ic_placeholder_3",
        "ic_placeholder_4",
        "ic_placeholder_5"
    )

    override suspend fun getItems(filters: Filters?): List<Item> = withContext(Dispatchers.IO) {
        var filteredList = items

        filters?.let {
            it.category?.let { category ->
                filteredList = filteredList.filter { item -> item.category == category } as MutableList<Item>
            }

            it.status?.let { status ->
                filteredList = filteredList.filter { item ->
                    item.status.name.equals(status, ignoreCase = true)
                } as MutableList<Item>
            }

            it.ownerId?.let { ownerId ->
                filteredList = filteredList.filter { item -> item.ownerId == ownerId } as MutableList<Item>
            }

            it.query?.let { query ->
                val lowerQuery = query.lowercase()
                filteredList = filteredList.filter { item ->
                    item.title.lowercase().contains(lowerQuery) ||
                            item.description.lowercase().contains(lowerQuery)
                } as MutableList<Item>
            }

            it.excludeOwnerId?.let { excludeOwnerId ->
                filteredList = filteredList.filter { item -> item.ownerId != excludeOwnerId } as MutableList<Item>
            }

            it.fromDate?.let { fromDate ->
                filteredList = filteredList.filter { item -> item.createdAt >= fromDate } as MutableList<Item>
            }

            it.toDate?.let { toDate ->
                filteredList = filteredList.filter { item -> item.createdAt <= toDate } as MutableList<Item>
            }
        }

        filteredList = when (filters?.sortBy) {
            SortType.NEWEST -> filteredList.sortedByDescending { it.createdAt }
            SortType.OLDEST -> filteredList.sortedBy { it.createdAt }
            else -> filteredList
        } as MutableList<Item>

        return@withContext filteredList
    }

    override suspend fun getAvailableItems(currentUserId: String?): List<Item> = withContext(Dispatchers.IO) {
        return@withContext items.filter { item ->
            item.status == ItemStatus.AVAILABLE &&
                    (currentUserId == null || item.ownerId != currentUserId)
        }.sortedByDescending { it.createdAt }
    }

    override suspend fun getItemById(id: String): Item? = withContext(Dispatchers.IO) {
        return@withContext items.find { it.id == id }
    }

    override suspend fun getNearbyItems(
        lat: Double,
        lng: Double,
        radius: Double,
        currentUserId: String?
    ): List<Item> = withContext(Dispatchers.IO) {
        return@withContext items.filter { item ->
            item.status == ItemStatus.AVAILABLE &&
                    (currentUserId == null || item.ownerId != currentUserId) &&
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
        // Получаем ID забронированных вещей из SessionManager
        val bookedItemIds = SessionManager.getBookedItems()
        return@withContext items.filter { it.id in bookedItemIds }
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

            // Сохраняем ID вещи в SessionManager для владельца
            SessionManager.saveMyItem(newItem.id)

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
                        ItemStatus.BOOKED -> {
                            userId?.let { SessionManager.saveBookedItem(itemId) }
                            userId ?: currentItem.bookedBy
                        }
                        ItemStatus.AVAILABLE -> {
                            userId?.let { SessionManager.removeBookedItem(itemId) }
                            null
                        }
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
                SessionManager.removeMyItem(itemId)
                SessionManager.removeBookedItem(itemId)
                saveItemsToJson()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Item not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun incrementItemViews(itemId: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val index = items.indexOfFirst { it.id == itemId }
            if (index >= 0) {
                val currentItem = items[index]
                val updated = currentItem.copy(
                    views = currentItem.views + 1
                )
                items[index] = updated
                saveItemsToJson()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Item not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchItems(query: String, filters: Filters?): List<Item> = withContext(Dispatchers.IO) {
        val searchFilters = (filters ?: Filters()).copy(query = query)
        return@withContext getItems(searchFilters)
    }

    override suspend fun getItemsByCategory(category: String, currentUserId: String?): List<Item> = withContext(Dispatchers.IO) {
        return@withContext items.filter {
            it.category.equals(category, ignoreCase = true) &&
                    it.status == ItemStatus.AVAILABLE &&
                    (currentUserId == null || it.ownerId != currentUserId)
        }.sortedByDescending { it.createdAt }
    }

    override suspend fun getPopularItems(limit: Int, currentUserId: String?): List<Item> = withContext(Dispatchers.IO) {
        return@withContext items.filter {
            it.status == ItemStatus.AVAILABLE &&
                    (currentUserId == null || it.ownerId != currentUserId)
        }.sortedByDescending { it.views }
            .take(limit)
    }

    override suspend fun getRecentItems(limit: Int, currentUserId: String?): List<Item> = withContext(Dispatchers.IO) {
        return@withContext items.filter {
            it.status == ItemStatus.AVAILABLE &&
                    (currentUserId == null || it.ownerId != currentUserId)
        }.sortedByDescending { it.createdAt }
            .take(limit)
    }

    override suspend fun getUserStats(userId: String): UserItemsStats = withContext(Dispatchers.IO) {
        val userItems = items.filter { it.ownerId == userId }
        val userBookings = items.filter { it.id in SessionManager.getBookedItems() }

        return@withContext UserItemsStats(
            total = userItems.size,
            available = userItems.count { it.status == ItemStatus.AVAILABLE },
            booked = userItems.count { it.status == ItemStatus.BOOKED },
            completed = userItems.count { it.status == ItemStatus.COMPLETED },
            cancelled = userItems.count { it.status == ItemStatus.CANCELLED }
        )
    }

    override suspend fun getCategoriesWithCount(currentUserId: String?): Map<String, Int> = withContext(Dispatchers.IO) {
        return@withContext items.filter {
            it.status == ItemStatus.AVAILABLE &&
                    (currentUserId == null || it.ownerId != currentUserId)
        }.groupBy { it.category }
            .mapValues { it.value.size }
            .toSortedMap()
    }

    // ============== РАБОТА С JSON ФАЙЛАМИ ==============

    private fun loadItemsFromJson(): List<Item> {
        return try {
            if (!itemsFile.exists()) {
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

    private fun createInitialData(): List<Item> {
        val currentTime = System.currentTimeMillis()
        val random = Random(System.currentTimeMillis())

        val initialItems = listOf(
            Item(
                id = "1",
                title = "Кухонный комбайн Braun",
                category = "Техника",
                description = "Почти новый, использовался пару раз. Все насадки в комплекте. Мощность 1000Вт, 5 скоростей. Самовывоз от метро Охотный ряд.",
                photos = generateRandomPhotos(3, "1", random),
                location = Location(55.7558, 37.6176, "м. Охотный ряд"),
                ownerId = "user1",
                ownerName = "Иван Петров",
                ownerPhone = "+7 (999) 123-45-67",
                status = ItemStatus.AVAILABLE,
                createdAt = currentTime - 86400000 * random.nextInt(1, 10),
                bookedBy = null,
                views = random.nextInt(5, 50)
            ),
            Item(
                id = "2",
                title = "Детские книжки (20 штук)",
                category = "Книги",
                description = "Для детей 3-5 лет. Сказки, раскраски, обучающие книги. В отличном состоянии.",
                photos = generateRandomPhotos(4, "2", random),
                location = Location(55.7512, 37.6289, "м. Китай-город"),
                ownerId = "user2",
                ownerName = "Мария Иванова",
                ownerPhone = "+7 (999) 234-56-78",
                status = ItemStatus.AVAILABLE,
                createdAt = currentTime - 86400000 * random.nextInt(1, 10),
                bookedBy = null,
                views = random.nextInt(5, 50)
            ),
            Item(
                id = "3",
                title = "Зимнее пальто, размер 46",
                category = "Одежда",
                description = "Носила один сезон, состояние отличное. Торг уместен. Цвет темно-синий, длина до колена.",
                photos = generateRandomPhotos(2, "3", random),
                location = Location(55.7602, 37.6195, "м. Лубянка"),
                ownerId = "user1",
                ownerName = "Иван Петров",
                ownerPhone = "+7 (999) 123-45-67",
                status = ItemStatus.AVAILABLE,
                createdAt = currentTime - 86400000 * random.nextInt(1, 10),
                bookedBy = null,
                views = random.nextInt(5, 50)
            ),
            Item(
                id = "4",
                title = "Настольная лампа",
                category = "Освещение",
                description = "С регулировкой яркости, светодиодная. Цвет белый, стильный дизайн.",
                photos = generateRandomPhotos(2, "4", random),
                location = Location(55.7587, 37.6153, "м. Театральная"),
                ownerId = "user3",
                ownerName = "Петр Сидоров",
                ownerPhone = "+7 (999) 345-67-89",
                status = ItemStatus.BOOKED,
                createdAt = currentTime - 86400000 * random.nextInt(1, 10),
                bookedBy = "user4",
                views = random.nextInt(5, 50)
            ),
            Item(
                id = "5",
                title = "Велосипед детский",
                category = "Детские товары",
                description = "Для ребенка 3-5 лет, колеса 12 дюймов. С дополнительными колесами, в хорошем состоянии.",
                photos = generateRandomPhotos(3, "5", random),
                location = Location(55.7492, 37.6215, "м. Лубянка"),
                ownerId = "user4",
                ownerName = "Анна Смирнова",
                ownerPhone = "+7 (999) 456-78-90",
                status = ItemStatus.AVAILABLE,
                createdAt = currentTime - 86400000 * random.nextInt(1, 10),
                bookedBy = null,
                views = random.nextInt(5, 50)
            ),
            Item(
                id = "6",
                title = "Микроволновка Samsung",
                category = "Техника",
                description = "Рабочая, 20 литров, гриль. В отличном состоянии, пользовались аккуратно.",
                photos = generateRandomPhotos(3, "6", random),
                location = Location(55.7634, 37.6102, "м. Охотный ряд"),
                ownerId = "user5",
                ownerName = "Дмитрий Козлов",
                ownerPhone = "+7 (999) 567-89-01",
                status = ItemStatus.AVAILABLE,
                createdAt = currentTime - 86400000 * random.nextInt(1, 10),
                bookedBy = null,
                views = random.nextInt(5, 50)
            )
        )

        saveInitialItemsToJson(initialItems)
        return initialItems
    }

    private fun generateRandomPhotos(count: Int, itemId: String, random: Random): List<String> {
        val photos = mutableListOf<String>()
        val numPhotos = random.nextInt(1, minOf(count + 1, 5))

        for (i in 1..numPhotos) {
            val randomImageIndex = random.nextInt(placeholderImages.size)
            val imageName = placeholderImages[randomImageIndex]
            photos.add("photos/${itemId}_${imageName}_$i.jpg")
        }

        return photos
    }

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

    private fun calculateDistance(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Double {
        val R = 6371
        val latDistance = Math.toRadians(lat1 - lat2)
        val lonDistance = Math.toRadians(lng1 - lng2)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

}