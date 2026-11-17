package com.nutritrack.backend.service;

import com.nutritrack.backend.model.Nutrients;
import org.springframework.stereotype.Component;

/**
 * Deterministic health score calculator (0–100) based on per-100 g nutrient values.
 *
 * <p>Scoring rules (all thresholds are per 100 g unless noted):
 *
 * <pre>
 * CALORIES  (max 20 pts)
 *   <= 100 kcal  → 20 pts
 *   <= 200 kcal  → 15 pts
 *   <= 350 kcal  → 10 pts
 *   <= 500 kcal  →  5 pts
 *   >  500 kcal  →  0 pts
 *
 * PROTEIN  (max 20 pts)
 *   >= 15 g  → 20 pts
 *   >=  8 g  → 15 pts
 *   >=  4 g  → 10 pts
 *   >=  2 g  →  5 pts
 *   <   2 g  →  0 pts
 *
 * FAT  (penalty, max 20 pts)
 *   <= 3 g   → 20 pts (no penalty)
 *   <= 10 g  → 15 pts
 *   <= 17 g  → 10 pts
 *   <= 25 g  →  5 pts
 *   >  25 g  →  0 pts
 *
 * CARBS  (penalty on high net carbs, max 20 pts)
 *   <= 10 g  → 20 pts
 *   <= 25 g  → 15 pts
 *   <= 40 g  → 10 pts
 *   <= 60 g  →  5 pts
 *   >  60 g  →  0 pts
 *
 * PROCESSING PENALTY  (max deduction 20 pts)
 *   Applied when the ingredients string contains known ultra-processing markers
 *   (modified starch, E-numbers, hydrogenated, syrup, flavouring, emulsifier, etc.).
 *   Each distinct marker found deducts 4 pts, capped at −20.
 *
 * RATING THRESHOLDS
 *   80-100  → Excellent
 *   60-79   → Good
 *   40-59   → Fair
 *   0-39    → Poor
 * </pre>
 */
@Component
public class HealthScoreCalculator {

    // ── Ingredient keywords that signal ultra-processing ──────────────────────
    private static final String[] PROCESSING_MARKERS = {
        "modified starch", "hydrogenated", "partially hydrogenated",
        "high fructose", "corn syrup", "glucose syrup", "fructose syrup",
        "artificial flavour", "artificial flavor", "natural flavour", "natural flavor",
        "flavouring", "flavoring",
        "emulsifier", "stabiliser", "stabilizer",
        "preservative", "sodium benzoate", "potassium sorbate",
        "sodium nitrate", "sodium nitrite",
        "acesulfame", "aspartame", "sucralose", "saccharin",
        " e1", " e2", " e3", " e4", " e5",    // catches E1xx E2xx E3xx E4xx E5xx
        "e621", "e951", "e952", "e955",         // msg, sweeteners
        "mono- and diglycerides", "carrageenan", "xanthan gum",
        "sodium caseinate", "maltodextrin", "dextrose"
    };

    public record Result(int score, String rating) {}

    /**
     * Compute a deterministic health score.
     *
     * @param nutrients per-100 g nutrient values (never null)
     * @param ingredients raw ingredients string (may be null or blank)
     * @return {@link Result} with score [0-100] and human-readable rating
     */
    public Result compute(Nutrients nutrients, String ingredients) {
        int score = 0;

        // ── Calories (0–20) ───────────────────────────────────────────────────
        double kcal = nutrients.getCalories();
        if      (kcal <= 100) score += 20;
        else if (kcal <= 200) score += 15;
        else if (kcal <= 350) score += 10;
        else if (kcal <= 500) score +=  5;
        // > 500 → 0

        // ── Protein (0–20) ────────────────────────────────────────────────────
        double protein = nutrients.getProteinG();
        if      (protein >= 15) score += 20;
        else if (protein >=  8) score += 15;
        else if (protein >=  4) score += 10;
        else if (protein >=  2) score +=  5;
        // < 2 → 0

        // ── Fat (0–20, lower is better) ───────────────────────────────────────
        double fat = nutrients.getFatG();
        if      (fat <=  3) score += 20;
        else if (fat <= 10) score += 15;
        else if (fat <= 17) score += 10;
        else if (fat <= 25) score +=  5;
        // > 25 → 0

        // ── Carbohydrates (0–20, lower net carbs is better) ───────────────────
        double carbs = nutrients.getCarbsG();
        if      (carbs <= 10) score += 20;
        else if (carbs <= 25) score += 15;
        else if (carbs <= 40) score += 10;
        else if (carbs <= 60) score +=  5;
        // > 60 → 0

        // ── Processing penalty (0–20 deducted) ───────────────────────────────
        int penalty = computeProcessingPenalty(ingredients);
        score = Math.max(0, score - penalty);

        score = Math.max(0, Math.min(100, score));
        return new Result(score, toRating(score));
    }

    /**
     * Count distinct ultra-processing markers in the ingredients string.
     * Each match deducts 4 points, capped at 20.
     */
    private int computeProcessingPenalty(String ingredients) {
        if (ingredients == null || ingredients.isBlank()) return 0;
        String lower = ingredients.toLowerCase();
        int matches = 0;
        for (String marker : PROCESSING_MARKERS) {
            if (lower.contains(marker)) {
                matches++;
            }
        }
        return Math.min(matches * 4, 20);
    }

    private static String toRating(int score) {
        if (score >= 80) return "Excellent";
        if (score >= 60) return "Good";
        if (score >= 40) return "Fair";
        return "Poor";
    }
}
