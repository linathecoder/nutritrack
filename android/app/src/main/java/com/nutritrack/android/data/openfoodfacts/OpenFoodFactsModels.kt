package com.nutritrack.android.data.openfoodfacts

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class OffProductResponse(
    val code: String?,
    val status: Int?,
    @SerializedName("status_verbose") val statusVerbose: String?,
    val product: OffProduct?
)

data class OffProduct(
    val code: String?,
    @SerializedName("product_name") val productName: String?,
    val brands: String?,
    val quantity: String?,
    @SerializedName("serving_size") val servingSize: String?,
    @SerializedName("serving_quantity") val servingQuantity: JsonElement?,
    @SerializedName("image_front_url") val imageFrontUrl: String?,
    @SerializedName("ingredients_text") val ingredientsText: String?,
    @SerializedName("allergens_tags") val allergensTags: List<String>?,
    @SerializedName("nutriscore_grade") val nutriscoreGrade: String?,
    @SerializedName("nova_group") val novaGroup: JsonElement?,
    val nutriments: JsonObject?
)
