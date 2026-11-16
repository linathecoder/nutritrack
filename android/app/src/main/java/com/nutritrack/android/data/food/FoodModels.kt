package com.nutritrack.android.data.food

data class Per100g(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val satFat: Double = 0.0,
    val sodiumMg: Double = 0.0
)

data class Portion(
    val label: String = "",
    val grams: Double = 0.0
)

data class FoodItem(
    val id: String = "",
    val name: String = "",
    val nameFr: String? = null,
    val category: String = "",
    val icon: String = "",
    val per100g: Per100g = Per100g(),
    val portions: List<Portion> = emptyList(),
    val popular: Boolean = false,
    val missing: Set<String> = emptySet()
)

data class FoodCategory(
    val id: String = "",
    val name: String = "",
    val nameFr: String? = null,
    val tint: String = "#F0F7F3"
)

data class FoodDataset(
    val version: Int = 0,
    val categories: List<FoodCategory> = emptyList(),
    val ingredients: List<FoodItem> = emptyList()
)

fun FoodItem.shortName(): String {
    val parts = name.split(", ")
    if (parts.size <= 3) return name
    val head = parts.take(3).joinToString(", ")
    val tail = parts.last()
    val keepTail = listOf("raw", "cooked", "dry", "boiled", "roasted", "grilled").any { tail.startsWith(it, ignoreCase = true) }
    return if (keepTail) "$head, $tail" else head
}
