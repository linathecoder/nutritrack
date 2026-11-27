package com.nutritrack.android.data.model

/** GET /api/logs/history */
data class LogHistoryItemDto(
    val barcode: String? = null,
    val name: String? = null,
    val quantityG: Double = 0.0,
    val calories: Double = 0.0
)

data class LogHistoryDayDto(
    val date: String? = null,
    val totals: NutrientsDto? = null,
    val items: List<LogHistoryItemDto>? = null
)

data class LogHistoryResponse(
    val from: String? = null,
    val to: String? = null,
    val days: List<LogHistoryDayDto>? = null
)

/** GET /api/meals : a meal created with "Create a meal". */
data class SavedMealDto(
    val lastEntryId: String? = null,
    val barcode: String? = null,
    val name: String? = null,
    val ingredients: String? = null,
    val imageUrl: String? = null,
    val per100g: NutrientsDto? = null,
    val quantityG: Double = 0.0,
    val totals: NutrientsDto? = null,
    val lastLoggedAt: String? = null
)
