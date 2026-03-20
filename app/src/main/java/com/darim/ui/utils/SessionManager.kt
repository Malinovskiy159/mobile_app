// ui/utils/SessionManager.kt
package com.darim.ui.utils

import android.content.Context
import android.content.SharedPreferences
import com.darim.domain.model.User
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object SessionManager {

    private const val PREF_NAME = "session_prefs"
    private const val KEY_CURRENT_USER_ID = "current_user_id"
    private const val KEY_CURRENT_USER_NAME = "current_user_name"
    private const val KEY_CURRENT_USER_PHONE = "current_user_phone"
    private const val KEY_CURRENT_USER_RATING = "current_user_rating"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_TYPE = "user_type" // "registered" или "guest"

    // Ключи для хранения забронированных вещей пользователя
    private const val KEY_BOOKED_ITEMS = "booked_items_"
    private const val KEY_MY_ITEMS = "my_items_"

    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveCurrentUser(user: User) {
        sharedPreferences.edit().apply {
            putString(KEY_CURRENT_USER_ID, user.id)
            putString(KEY_CURRENT_USER_NAME, user.name)
            putString(KEY_CURRENT_USER_PHONE, user.phone)
            putFloat(KEY_CURRENT_USER_RATING, user.rating)
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_TYPE, if (user.id.startsWith("guest_")) "guest" else "registered")
            apply()
        }
    }

    fun getCurrentUserId(): String? {
        return sharedPreferences.getString(KEY_CURRENT_USER_ID, null)
    }

    fun getCurrentUserName(): String? {
        return sharedPreferences.getString(KEY_CURRENT_USER_NAME, null)
    }

    fun getCurrentUserPhone(): String? {
        return sharedPreferences.getString(KEY_CURRENT_USER_PHONE, null)
    }

    fun getCurrentUserRating(): Float {
        return sharedPreferences.getFloat(KEY_CURRENT_USER_RATING, 0f)
    }

    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun isGuest(): Boolean {
        return sharedPreferences.getString(KEY_USER_TYPE, "registered") == "guest"
    }

    fun clearSession() {
        sharedPreferences.edit().clear().apply()
    }

    fun getCurrentUser(): User? {
        val id = getCurrentUserId() ?: return null
        return User(
            id = id,
            name = getCurrentUserName() ?: "Пользователь",
            phone = getCurrentUserPhone() ?: "+7 (999) 123-45-67",
            rating = getCurrentUserRating(),
            reviews = emptyList(),
            itemsGiven = 0,
            itemsTaken = 0
        )
    }

    // ============== РАБОТА С ЗАБРОНИРОВАННЫМИ ВЕЩАМИ ==============

    fun saveBookedItem(itemId: String) {
        val userId = getCurrentUserId() ?: return
        val key = KEY_BOOKED_ITEMS + userId
        val bookedItems = getBookedItems().toMutableSet()
        bookedItems.add(itemId)
        sharedPreferences.edit().putString(key, gson.toJson(bookedItems)).apply()
    }

    fun removeBookedItem(itemId: String) {
        val userId = getCurrentUserId() ?: return
        val key = KEY_BOOKED_ITEMS + userId
        val bookedItems = getBookedItems().toMutableSet()
        bookedItems.remove(itemId)
        sharedPreferences.edit().putString(key, gson.toJson(bookedItems)).apply()
    }

    fun getBookedItems(): Set<String> {
        val userId = getCurrentUserId() ?: return emptySet()
        val key = KEY_BOOKED_ITEMS + userId
        val json = sharedPreferences.getString(key, null) ?: return emptySet()
        return try {
            gson.fromJson(json, object : TypeToken<Set<String>>() {}.type)
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun clearBookedItems() {
        val userId = getCurrentUserId() ?: return
        val key = KEY_BOOKED_ITEMS + userId
        sharedPreferences.edit().remove(key).apply()
    }

    // ============== РАБОТА С ВЕЩАМИ ПОЛЬЗОВАТЕЛЯ ==============

    fun saveMyItem(itemId: String) {
        val userId = getCurrentUserId() ?: return
        val key = KEY_MY_ITEMS + userId
        val myItems = getMyItems().toMutableSet()
        myItems.add(itemId)
        sharedPreferences.edit().putString(key, gson.toJson(myItems)).apply()
    }

    fun removeMyItem(itemId: String) {
        val userId = getCurrentUserId() ?: return
        val key = KEY_MY_ITEMS + userId
        val myItems = getMyItems().toMutableSet()
        myItems.remove(itemId)
        sharedPreferences.edit().putString(key, gson.toJson(myItems)).apply()
    }

    fun getMyItems(): Set<String> {
        val userId = getCurrentUserId() ?: return emptySet()
        val key = KEY_MY_ITEMS + userId
        val json = sharedPreferences.getString(key, null) ?: return emptySet()
        return try {
            gson.fromJson(json, object : TypeToken<Set<String>>() {}.type)
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun clearMyItems() {
        val userId = getCurrentUserId() ?: return
        val key = KEY_MY_ITEMS + userId
        sharedPreferences.edit().remove(key).apply()
    }

    // ============== ОЧИСТКА ВСЕХ ДАННЫХ ПОЛЬЗОВАТЕЛЯ ==============

    fun clearAllUserData() {
        val userId = getCurrentUserId() ?: return
        sharedPreferences.edit()
            .remove(KEY_BOOKED_ITEMS + userId)
            .remove(KEY_MY_ITEMS + userId)
            .apply()
    }
}