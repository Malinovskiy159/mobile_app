// data/repository/UserRepositoryImpl.kt
package com.darim.data.repository

import android.content.Context
import com.darim.domain.model.ItemStatus
import com.darim.domain.model.Review
import com.darim.domain.model.User
import com.darim.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

class UserRepositoryImpl(private val context: Context) : UserRepository {

    private val filesDir: File
        get() = context.filesDir

    private val usersFile: File by lazy {
        File(filesDir, "users.json")
    }

    private val users: MutableList<User> by lazy {
        loadUsersFromJson().toMutableList()
    }

    override suspend fun getUser(userId: String): User? = withContext(Dispatchers.IO) {
        return@withContext users.find { it.id == userId }
    }

    override suspend fun updateUser(user: User): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val index = users.indexOfFirst { it.id == user.id }
            if (index >= 0) {
                users[index] = user
            } else {
                users.add(user)
            }
            saveUsersToJson()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateRating(userId: String, newRating: Int): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val user = users.find { it.id == userId }
            user?.let {
                // Рассчитываем новый средний рейтинг
                val totalReviews = it.reviews.size
                val currentTotal = it.rating * totalReviews
                val newTotal = currentTotal + newRating
                val newAverage = newTotal / (totalReviews + 1)

                val updatedUser = it.copy(rating = newAverage)
                val index = users.indexOfFirst { u -> u.id == userId }
                users[index] = updatedUser
                saveUsersToJson()
                Result.success(Unit)
            } ?: Result.failure(Exception("User not found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addReview(review: Review): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val user = users.find { it.id == review.toUserId }
            user?.let {
                val updatedReviews = it.reviews.toMutableList()
                updatedReviews.add(review)

                // Пересчитываем рейтинг на основе всех отзывов
                val newRating = updatedReviews.map { r -> r.rating.toFloat() }.average().toFloat()

                val updatedUser = it.copy(
                    reviews = updatedReviews,
                    rating = newRating
                )

                val index = users.indexOfFirst { u -> u.id == review.toUserId }
                users[index] = updatedUser
                saveUsersToJson()
                Result.success(Unit)
            } ?: Result.failure(Exception("User not found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Загружает пользователей из JSON файла
     */
    private fun loadUsersFromJson(): List<User> {
        return try {
            if (!usersFile.exists()) {
                // Если файл не существует, создаем начальные данные
                return createInitialUsers()
            }

            val jsonString = usersFile.readText()
            if (jsonString.isBlank()) {
                return createInitialUsers()
            }

            val jsonArray = JSONArray(jsonString)
            val usersList = mutableListOf<User>()

            for (i in 0 until jsonArray.length()) {
                try {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val user = jsonToUser(jsonObject)
                    usersList.add(user)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (usersList.isEmpty()) {
                return createInitialUsers()
            }

            usersList
        } catch (e: Exception) {
            e.printStackTrace()
            createInitialUsers()
        }
    }

    /**
     * Создает начальные тестовые данные
     */
    private fun createInitialUsers(): List<User> {
        val initialUsers = listOf(
            User(
                id = "user1",
                name = "Иван Петров",
                phone = "+7 (999) 123-45-67",
                rating = 4.8f,
                reviews = listOf(
                    Review(
                        id = UUID.randomUUID().toString(),
                        fromUserId = "user2",
                        toUserId = "user1",
                        rating = 5,
                        comment = "Отличный человек! Все отдал вовремя.",
                        date = System.currentTimeMillis() - 86400000,
                        transferId = "transfer1"
                    ),
                    Review(
                        id = UUID.randomUUID().toString(),
                        fromUserId = "user3",
                        toUserId = "user1",
                        rating = 4,
                        comment = "Хороший собеседник, но немного опоздал.",
                        date = System.currentTimeMillis() - 172800000,
                        transferId = "transfer2"
                    )
                ),
                itemsGiven = 5,
                itemsTaken = 3
            ),
            User(
                id = "user2",
                name = "Мария Иванова",
                phone = "+7 (999) 234-56-78",
                rating = 4.9f,
                reviews = listOf(
                    Review(
                        id = UUID.randomUUID().toString(),
                        fromUserId = "user1",
                        toUserId = "user2",
                        rating = 5,
                        comment = "Очень приятная девушка, вещи в отличном состоянии.",
                        date = System.currentTimeMillis() - 259200000,
                        transferId = "transfer3"
                    )
                ),
                itemsGiven = 8,
                itemsTaken = 4
            ),
            User(
                id = "user3",
                name = "Петр Сидоров",
                phone = "+7 (999) 345-67-89",
                rating = 4.5f,
                reviews = emptyList(),
                itemsGiven = 3,
                itemsTaken = 2
            ),
            User(
                id = "user4",
                name = "Анна Смирнова",
                phone = "+7 (999) 456-78-90",
                rating = 5.0f,
                reviews = listOf(
                    Review(
                        id = UUID.randomUUID().toString(),
                        fromUserId = "user1",
                        toUserId = "user4",
                        rating = 5,
                        comment = "Все отлично! Спасибо!",
                        date = System.currentTimeMillis() - 43200000,
                        transferId = "transfer4"
                    )
                ),
                itemsGiven = 12,
                itemsTaken = 6
            )
        )

        // Сохраняем начальные данные в файл
        saveInitialUsersToJson(initialUsers)

        return initialUsers
    }

    /**
     * Сохраняет начальные данные в JSON файл
     */
    private fun saveInitialUsersToJson(users: List<User>) {
        try {
            val jsonArray = JSONArray()
            users.forEach { user ->
                jsonArray.put(userToJson(user))
            }
            usersFile.writeText(jsonArray.toString(2))
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Сохраняет текущий список пользователей в JSON файл
     */
    private fun saveUsersToJson() {
        try {
            val jsonArray = JSONArray()
            users.forEach { user ->
                jsonArray.put(userToJson(user))
            }
            usersFile.writeText(jsonArray.toString(2))
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Преобразует объект User в JSON
     */
    private fun userToJson(user: User): JSONObject {
        return JSONObject().apply {
            put("id", user.id)
            put("name", user.name)
            put("phone", user.phone)
            put("rating", user.rating)
            put("itemsGiven", user.itemsGiven)
            put("itemsTaken", user.itemsTaken)

            val reviewsArray = JSONArray()
            user.reviews.forEach { review ->
                reviewsArray.put(JSONObject().apply {
                    put("id", review.id)
                    put("fromUserId", review.fromUserId)
                    put("toUserId", review.toUserId)
                    put("rating", review.rating)
                    put("comment", review.comment)
                    put("date", review.date)
                    if (review.transferId != null) {
                        put("transferId", review.transferId)
                    }
                })
            }
            put("reviews", reviewsArray)
        }
    }

    /**
     * Преобразует JSON в объект User
     */
    private fun jsonToUser(json: JSONObject): User {
        // Получаем массив отзывов
        val reviewsArray = json.optJSONArray("reviews") ?: JSONArray()
        val reviews = mutableListOf<Review>()

        for (i in 0 until reviewsArray.length()) {
            try {
                val reviewJson = reviewsArray.getJSONObject(i)
                val review = Review(
                    id = reviewJson.optString("id", UUID.randomUUID().toString()),
                    fromUserId = reviewJson.getString("fromUserId"),
                    toUserId = reviewJson.getString("toUserId"),
                    rating = reviewJson.getInt("rating"),
                    comment = reviewJson.getString("comment"),
                    date = reviewJson.getLong("date"),
                    transferId = reviewJson.getString("transferId")
                )
                reviews.add(review)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return User(
            id = json.getString("id"),
            name = json.getString("name"),
            phone = json.getString("phone"),
            rating = json.optDouble("rating", 0.0).toFloat(),
            reviews = reviews,
            itemsGiven = json.optInt("itemsGiven", 0),
            itemsTaken = json.optInt("itemsTaken", 0)
        )
    }
}