package com.darim.ui.utils

import android.content.Context

object FavoritesManager {
    private const val PREFS_NAME = "darim_favorites"
    private const val KEY_FAVORITES = "favorite_ids"

    fun getFavoriteIds(context: Context): Set<String> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun toggleFavorite(context: Context, itemId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ids = getFavoriteIds(context).toMutableSet()
        val added = if (ids.contains(itemId)) { ids.remove(itemId); false } else { ids.add(itemId); true }
        prefs.edit().putStringSet(KEY_FAVORITES, ids).apply()
        return added
    }

    fun isFavorite(context: Context, itemId: String): Boolean = getFavoriteIds(context).contains(itemId)
}