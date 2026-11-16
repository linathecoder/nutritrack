package com.nutritrack.android.data.food

import java.util.Locale
import kotlin.math.roundToInt

data class Nutrition(
    val grams: Double = 0.0,
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val satFat: Double = 0.0,
    val sodiumMg: Double = 0.0
) {
    operator fun plus(o: Nutrition) = Nutrition(
        grams + o.grams, kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat,
        fiber + o.fiber, sugar + o.sugar, satFat + o.satFat, sodiumMg + o.sodiumMg
    )

    companion object {
        fun of(p: Per100g, grams: Double): Nutrition {
            val f = grams / 100.0
            return Nutrition(grams, p.kcal * f, p.protein * f, p.carbs * f, p.fat * f, p.fiber * f, p.sugar * f, p.satFat * f, p.sodiumMg * f)
        }
    }
}

object NutrientKey {
    const val FIBER = "fiber"
    const val SUGAR = "sugar"
    const val SAT_FAT = "satFat"
    const val SODIUM = "sodium"
}

data class Facts(
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val fiber: Double?,
    val sugar: Double?,
    val satFat: Double?,
    val sodiumMg: Double?
) {
    fun missingNames(): List<String> {
        val out = mutableListOf<String>()
        if (kcal == null) out += "calories"
        if (fiber == null) out += "fiber"
        if (sugar == null) out += "sugars"
        if (satFat == null) out += "saturated fat"
        if (sodiumMg == null) out += "sodium"
        return out
    }
}

enum class UnitKind { GRAMS, ML, COUNT }

class UnitOption(val label: String, val gramsPerUnit: Double, val kind: UnitKind, val step: Double) {
    val isMass: Boolean get() = kind != UnitKind.COUNT
}

class MealLine(val uid: Long, val food: FoodItem, var qty: Double, val units: List<UnitOption>, var unitIndex: Int = 0) {
    val unit: UnitOption get() = units[unitIndex]
    val grams: Double get() = qty * unit.gramsPerUnit
    val nutrition: Nutrition get() = Nutrition.of(food.per100g, grams)
}

enum class HealthTier { NONE, POOR, MODERATE, GOOD, EXCELLENT }

data class HealthResult(
    val score: Int,
    val tier: HealthTier,
    val label: String,
    val explanation: String,
    val criteriaUsed: Int = 0
) {
    val hasScore: Boolean get() = tier != HealthTier.NONE
}

data class Insight(val title: String, val text: String, val neutral: Boolean = false)
data class MealInsights(val summary: String, val good: List<Insight>, val watch: List<Insight>, val hasData: Boolean)

object HealthScoring {
    const val CRITERIA_TOTAL = 5
    const val MIN_CRITERIA = 3

    fun score(f: Facts, ingredientCount: Int = 0): HealthResult {
        val kcal = f.kcal?.takeIf { it > 0.0 }
        var score = 62
        var used = 0
        val strengths = mutableListOf<String>()
        val weaknesses = mutableListOf<String>()

        fun apply(delta: Int, strength: String, weakness: String) {
            score += delta
            used++
            if (delta >= 4) strengths += strength else if (delta < 0) weaknesses += weakness
        }

        val protein = f.protein
        if (kcal != null && protein != null) {
            val per100Kcal = protein / kcal * 100
            apply(
                when {
                    per100Kcal >= 8 -> 12
                    per100Kcal >= 5 -> 7
                    per100Kcal < 2.5 -> -6
                    else -> 0
                }, "good protein density", "low protein density"
            )
        }
        val fiber = f.fiber
        if (fiber != null) {
            apply(
                when {
                    fiber >= 8 -> 11
                    fiber >= 5 -> 7
                    fiber >= 2.5 -> 3
                    else -> -5
                }, "good fiber", "low fiber"
            )
        }
        val satFat = f.satFat
        if (kcal != null && satFat != null) {
            val pct = satFat * 9 / kcal * 100
            apply(
                when {
                    pct <= 6 -> 6
                    pct <= 10 -> 2
                    else -> -8
                }, "low saturated fat", "high saturated fat"
            )
        }
        val sodium = f.sodiumMg
        if (sodium != null) {
            apply(
                when {
                    sodium <= 350 -> 5
                    sodium <= 650 -> 1
                    else -> -7
                }, "low sodium", "high sodium"
            )
        }
        val sugar = f.sugar
        if (kcal != null && sugar != null) {
            val pct = sugar * 4 / kcal * 100
            apply(
                when {
                    pct <= 8 -> 4
                    pct > 20 -> -6
                    else -> 0
                }, "low sugar", "high sugar"
            )
        }

        if (used < MIN_CRITERIA) {
            return HealthResult(
                0, HealthTier.NONE, "Not enough data",
                "Only $used of $CRITERIA_TOTAL nutrition criteria have data, so no score can be given. " +
                    "At least $MIN_CRITERIA are needed.",
                used
            )
        }
        if (ingredientCount >= 4) score += 2
        score = score.coerceIn(15, 98)

        val parts = mutableListOf<String>()
        if (strengths.isNotEmpty()) parts += "Strengths: ${strengths.joinToString(", ")}."
        if (weaknesses.isNotEmpty()) parts += "Weak points: ${weaknesses.joinToString(", ")}."
        if (parts.isEmpty()) parts += "No standout strengths or weak points."
        if (used < CRITERIA_TOTAL) parts += "Based on $used of $CRITERIA_TOTAL criteria."
        val explanation = parts.joinToString(" ")

        val (tier, label) = when {
            score >= 80 -> HealthTier.EXCELLENT to "Excellent"
            score >= 65 -> HealthTier.GOOD to "Good"
            score >= 45 -> HealthTier.MODERATE to "Moderate"
            else -> HealthTier.POOR to "Poor"
        }
        return HealthResult(score, tier, label, explanation, used)
    }
}

object MealMath {

    private val SMALL_DOSE_CATEGORIES = setOf("fats_sweeteners", "herbs_spices")
    private val FATTY_FISH = Regex("salmon|sardine|mackerel|herring|trout|anchov", RegexOption.IGNORE_CASE)

    private const val MIN_KNOWN_WEIGHT = 0.5

    fun total(lines: List<MealLine>): Nutrition = lines.fold(Nutrition()) { acc, l -> acc + l.nutrition }

    fun facts(lines: List<MealLine>): Facts {
        val t = total(lines)
        fun known(key: String): Boolean {
            if (t.grams <= 0.0) return false
            val knownGrams = lines.filter { key !in it.food.missing }.sumOf { it.grams }
            return knownGrams / t.grams >= MIN_KNOWN_WEIGHT
        }
        return Facts(
            kcal = t.kcal, protein = t.protein, carbs = t.carbs, fat = t.fat,
            fiber = t.fiber.takeIf { known(NutrientKey.FIBER) },
            sugar = t.sugar.takeIf { known(NutrientKey.SUGAR) },
            satFat = t.satFat.takeIf { known(NutrientKey.SAT_FAT) },
            sodiumMg = t.sodiumMg.takeIf { known(NutrientKey.SODIUM) }
        )
    }

    fun unitsFor(food: FoodItem): List<UnitOption> {
        val small = food.category in SMALL_DOSE_CATEGORIES
        val list = mutableListOf(UnitOption("g", 1.0, UnitKind.GRAMS, if (small) 5.0 else 25.0))
        val seen = mutableSetOf("g")
        for (p in food.portions) {
            if (p.grams <= 0.0 || p.label.isBlank()) continue
            if (p.label == "ml") {
                if (seen.add("ml")) list += UnitOption("ml", p.grams, UnitKind.ML, if (small) 5.0 else 10.0)
            } else {
                val label = "${p.label} (${qty(p.grams)}g)"
                if (seen.add(label)) list += UnitOption(label, p.grams, UnitKind.COUNT, 0.5)
            }
        }
        return list
    }

    fun defaultQty(food: FoodItem, unit: UnitOption): Double =
        if (unit.isMass) (if (food.category in SMALL_DOSE_CATEGORIES) 10.0 else 100.0) else 1.0

    fun nextQty(current: Double, unit: UnitOption, direction: Int): Double {
        val raw = current + direction * unit.step
        return if (unit.isMass) maxOf(5.0, raw.roundToInt().toDouble())
        else maxOf(0.5, (raw * 10).roundToInt() / 10.0)
    }

    fun healthScore(f: Facts, ingredientCount: Int): HealthResult {
        if (ingredientCount == 0) {
            return HealthResult(0, HealthTier.NONE, "No Data", "Add ingredients above to evaluate nutritional quality and meal balance.")
        }
        return HealthScoring.score(f, ingredientCount)
    }

    fun insights(lines: List<MealLine>, t: Nutrition, f: Facts): MealInsights {
        if (lines.isEmpty() || t.kcal <= 0.0) {
            return MealInsights("Add ingredients to your meal to generate an ingredient-level nutritional analysis.", emptyList(), emptyList(), false)
        }
        val topProtein = lines.maxByOrNull { it.nutrition.protein }!!
        val topCarbs = lines.maxByOrNull { it.nutrition.carbs }!!
        val topFat = lines.maxByOrNull { it.nutrition.fat }!!
        val topFiber = lines.maxByOrNull { it.nutrition.fiber }!!
        val topSatFat = lines.maxByOrNull { it.nutrition.satFat }!!

        val anchors = buildString {
            append(topProtein.food.shortName().lowercase())
            if (topCarbs.uid != topProtein.uid && topCarbs.nutrition.carbs >= 8) append(" and ").append(topCarbs.food.shortName().lowercase())
        }
        val summary = "Provides ${t.kcal.roundToInt()} kcal and ${d1(t.protein)}g protein, anchored by $anchors."

        val fiber = f.fiber
        val sodium = f.sodiumMg
        val satFat = f.satFat
        val sugar = f.sugar

        val good = mutableListOf<Insight>()
        if (t.protein >= 25 && topProtein.nutrition.protein > 5) {
            good += Insight(
                "High Protein (${d1(t.protein)}g)",
                "${topProtein.food.shortName()} (${topProtein.nutrition.grams.roundToInt()}g) supplies ${d1(topProtein.nutrition.protein)}g of protein to support muscle recovery and satiety."
            )
        }
        if (fiber != null && fiber >= 4.5 && topFiber.nutrition.fiber > 1) {
            good += Insight(
                "Beneficial Dietary Fiber (${d1(fiber)}g)",
                "${topFiber.food.shortName()} contributes ${d1(topFiber.nutrition.fiber)}g of fiber for steady digestion and sustained energy."
            )
        }
        if (isOliveOil(topFat.food)) {
            good += Insight(
                "Heart-Healthy Monounsaturated Fats",
                "${topFat.food.shortName()} (${topFat.nutrition.grams.roundToInt()}g) adds oleic acid and helps absorb fat-soluble vitamins."
            )
        } else if (isFattyFish(topFat.food)) {
            good += Insight(
                "Rich in Omega-3 Fatty Acids",
                "${topFat.food.shortName()} (${topFat.nutrition.grams.roundToInt()}g) provides EPA and DHA omega-3 fats alongside high-quality protein."
            )
        }
        if (sodium != null && sodium <= 350) {
            good += Insight("Low Sodium (${sodium.roundToInt()}mg)", "Total sodium for the whole meal stays well under typical per-meal limits.")
        }
        if (good.isEmpty()) {
            good += Insight(
                "Transparent Ingredient Tracking",
                "Built from ${lines.size} ingredient${if (lines.size > 1) "s" else ""}, each with its own amount and macros."
            )
        }

        val watch = mutableListOf<Insight>()
        if (fiber != null && fiber < 5) {
            watch += Insight(
                "Moderate Fiber Content (${d1(fiber)}g)",
                "Below the ~8g per-meal fiber goal. Try adding spinach, vegetables or lentils to boost fiber and micronutrients."
            )
        }
        val topOil = lines.filter { it.food.category == "fats_sweeteners" }.maxByOrNull { it.nutrition.kcal }
        if (topOil != null && topOil.nutrition.grams >= 9 && topOil.nutrition.kcal / t.kcal >= 0.25) {
            watch += Insight(
                "Calorie-Dense Fat Portion (${topOil.nutrition.kcal.roundToInt()} kcal)",
                "${topOil.food.shortName()} accounts for ${(topOil.nutrition.kcal / t.kcal * 100).roundToInt()}% of the meal's calories; trim the portion if you are aiming for a tighter calorie target."
            )
        }
        if (satFat != null && satFat >= 7) {
            watch += Insight(
                "Elevated Saturated Fat (${d1(satFat)}g)",
                "Mostly from ${topSatFat.food.shortName()}. Balance the rest of your day with leaner protein and plant fats."
            )
        }
        if (sodium != null && sodium > 650) {
            watch += Insight(
                "High Sodium (${sodium.roundToInt()}mg)",
                "Well above a typical per-meal amount. Check salted, cured or canned ingredients."
            )
        }
        if (sugar != null && sugar * 4 / t.kcal * 100 > 20) {
            watch += Insight(
                "High Sugar Share (${d1(sugar)}g)",
                "More than 20% of the calories come from sugars; consider swapping a sweet ingredient."
            )
        }
        if (t.protein < 20) {
            watch += Insight(
                "Modest Protein (${d1(t.protein)}g)",
                "Consider adding a lean protein such as chicken breast, fish, eggs or lentils to improve satiety."
            )
        }
        missingNote(f)?.let { watch += it }
        if (watch.isEmpty()) {
            watch += Insight(
                "Seasoning & Sodium Tracking",
                "Values reflect the ingredients as entered; remember to account for added salt or dressings."
            )
        }
        return MealInsights(summary, good, watch, true)
    }

    fun productInsights(f: Facts, novaGroup: Int?, nutriScore: String?, amountLabel: String): MealInsights {
        if (f.kcal == null && f.protein == null && f.carbs == null && f.fat == null) {
            return MealInsights("This product has no nutrition data to analyse.", emptyList(), emptyList(), false)
        }
        val kcal = f.kcal?.takeIf { it > 0.0 }
        val protein = f.protein
        val fiber = f.fiber
        val sugar = f.sugar
        val satFat = f.satFat
        val sodium = f.sodiumMg

        val parts = mutableListOf<String>()
        f.kcal?.let { parts += "${it.roundToInt()} kcal" }
        protein?.let { parts += "${d1(it)}g protein" }
        val summary = if (parts.isEmpty()) "Limited nutrition data for $amountLabel."
        else "Provides ${parts.joinToString(" and ")} for $amountLabel."

        val good = mutableListOf<Insight>()
        if (kcal != null && protein != null && protein / kcal * 100 >= 8 && protein >= 3) {
            val share = (protein * 4 / kcal * 100).roundToInt().coerceAtMost(100)
            good += Insight("High Protein (${d1(protein)}g)", "$share% of the calories come from protein, which supports muscle recovery and satiety.")
        }
        if (fiber != null && fiber >= 4.5) {
            good += Insight("Beneficial Dietary Fiber (${d1(fiber)}g)", "Fiber supports steady digestion and sustained energy.")
        }
        if (kcal != null && satFat != null && satFat * 9 / kcal * 100 <= 6) {
            good += Insight("Low Saturated Fat (${d1(satFat)}g)", "Saturated fat is a small share of the calories.")
        }
        if (kcal != null && sugar != null && sugar * 4 / kcal * 100 <= 8) {
            good += Insight("Low Sugar (${d1(sugar)}g)", "Sugars are a small share of the calories.")
        }
        if (sodium != null && sodium <= 350) {
            good += Insight("Low Sodium (${sodium.roundToInt()}mg)", "Sodium stays low for this amount.")
        }
        if (novaGroup == 1) {
            good += Insight("Minimally Processed (NOVA 1)", "Whole or minimally processed food.")
        }
        if (nutriScore == "A" || nutriScore == "B") {
            good += Insight("Nutri-Score $nutriScore", "Rated favourably by the Nutri-Score system.")
        }

        val watch = mutableListOf<Insight>()
        if (fiber != null && fiber < 5) {
            watch += Insight("Moderate Fiber Content (${d1(fiber)}g)", "Below the ~8g fiber goal. Pair it with vegetables, fruit, oats or legumes.")
        }
        if (satFat != null && satFat >= 7) {
            watch += Insight("Elevated Saturated Fat (${d1(satFat)}g)", "Balance the rest of your day with leaner protein and plant fats.")
        }
        if (sodium != null && sodium > 650) {
            watch += Insight("High Sodium (${sodium.roundToInt()}mg)", "Well above a typical per-meal amount.")
        }
        if (kcal != null && sugar != null && sugar * 4 / kcal * 100 > 20) {
            watch += Insight("High Sugar Share (${d1(sugar)}g)", "More than 20% of the calories come from sugars.")
        }
        if (kcal != null && kcal >= 50 && protein != null && protein / kcal * 100 < 2.5) {
            watch += Insight("Low Protein Density (${d1(protein)}g)", "Few grams of protein for the calories it provides.")
        }
        if (novaGroup == 4) {
            watch += Insight("Ultra-Processed (NOVA 4)", "Industrially formulated food; consider limiting how often you eat it.")
        }
        if (nutriScore == "D" || nutriScore == "E") {
            watch += Insight("Nutri-Score $nutriScore", "Rated unfavourably by the Nutri-Score system.")
        }
        missingNote(f)?.let { watch += it }
        return MealInsights(summary, good, watch, true)
    }

    private fun missingNote(f: Facts): Insight? {
        val names = f.missingNames()
        if (names.isEmpty()) return null
        val list = when (names.size) {
            1 -> names[0]
            2 -> "${names[0]} and ${names[1]}"
            else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
        }
        val verb = if (names.size == 1) "isn't" else "aren't"
        val pronoun = if (names.size == 1) "it isn't" else "they aren't"
        return Insight(
            "Some data not listed",
            "${list.replaceFirstChar { it.uppercase() }} $verb listed, so $pronoun counted in the score.",
            neutral = true
        )
    }

    private fun isOliveOil(f: FoodItem) = f.category == "fats_sweeteners" && f.name.contains("olive", ignoreCase = true)
    private fun isFattyFish(f: FoodItem) = f.category == "fish" && FATTY_FISH.containsMatchIn(f.name)

    private fun d1(v: Double) = String.format(Locale.US, "%.1f", v)
    private fun qty(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else d1(v)
}
