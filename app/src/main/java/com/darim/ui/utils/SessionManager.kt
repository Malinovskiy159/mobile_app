// ui/utils/SessionManager.kt
package com.darim.ui.utils

import android.content.Context
import android.content.SharedPreferences
import com.darim.domain.model.User

object SessionManager {

    private const val PREF_NAME = "session_prefs"
    private const val KEY_CURRENT_USER_ID = "current_user_id"
    private const val KEY_CURRENT_USER_NAME = "current_user_name"
    private const val KEY_CURRENT_USER_PHONE = "current_user_phone"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"

    private lateinit var sharedPreferences: SharedPreferences

    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveCurrentUser(user: User) {
        sharedPreferences.edit().apply {
            putString(KEY_CURRENT_USER_ID, user.id)
            putString(KEY_CURRENT_USER_NAME, user.name)
            putString(KEY_CURRENT_USER_PHONE, user.phone)
            putBoolean(KEY_IS_LOGGED_IN, true)
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

    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
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
            rating = 0f,
            reviews = emptyList(),
            itemsGiven = 0,
            itemsTaken = 0
        )
    }
}