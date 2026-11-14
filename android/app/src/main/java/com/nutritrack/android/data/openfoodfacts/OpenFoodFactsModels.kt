package com.nutritrack.android.data.openfoodfacts

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/** Raw response of GET /api/v2/product/{barcode}.json (only the fields we ask for). */
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
    // Open Food Facts sometimes sends this as a number and sometimes as a string.
    @SerializedName("serving_quantity") val servingQuantity: JsonElement?,
    @SerializedName("image_front_url") val imageFrontUrl: String?,
    @SerializedName("ingredients_text") val ingredientsText: String?,
    @SerializedName("allergens_tags") val allergensTags: List<String>?,
    @SerializedName("nutriscore_grade") val nutriscoreGrade: String?,
    // Number or string depending on the product.
    @SerializedName("nova_group") val novaGroup: JsonElement?,
    // Kept as raw JSON: values can be numbers, numeric strings or even "" depending on the product.
    val nutriments: JsonObject?
)
