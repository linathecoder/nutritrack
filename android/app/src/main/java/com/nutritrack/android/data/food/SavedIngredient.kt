package com.nutritrack.android.data.food


data class SavedIngredient(
    val foodId: String,
    val qty: Double,
    val unitIndex: Int = 0,
    val food: FoodItem? = null
)
