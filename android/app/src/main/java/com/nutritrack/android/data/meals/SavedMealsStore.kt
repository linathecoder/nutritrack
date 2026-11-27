package com.nutritrack.android.data.meals

import android.content.Context

/**
 * Small local store for the My Meals page:
 *  - favorites : the meals the user marked with the heart
 *  - hidden    : meals the user removed from My Meals (the logged history is kept)
 */
class SavedMealsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun favorites(): Set<String> = read(KEY_FAVORITES)

    fun hidden(): Set<String> = read(KEY_HIDDEN)

    /** Returns the new state (true = now a favorite). */
    fun toggleFavorite(barcode: String): Boolean {
        val set = read(KEY_FAVORITES).toMutableSet()
        val nowFavorite = set.add(barcode)
        if (!nowFavorite) set.remove(barcode)
        prefs.edit().putStringSet(KEY_FAVORITES, set).apply()
        return nowFavorite
    }

    fun hide(barcode: String) {
        val set = read(KEY_HIDDEN).toMutableSet()
        set.add(barcode)
        prefs.edit().putStringSet(KEY_HIDDEN, set).apply()
    }

    private fun read(key: String): Set<String> =
        HashSet(prefs.getStringSet(key, emptySet()) ?: emptySet())

    companion object {
        private const val PREFS = "nutritrack_my_meals"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HIDDEN = "hidden"
    }
}
