package com.nutritrack.android.data.meals

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nutritrack.android.data.food.FoodItem
import com.nutritrack.android.data.food.MealLine
import com.nutritrack.android.data.food.MealMath
import com.nutritrack.android.data.food.shortName

/**
 * Reads the ingredient JSON written by CreateMealActivity and reuses the app's own
 * health-score algorithm (HealthScoring) so My Meals shows the same score as Create a Meal.
 */
object SavedMealScorer {

    /** Same JSON shape as CreateMealActivity.SavedIngredient. */
    private data class SavedIngredient(
        val foodId: String = "",
        val qty: Double = 0.0,
        val unitIndex: Int = 0,
        val food: FoodItem? = null
    )

    private fun parse(raw: String?): List<SavedIngredient> {
        if (raw.isNullOrBlank() || !raw.trimStart().startsWith("[")) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<SavedIngredient>>() {}.type
            Gson().fromJson<List<SavedIngredient>>(raw, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** "Chicken breast, Rice, Broccoli" (or the raw text for meals saved with the old format). */
    fun readableIngredients(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val parsed = parse(raw)
        if (parsed.isEmpty()) {
            return if (raw.trimStart().startsWith("[")) "" else raw.trim()
        }
        return parsed.mapNotNull { it.food?.shortName() }.joinToString(", ")
    }

    /** Health score 0..100, or null when the meal has no usable ingredient data. */
    fun score(raw: String?): Int? {
        val parsed = parse(raw)
        if (parsed.isEmpty()) return null
        var uid = 0L
        val lines = parsed.mapNotNull { saved ->
            val food = saved.food ?: return@mapNotNull null
            val units = MealMath.unitsFor(food)
            val index = saved.unitIndex.coerceIn(0, units.lastIndex)
            MealLine(uid++, food, saved.qty, units, index)
        }
        if (lines.isEmpty()) return null
        val result = MealMath.healthScore(MealMath.facts(lines), lines.size)
        return if (result.hasScore) result.score else null
    }
}
