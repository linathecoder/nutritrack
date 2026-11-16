package com.nutritrack.android.data.openfoodfacts

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.math.max

data class NutrientValues(
    val kcal: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val saturatedFatG: Double? = null,
    val fiberG: Double? = null,
    val sugarsG: Double? = null,
    val sodiumMg: Double? = null,
    val saltG: Double? = null
) {
    val hasAny: Boolean get() = hasMain || hasDetails
    val hasMain: Boolean get() = kcal != null || proteinG != null || carbsG != null || fatG != null
    val hasMacros: Boolean get() = proteinG != null || carbsG != null || fatG != null
    val hasDetails: Boolean
        get() = saturatedFatG != null || fiberG != null || sugarsG != null || sodiumMg != null || saltG != null

    fun scaled(factor: Double) = NutrientValues(
        kcal?.times(factor), proteinG?.times(factor), carbsG?.times(factor), fatG?.times(factor),
        saturatedFatG?.times(factor), fiberG?.times(factor), sugarsG?.times(factor),
        sodiumMg?.times(factor), saltG?.times(factor)
    )
}

data class ScannedProduct(
    val barcode: String,
    val name: String?,
    val brand: String?,
    val quantity: String?,
    val servingGrams: Double?,
    val packageGrams: Double?,
    val imageUrl: String?,
    val ingredients: List<String>,
    val allergens: List<String>,
    val nutriScore: String?,
    val novaGroup: Int?,
    val per100g: NutrientValues
)

private const val KJ_PER_KCAL = 4.184
private const val SALT_PER_SODIUM = 2.5

fun OffProduct.toScannedProduct(fallbackBarcode: String): ScannedProduct {
    val n = nutriments

    val servingGrams = (servingQuantity.asNonNegativeDouble()
        ?: servingSize?.let { GRAMS_REGEX.find(it)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() })
        ?.takeIf { it > 0 }

    var per100 = readNutrients(n, "100g")
    if (!per100.hasAny && servingGrams != null) {
        val perServing = readNutrients(n, "serving")
        if (perServing.hasAny) per100 = perServing.scaled(100 / servingGrams)
    }

    return ScannedProduct(
        barcode = code?.takeIf { it.isNotBlank() } ?: fallbackBarcode,
        name = productName?.trim()?.takeIf { it.isNotEmpty() },
        brand = brands?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
        quantity = quantity?.trim()?.takeIf { it.isNotEmpty() },
        servingGrams = servingGrams,
        packageGrams = parsePackageGrams(quantity),
        imageUrl = imageFrontUrl?.takeIf { it.isNotBlank() },
        ingredients = parseIngredients(ingredientsText),
        allergens = parseAllergens(allergensTags),
        nutriScore = nutriscoreGrade?.trim()?.uppercase()?.takeIf { it.length == 1 && it[0] in 'A'..'E' },
        novaGroup = novaGroup.asNonNegativeDouble()?.toInt()?.takeIf { it in 1..4 },
        per100g = per100
    )
}

private val GRAMS_REGEX = Regex("""(\d+(?:[.,]\d+)?)\s*g\b""", RegexOption.IGNORE_CASE)
private val PACKAGE_REGEX = Regex("""^\D*?(\d+(?:[.,]\d+)?)\s*(kg|g)\b""", RegexOption.IGNORE_CASE)
private val MULTIPACK_REGEX = Regex("""\d\s*[x×]\s*\d""", RegexOption.IGNORE_CASE)

internal fun parsePackageGrams(quantity: String?): Double? {
    if (quantity.isNullOrBlank() || MULTIPACK_REGEX.containsMatchIn(quantity)) return null
    val m = PACKAGE_REGEX.find(quantity.trim()) ?: return null
    val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val grams = if (m.groupValues[2].equals("kg", ignoreCase = true)) value * 1000 else value
    return grams.takeIf { it > 0 }
}

private fun readNutrients(n: JsonObject?, suffix: String): NutrientValues {
    val kcal = n.num("energy-kcal_$suffix")
        ?: (n.num("energy-kj_$suffix") ?: n.num("energy_$suffix"))?.div(KJ_PER_KCAL)

    val sodiumG = n.num("sodium_$suffix")
    val saltG = n.num("salt_$suffix")
    return NutrientValues(
        kcal = kcal,
        proteinG = n.num("proteins_$suffix"),
        carbsG = n.num("carbohydrates_$suffix"),
        fatG = n.num("fat_$suffix"),
        saturatedFatG = n.num("saturated-fat_$suffix"),
        fiberG = n.num("fiber_$suffix"),
        sugarsG = n.num("sugars_$suffix"),
        sodiumMg = (sodiumG ?: saltG?.div(SALT_PER_SODIUM))?.times(1000),
        saltG = saltG ?: sodiumG?.times(SALT_PER_SODIUM)
    )
}

private fun JsonObject?.num(key: String): Double? = this?.get(key).asNonNegativeDouble()

private fun JsonElement?.asNonNegativeDouble(): Double? {
    if (this == null || isJsonNull || !isJsonPrimitive) return null
    return asString.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
}

internal fun parseIngredients(text: String?): List<String> {
    if (text.isNullOrBlank()) return emptyList()
    val cleaned = text.replace(Regex("^\\s*(ingredients|ingr[ée]dients)\\s*:\\s*", RegexOption.IGNORE_CASE), "")

    val result = mutableListOf<String>()
    val current = StringBuilder()
    var depth = 0

    fun flush() {
        val item = current.toString()
            .replace("_", "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.', ' ')
        if (item.isNotEmpty()) result += item.replaceFirstChar { it.uppercase() }
        current.clear()
    }

    for (c in cleaned) {
        when {
            c == '(' || c == '[' -> { depth++; current.append(c) }
            c == ')' || c == ']' -> { depth = max(0, depth - 1); current.append(c) }
            (c == ',' || c == ';') && depth == 0 -> flush()
            else -> current.append(c)
        }
    }
    flush()
    return result
}

internal fun parseAllergens(tags: List<String>?): List<String> =
    tags.orEmpty()
        .map { it.substringAfter(':').replace('-', ' ').trim() }
        .filter { it.isNotEmpty() }
        .map { it.replaceFirstChar { c -> c.uppercase() } }
        .distinct()
