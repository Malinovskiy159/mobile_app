// data/repository/UserRepositoryImpl.kt
package com.darim.data.repository

import android.content.Context
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
                val updatedUser = it.copy(rating = (it.rating + newRating) / 2)
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

                val newRating = updatedReviews.map { r -> r.rating }.average().toFloat()

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

    private fun loadUsersFromJson(): List<User> {
        return try {
            if (!usersFile.exists()) {
                return createInitialUsers()
            }

            val jsonString = usersFile.readText()
            if (jsonString.isBlank()) {
                return createInitialUsers()
            }

            val jsonArray = JSONArray(jsonString)

            (0 until jsonArray.length()).mapNotNull { index ->
                try {
                    val jsonObject = jsonArray.getJSONObject(index)
                    jsonToUser(jsonObject)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            createInitialUsers()
        }
    }

    private fun createInitialUsers(): List<User> {
        val initialUsers = listOf(
            User(
                id = "user1",
                name = "Иван Петров",
                phone = "+7 (999) 123-45-67",
                rating = 4.8f,
                reviews = emptyList(),
                itemsGiven = 5,
                itemsTaken = 3
            ),
            User(
                id = "user2",
                name = "Мария Иванова",
                phone = "+7 (999) 234-56-78",
                rating = 4.9f,
                reviews = emptyList(),
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
            )
        )

        saveInitialUsersToJson(initialUsers)
        return initialUsers
    }

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
                    put("id", review.id)                          // ← добавляем id
                    put("fromUserId", review.fromUserId)
                    put("toUserId", review.toUserId)
                    put("rating", review.rating)
                    put("comment", review.comment)
                    put("date", review.date)
                    put("transferId", review.transferId ?: JSONObject.NULL)  // ← добавляем transferId
                })
            }
            put("reviews", reviewsArray)
        }
    }

    private fun jsonToUser(json: JSONObject): User {
        val reviewsArray = json.optJSONArray("reviews") ?: JSONArray()
        val reviews = (0 until reviewsArray.length()).mapNotNull { index ->
            try {
                val reviewJson = reviewsArray.getJSONObject(index)
                Review(
                    id = reviewJson.optString("id", UUID.randomUUID().toString()),  // ← генерируем id, если нет
                    fromUserId = reviewJson.getString("fromUserId"),
                    toUserId = reviewJson.getString("toUserId"),
                    rating = reviewJson.getInt("rating"),
                    comment = reviewJson.getString("comment"),
                    date = reviewJson.getLong("date"),
                    transferId = reviewJson.optString("transferId")
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
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

    override suspend fun getAllUsers(): List<User> = withContext(Dispatchers.IO) {
        return@withContext users.toList()
    }

    override suspend fun findUserByPhone(phone: String): User? = withContext(Dispatchers.IO) {
        return@withContext users.find { it.phone == phone }
    }
}