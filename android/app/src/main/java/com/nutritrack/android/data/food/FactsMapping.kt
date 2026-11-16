package com.nutritrack.android.data.food

import com.nutritrack.android.data.openfoodfacts.NutrientValues

fun NutrientValues.toFacts(): Facts {
    val kcalValue = kcal ?: if (proteinG != null && carbsG != null && fatG != null) {
        proteinG * 4 + carbsG * 4 + fatG * 9
    } else null
    return Facts(
        kcal = kcalValue,
        protein = proteinG,
        carbs = carbsG,
        fat = fatG,
        fiber = fiberG,
        sugar = sugarsG,
        satFat = saturatedFatG,
        sodiumMg = sodiumMg
    )
}
