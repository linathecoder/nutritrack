package com.nutritrack.android.data.model

data class ProfileCalculationRequest(
    val name: String? = null,
    val sex: String,
    val weightKg: Double,
    val heightCm: Double,
    val age: Int,
    val activityLevel: String,
    val workoutFrequency: String,
    val goal: String,
    val targetWeightKg: Double? = null
)

data class ProfileCalculationResponse(
    val userId: String?,
    val profileId: String?,
    val macroTargetId: String?,
    val name: String?,
    val sex: String?,
    val weightKg: Double?,
    val heightCm: Double?,
    val age: Int?,
    val activityLevel: String?,
    val workoutFrequency: String?,
    val goal: String?,
    val bmr: Double?,
    val tdee: Double?,
    val calories: Int?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val targetWeightKg: Double? = null
)

data class WeightRequest(
    val weightKg: Double,
    val date: String? = null
)

data class WeightEntryDto(
    val date: String,
    val weightKg: Double
)

data class WeightHistoryResponse(
    val targetWeightKg: Double? = null,
    val entries: List<WeightEntryDto> = emptyList()
)
