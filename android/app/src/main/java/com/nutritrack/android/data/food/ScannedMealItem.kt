package com.nutritrack.android.data.food

import android.content.Intent
import com.nutritrack.android.data.openfoodfacts.ScannedProduct

data class ScannedMealItem(
    val barcode: String,
    val name: String,
    val imageUrl: String?,
    val grams: Double,
    val per100g: Per100g,
    val servingGrams: Double?,
    val packageGrams: Double?,
    val missing: Set<String> = emptySet()
) {
    fun toIntent(): Intent = Intent()
        .putExtra(K_BARCODE, barcode)
        .putExtra(K_NAME, name)
        .putExtra(K_IMAGE, imageUrl)
        .putExtra(K_GRAMS, grams)
        .putExtra(K_KCAL, per100g.kcal)
        .putExtra(K_PROTEIN, per100g.protein)
        .putExtra(K_CARBS, per100g.carbs)
        .putExtra(K_FAT, per100g.fat)
        .putExtra(K_FIBER, per100g.fiber)
        .putExtra(K_SUGAR, per100g.sugar)
        .putExtra(K_SATFAT, per100g.satFat)
        .putExtra(K_SODIUM, per100g.sodiumMg)
        .putExtra(K_SERVING, servingGrams ?: 0.0)
        .putExtra(K_PACKAGE, packageGrams ?: 0.0)
        .putStringArrayListExtra(K_MISSING, ArrayList(missing))

    fun toFoodItem(): FoodItem {
        val portions = mutableListOf<Portion>()
        servingGrams?.takeIf { it > 0 }?.let { portions += Portion("serving", it) }
        packageGrams?.takeIf { it > 0 }?.let { portions += Portion("package", it) }
        return FoodItem(
            id = "scan-$barcode",
            name = name,
            category = CATEGORY,
            icon = "📦",
            per100g = per100g,
            portions = portions,
            missing = missing
        )
    }

    companion object {
        const val CATEGORY = "scanned"

        private const val K_BARCODE = "scanned_barcode"
        private const val K_NAME = "scanned_name"
        private const val K_IMAGE = "scanned_image"
        private const val K_GRAMS = "scanned_grams"
        private const val K_KCAL = "scanned_kcal"
        private const val K_PROTEIN = "scanned_protein"
        private const val K_CARBS = "scanned_carbs"
        private const val K_FAT = "scanned_fat"
        private const val K_FIBER = "scanned_fiber"
        private const val K_SUGAR = "scanned_sugar"
        private const val K_SATFAT = "scanned_satfat"
        private const val K_SODIUM = "scanned_sodium"
        private const val K_SERVING = "scanned_serving"
        private const val K_PACKAGE = "scanned_package"
        private const val K_MISSING = "scanned_missing"

        fun from(p: ScannedProduct, grams: Double): ScannedMealItem {
            val n = p.per100g
            val protein = n.proteinG ?: 0.0
            val carbs = n.carbsG ?: 0.0
            val fat = n.fatG ?: 0.0
            val title = p.name ?: "Scanned product ${p.barcode}"
            val brand = p.brand
            return ScannedMealItem(
                barcode = p.barcode,
                name = if (brand != null && !title.contains(brand, ignoreCase = true)) "$title ($brand)" else title,
                imageUrl = p.imageUrl,
                grams = grams,
                per100g = Per100g(
                    kcal = n.kcal ?: (protein * 4 + carbs * 4 + fat * 9),
                    protein = protein,
                    carbs = carbs,
                    fat = fat,
                    fiber = n.fiberG ?: 0.0,
                    sugar = n.sugarsG ?: 0.0,
                    satFat = n.saturatedFatG ?: 0.0,
                    sodiumMg = n.sodiumMg ?: 0.0
                ),
                servingGrams = p.servingGrams,
                packageGrams = p.packageGrams,
                missing = buildSet {
                    if (n.fiberG == null) add(NutrientKey.FIBER)
                    if (n.sugarsG == null) add(NutrientKey.SUGAR)
                    if (n.saturatedFatG == null) add(NutrientKey.SAT_FAT)
                    if (n.sodiumMg == null) add(NutrientKey.SODIUM)
                }
            )
        }

        fun fromIntent(i: Intent?): ScannedMealItem? {
            if (i == null || !i.hasExtra(K_BARCODE)) return null
            val barcode = i.getStringExtra(K_BARCODE) ?: return null
            return ScannedMealItem(
                barcode = barcode,
                name = i.getStringExtra(K_NAME) ?: "Scanned product $barcode",
                imageUrl = i.getStringExtra(K_IMAGE),
                grams = i.getDoubleExtra(K_GRAMS, 0.0),
                per100g = Per100g(
                    kcal = i.getDoubleExtra(K_KCAL, 0.0),
                    protein = i.getDoubleExtra(K_PROTEIN, 0.0),
                    carbs = i.getDoubleExtra(K_CARBS, 0.0),
                    fat = i.getDoubleExtra(K_FAT, 0.0),
                    fiber = i.getDoubleExtra(K_FIBER, 0.0),
                    sugar = i.getDoubleExtra(K_SUGAR, 0.0),
                    satFat = i.getDoubleExtra(K_SATFAT, 0.0),
                    sodiumMg = i.getDoubleExtra(K_SODIUM, 0.0)
                ),
                servingGrams = i.getDoubleExtra(K_SERVING, 0.0).takeIf { it > 0 },
                packageGrams = i.getDoubleExtra(K_PACKAGE, 0.0).takeIf { it > 0 },
                missing = i.getStringArrayListExtra(K_MISSING)?.toSet().orEmpty()
            )
        }
    }
}
