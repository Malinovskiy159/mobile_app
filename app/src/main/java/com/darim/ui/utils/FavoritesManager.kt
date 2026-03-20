package com.darim.ui.utils

import android.content.Context

object FavoritesManager {
    private const val PREFS_NAME = "darim_favorites"
    private const val KEY_FAVORITES = "favorite_ids"

    // Получить список сохраненных ID
    fun getFavoriteIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    // Проверить, находится ли ID в избранном
    fun isFavorite(context: Context, itemId: String): Boolean {
        return getFavoriteIds(context).contains(itemId)
    }

    // Добавить или удалить ID из списка
    fun toggleFavorite(context: Context, itemId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentIds = getFavoriteIds(context).toMutableSet()

        if (currentIds.contains(itemId)) {
            currentIds.remove(itemId)
        } else {
            currentIds.add(itemId)
        }

        prefs.edit().putStringSet(KEY_FAVORITES, currentIds).apply()
    }
}