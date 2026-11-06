package com.nutritrack.android.data.model

data class NutrientsDto(
    val calories: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0
)

data class LogEntryDto(
    val id: String?,
    val barcode: String?,
    val name: String?,
    val quantityG: Double = 0.0,
    val computed: NutrientsDto?,
    val loggedAt: String?
)

data class DailyLogResponse(
    val date: String?,
    val totals: NutrientsDto?,
    val entries: List<LogEntryDto>?
)
