package com.nutritrack.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nutritrack.android.data.model.LogEntryDto
import com.nutritrack.android.data.model.NutrientsDto

/** The authenticated user + JWT token (session persisted in Room). */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val token: String,
    val name: String? = null,
    val profileCompleted: Boolean = false,
    val dailyCalories: Int = 0
)


@Entity(tableName = "log_entries")
data class LogEntryEntity(
    @PrimaryKey val id: String,
    val barcode: String?,
    val name: String?,
    val quantityG: Double,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val loggedAt: String?,
    val imageUrl: String?,
    val ingredients: String?
)


@Entity(tableName = "weight_entries")
data class WeightEntity(
    @PrimaryKey val date: String,
    val weightKg: Double
)

fun LogEntryDto.toEntity(): LogEntryEntity? {
    val entryId = id ?: return null
    val n = computed ?: NutrientsDto()
    return LogEntryEntity(
        id = entryId,
        barcode = barcode,
        name = name,
        quantityG = quantityG,
        calories = n.calories,
        proteinG = n.proteinG,
        carbsG = n.carbsG,
        fatG = n.fatG,
        loggedAt = loggedAt,
        imageUrl = imageUrl,
        ingredients = ingredients
    )
}
