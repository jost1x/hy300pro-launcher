package io.jostx.hy300launcher.data

import android.content.Context

class FavoritesStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): List<String> = preferences.getString(KEY_FAVORITES, "").orEmpty()
        .split('|')
        .filter(String::isNotBlank)

    fun save(packages: List<String>) {
        preferences.edit().putString(KEY_FAVORITES, packages.joinToString("|")).apply()
    }

    companion object {
        const val MAX_FAVORITES = 8
        const val PREFERENCES_NAME = "launcher"
        const val KEY_FAVORITES = "favorites"
    }
}
