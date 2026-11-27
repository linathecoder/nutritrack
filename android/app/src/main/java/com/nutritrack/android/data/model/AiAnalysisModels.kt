package com.nutritrack.android.data.model

/**
 * Data model for Product AI Health Analysis (BR-09).
 * Ready for backend endpoint when implemented.
 */
data class AiHealthAnalysis(
    val productName: String,
    val score: Int, // 0 - 100
    val rating: String, // e.g. "Optimal", "Good", "Moderate", "Poor"
    val explanation: String,
    val pairingSuggestion: String,
    val keyNutrients: List<String> = emptyList(),
    val healthBenefits: List<String> = emptyList()
)
